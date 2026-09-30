package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One choice of a single-selection set: a square with a dot when its value is
 * the {@link RadioGroup}'s selection, and a label beside it.
 *
 * <pre>{@code
 * RadioGroup<Mode> mode = RadioGroup.state(() -> config.mode, m -> config.mode = m);
 * Radio.builder(mode, Mode.FAST).label(Component.literal("Fast")).build();
 * }</pre>
 *
 * <p>The Radio stores nothing: it asks the group, which asks the consumer, every
 * frame. A click anywhere on it (square or label) selects its value, with
 * vanilla's click sound; Tab focuses it and Enter or Space selects it; the
 * narrator reads its label and whether it is selected.
 *
 * <p>Sizes itself from its label, as {@link Checkbox} does, and wraps it the same
 * way when the panel is narrower.
 *
 * @param <T> the value type of this Radio and its group
 */
public class Radio<T> extends AbstractPanelElement {

    /** Size of the radio square, in pixels. */
    public static final int BOX_SIZE = ElementConstants.BOX_SIZE;

    /** Gap between the square and the label. */
    public static final int LABEL_GAP = ElementConstants.BOX_GAP;

    /** Size of the selection dot inside the square. */
    public static final int INDICATOR_SIZE = 4;

    /** Colour of the selection dot (visible on the inset interior). */
    public static final int INDICATOR_COLOR = 0xFF606060;

    private final RadioGroup<T> group;
    private final T value;
    private final Supplier<Component> label;
    // The vanilla stand-in for sound, focus and narration (ElementWidget), made at
    // the first screen attach, not with the element: a Containers menu builds its
    // panels on the dedicated server too, where no screen class exists.
    private @Nullable ElementWidget widget;

    // The label's wrap width beside the square (the one wrap helper): 0 = one line.
    private int wrapWidth = 0;

    protected Radio(Builder<T> b) {
        super(b);
        this.group = b.group;
        this.value = b.value;
        this.label = b.label;
    }

    /** Starts the Radio for {@code value} in {@code group}. Give it a {@code label}. */
    public static <T> Builder<T> builder(RadioGroup<T> group, T value) {
        return new Builder<>(group, value);
    }

    /** This Radio's value. */
    public T getValue() { return value; }

    // ── Size: from the label ───────────────────────────────────────────

    @Override
    public int naturalWidth() {
        Component text = label.get();
        int w = text != null ? Minecraft.getInstance().font.width(text) : 0;
        return BOX_SIZE + LABEL_GAP + w;
    }

    @Override
    public int getWidth() {
        return wrapWidth > 0 ? BOX_SIZE + LABEL_GAP + wrapWidth : naturalWidth();
    }

    @Override
    public int getHeight() {
        Component text = label.get();
        if (text == null || wrapWidth <= 0) return BOX_SIZE;
        return Math.max(BOX_SIZE, MKText.lineCount(text, wrapWidth) * Minecraft.getInstance().font.lineHeight);
    }

    @Override
    public void layoutWithin(int budget) {
        wrapWidth = MKText.wrapWidth(label.get(), budget - BOX_SIZE - LABEL_GAP);
    }

    @Override
    public int extraLayoutHeight() {
        return Math.max(0, getHeight() - BOX_SIZE);
    }

    @Override
    public void fillWidth(int width) {}

    @Override public boolean isInteractive() { return true; }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        boolean selected = group.isSelected(value);
        boolean hovered = isHovered(ctx);
        Component text = label.get();
        if (widget != null) widget.track(sx, sy, getWidth(), getHeight(), hovered, !disabled, CommonComponents.optionNameValue(
                text != null ? text : Component.empty(), CommonComponents.optionStatus(selected)));
        var g = ctx.graphics();

        PanelRendering.renderPanel(g, sx, sy, BOX_SIZE, BOX_SIZE, disabled ? PanelStyle.DARK : PanelStyle.INSET);
        if (!disabled && (hovered || (widget != null && widget.focused()))) {
            g.fill(sx + 1, sy + 1, sx + BOX_SIZE - 1, sy + BOX_SIZE - 1, ElementConstants.HOVER_OVERLAY);
        }
        if (selected) {
            int ix = sx + (BOX_SIZE - INDICATOR_SIZE) / 2;
            int iy = sy + (BOX_SIZE - INDICATOR_SIZE) / 2;
            g.fill(ix, iy, ix + INDICATOR_SIZE, iy + INDICATOR_SIZE, INDICATOR_COLOR);
        }
        if (text != null) {
            int color = disabled ? ElementConstants.TEXT_DISABLED : ElementConstants.TEXT_DARK;
            int textY = wrapWidth > 0 ? sy : MKText.centeredTextY(sy, sy + BOX_SIZE);
            MKText.drawWrapped(g, text, wrapWidth, sx + BOX_SIZE + LABEL_GAP, textY, 0, color, false);
        }
        if (hovered) queueTooltip(ctx);
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (button != Click.LEFT || disabled(in)) return false;
        ElementWidget.playClickSound();
        group.select(value);
        return true;
    }

    private void press() {
        if (!ownDisabled()) group.select(value);
    }

    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget == null) widget = new ElementWidget(this::press);
        widget.attach(screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget != null) widget.detach(screen);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static class Builder<T> extends AbstractPanelElement.Builder<Radio<T>, Builder<T>> {
        private final RadioGroup<T> group;
        private final T value;
        private @Nullable Supplier<Component> label;

        protected Builder(RadioGroup<T> group, T value) {
            this.group = Objects.requireNonNull(group, "group");
            this.value = value;
        }

        @Override protected Builder<T> self() { return this; }

        /** Required: the label beside the square. */
        public Builder<T> label(Component label) {
            Objects.requireNonNull(label, "label");
            return label(() -> label);
        }

        /** A label read every frame. */
        public Builder<T> label(Supplier<Component> label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        @Override
        public Radio<T> build() {
            require(label != null, "label(...) is required");
            return new Radio<>(this);
        }
    }
}
