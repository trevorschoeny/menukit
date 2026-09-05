package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A two-state on/off control. The general primitive for boolean setting
 * interactions. Clicking flips the state and fires a callback with the new
 * boolean value.
 *
 * <p>Renders using MenuKit's existing {@link PanelStyle} vocabulary rather
 * than a custom toggle sprite:
 * <ul>
 *   <li><b>Off:</b> {@link PanelStyle#RAISED} background</li>
 *   <li><b>On:</b> {@link PanelStyle#INSET} background (visually "pressed in")</li>
 *   <li><b>Hover (either state):</b> translucent white highlight overlay</li>
 *   <li><b>Disabled:</b> {@link PanelStyle#DARK} background; no hover highlight;
 *   clicks are ignored</li>
 * </ul>
 *
 * <p>For a sprite-backed toggle, see
 * {@link #sprite(int, int, int, int, boolean, Consumer, Identifier)} — the
 * on state renders the same sprite through MenuKit's HSL-lightness
 * inversion pipeline, so consumers don't author two textures.
 *
 * <p>Toggle supports an optional {@link #label(Component) label}. A labeled Toggle
 * renders as a <b>bar that shows its label</b> (raised = off, inset = on), auto-sized
 * to fit the text — the label sits on the toggle body, so it is unmistakably the
 * toggle's own, not text beside it. An unlabeled Toggle is the bare switch. For the
 * conventional settings-checkbox visual (square + check-mark) instead, use
 * {@code Checkbox}.
 *
 * <h3>Mutable-state exception to the declared-structure discipline</h3>
 *
 * MenuKit's declared-structure discipline says structure is frozen at
 * construction and visibility is the only mutable dimension. Toggle is one
 * of a narrow set of elements (alongside {@code Checkbox} and {@code Radio})
 * that owns mutable boolean state as a second mutable dimension.
 *
 * <p>The exception is legitimate because state changes do not affect
 * structural shape — flipping a toggle does not add or remove elements,
 * does not alter layout, and does not mutate the panel's element list.
 * The only things that change are the internal boolean and the subsequent
 * render pass. No downstream structural consequence.
 *
 * <p>Scope of the exception: Toggle, Checkbox, Radio. Does not extend to
 * other elements. For state that lives outside the element (config files,
 * block entities, player attachments), use the Phase 9 state-linked variant
 * (see {@code Toggle.linked(...)}) which reads from a consumer-owned
 * {@link BooleanSupplier} instead of owning state internally.
 *
 * @see PanelElement The interface this implements
 * @see Button       Non-toggling interactive primitive
 */
public class Toggle extends AbstractPanelElement<Toggle> {

    @Override protected Toggle self() { return this; }

    /** Horizontal inset for an on-body label inside the toggle bar. */
    public static final int LABEL_PAD = 6;
    /** Vertical inset (top == bottom) for a WRAPPED multi-line on-body label —
     *  the breathing room above the first line and below the last so the
     *  wrapped text doesn't kiss the bar's RAISED/INSET border. */
    public static final int LABEL_VPAD = 4;
    /** On-body label color — readable on the RAISED/INSET bar (matches Button text). */
    public static final int LABEL_COLOR = 0xFFFFFFFF;
    /** Muted on-body label color when disabled. */
    public static final int LABEL_DISABLED_COLOR = 0xFF808080;

    private int width;
    private int height;
    private final Consumer<Boolean> onToggle;
    private final @Nullable BooleanSupplier disabledWhen;

    // Optional label drawn centered on the bar (like a Button's text). Null =
    // unlabeled (the original switch-only Toggle). Set via the chainable label(...)
    // — the element auto-widens to fit the label and the whole bounds toggles.
    private @Nullable Supplier<Component> labelSupplier = null;

    // Mutable state — the one narrow exception to the declared-structure
    // discipline, documented in the class javadoc above.
    private boolean state;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

    // Render-frame state — hover updated each render, read by mouseClicked.
    private boolean hovered = false;

    /**
     * Creates an always-enabled Toggle.
     *
     * @param childX       X position within panel content area
     * @param childY       Y position within panel content area
     * @param width        width in pixels
     * @param height       height in pixels
     * @param initialState starting boolean state
     * @param onToggle     fired on each state change with the new state
     */
    public Toggle(int childX, int childY, int width, int height,
                  boolean initialState,
                  Consumer<Boolean> onToggle) {
        this(childX, childY, width, height, initialState, onToggle, null);
    }

    /**
     * Creates a Toggle with a disabled-state predicate. When the predicate
     * returns true, the toggle renders with a dark background and ignores
     * clicks.
     *
     * @param childX       X position within panel content area
     * @param childY       Y position within panel content area
     * @param width        width in pixels
     * @param height       height in pixels
     * @param initialState starting boolean state
     * @param onToggle     fired on each state change with the new state
     * @param disabledWhen returns true when the toggle should be disabled,
     *                     or null for always enabled
     */
    public Toggle(int childX, int childY, int width, int height,
                  boolean initialState,
                  Consumer<Boolean> onToggle,
                  @Nullable BooleanSupplier disabledWhen) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.state = initialState;
        this.onToggle = onToggle;
        this.disabledWhen = disabledWhen;
    }

    // ── M8 Layout Spec ─────────────────────────────────────────────────

    /**
     * Returns an {@link com.trevorschoeny.menukit.core.layout.ElementSpec}
     * for use in {@link com.trevorschoeny.menukit.core.layout.Row} or
     * {@link com.trevorschoeny.menukit.core.layout.Column} layouts.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            int width, int height, boolean initialState, Consumer<Boolean> onToggle) {
        return spec(width, height, initialState, onToggle, null);
    }

    /** Layout spec with optional disabled-predicate. */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            int width, int height, boolean initialState,
            Consumer<Boolean> onToggle, @Nullable BooleanSupplier disabledWhen) {
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return width; }
            @Override public int height() { return height; }
            @Override public PanelElement at(int x, int y) {
                return new Toggle(x, y, width, height, initialState, onToggle, disabledWhen);
            }
        };
    }

    // ── PanelElement Implementation ────────────────────────────────────

    // Panel-assigned width cap (Verification-4). A labeled bar caps to this so
    // it never bleeds past the panel edge; MAX_VALUE = uncapped. A bare switch
    // ignores it (intrinsic). Reversible — re-set each layout pass.
    private int widthCap = Integer.MAX_VALUE;

    // ── Reactive label wrap ────────────────────────────────────────────
    // Wrapped line count for the LABELED bar's on-body label at the capped
    // inner width. 0/1 = the label fits on a single line → legacy intrinsic
    // height + the existing centered-scroll render path. >1 = the label
    // wrapped: getHeight() grows the bar to fit the lines and render() draws
    // the FormattedCharSequence lines centered at successive lineHeight
    // offsets, exactly mirroring TextLabel's font.split(...) wrap mechanism.
    //
    // Mutable + recomputed every layoutWithin pass (like widthCap), so wrap
    // is fully REVERSIBLE: a later wider budget that fits the label on one
    // line resets this back to single-line and the bar shrinks to its
    // authored height. A bare/sprite switch never touches this (its
    // layoutWithin is a no-op), so it stays intrinsic.
    private int wrappedLineCount = 1;

    @Override
    public int getWidth() {
        if (labelSupplier == null) return width; // bare switch — intrinsic
        Component label = labelSupplier.get();
        if (label == null) return width;
        // Labeled = a bar auto-sized to the text (min the passed switch width),
        // then capped to the panel's budget (the label scrolls inside if it
        // can't fit the capped bar).
        int natural = Math.max(width, Minecraft.getInstance().font.width(label) + 2 * LABEL_PAD);
        return Math.min(natural, widthCap);
    }

    @Override
    public int getHeight() {
        // Single-line (the common case + every bare/sprite switch): the
        // authored height, untouched.
        if (wrappedLineCount <= 1) return height;
        // Wrapped: grow the bar to fit the stacked lines plus top/bottom
        // breathing room. Math.max guards the floor — a wrapped label NEVER
        // shrinks the bar below its authored height, it only grows it (mirrors
        // TextLabel's grow-only contract).
        var font = Minecraft.getInstance().font;
        int wrapped = LABEL_VPAD + wrappedLineCount * font.lineHeight + LABEL_VPAD;
        return Math.max(height, wrapped);
    }

    /** Natural (uncapped) bar width — the auto-widen-to-label extent, or the
     *  switch width when unlabeled. Drives the panel hug-width. */
    @Override
    public int naturalWidth() {
        if (labelSupplier == null) return width;
        Component label = labelSupplier.get();
        if (label == null) return width;
        return Math.max(width, Minecraft.getInstance().font.width(label) + 2 * LABEL_PAD);
    }

    /** Cap the labeled BAR to the panel's budget (reversible); a bare/sprite
     *  switch is intrinsic and ignores it, mirroring its fillWidth no-op.
     *
     *  <p>Beyond the width cap, this also resolves the label's WRAP: at the
     *  capped inner width (the bar's budget minus the L+R label inset) it asks
     *  the vanilla font splitter how many lines the label takes. If the label's
     *  natural single-line width exceeds that inner area it wraps (line count
     *  &gt; 1) and {@link #getHeight()} grows the bar to fit; if it fits, the
     *  count resets to 1 and the bar stays single-line. Recomputed every pass,
     *  so a later wider budget un-wraps it — fully reversible (mirrors
     *  TextLabel.layoutWithin). */
    @Override
    public void layoutWithin(int budget) {
        // Bare/sprite switch — intrinsic, no cap and no wrap. Reset wrap state
        // so a label that was later cleared can't leave a stale grown height.
        if (labelSupplier == null) { this.wrappedLineCount = 1; return; }

        this.widthCap = budget;

        Component label = labelSupplier.get();
        if (label == null) { this.wrappedLineCount = 1; return; }

        var font = Minecraft.getInstance().font;
        // The bar's resolved width is its natural extent clamped to the budget
        // (same min() getWidth() applies); the label lives inside that minus
        // the L+R inset. Wrapping engages only when the label's natural
        // single-line width exceeds this inner area.
        int barWidth = Math.min(naturalWidth(), budget);
        int innerWrapWidth = Math.max(1, barWidth - 2 * LABEL_PAD);
        // font.split is the same vanilla wrapper chat / tooltips / book pages
        // use, so the wrapped break points match player expectations.
        int lines = font.split(label, innerWrapWidth).size();
        this.wrappedLineCount = Math.max(1, lines);
    }

    /**
     * Extra vertical pixels the WRAPPED bar occupies beyond its authored
     * height — {@code getHeight() - height} when wrapped, else {@code 0}. The
     * owning {@link Panel} reflows the elements below this toggle downward by
     * exactly this amount, so a label that grows from one line to two pushes
     * (never paints over) what's beneath it. Mirrors TextLabel.extraLayoutHeight.
     */
    @Override
    public int extraLayoutHeight() {
        if (wrappedLineCount <= 1) return 0;
        return Math.max(0, getHeight() - height);
    }

    /**
     * Column-fill (Pass 3): stretch the LABELED bar form to the column's widest
     * extent. A bare/sprite switch is intrinsically sized — stretching its track
     * would distort it — so {@code fillWidth} is a no-op when there's no label
     * (the same intrinsic-width principle Icon/Checkbox/Radio follow).
     */
    @Override
    public void fillWidth(int width) {
        if (labelSupplier != null) this.width = width;
    }

    /** Interactive — handles clicks, so it claims (blocks vanilla behind) on a non-opaque panel. */
    @Override public boolean isInteractive() { return true; }

    // ── State ──────────────────────────────────────────────────────────

    /** Returns the current toggle state. */
    public boolean isOn() { return currentState(); }

    /**
     * Sets the toggle state programmatically. Fires {@code onToggle} with
     * the new state if it differs from the current state; no-op otherwise.
     * This lets chat commands, keybinds, or other non-click paths flip the
     * toggle while keeping observed callback behavior consistent.
     */
    public void setOn(boolean newState) {
        toggleTo(newState);
    }

    /** Returns whether the toggle is currently disabled. */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Returns whether the mouse is currently over this toggle (updated each frame). */
    public boolean isHovered() { return hovered; }

    // ── State extension points (factored for subclasses) ──────────────

    /**
     * Returns the Toggle's current boolean state.
     *
     * <p><b>Stable extension point for consumer Toggle subclasses.</b>
     * Override to read state from external storage (supplier, block entity,
     * config file, etc.). The default implementation returns the
     * element-owned internal state.
     *
     * <p>Base Toggle's render and click handling call {@code currentState()}
     * exactly once per frame. Subclasses overriding {@code currentState()}
     * may rely on this: their supplier is invoked once per frame for base
     * Toggle's rendering purposes. If the supplier returns different values
     * across rapid successive calls, only the first call per frame affects
     * the rendered output.
     */
    protected boolean currentState() {
        return state;
    }

    /**
     * Commits a state transition. Subclasses define what "commit" means for
     * their state-ownership model:
     *
     * <ul>
     *   <li>Base Toggle (element-owned state): writes the new state to
     *       internal storage and fires the {@code onToggle} callback with
     *       the new state.</li>
     *   <li>{@link #linked(int, int, int, int, java.util.function.BooleanSupplier, java.util.function.Consumer) Toggle.linked}
     *       (consumer-owned state): fires the consumer's callback with the new
     *       state; consumer is responsible for updating their own state. No
     *       internal storage commit happens.</li>
     * </ul>
     *
     * <p>Called from the {@code toggleTo} orchestration helper after the
     * short-circuit no-op check passes. Implementations should be atomic —
     * the state transition and the callback notification are conceptually
     * a single event.
     *
     * <p><b>Stable extension point.</b> Signature and semantic contract
     * maintained across MenuKit versions.
     */
    protected void applyState(boolean newState) {
        this.state = newState;
        onToggle.accept(newState);
    }

    /**
     * Orchestration: short-circuit on same-state, then applyState commits
     * and fires the callback atomically. Used by mouseClicked and setOn.
     */
    private void toggleTo(boolean newState) {
        if (currentState() == newState) return;
        applyState(newState);
    }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip + at return Toggle for free via the SELF self-type.

    /**
     * Fluent resize sugar — sets the toggle's base pixel dimensions and
     * returns this toggle for chaining. For a labeled toggle the effective
     * width auto-widens to fit the label (see {@link #getWidth()}); this sets
     * the unlabeled/minimum width. Additive to the positional constructors.
     */
    public Toggle size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets an on-body label: the Toggle becomes a bar that shows this label (raised
     * = off, inset = on), auto-sized to fit it, the whole bar hovering + toggling as
     * one — the label is the toggle's own, not text beside it. Pass {@code null} to
     * clear (reverting to the bare switch).
     */
    public Toggle label(@Nullable Component label) {
        this.labelSupplier = label == null ? null : () -> label;
        return this;
    }

    /** Supplier-driven label (re-evaluated each frame for dynamic text). */
    public Toggle label(@Nullable Supplier<Component> label) {
        this.labelSupplier = label;
        return this;
    }

    // ── Rendering ──────────────────────────────────────────────────────

    /**
     * Orchestrates the render pass: hover-state update, background paint
     * (via the extension hook), tooltip dispatch. Final by design — the
     * extension surface for consumer subclasses is
     * {@link #renderBackground(RenderContext, int, int, boolean, boolean, boolean)},
     * not this orchestration method.
     */
    @Override
    public final void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;

        // Update hover state — false on HUDs (no input dispatch).
        hovered = isHovered(ctx);

        // Read current state exactly once per frame so the render pass is
        // internally consistent even when currentState() is backed by a
        // consumer-supplied BooleanSupplier (e.g., Toggle.linked).
        boolean disabled = isDisabled();
        boolean on = currentState();

        renderBackground(ctx, sx, sy, on, disabled, hovered);

        // On-body label: a labeled Toggle is a bar showing its label, so the label is
        // unmistakably the toggle's own (not text beside it). Drawn exactly like
        // Button.renderContent — centered, shadowed, scroll-on-overflow, same text
        // colors — so a labeled toggle is visually a button (raised) whose on-state
        // simply stays depressed (inset). getWidth() already covers it for hover +
        // hit-testing.
        if (labelSupplier != null) {
            Component label = labelSupplier.get();
            if (label != null) {
                int textColor = disabled ? LABEL_DISABLED_COLOR : LABEL_COLOR;
                if (wrappedLineCount > 1) {
                    // Wrapped: the single-line centered-scroll path can't show a
                    // label too long for the bar, so split it into the SAME
                    // FormattedCharSequence lines vanilla uses (font.split at the
                    // capped inner width) and draw each centered, stacked at
                    // successive lineHeight offsets. The block is vertically
                    // centered in the (grown) bar so it sits evenly between the
                    // top/bottom borders; each line is horizontally centered like
                    // the single-line path. The on-state "depressed" INSET body
                    // is painted by renderBackground (which reads getHeight()),
                    // so it grows with the bar automatically — unchanged here.
                    var font = Minecraft.getInstance().font;
                    int barW = getWidth();
                    int innerWrapWidth = Math.max(1, barW - 2 * LABEL_PAD);
                    List<FormattedCharSequence> lines = font.split(label, innerWrapWidth);
                    int blockH = lines.size() * font.lineHeight;
                    int lineY = sy + (getHeight() - blockH) / 2; // vertical center
                    for (FormattedCharSequence line : lines) {
                        int lineW = font.width(line);
                        int lineX = sx + (barW - lineW) / 2; // horizontal center
                        ctx.graphics().text(font, line, lineX, lineY, textColor, true);
                        lineY += font.lineHeight;
                    }
                } else {
                    // Single-line: unchanged centered-scroll path (drawn exactly
                    // like Button.renderContent).
                    MKText.renderCentered(ctx.graphics(), label,
                            sx, sy, getWidth(), height, textColor, true);
                }
            }
        }

        // Hover-triggered tooltip — deferred to end-of-frame by vanilla.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(ctx.graphics(), ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    /**
     * Paints the toggle's full visual — background and any state-dependent
     * overlays. Called from {@link #render(RenderContext)} after hover and
     * on/off state have been resolved for the frame. Default implementation
     * uses {@link PanelStyle} backgrounds (RAISED off, INSET on, DARK
     * disabled) plus the translucent white hover highlight.
     *
     * <p><b>Stable extension point for consumer Toggle subclasses.</b>
     * The signature {@code (RenderContext, int sx, int sy, boolean on,
     * boolean disabled, boolean hovered)} and the semantic contract —
     * {@code sx}/{@code sy} are the absolute screen-space top-left of
     * the toggle; this hook owns ALL state-dependent painting (subclasses
     * paint their own hover/disabled overlays) — are maintained across
     * MenuKit versions. Consumer subclasses may rely on these properties.
     */
    protected void renderBackground(RenderContext ctx, int sx, int sy,
                                     boolean on, boolean disabled, boolean hovered) {
        int w = getWidth();   // labeled = a bar sized to its label; unlabeled = the switch
        // getHeight() (not the authored `height`) so a WRAPPED labeled bar's
        // RAISED/INSET body grows to contain its stacked lines — the on-state
        // "depressed" styling stays, it just gets taller. For every single-line
        // / bare / sprite variant getHeight() == height, so this is a no-op
        // there and the non-wrapped visual is byte-for-byte unchanged.
        int h = getHeight();
        PanelStyle bg = disabled ? PanelStyle.DARK
                      : on       ? PanelStyle.INSET
                                 : PanelStyle.RAISED;
        PanelRendering.renderPanel(ctx.graphics(), sx, sy, w, h, bg);

        // Hover highlight — same pattern as Button
        if (!disabled && hovered) {
            ctx.graphics().fill(sx + 1, sy + 1, sx + w - 1, sy + h - 1,
                    0x30FFFFFF);
        }
    }

    // ── Click Handling ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isDisabled()) return false;
        if (!hovered) return false;

        toggleTo(!currentState());
        return true;
    }

    // ── State-linked variant ───────────────────────────────────────────

    /**
     * Creates a Toggle whose state lives in consumer code instead of inside
     * the element. The consumer provides a {@link BooleanSupplier} that
     * drives rendering each frame and a {@link Consumer Consumer&lt;Boolean&gt;}
     * that fires with the new state when the user clicks to toggle.
     *
     * <h4>Persistence framing</h4>
     *
     * State persistence is a consumer concern. MenuKit does not ship a
     * persistence abstraction — no {@code PersistentValue<T>}, no
     * {@code BooleanFlag}, no config-backed state helpers. If you need a
     * toggle whose state persists, use {@code Toggle.linked} and back the
     * supplier and callback with wherever your state actually lives: a
     * block entity, player attachment, config file, static field on a
     * singleton, or anywhere else. The supplier reads; the callback
     * signals. The library gives you the visual element and user input
     * handling; the storage is yours to define.
     *
     * <h4>Self-healing behavior</h4>
     *
     * If the {@code onToggle} callback fails to update consumer state
     * (bug, exception, swallowed error), the next frame's render reads
     * the supplier and shows the unchanged state. The toggle visually
     * snaps back to its pre-click appearance. State displayed is always
     * state reported by the supplier — there is no internal state that
     * could diverge from consumer state.
     *
     * <h4>Typical usage</h4>
     *
     * <pre>{@code
     * Toggle.linked(x, y, w, h,
     *     () -> config.autoSort,
     *     newState -> config.autoSort = newState);
     * }</pre>
     *
     * @param childX   X position within panel content area
     * @param childY   Y position within panel content area
     * @param width    width in pixels
     * @param height   height in pixels
     * @param state    supplier invoked each frame to drive rendering
     * @param onToggle fired on user-initiated state changes, carrying the
     *                 new state; consumer updates their own state store in
     *                 response. Unified with the element-owned Toggle's
     *                 {@code Consumer<Boolean>} callback shape.
     */
    public static Toggle linked(int childX, int childY, int width, int height,
                                BooleanSupplier state,
                                Consumer<Boolean> onToggle) {
        return new LinkedToggle(childX, childY, width, height, state, onToggle);
    }

    // ── Sprite-backed Toggle variant ───────────────────────────────────

    /**
     * Creates a Toggle whose visual is a consumer-supplied sprite. The off
     * state renders the sprite as-is; the on state renders the sprite through
     * {@link com.trevorschoeny.menukit.core.MKRenderPipelines#GUI_BRIGHTNESS_INVERTED
     * the HSL-lightness-inversion pipeline} so the same sprite asset
     * communicates both states without the consumer authoring a second
     * "toggled" texture. Hue + saturation are preserved through the
     * inversion; only the per-pixel lightness flips. Hover overlay applies
     * on top of either state; disabled adds a dark overlay.
     *
     * <p>Pairs with the static-state Toggle constructor — state is owned
     * inside the element. For consumer-owned state, use the
     * {@link #spriteLinked(int, int, int, int, BooleanSupplier, Consumer, Identifier)
     * spriteLinked} variant.
     *
     * @param childX       X position within panel content area
     * @param childY       Y position within panel content area
     * @param width        toggle width in pixels (typically matches sprite width)
     * @param height       toggle height in pixels (typically matches sprite height)
     * @param initialState starting boolean state
     * @param onToggle     fired on each state change with the new state
     * @param sprite       sprite identifier for both off and on states (on state
     *                     is rendered through the HSL-inversion pipeline)
     */
    public static Toggle sprite(int childX, int childY, int width, int height,
                                 boolean initialState, Consumer<Boolean> onToggle,
                                 Identifier sprite) {
        return new SpriteToggle(childX, childY, width, height,
                initialState, onToggle, (Supplier<Identifier>) () -> sprite);
    }

    /**
     * Sprite-driven Toggle whose sprite is computed per-frame from a
     * {@link Supplier}. Enables state-swap patterns where on/off should use
     * different sprite assets entirely (rather than the default HSL-inversion
     * of one sprite). For the inversion-based pattern, prefer
     * {@link #sprite(int, int, int, int, boolean, Consumer, Identifier)}.
     */
    public static Toggle sprite(int childX, int childY, int width, int height,
                                 boolean initialState, Consumer<Boolean> onToggle,
                                 Supplier<Identifier> sprite) {
        return new SpriteToggle(childX, childY, width, height,
                initialState, onToggle, sprite);
    }

    /**
     * {@link #linked Linked} + {@link #sprite sprite} combination: consumer-
     * owned state, sprite-backed visual with HSL-inversion on the on state.
     * The {@code onToggle} callback carries the new state, matching every
     * other Toggle factory.
     */
    public static Toggle spriteLinked(int childX, int childY, int width, int height,
                                       BooleanSupplier state, Consumer<Boolean> onToggle,
                                       Identifier sprite) {
        return new SpriteLinkedToggle(childX, childY, width, height,
                state, onToggle, (Supplier<Identifier>) () -> sprite);
    }

    /**
     * Supplier-driven-sprite variant of
     * {@link #spriteLinked(int, int, int, int, BooleanSupplier, Consumer, Identifier)}.
     * The sprite is computed per frame from the {@link Supplier}, matching the
     * {@code sprite(...)} factory's Identifier/Supplier overload pair.
     */
    public static Toggle spriteLinked(int childX, int childY, int width, int height,
                                       BooleanSupplier state, Consumer<Boolean> onToggle,
                                       Supplier<Identifier> sprite) {
        return new SpriteLinkedToggle(childX, childY, width, height,
                state, onToggle, sprite);
    }

    /**
     * Sprite-backed Toggle specialization. Overrides
     * {@link #renderBackground} to paint a consumer-supplied sprite instead
     * of the default {@link PanelStyle} backgrounds. Off state uses vanilla's
     * {@link RenderPipelines#GUI_TEXTURED}; on state uses MenuKit's
     * brightness-inversion pipeline so the same sprite reads as "toggled."
     * Package-private — consumers reach this via
     * {@link #sprite(int, int, int, int, boolean, Consumer, Identifier)}.
     */
    static class SpriteToggle extends Toggle {
        /** Hover overlay color — same translucent white as default Toggle. */
        private static final int HOVER_OVERLAY = 0x30FFFFFF;
        /** Disabled overlay color — ~50% black darkens the sprite. */
        private static final int DISABLED_OVERLAY = 0x80000000;

        private final Supplier<Identifier> spriteSupplier;

        SpriteToggle(int childX, int childY, int width, int height,
                     boolean initialState, Consumer<Boolean> onToggle,
                     Supplier<Identifier> sprite) {
            super(childX, childY, width, height, initialState, onToggle);
            this.spriteSupplier = sprite;
        }

        @Override
        protected void renderBackground(RenderContext ctx, int sx, int sy,
                                         boolean on, boolean disabled, boolean hovered) {
            Identifier id = spriteSupplier.get();
            if (id == null) return;
            int w = getWidth();
            int h = getHeight();

            if (disabled) {
                // Off-state sprite + dark overlay. Disabled overrides the
                // toggled-state visual; whatever the consumer expects to
                // see for a disabled control wins over the on/off look.
                ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
                ctx.graphics().fill(sx, sy, sx + w, sy + h, DISABLED_OVERLAY);
            } else if (on) {
                // Toggled — HSL-lightness inversion of the same sprite.
                // Animation, alpha edges, etc. all pass through unchanged;
                // only the per-pixel lightness flips.
                ctx.graphics().blitSprite(
                        MKRenderPipelines.GUI_BRIGHTNESS_INVERTED, id, sx, sy, w, h);
            } else {
                // Off — sprite as-is.
                ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
            }

            // Hover overlay on top of either state. Mirrors default Toggle
            // hover treatment; gives consistent hover feedback regardless of
            // whether the toggle is on or off.
            if (!disabled && hovered) {
                ctx.graphics().fill(sx + 1, sy + 1, sx + w - 1, sy + h - 1,
                        HOVER_OVERLAY);
            }
        }
    }

    /**
     * {@link SpriteToggle} + {@link LinkedToggle} composition: sprite visual
     * with HSL-inversion on the on state, plus consumer-owned state via
     * {@link BooleanSupplier} + {@link Consumer Consumer&lt;Boolean&gt;}.
     * Package-private — consumers reach this via
     * {@link #spriteLinked(int, int, int, int, BooleanSupplier, Consumer, Identifier)}.
     */
    static final class SpriteLinkedToggle extends SpriteToggle {
        private final BooleanSupplier stateSupplier;
        private final Consumer<Boolean> onToggleConsumer;

        SpriteLinkedToggle(int childX, int childY, int width, int height,
                           BooleanSupplier state, Consumer<Boolean> onToggle,
                           Supplier<Identifier> sprite) {
            // Super's Consumer<Boolean> is a dummy — applyState override
            // below replaces state-commit behavior, same shape as LinkedToggle.
            super(childX, childY, width, height, state.getAsBoolean(), b -> {}, sprite);
            this.stateSupplier = state;
            this.onToggleConsumer = onToggle;
        }

        @Override
        protected boolean currentState() {
            return stateSupplier.getAsBoolean();
        }

        @Override
        protected void applyState(boolean newState) {
            // Consumer-owned state — fire the new-state callback; consumer
            // mutates their own state; the supplier returns it next frame.
            onToggleConsumer.accept(newState);
        }
    }

    /**
     * State-linked Toggle specialization. Overrides {@link #currentState}
     * to read from a consumer-supplied {@link BooleanSupplier} and
     * {@link #applyState} to fire a {@link Consumer Consumer&lt;Boolean&gt;}
     * new-state signal without any internal state commit. Package-private —
     * consumers access via
     * {@link #linked(int, int, int, int, BooleanSupplier, Consumer)}.
     */
    static final class LinkedToggle extends Toggle {
        private final BooleanSupplier stateSupplier;
        private final Consumer<Boolean> onToggleConsumer;

        LinkedToggle(int childX, int childY, int width, int height,
                     BooleanSupplier state, Consumer<Boolean> onToggle) {
            // Super's Consumer<Boolean> is a dummy — the applyState override
            // below fully replaces parent's state-commit behavior, so super's
            // callback is never fired. Super's `state` field is also dead
            // storage after construction (currentState() override reads the
            // supplier instead).
            super(childX, childY, width, height, state.getAsBoolean(), b -> {});
            this.stateSupplier = state;
            this.onToggleConsumer = onToggle;
        }

        @Override
        protected boolean currentState() {
            return stateSupplier.getAsBoolean();
        }

        @Override
        protected void applyState(boolean newState) {
            // Consumer-owned state — no internal commit to do.
            // Fire the new-state callback; consumer mutates their state; the
            // supplier returns the new value on next frame.
            onToggleConsumer.accept(newState);
        }
    }
}
