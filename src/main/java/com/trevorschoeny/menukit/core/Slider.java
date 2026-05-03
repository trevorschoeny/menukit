package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.mixin.ScreenAccessor;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Continuous-value slider control. Phase 14d-4 — wraps vanilla
 * {@link AbstractSliderButton} via composition, per the
 * <i>follow-vanilla-when-wrapping</i> discipline. ~150 LOC of vanilla-
 * tested mechanism (drag, keyboard navigation, narration, sprite states,
 * value clamping, cursor changes, sound on release) inherited cleanly.
 * Library owns layout integration + lifecycle; vanilla owns the slider
 * mechanism.
 *
 * <h3>Lens pattern (Principle 8) — Supplier+Consumer</h3>
 *
 * Consumer holds the value as a normalized double in [0, 1]. The slider
 * reads via {@link DoubleSupplier} per frame to stay in sync with consumer
 * state (programmatic external updates, settings sync, etc.) and writes
 * via {@link DoubleConsumer} on user input (drag, keyboard step). Vanilla's
 * {@code setValue} clamp + change-guard makes per-frame supplier-pull
 * idempotent — no spurious onChange fires when supplier returns the
 * already-stored value.
 *
 * <p>No imperative {@code setValue(double)} escape hatch (unlike
 * {@code TextField}'s Consumer-only-plus-setValue shape) — consumer-as-
 * source-of-truth means there's no "library holds state, consumer pushes
 * in" gap to fill. For programmatic resets, consumers just write to their
 * own state; the slider auto-syncs via supplier-pull on the next frame.
 *
 * <p>Map to consumer's domain externally — internal value is always [0, 1]:
 * <pre>{@code
 * // 30-110 FOV range:
 * .value(() -> (fov - 30) / 80.0, v -> fov = (int)(30 + v * 80))
 *
 * // 0-100 percent:
 * .value(() -> percent / 100.0, v -> percent = (int)(v * 100))
 * }</pre>
 *
 * <h3>In-track label — `.label(DoubleFunction&lt;Component&gt;)`</h3>
 *
 * Vanilla bakes label rendering into the slider track via
 * {@code getMessage()} / {@code updateMessage()}; the displayed text
 * updates whenever {@code updateMessage()} is called. {@link Builder#label}
 * exposes this — consumer-supplied function called on every value change
 * to compute the displayed text. Default: empty.
 *
 * <p>Narration auto-derives from the same source — vanilla reads
 * {@code "gui.narrate.slider"} translated with the current message, so
 * screen readers announce "Slider: Volume: 50%" without consumer effort.
 * No separate narration-label override exposed (vanilla doesn't expose
 * one either, and following vanilla keeps the wrap thin).
 *
 * <h3>Lifecycle</h3>
 *
 * Reuses 14d-3's {@link PanelElement#onAttach} / {@link PanelElement#onDetach}
 * hooks. The wrapped slider is registered with the host screen via
 * {@code addWidget} (input-dispatch only, NOT renderables) so vanilla's
 * screen widget pipeline routes keyboard / focus / narration to it. The
 * slider renders manually in {@link #render} after panel backgrounds —
 * sidesteps the renderables-list "panel background covers widget" trap
 * documented in 14d-3 / {@link ScreenAccessor}.
 *
 * <h3>Cross-context applicability</h3>
 *
 * <ul>
 *   <li><b>MenuContext:</b> yes — settings panels, brightness/opacity controls.</li>
 *   <li><b>StandaloneContext:</b> yes — MenuKit-native screens.</li>
 *   <li><b>SlotGroupContext:</b> no — slot-group anchors are for slot decorations.</li>
 *   <li><b>HudContext:</b> no — HUDs are render-only (no input dispatch).</li>
 * </ul>
 *
 * <h3>Visibility-driven lifecycle gotcha (Q7 deferred per
 * {@code DEFERRED.md} 14d-3 — inherited)</h3>
 *
 * Same shape as TextField: v1 fires onAttach at screen init only
 * (regardless of panel visibility), onDetach at screen close. Mid-screen
 * visibility changes don't re-attach. Mild gotcha for slider since drag
 * binds to mouse-up which fires regardless of focus, but the keyboard-
 * edit-mode flag could end up stale if the panel is hidden mid-edit.
 * Recommended consumer pattern: blur via {@code screen.setFocused(null)}
 * before hiding a panel containing an active slider.
 *
 * <h3>Modal-with-slider (Q4 deferred per {@code DEFERRED.md} 14d-3 —
 * inherited)</h3>
 *
 * Inside a {@code tracksAsModal} panel, M9's keyboard mixin eats keystrokes
 * (except Escape) before vanilla's pipeline routes them to the focused
 * widget — keyboard arrow stepping doesn't work. Mouse drag still works
 * (M9 dispatches clicks to the modal's elements). Same fold-on-evidence
 * trigger as TextField's modal case; defer until concrete consumer
 * surfaces the need.
 */
public class Slider implements PanelElement {

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final DoubleSupplier valueSupplier;
    private final MenuKitSlider slider;

    /** Track which screen we're attached to so detach knows what to remove from. */
    private @Nullable Screen attachedScreen;

    private Slider(Builder b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.width = b.width;
        this.height = b.height;
        this.valueSupplier = b.valueSupplier;

        // Pull initial value from consumer state via supplier; clamp to
        // the [0, 1] contract before passing to vanilla.
        double initialValue = clamp01(valueSupplier.getAsDouble());
        this.slider = new MenuKitSlider(0, 0, width, height,
                b.labelFn.apply(initialValue), initialValue,
                b.valueConsumer, b.labelFn);
    }

    private static double clamp01(double d) {
        if (d < 0.0) return 0.0;
        if (d > 1.0) return 1.0;
        return d;
    }

    // ── PanelElement protocol ──────────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void render(RenderContext ctx) {
        // Per-frame supplier pull — keeps the wrapped slider in sync with
        // consumer state (programmatic resets, settings syncs). Clamped
        // for display robustness; supplier contract is "return [0, 1]"
        // but defensive clamp avoids visual oddities if consumer state
        // drifts out of range.
        double supplied = clamp01(valueSupplier.getAsDouble());
        slider.syncFromSupplier(supplied);

        // Update wrapped slider screen-space coords to match the panel's
        // current content origin + this element's panel-local position.
        int screenX = ctx.originX() + childX;
        int screenY = ctx.originY() + childY;
        slider.setX(screenX);
        slider.setY(screenY);

        // Render manually here so it draws AFTER the panel background
        // (which renders between super.render and this point). The slider
        // is registered with the screen via Screen.addWidget (children +
        // narratables only — NOT renderables) so vanilla's input dispatch
        // / focus / keyboard / narration still reach it. Same pattern as
        // TextField — see ScreenAccessor mixin.
        if (ctx.hasMouseInput()) {
            slider.render(ctx.graphics(), ctx.mouseX(), ctx.mouseY(), 0f);
        } else {
            // HudContext or other input-less render path — render with
            // sentinel mouse coords so the slider's hover state stays false.
            slider.render(ctx.graphics(), -1, -1, 0f);
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onAttach(Screen screen) {
        if (attachedScreen == screen) return;
        attachedScreen = screen;
        ((ScreenAccessor) screen).menuKit$addWidget(slider);
    }

    @Override
    public void onDetach(Screen screen) {
        if (attachedScreen == screen) {
            ((ScreenAccessor) screen).menuKit$removeWidget(slider);
            attachedScreen = null;
        }
    }

    // ── Imperative API ─────────────────────────────────────────────────

    /**
     * Returns the slider's current internal value in [0, 1]. Snapshot —
     * re-read for latest. Canonical pattern is to track value via your own
     * consumer state (the lens-write side); getValue is the read-side
     * counterpart for direct access when needed.
     */
    public double getValue() {
        return slider.getValueAccess();
    }

    /** Returns whether the wrapped slider is currently focused. */
    public boolean isFocused() {
        return slider.isFocused();
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int childX = 0;
        private int childY = 0;
        private int width = -1;
        private int height = -1;
        private @Nullable DoubleSupplier valueSupplier;
        private @Nullable DoubleConsumer valueConsumer;
        private DoubleFunction<Component> labelFn = v -> Component.empty();

        private Builder() {}

        /** Panel-local position. Default (0, 0). */
        public Builder at(int childX, int childY) {
            this.childX = childX;
            this.childY = childY;
            return this;
        }

        /** Required: width × height in pixels. Vanilla's DEFAULT_HEIGHT is 20. */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Required: lens pair for the slider's normalized [0, 1] value.
         * Library reads supplier each frame to sync the slider's display;
         * library calls consumer on user input (drag, keyboard step).
         *
         * <p>Map to consumer's domain externally — internal value is
         * always [0, 1].
         */
        public Builder value(DoubleSupplier supplier, DoubleConsumer consumer) {
            this.valueSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
            this.valueConsumer = Objects.requireNonNull(consumer, "consumer must not be null");
            return this;
        }

        /**
         * Optional in-track label function — called on every value change
         * to compute the displayed text rendered inside the slider track.
         * Default: {@code v -> Component.empty()} (no in-track label).
         *
         * <p>Vanilla bakes label rendering into the slider track via
         * {@code getMessage()} / {@code updateMessage()}; this builder
         * exposes that pattern. Narration auto-derives from the label
         * output, so screen readers announce the live value.
         */
        public Builder label(DoubleFunction<Component> labelFn) {
            this.labelFn = Objects.requireNonNull(labelFn, "labelFn must not be null");
            return this;
        }

        public Slider build() {
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException(
                        "Slider.Builder: .size(w, h) must be called with positive values; "
                        + "got width=" + width + ", height=" + height);
            }
            if (valueSupplier == null || valueConsumer == null) {
                throw new IllegalStateException(
                        "Slider.Builder: .value(supplier, consumer) is required");
            }
            return new Slider(this);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // MenuKitSlider — AbstractSliderButton subclass wiring vanilla's
    // abstract methods to the lens callbacks
    // ──────────────────────────────────────────────────────────────────

    /**
     * AbstractSliderButton subclass that wires vanilla's abstract methods
     * to the lens callbacks: {@code applyValue()} fires the consumer;
     * {@code updateMessage()} computes the in-track label via the
     * builder's labelFn.
     *
     * <p>Also exposes a {@link #syncFromSupplier} path: directly updates
     * the internal value field (bypassing setValue's applyValue trigger)
     * when the per-frame supplier pull returns a value that differs from
     * the stored one. Without this, supplier-pull → setValue(d) →
     * applyValue() → consumer.accept(d) would create a no-op write-back
     * loop on every external state update (consumer state was already d;
     * just got told to accept(d) again). The bypass keeps internal/external
     * sync frictionless without spurious onChange fires.
     *
     * <p>Per Q3 advisor verdict (round 1 sign-off): subclass over mixin —
     * subclass is per-element scoped (only affects MenuKit's wrapped
     * sliders), mixin would affect ALL AbstractSliderButton instances
     * ecosystem-wide. Same precedent as TextField's MenuKitEditBox.
     */
    private static final class MenuKitSlider extends AbstractSliderButton {

        private final DoubleConsumer valueConsumer;
        private final DoubleFunction<Component> labelFn;

        MenuKitSlider(int x, int y, int width, int height,
                      Component initialMessage, double initialValue,
                      DoubleConsumer valueConsumer,
                      DoubleFunction<Component> labelFn) {
            super(x, y, width, height, initialMessage, initialValue);
            this.valueConsumer = valueConsumer;
            this.labelFn = labelFn;
        }

        /**
         * Sync the internal value from a supplier-pulled value, bypassing
         * applyValue (so the consumer doesn't get told to accept the value
         * it just supplied). Only updates if values differ to avoid
         * redundant updateMessage calls per frame.
         *
         * <p>Direct field access on {@code this.value} (protected on
         * AbstractSliderButton) — sidesteps vanilla's {@code setValue}
         * which calls {@code applyValue} on changed values.
         */
        void syncFromSupplier(double supplied) {
            if (supplied != this.value) {
                this.value = supplied;
                updateMessage();
            }
        }

        /** Read-access to the protected value field for {@link Slider#getValue}. */
        double getValueAccess() {
            return this.value;
        }

        @Override
        protected void applyValue() {
            // Fired when user input (drag, keyboard) changes the value
            // via vanilla's setValue path. Push to consumer's lens-write
            // callback. (Not fired by our syncFromSupplier path — that
            // bypasses by design.)
            valueConsumer.accept(this.value);
        }

        @Override
        protected void updateMessage() {
            // Fired after every value change (user input via vanilla's
            // setValue OR our supplier sync via syncFromSupplier) to
            // refresh the in-track display text. Reads label fn against
            // the current value and pushes through AbstractWidget.setMessage.
            this.setMessage(labelFn.apply(this.value));
        }
    }
}
