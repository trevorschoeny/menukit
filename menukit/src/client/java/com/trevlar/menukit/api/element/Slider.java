package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Focus;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A slider: vanilla's {@link AbstractSliderButton} (§0020), laid out and
 * lensed by MenuKit (§0026, §0066). Vanilla owns the mechanism (drag, arrow keys,
 * narration, sprites, the cursor, the sound); MenuKit owns placement, the lens
 * and the disabled cascade. The vanilla widget is exposed through
 * {@link #widget()}.
 *
 * <h3>Three scales, one lens</h3>
 *
 * <pre>{@code
 * // a fraction, 0 to 1
 * Slider.builder().size(120, 16)
 *         .state(() -> volume, v -> volume = v)
 *         .label(v -> Component.literal(Math.round(v * 100) + "%"))
 *         .build();
 *
 * // whole numbers from 2 to 50 (the integer lens)
 * Slider.ofInts(2, 50).size(180, 16)
 *         .state(config::threshold, config::setThreshold)
 *         .build();                                   // label: the number
 *
 * // an enum's constants in order (the enum lens)
 * Slider.ofEnum(Speed.class).size(120, 16)
 *         .state(config::speed, config::setSpeed)
 *         .label(s -> Component.literal(s.displayName()))
 *         .build();
 * }</pre>
 *
 * {@code state(get, set)} is required. The slider reads {@code get} every frame,
 * so a value changed elsewhere shows at once, and hands {@code set} a new value
 * on each drag step or arrow key; it stores nothing. On an integer or enum scale
 * the handle snaps to the steps, and the arrow keys move one step.
 *
 * <h3>Lifecycle</h3>
 *
 * The vanilla widget is registered with the screen as a widget, not a
 * renderable ({@link #onAttach}), so vanilla's focus, keyboard and narration
 * reach it while MenuKit draws it after the panel background. Known limit,
 * shared with {@link TextField}: a slider in a panel hidden mid-screen stays
 * registered and can keep keyboard focus.
 *
 * @param <V> the value type: {@code Double} (a fraction), {@code Integer}, or an enum
 */
public class Slider<V> extends AbstractPanelElement {

    /**
     * Where a value sits on the track, as a fraction, and back. {@code steps} is
     * the number of distinct values (0 for a continuous scale).
     */
    record Scale<V>(Function<V, Double> toFraction, Function<Double, V> fromFraction, int steps,
                    Function<V, Component> defaultLabel) {

        /** The continuous scale, 0 to 1, clamped. */
        static Scale<Double> fraction() {
            return new Scale<>(v -> clamp01(v), f -> clamp01(f), 0, v -> Component.empty());
        }

        /** Whole numbers from {@code min} to {@code max} inclusive. */
        static Scale<Integer> ints(int min, int max) {
            if (max <= min) throw new IllegalArgumentException("Slider.ofInts: max must be above min, got " + min + ".." + max);
            int span = max - min;
            return new Scale<>(v -> clamp01((Math.max(min, Math.min(max, v)) - min) / (double) span),
                    f -> min + (int) Math.round(clamp01(f) * span), span + 1,
                    v -> Component.literal(String.valueOf(v)));
        }

        /** The given values in order, evenly spaced along the track. */
        static <E> Scale<E> of(List<E> values) {
            if (values.size() < 2) throw new IllegalArgumentException("Slider: a list scale needs at least two values");
            List<E> copy = List.copyOf(values);
            int last = copy.size() - 1;
            return new Scale<>(v -> {
                        int i = copy.indexOf(v);
                        return i < 0 ? 0.0 : i / (double) last;
                    },
                    f -> copy.get((int) Math.round(clamp01(f) * last)), copy.size(),
                    v -> Component.literal(String.valueOf(v)));
        }

        /** The value one step from {@code fraction} in direction {@code dir} (-1 or +1), as a fraction. */
        double step(double fraction, int dir) {
            if (steps <= 1) return fraction;
            int last = steps - 1;
            int index = (int) Math.round(clamp01(fraction) * last);
            return Math.max(0, Math.min(last, index + dir)) / (double) last;
        }
    }

    private final Scale<V> scale;
    private final Supplier<V> stateGet;
    private final Function<V, Component> label;
    private final MKSlider slider;
    private @Nullable Screen attachedScreen;

    protected Slider(Builder<V> b) {
        super(b);
        this.scale = b.scale;
        this.stateGet = b.stateGet;
        this.label = b.label != null ? b.label : b.scale.defaultLabel();
        double initial = scale.toFraction().apply(stateGet.get());
        Consumer<V> set = b.stateSet;
        this.slider = new MKSlider(width, height, label.apply(stateGet.get()), initial,
                f -> set.accept(scale.fromFraction().apply(f)),
                f -> label.apply(scale.fromFraction().apply(f)), scale);
    }

    /** A slider over a fraction, 0 to 1. */
    public static Builder<Double> builder() {
        return new Builder<>(Scale.fraction());
    }

    /** A slider over the whole numbers {@code min} to {@code max} inclusive (the integer lens). */
    public static Builder<Integer> ofInts(int min, int max) {
        return new Builder<>(Scale.ints(min, max));
    }

    /** A slider over an enum's constants, in declaration order (the enum lens). */
    public static <E extends Enum<E>> Builder<E> ofEnum(Class<E> type) {
        return new Builder<>(Scale.of(List.of(type.getEnumConstants())));
    }

    private static double clamp01(double d) {
        return d < 0.0 ? 0.0 : Math.min(d, 1.0);
    }

    /** The vanilla widget this element draws and registers (§0020): for narration or focus checks. */
    public AbstractSliderButton widget() {
        return slider;
    }

    // ── PanelElement ───────────────────────────────────────────────────

    @Override public boolean isInteractive() { return true; }

    @Override
    public void render(RenderContext ctx) {
        // The lens, every frame: the widget shows what the consumer holds.
        slider.syncFromSupplier(scale.toFraction().apply(stateGet.get()));
        // Disabled (own or cascaded): vanilla's inactive widget draws greyed and
        // ignores drag, keys and wheel on its own.
        slider.active = !disabled(ctx);
        slider.setX(ctx.originX() + childX);
        slider.setY(ctx.originY() + childY);
        slider.setWidth(width);
        // Drawn here, after the panel background (it is a widget, not a renderable).
        slider.extractRenderState(ctx.graphics(), ctx.hasMouseInput() ? ctx.mouseX() : -1,
                ctx.hasMouseInput() ? ctx.mouseY() : -1, 0f);
        queueTooltip(ctx);
    }

    @Override
    public void onAttach(Screen screen) {
        if (attachedScreen == screen) return;
        if (attachedScreen != null) onDetach(attachedScreen);
        attachedScreen = screen;
        Focus.addWidget(screen, slider);
    }

    @Override
    public void onDetach(Screen screen) {
        if (attachedScreen != screen) return;
        Focus.removeWidget(screen, slider);
        attachedScreen = null;
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static class Builder<V> extends AbstractPanelElement.Builder<Slider<V>, Builder<V>> {
        private final Scale<V> scale;
        private @Nullable Supplier<V> stateGet;
        private @Nullable Consumer<V> stateSet;
        private @Nullable Function<V, Component> label;

        Builder(Scale<V> scale) {
            this.scale = scale;
        }

        @Override protected Builder<V> self() { return this; }

        /** Required: size in pixels. Vanilla's slider is 20 tall. */
        @Override
        public Builder<V> size(int width, int height) {
            return super.size(width, height);
        }

        /**
         * Required: the lens. {@code get} is read every frame; {@code set} receives
         * the new value on each drag step or arrow key.
         */
        public Builder<V> state(Supplier<V> get, Consumer<V> set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        /**
         * The text inside the track for a value; the narrator reads it too.
         * Default: empty for a fraction, the value for an integer or enum scale.
         */
        public Builder<V> label(Function<V, Component> label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        @Override
        public Slider<V> build() {
            require(width > 0 && height > 0, "size(w, h) is required");
            require(stateGet != null, "state(get, set) is required; a Slider shows the consumer's value");
            return new Slider<>(this);
        }
    }

    // ── The vanilla widget ─────────────────────────────────────────────

    /**
     * Vanilla's slider wired to the lens: {@code applyValue} (a player's change)
     * writes through; {@code syncFromSupplier} (the consumer's value) updates the
     * handle without writing back. A subclass, not a mixin, so only MenuKit's
     * sliders are affected.
     */
    private static final class MKSlider extends AbstractSliderButton {

        private final Consumer<Double> write;
        private final Function<Double, Component> text;
        private final Scale<?> scale;

        MKSlider(int width, int height, Component message, double initial,
                 Consumer<Double> write, Function<Double, Component> text, Scale<?> scale) {
            super(0, 0, width, height, message, initial);
            this.write = write;
            this.text = text;
            this.scale = scale;
        }

        /** The consumer's value, shown without being written back. */
        void syncFromSupplier(double fraction) {
            if (fraction != this.value) {
                this.value = fraction;
                updateMessage();
            }
        }

        /** On a stepped scale the arrow keys move one step (vanilla's per-pixel step could stay on one value). */
        @Override
        public boolean keyPressed(KeyEvent event) {
            if (scale.steps() > 1 && canChangeValue && (event.isLeft() || event.isRight())) {
                setValue(scale.step(value, event.isLeft() ? -1 : 1));
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        protected void applyValue() {
            write.accept(this.value);
        }

        @Override
        protected void updateMessage() {
            setMessage(text.apply(this.value));
        }
    }
}
