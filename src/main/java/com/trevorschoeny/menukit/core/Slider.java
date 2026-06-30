package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.mixin.ScreenAccessor;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import com.trevorschoeny.menukit.core.layout.ElementSpec;

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
public class Slider extends AbstractPanelElement<Slider> {

    @Override protected Slider self() { return this; }

    // Non-final since Pass 3: column-fill (fillWidth) restretches the slider
    // to the column's widest extent. Mutating it also re-widths the wrapped
    // vanilla MKSlider so its render + internal hit-test agree with getWidth().
    private int width;
    private final int height;
    private final DoubleSupplier valueSupplier;
    private final MKSlider slider;

    /**
     * Optional disabled predicate (Phase 3b — Item 8). When it returns true,
     * the slider renders greyed (vanilla {@code active = false} → the disabled
     * sprite + gray text) and ignores all interaction (drag, keyboard, scroll)
     * because the wrapped widget's {@code active} flag gates vanilla's own
     * input handling. Per-frame predicate shape, matching Button/Toggle's
     * {@code disabledWhen}. Null = always enabled.
     */
    private final @Nullable BooleanSupplier disabledWhen;

    /** Track which screen we're attached to so detach knows what to remove from. */
    private @Nullable Screen attachedScreen;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

    private Slider(Builder b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.width = b.width;
        this.height = b.height;
        this.valueSupplier = b.valueSupplier;
        this.disabledWhen = b.disabledWhen;

        // Pull initial value from consumer state via supplier; clamp to
        // the [0, 1] contract before passing to vanilla.
        double initialValue = clamp01(valueSupplier.getAsDouble());
        this.slider = new MKSlider(0, 0, width, height,
                b.labelFn.apply(initialValue), initialValue,
                b.valueConsumer, b.labelFn);
    }

    private static double clamp01(double d) {
        if (d < 0.0) return 0.0;
        if (d > 1.0) return 1.0;
        return d;
    }

    // ── PanelElement protocol ──────────────────────────────────────────

    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    // Authored width for the reactive cap (Verification-4) — see Button.
    private int authoredWidth = Integer.MIN_VALUE;
    private int authoredW() {
        if (authoredWidth == Integer.MIN_VALUE) authoredWidth = width;
        return authoredWidth;
    }

    /**
     * Column-fill (Pass 3): stretch this slider to the column's widest extent.
     * Re-widths BOTH this element's reported width AND the wrapped vanilla
     * {@link MKSlider} (whose own render + drag hit-test key off its width), so
     * the full filled track is draggable — no dead strip on the right.
     */
    @Override
    public void fillWidth(int width) {
        this.authoredWidth = width;
        this.width = width;
        this.slider.setWidth(width);
    }

    /** Natural (authored) track width before any panel constraint. */
    @Override public int naturalWidth() { return authoredW(); }

    /** Cap the track to the panel's budget so it never bleeds; reversible. */
    @Override
    public void layoutWithin(int budget) {
        int w = Math.min(authoredW(), budget);
        this.width = w;
        this.slider.setWidth(w);
    }

    /** Interactive — handles click/drag, so it claims (blocks vanilla behind) on a non-opaque panel. */
    @Override public boolean isInteractive() { return true; }

    @Override
    public void render(RenderContext ctx) {
        // Per-frame supplier pull — keeps the wrapped slider in sync with
        // consumer state (programmatic resets, settings syncs). Clamped
        // for display robustness; supplier contract is "return [0, 1]"
        // but defensive clamp avoids visual oddities if consumer state
        // drifts out of range.
        double supplied = clamp01(valueSupplier.getAsDouble());
        slider.syncFromSupplier(supplied);

        // Disabled-state sync (Phase 3b — Item 8). Drive vanilla's `active`
        // flag from the predicate each frame: active=false makes vanilla
        // render the disabled sprite + gray text AND skip its own input
        // handling (mouseClicked/keyPressed/drag are all gated on active in
        // AbstractWidget/AbstractSliderButton), so a disabled slider is both
        // greyed and inert with no extra plumbing on our side.
        slider.active = !isDisabled();

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

        // Tooltip — fires over the slider track bounds. Skipped if the user
        // is actively dragging (slider.isHoveredOrFocused captures drag focus
        // too; combining ctx.isHovered() with that wouldn't change behavior
        // since drag implies hover). Queued for end-of-frame flush.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (tooltipSupplier != null && ctx.hasMouseInput() && isHovered(ctx)) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(ctx.graphics(),
                        ttText, ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip return Slider for free via the SELF self-type.
    // Position + size are configured on the Builder (.at()/.size()).

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onAttach(Screen screen) {
        if (attachedScreen == screen) return;
        attachedScreen = screen;
        ((ScreenAccessor) screen).mk$addWidget(slider);
    }

    @Override
    public void onDetach(Screen screen) {
        if (attachedScreen == screen) {
            ((ScreenAccessor) screen).mk$removeWidget(slider);
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

    /** Returns whether the slider is currently disabled (Phase 3b — Item 8). */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
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
        private @Nullable BooleanSupplier disabledWhen;

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

        /**
         * Optional disabled predicate (Phase 3b — Item 8). When it returns
         * true, the slider renders greyed and ignores all interaction (drag,
         * keyboard, scroll). Per-frame predicate shape, matching
         * Button/Toggle's {@code disabledWhen}. Default: always enabled.
         */
        public Builder disabledWhen(BooleanSupplier disabledWhen) {
            this.disabledWhen = Objects.requireNonNull(disabledWhen, "disabledWhen must not be null");
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

        /**
         * Layout terminal (Phase 3b — Item 6). Returns an {@link ElementSpec}
         * for use in {@link com.trevorschoeny.menukit.core.layout.Row} /
         * {@link com.trevorschoeny.menukit.core.layout.Column}, instead of
         * building the Slider at a fixed position. The spec's reported
         * dimensions are the configured {@code .size(w, h)}; the layout helper
         * calls {@link ElementSpec#at(int, int)}, which re-runs this builder's
         * configuration positioned at the computed coordinates.
         *
         * <p>Validates the same required fields as {@link #build()} up front,
         * so a misconfigured builder fails at {@code spec()} call time rather
         * than later inside the layout helper.
         */
        public ElementSpec spec() {
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException(
                        "Slider.Builder.spec(): .size(w, h) must be called with positive values; "
                        + "got width=" + width + ", height=" + height);
            }
            if (valueSupplier == null || valueConsumer == null) {
                throw new IllegalStateException(
                        "Slider.Builder.spec(): .value(supplier, consumer) is required");
            }
            // Capture config into a final snapshot so each at(x,y) builds a
            // fresh, correctly-positioned Slider (childX/childY are fixed at
            // construction per THESIS Principle 4 — ElementSpec is the
            // deferred-construction path that supplies them).
            final int w = width, h = height;
            final DoubleSupplier vs = valueSupplier;
            final DoubleConsumer vc = valueConsumer;
            final DoubleFunction<Component> lf = labelFn;
            final BooleanSupplier dw = disabledWhen;
            return new ElementSpec() {
                @Override public int width()  { return w; }
                @Override public int height() { return h; }
                @Override public PanelElement at(int x, int y) {
                    Builder b = Slider.builder().at(x, y).size(w, h)
                            .value(vs, vc).label(lf);
                    if (dw != null) b.disabledWhen(dw);
                    return b.build();
                }
            };
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // MKSlider — AbstractSliderButton subclass wiring vanilla's
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
     * ecosystem-wide. Same precedent as TextField's MKEditBox.
     */
    private static final class MKSlider extends AbstractSliderButton {

        private final DoubleConsumer valueConsumer;
        private final DoubleFunction<Component> labelFn;

        MKSlider(int x, int y, int width, int height,
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
