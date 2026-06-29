package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import org.lwjgl.glfw.GLFW;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.trevorschoeny.menukit.core.layout.ElementSpec;

/**
 * Single-selection dropdown control. Phase 14d-5 — bespoke composition (no
 * vanilla wrap; vanilla 1.21.11 ships {@code CycleButton} which cycles in
 * place, but no popover-list dropdown widget). Owns trigger render +
 * popover render + click routing internally; new
 * {@link PanelElement#hitTest} primitive lets the popover area receive
 * clicks despite rendering outside the trigger's layout bounds.
 *
 * <p>The architectural patterns inherited from vanilla are <b>not</b> a
 * widget wrap but the host-screen-owns-dispatch pattern from
 * {@code CommandSuggestions} / {@code SuggestionsList} (chat command
 * autocomplete) plus the edge-flip placement pattern from
 * {@code BelowOrAboveWidgetTooltipPositioner}. Heuristic 6 (follow vanilla)
 * applied at the pattern level rather than the widget level.
 *
 * <h3>Lens pattern (Principle 8) — Supplier+Consumer over T</h3>
 *
 * Generic-typed lens pair. Library reads supplier each frame to render
 * the current selection in the trigger label; library calls consumer when
 * the user picks an item from the popover. Selection identity via
 * {@code T.equals()}. {@code null} supplier values OK — trigger renders
 * the empty placeholder.
 *
 * <p>Per-frame supplier-pull is idempotent: Dropdown stores no value
 * internally — the supplier IS the source of truth. Programmatic resets
 * (server sync, reset-to-default) work transparently — consumer changes
 * its own state, next frame the trigger label updates.
 *
 * <p>No imperative {@code setValue(T)} escape hatch (matches Slider /
 * ScrollContainer; consumer-as-source-of-truth eliminates the "library
 * holds state, consumer pushes in" gap).
 *
 * <h3>API surface</h3>
 *
 * <pre>{@code
 * Dropdown<GameMode> dropdown = Dropdown.<GameMode>builder()
 *     .at(0, 0)
 *     .triggerSize(120, 20)
 *     .items(List.of(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE))
 *     .label(gm -> Component.literal(gm.name()))
 *     .selection(() -> currentMode, m -> currentMode = m)
 *     .maxVisibleItems(8)            // optional; default 8
 *     .build();
 * }</pre>
 *
 * Same {@code label} function does double duty: trigger shows
 * {@code label.apply(currentSelection)}; popover items show
 * {@code label.apply(item)} per row (matches vanilla {@code CycleButton}'s
 * single-stringifier shape).
 *
 * <h3>Popover placement — auto edge-flip</h3>
 *
 * Open direction is determined at open time:
 * <ul>
 *   <li>Default: below trigger (popover renders at {@code triggerY + triggerHeight}).</li>
 *   <li>If trigger sits low enough that {@code triggerY + triggerHeight + popoverHeight}
 *       overflows {@code screen.height}, popover flips above
 *       ({@code triggerY - popoverHeight}).</li>
 *   <li>Popover X-axis: left-aligns with trigger; clamps to
 *       {@code screen.width - popoverWidth} so the right edge stays on-screen
 *       (matches the {@code Mth.clamp} pattern from {@code CommandSuggestions}).</li>
 * </ul>
 *
 * No {@code .openDirection()} builder option in v1; AUTO only. Defer
 * override to evidence per the principle of <i>fold-on-evidence</i>.
 *
 * <h3>Render-overlay z-order (Phase 18t)</h3>
 *
 * The popover renders via {@link #renderOverlay} — the second pass
 * {@link PanelDispatch#renderElements} runs after every element's base
 * {@link #render}. Result: an open popover always paints on top of every
 * sibling element regardless of declaration order. Consumers no longer
 * need to declare Dropdowns LAST — any order works.
 *
 * <p>The one residual ordering rule: when multiple Dropdowns are open
 * simultaneously (rare — typically only one popover is open at a time),
 * later-declared Dropdowns' popovers paint over earlier-declared ones,
 * because {@code renderOverlay} iterates in declaration order too. In
 * the common single-open case this is invisible.
 *
 * <h3>Cross-context applicability</h3>
 *
 * <ul>
 *   <li><b>MenuContext (inventory menus):</b> yes — settings panels,
 *       enum selectors.</li>
 *   <li><b>StandaloneContext (MKScreen):</b> yes — full-screen
 *       MenuKit-native UIs.</li>
 *   <li><b>SlotGroupContext:</b> no — slot-group anchors are for slot
 *       decorations only.</li>
 *   <li><b>HudContext:</b> no — HUDs are render-only (no input dispatch);
 *       Dropdown's defining feature is interactive selection.</li>
 * </ul>
 *
 * <h3>What v1 does NOT do (deferred)</h3>
 *
 * <ul>
 *   <li><b>Multi-select / search-as-you-type</b> — separate primitives.</li>
 *   <li><b>Keyboard nav</b> (arrows / Enter / Esc) — no vanilla widget to
 *       inherit from; bespoke implementation deferred. Esc still closes
 *       the parent screen via vanilla's standard handling.</li>
 *   <li><b>Programmatic open/close</b> — {@code open} is internal state
 *       per the Slider {@code dragging} precedent.</li>
 *   <li><b>Custom item rendering</b> (icons, multi-line) — v1 ships
 *       single-line text rows via {@code label.apply(item)}.</li>
 *   <li><b>Dynamic items list</b> — {@code items} is immutable post-build;
 *       dynamic items is a separate primitive (Combobox / Autocomplete).</li>
 * </ul>
 *
 * @param <T> selection value type; identity via {@code T.equals()}
 */
public final class Dropdown<T> extends AbstractPanelElement<Dropdown<T>> {

    @Override protected Dropdown<T> self() { return this; }

    // ── Layout / render constants ──────────────────────────────────────

    /** Vertical pixels per popover row. Matches Button DEFAULT_HEIGHT minus 2 for tighter list density. */
    private static final int ROW_HEIGHT = 14;
    /** Default cap on visible rows in the popover before scrolling kicks in. */
    private static final int DEFAULT_MAX_VISIBLE = 8;
    /** Pixels of horizontal padding inside the popover (between border and text). */
    private static final int POPOVER_TEXT_PAD_X = 4;
    /** Pixels of horizontal padding inside the trigger (between border and text). */
    private static final int TRIGGER_TEXT_PAD_X = 4;
    /** Pixels of right padding to reserve for the chevron in the trigger. */
    private static final int CHEVRON_RESERVED_W = 10;
    /** Width of the scrollbar track on the popover right edge when scrollable. */
    private static final int SCROLLBAR_W = 4;

    // ── Colors (ARGB; alpha non-zero — vanilla drawString silently drops zero-alpha text) ──

    /** Trigger / popover text — white with shadow, like Button. */
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** Hover-row background overlay inside the popover. */
    private static final int COLOR_HOVER_OVERLAY = 0x40FFFFFF;
    /** Selected-row background overlay (rendered always, on top of base). */
    private static final int COLOR_SELECTED_OVERLAY = 0x60FFFFFF;
    /** Scrollbar thumb color — light gray-ish. */
    private static final int COLOR_SCROLLBAR_THUMB = 0xFFC6C6C6;

    // ── Builder-supplied state (immutable) ─────────────────────────────

    // Non-final since Pass 3 column-fill (fillWidth); the popover derives its
    // width from the trigger at render, so it follows automatically.
    private int triggerWidth;
    private final int triggerHeight;
    private final List<T> items;
    private final Function<T, Component> labelFn;
    private final Supplier<@Nullable T> selectionSupplier;
    private final Consumer<T> selectionConsumer;
    private final int maxVisibleItems;
    /**
     * Optional per-item tooltip function. When non-null and the user hovers
     * a popover row, the tooltip is queued via
     * {@link net.minecraft.client.gui.GuiGraphics#setTooltipForNextFrame}.
     * Set via {@link Builder#itemTooltip(Function)}.
     */
    private final @Nullable Function<T, Component> itemTooltipFn;

    /**
     * Phase 18s follow-up — visual style for the trigger background only.
     * Defaults to {@link ControlStyle#MK}. {@link ControlStyle#VANILLA}
     * picks vanilla Minecraft's button sprite atlas. Popover always
     * stays {@link PanelStyle#RAISED} regardless — popover is a container
     * for choices, not itself a button.
     */
    private final ControlStyle controlStyle;

    /**
     * Optional disabled predicate (Phase 3b — Item 8). When it returns true,
     * the trigger renders disabled (VANILLA → {@code widget/button_disabled}
     * sprite via {@code renderVanillaButton(enabled=false)}; MK →
     * {@link PanelStyle#DARK} panel) and all interaction is ignored
     * (mouseClicked/mouseScrolled/keyPressed no-op; any open popover is
     * force-closed). Per-frame predicate shape, matching Button/Toggle's
     * {@code disabledWhen}. Null = always enabled.
     */
    private final @Nullable BooleanSupplier disabledWhen;

    // ── Internal mutable state ─────────────────────────────────────────
    //
    // open/scrollOffset are UI-mode state — internal to the dropdown,
    // not consumer-meaningful (matches Slider's `dragging`). Keeping
    // them out of the lens avoids forcing every consumer to allocate
    // boolean state for every dropdown.
    //
    // volatile because Minecraft sometimes processes input on a thread
    // distinct from the render thread; the ScrollContainer / Slider
    // precedent uses volatile for the same reason.

    /** True when popover is showing. */
    private volatile boolean open = false;
    /** First-visible row index when items.size() > maxVisibleItems. */
    private volatile int scrollOffset = 0;

    /**
     * Keyboard-nav highlighted row index (completion of the input model).
     * {@code -1} = no keyboard highlight yet (reset on each open); becomes
     * {@code 0..items.size()-1} once the user presses an arrow key in the
     * open popover. Distinct from mouse hover (computed from cursor position
     * in render) and from the selection (held by {@link #selectionSupplier}).
     * Volatile for the same render-thread/input-thread reason as
     * {@link #open}/{@link #scrollOffset}.
     */
    private volatile int highlightedIndex = -1;

    /**
     * Captured {@link Util#getMillis} value at the moment the popover
     * was last opened. Passed to {@link MKText#renderFromOpenTime} so
     * long item-text scroll cycles start at "beginning visible" when
     * the user opens the dropdown (instead of mid-cycle at whatever
     * phase global system time happens to be in). Volatile because
     * render reads it on the render thread and the open transition
     * sets it on the input thread. Stale value when popover is closed
     * — only read inside the {@code if (open)} branch of renderPopover.
     */
    private volatile long popoverOpenMillis = 0L;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2). The
    // dropdown-specific trigger gating ("only fire when popover closed")
    // lives in render() below — base only holds the supplier. (Pre-hoist,
    // this field was `volatile @Nullable`; the base field is non-volatile
    // because chainable .tooltip() is a single-threaded construction-time
    // setter in practice — if cross-thread mutation surfaces as a real
    // case, revisit.)

    private Dropdown(Builder<T> b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.triggerWidth = b.triggerWidth;
        this.triggerHeight = b.triggerHeight;
        this.items = List.copyOf(b.items);                      // defensive immutable copy
        this.labelFn = b.labelFn;
        this.selectionSupplier = b.selectionSupplier;
        this.selectionConsumer = b.selectionConsumer;
        this.maxVisibleItems = b.maxVisibleItems;
        this.itemTooltipFn = b.itemTooltipFn;
        this.controlStyle = b.controlStyle;
        this.disabledWhen = b.disabledWhen;
    }

    // ── PanelElement protocol ──────────────────────────────────────────

    @Override public int getWidth()  { return triggerWidth; }
    @Override public int getHeight() { return triggerHeight; }

    /** Column-fill (Pass 3): stretch the trigger (and thus the popover) to the
     *  column's widest extent. */
    @Override public void fillWidth(int width) { this.triggerWidth = width; }

    /** Returns whether the dropdown is currently disabled (Phase 3b — Item 8). */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Interactive — handles clicks/keyboard nav, so it claims (blocks vanilla behind) on a non-opaque panel. */
    @Override public boolean isInteractive() { return true; }

    /**
     * Phase 14d-5 active-overlay claim — when popover is open, declares
     * the popover's screen-space bounds as an exclusive modal-area. The
     * dispatcher routes ALL clicks (and scrolls) inside the popover
     * bounds solely to this Dropdown, regardless of {@link #mouseClicked}'s
     * return — elements behind the popover (including any Button or
     * vanilla widget visually occluded by it) stay innately inert.
     *
     * <p>This is the parallel to M9's panel-level modal click-eat at
     * the element level. Without it, a click on a popover item would
     * also fire whichever element's layout bounds happened to overlap
     * the popover region (e.g., a Reset button paint-occluded by an
     * edge-flipped popover).
     *
     * <p>Trigger bounds are NOT included in the overlay — the trigger
     * dispatches via the standard {@link #hitTest} pass (default = layout
     * bounds). When the popover is closed, returns null so no overlay
     * is active.
     */
    @Override
    public int @Nullable [] getActiveOverlayBounds() {
        if (!open) return null;
        // Use cached trigger screen-position from the most recent render frame.
        return computePopoverBounds(lastTriggerScreenX, lastTriggerScreenY);
    }

    // ── Render ─────────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int triggerX = ctx.originX() + childX;
        int triggerY = ctx.originY() + childY;

        // Cache for input-thread reads (mouseClicked/mouseScrolled don't
        // receive panel content origin; they reconstruct via this cache).
        // See lastTriggerScreenX/Y javadoc on race-safety.
        this.lastTriggerScreenX = triggerX;
        this.lastTriggerScreenY = triggerY;

        // Disabled-state gate (Phase 3b — Item 8). Read once per frame.
        // When disabled, force any open popover closed so a state change to
        // disabled-while-open doesn't leave a ghost popover, and suppress the
        // hover affordance + tooltip below.
        boolean disabled = isDisabled();
        if (disabled) open = false;

        // ── Trigger ────────────────────────────────────────────────────
        // Render a Button-style raised background. Hover state computed
        // against trigger bounds (popover hover is separate). Pressed-
        // look while popover open visually communicates open state.
        // Hover is suppressed when disabled so the trigger reads inert.
        boolean triggerHovered = !disabled
                && ctx.isHovered(childX, childY, triggerWidth, triggerHeight);
        renderTriggerBackground(ctx.graphics(), triggerX, triggerY, triggerHovered, disabled);
        renderTriggerContent(ctx.graphics(), triggerX, triggerY);

        // Popover is now rendered in renderOverlay() — see the override
        // below. Moved from this base render() in the Phase 18s follow-up
        // so the popover always paints AFTER every sibling element's
        // base render (z-order is no longer order-of-declaration
        // dependent on the consumer side).

        // ── Tooltip (hover trigger, only when popover is closed) ──────
        // Queue via setTooltipForNextFrame so the end-of-frame flush
        // picks it up. Skip when popover is open — popover IS the
        // interactive surface; competing tooltip would clutter.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (triggerHovered && !open && !disabled && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(ctx.graphics(), ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip return Dropdown<T> for free via the SELF self-type.
    // Position + trigger size are configured on the Builder (.at()/.triggerSize()).

    /**
     * Phase 18s follow-up — popover renders on the overlay pass so it
     * always wins z-order regardless of consumer element-declaration
     * order. Pairs with {@link #getActiveOverlayBounds} for the input-
     * side exclusive claim — together they make the open popover both
     * visually on top AND inert-to-anything-underneath.
     *
     * <p>Uses the trigger screen-position cached during the base
     * {@link #render} pass (renderOverlay runs in the same frame, so
     * the cache is fresh).
     */
    @Override
    public void renderOverlay(RenderContext ctx) {
        if (!open) return;
        renderPopover(ctx, lastTriggerScreenX, lastTriggerScreenY);
    }

    private void renderTriggerBackground(GuiGraphics graphics, int sx, int sy,
                                         boolean hovered, boolean disabled) {
        if (controlStyle == ControlStyle.VANILLA) {
            // Vanilla style — sprite atlas encodes hover/disabled state
            // directly. Disabled wins (Phase 3b — Item 8): pass enabled=false
            // so renderVanillaButton picks widget/button_disabled, and skip
            // the pressed overlay (a disabled trigger is never "engaged").
            // When popover is open we suppress the highlighted sprite AND
            // apply the pressed-overlay so the trigger visually reads
            // "engaged / popover-active." Maps to MK Button's pressed visual
            // at the dropdown-trigger semantic level.
            ControlStyle.renderVanillaButton(graphics,
                    sx, sy, triggerWidth, triggerHeight,
                    !disabled,
                    hovered && !open);
            if (open && !disabled) {
                ControlStyle.renderVanillaPressedOverlay(graphics,
                        sx, sy, triggerWidth, triggerHeight);
            }
            return;
        }
        // MK style (default) — RAISED panel + translucent highlight on hover.
        // Disabled renders the DARK panel (matching Button/Toggle's disabled
        // look) with no hover highlight. When popover is open, render with the
        // open-look (no extra hover highlight; the popover itself signals
        // interactive state).
        PanelStyle bg = disabled ? PanelStyle.DARK : PanelStyle.RAISED;
        PanelRendering.renderPanel(graphics, sx, sy, triggerWidth, triggerHeight, bg);
        if (hovered && !open && !disabled) {
            graphics.fill(sx + 1, sy + 1, sx + triggerWidth - 1, sy + triggerHeight - 1,
                    COLOR_HOVER_OVERLAY);
        }
    }

    private void renderTriggerContent(GuiGraphics graphics, int sx, int sy) {
        Font font = Minecraft.getInstance().font;

        // Selection text — pulled per frame from supplier (lens-read).
        // null → empty (matches Q6 design — no "—" placeholder in v1).
        T sel = selectionSupplier.get();
        Component text = (sel != null) ? labelFn.apply(sel) : Component.empty();

        // Trigger text — left-aligned with text padding, vertically
        // centered. Scroll-on-overflow via MKText: when the selection
        // text is wider than the available trigger space (trigger
        // width minus chevron reservation minus padding), vanilla's
        // back-and-forth scroll animation kicks in. Replaces the
        // pre-18s-follow-up truncate-with-ellipsis behavior.
        int textAreaW = triggerWidth - CHEVRON_RESERVED_W - 2 * TRIGGER_TEXT_PAD_X;
        int textAreaX = sx + TRIGGER_TEXT_PAD_X;
        MKText.render(graphics, text, net.minecraft.client.gui.TextAlignment.LEFT,
                textAreaX, textAreaX + textAreaW,
                sy, sy + triggerHeight,
                COLOR_TEXT, true);

        // Chevron on the right edge — ▼ when closed, ▲ when open.
        // Centered vertically; reserved space already excluded from text area.
        Component chevron = Component.literal(open ? "▲" : "▼");
        int chevW = font.width(chevron);
        int chevX = sx + triggerWidth - CHEVRON_RESERVED_W + (CHEVRON_RESERVED_W - chevW) / 2 - 1;
        int chevY = sy + (triggerHeight - font.lineHeight) / 2;
        graphics.drawString(font, chevron, chevX, chevY, COLOR_TEXT, true);
    }

    /**
     * Renders the popover panel beneath / above the trigger with edge-
     * flip placement, hover highlight, selection highlight, scrollbar (if
     * scrollable), and per-row text labels.
     */
    private void renderPopover(RenderContext ctx, int triggerX, int triggerY) {
        GuiGraphics graphics = ctx.graphics();
        int[] popover = computePopoverBounds(triggerX, triggerY);
        int px = popover[0], py = popover[1], pw = popover[2], ph = popover[3];

        // Background — visual continuity with the trigger style.
        // MK: RAISED panel. VANILLA: darker gray (widget/button_disabled
        // sprite) so the popover reads as a distinct surface beneath the
        // lighter widget/button trigger above it.
        if (controlStyle == ControlStyle.VANILLA) {
            ControlStyle.renderVanillaPopoverBackground(graphics, px, py, pw, ph);
        } else {
            PanelRendering.renderPanel(graphics, px, py, pw, ph, PanelStyle.RAISED);
        }

        // Visible row range (after applying scroll offset).
        int visibleCount = visibleRowCount();
        int firstRow = clampScrollOffset(scrollOffset);
        int lastRow = Math.min(items.size(), firstRow + visibleCount);

        Font font = Minecraft.getInstance().font;
        T currentSelection = selectionSupplier.get();

        // Available text area width — minus border, minus padding, minus
        // scrollbar width when scrollable.
        boolean scrollable = items.size() > maxVisibleItems;
        int rowsContentW = pw - 2 - 2 * POPOVER_TEXT_PAD_X
                - (scrollable ? SCROLLBAR_W : 0);

        // Row backgrounds + text. Each row is ROW_HEIGHT tall, starting at
        // py + 1 (inside the top border). Hover highlight on cursor row;
        // selection highlight always.
        for (int i = firstRow; i < lastRow; i++) {
            int rowY = py + 1 + (i - firstRow) * ROW_HEIGHT;
            T item = items.get(i);

            // Hover highlight (mouse over THIS row, in popover X bounds)
            boolean rowHovered = ctx.hasMouseInput()
                    && ctx.mouseX() >= px + 1 && ctx.mouseX() < px + pw - 1
                    && ctx.mouseY() >= rowY && ctx.mouseY() < rowY + ROW_HEIGHT;
            if (rowHovered) {
                graphics.fill(px + 1, rowY,
                        px + pw - 1 - (scrollable ? SCROLLBAR_W : 0), rowY + ROW_HEIGHT,
                        COLOR_HOVER_OVERLAY);

                // Per-item tooltip — queues when the hovered row has one.
                // setTooltipForNextFrame is last-call-wins; queue happens
                // INSIDE the popover render which runs after the trigger
                // render, so this win condition is straightforward.
                if (itemTooltipFn != null) {
                    Component ttText = itemTooltipFn.apply(item);
                    if (ttText != null) {
                        MKTooltip.queue(graphics, ttText, ctx.mouseX(), ctx.mouseY());
                    }
                }
            }
            // Keyboard-nav highlight — the arrow-key-selected row. Same
            // overlay as hover so mouse and keyboard share one affordance;
            // gated on !rowHovered to avoid additive double-draw when the
            // cursor and keyboard highlight land on the same row.
            if (i == highlightedIndex && !rowHovered) {
                graphics.fill(px + 1, rowY,
                        px + pw - 1 - (scrollable ? SCROLLBAR_W : 0), rowY + ROW_HEIGHT,
                        COLOR_HOVER_OVERLAY);
            }
            // Selection highlight (this item == current selection)
            if (currentSelection != null && currentSelection.equals(item)) {
                graphics.fill(px + 1, rowY,
                        px + pw - 1 - (scrollable ? SCROLLBAR_W : 0), rowY + ROW_HEIGHT,
                        COLOR_SELECTED_OVERLAY);
            }

            // Row text — left-aligned with text padding, vertically
            // centered in the row. Scroll-on-overflow via MKText, with
            // the scroll cycle anchored to popoverOpenMillis so long
            // labels start at "text beginning visible" when the user
            // opens the popover (rather than mid-cycle at whatever
            // phase global system time happens to be in).
            Component itemText = labelFn.apply(item);
            int textX = px + 1 + POPOVER_TEXT_PAD_X;
            MKText.renderFromOpenTime(graphics, itemText, net.minecraft.client.gui.TextAlignment.LEFT,
                    textX, textX + rowsContentW,
                    rowY, rowY + ROW_HEIGHT,
                    COLOR_TEXT, true,
                    popoverOpenMillis);
        }

        // Scrollbar — solid thumb on right edge when items > maxVisibleItems.
        // Match ScrollContainer's mechanism: thumb height = visible/total
        // proportion; thumb position = scrollOffset/(total - visible) ratio.
        if (scrollable) {
            int trackX = px + pw - 1 - SCROLLBAR_W;
            int trackY = py + 1;
            int trackH = ph - 2;
            // Inset track for visual recess
            PanelRendering.renderInsetRect(graphics, trackX, trackY, SCROLLBAR_W, trackH);

            int total = items.size();
            int thumbH = Math.max(8, trackH * visibleCount / total);
            int range = total - visibleCount;
            int thumbY = trackY + (range > 0 ? (trackH - thumbH) * firstRow / range : 0);
            graphics.fill(trackX + 1, thumbY,
                    trackX + SCROLLBAR_W - 1, thumbY + thumbH,
                    COLOR_SCROLLBAR_THUMB);
        }
    }

    /**
     * Computes [x, y, w, h] of the popover. X clamped to keep right edge
     * on-screen (matches CommandSuggestions Mth.clamp). Y flips to above
     * trigger if there's not enough room below.
     */
    private int[] computePopoverBounds(int triggerX, int triggerY) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int popoverW = triggerWidth;
        int popoverH = popoverHeight();

        // X — left-align with trigger, clamp to screen-right.
        int popoverX = Mth.clamp(triggerX, 0, Math.max(0, screenW - popoverW));

        // Y — below by default; flip above if no room.
        int below = triggerY + triggerHeight;
        int popoverY;
        if (below + popoverH <= screenH) {
            popoverY = below;
        } else {
            // Not enough room below; try above
            int above = triggerY - popoverH;
            popoverY = (above >= 0) ? above
                                    // Extreme edge case: popover is taller than the
                                    // available space on either side. Render at top
                                    // and let it overflow at the bottom — better than
                                    // clipping the top items the user is trying to
                                    // see. Document in the design doc §6.3.
                                    : 0;
        }
        return new int[]{popoverX, popoverY, popoverW, popoverH};
    }

    /** Returns the popover's full pixel height (visible rows + 1px top + 1px bottom). */
    private int popoverHeight() {
        return visibleRowCount() * ROW_HEIGHT + 2;
    }

    /** Returns how many rows the popover renders (capped by maxVisibleItems). */
    private int visibleRowCount() {
        return Math.min(items.size(), maxVisibleItems);
    }

    /** Clamps a scroll offset to the valid range [0, items.size - visible]. */
    private int clampScrollOffset(int offset) {
        int visible = visibleRowCount();
        int max = Math.max(0, items.size() - visible);
        return Mth.clamp(offset, 0, max);
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Only respond to left-click for v1 (matches Button); right/middle
        // clicks fall through unchanged.
        if (button != 0) return false;
        if (isDisabled()) return false; // Inert when disabled (Phase 3b — Item 8).

        // Two routing cases:
        //  (a) Pass 1 (active-overlay): cursor is inside popover bounds
        //      → handle as item-pick.
        //  (b) Pass 2 (hitTest): cursor is inside trigger bounds (default
        //      hitTest = layout bounds) → handle as trigger-toggle.
        //
        // A click outside both the popover and the trigger is never routed
        // HERE (Dropdown's hitTest is the default layout-bounds check, so the
        // dispatcher won't call mouseClicked for an outside click). Outside-
        // click dismiss is handled by notifyClickOutsideOverlay below, which
        // the dispatcher calls on every element for clicks it didn't claim.

        if (open) {
            int[] popover = computePopoverBounds(lastTriggerScreenX, lastTriggerScreenY);
            int px = popover[0], py = popover[1], pw = popover[2], ph = popover[3];
            boolean inPopover = mouseX >= px && mouseX < px + pw
                    && mouseY >= py && mouseY < py + ph;
            if (inPopover) {
                return handlePopoverClick(mouseX, mouseY, popover);
            }
        }

        // Pass 2 case — cursor in trigger bounds. Toggle open/close.
        if (!open) {
            open = true;
            // Reset scroll to top each time we open — predictable UX.
            scrollOffset = 0;
            // Keyboard highlight resets each open; the first arrow key seeds
            // it to the current selection (see keyPressed / initialHighlight).
            highlightedIndex = -1;
            // Capture open-time so long item-text scrolls start at
            // "text beginning visible" when the popover appears.
            popoverOpenMillis = Util.getMillis();
        } else {
            open = false;
        }
        return true;
    }

    /**
     * Outside-click dismiss — closes the open popover when a click lands outside
     * BOTH the popover and the trigger. The dispatcher calls this on every
     * visible element for a click it didn't route into the element (so it fires
     * even when another element consumed the click), making "click away to
     * close" work the way every native popup does. In-trigger clicks (toggle)
     * and in-popover clicks (item pick) are handled by {@link #mouseClicked} via
     * the hit-test / overlay passes, and are no-ops here.
     */
    @Override
    public void notifyClickOutsideOverlay(double mouseX, double mouseY) {
        if (!open) return;
        int[] popover = computePopoverBounds(lastTriggerScreenX, lastTriggerScreenY);
        boolean inPopover = mouseX >= popover[0] && mouseX < popover[0] + popover[2]
                && mouseY >= popover[1] && mouseY < popover[1] + popover[3];
        boolean inTrigger = mouseX >= lastTriggerScreenX
                && mouseX < lastTriggerScreenX + triggerWidth
                && mouseY >= lastTriggerScreenY
                && mouseY < lastTriggerScreenY + triggerHeight;
        if (!inPopover && !inTrigger) open = false;
    }

    /**
     * Handles a click inside the popover bounds (Pass 1 — exclusive
     * overlay claim). Maps Y to row index; ignores clicks on the
     * scrollbar column (consumed but no-op so the popover stays open).
     */
    private boolean handlePopoverClick(double mouseX, double mouseY, int[] popover) {
        int px = popover[0], py = popover[1], pw = popover[2], ph = popover[3];

        boolean scrollable = items.size() > maxVisibleItems;
        int textRightX = px + pw - 1 - (scrollable ? SCROLLBAR_W : 0);
        if (mouseX >= textRightX) {
            // Scrollbar click — wheel scroll is the primary path; consume
            // here just to prevent the click from being misinterpreted as
            // a row click. No state change.
            return true;
        }

        int rowYRel = (int) (mouseY - py - 1);    // -1 for top border
        int rowIndex = clampScrollOffset(scrollOffset) + rowYRel / ROW_HEIGHT;
        if (rowIndex >= 0 && rowIndex < items.size()) {
            T picked = items.get(rowIndex);
            T currentSelection = selectionSupplier.get();
            // Only fire consumer if selection actually changed — saves
            // consumer the equality check on every click.
            if (currentSelection == null || !currentSelection.equals(picked)) {
                selectionConsumer.accept(picked);
            }
            open = false;
            return true;
        }
        // Click between rows or outside row range (top/bottom border) — close.
        open = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (isDisabled()) return false; // Inert when disabled (Phase 3b — Item 8).
        // Pass 1 dispatcher routes scrolls in the popover bounds here
        // (when popover is open). Outside the popover, dispatch falls
        // through to other elements via Pass 2 — Dropdown doesn't claim
        // scroll over its trigger.
        if (!open) return false;

        // Scroll math — vanilla SuggestionsList uses signum on scrollY for
        // single-row stepping per wheel notch. Match that convention.
        if (items.size() <= maxVisibleItems) return true;   // nothing to scroll
        int delta = (scrollY > 0) ? -1 : (scrollY < 0 ? 1 : 0);
        scrollOffset = clampScrollOffset(scrollOffset + delta);
        return true;
    }

    /**
     * Keyboard navigation — the keyboard counterpart to {@link #mouseClicked},
     * completing the dropdown's input model. Scoped to the OPEN popover: a
     * closed dropdown returns false and consumes nothing, because there is no
     * keyboard-focus model to arbitrate which of several on-screen dropdowns
     * owns a key — opening stays mouse-driven, and a closed dropdown never
     * swallows a sibling's or vanilla's keystroke.
     *
     * <ul>
     *   <li><b>Up / Down</b> — move the highlighted row (the first press
     *       reveals the highlight at the current selection); auto-scrolls to
     *       keep it visible.</li>
     *   <li><b>Enter</b> — select the highlighted row and close.</li>
     *   <li><b>Escape</b> — close the popover (consumed, so the underlying
     *       screen stays open: Esc dismisses the dropdown first).</li>
     * </ul>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isDisabled()) return false; // Inert when disabled (Phase 3b — Item 8).
        if (!open || items.isEmpty()) return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                highlightedIndex = (highlightedIndex < 0)
                        ? initialHighlight()
                        : Math.min(items.size() - 1, highlightedIndex + 1);
                ensureHighlightVisible();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                highlightedIndex = (highlightedIndex < 0)
                        ? initialHighlight()
                        : Math.max(0, highlightedIndex - 1);
                ensureHighlightVisible();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (highlightedIndex >= 0 && highlightedIndex < items.size()) {
                    T picked = items.get(highlightedIndex);
                    T current = selectionSupplier.get();
                    // Only fire the consumer if the selection actually changed
                    // (matches handlePopoverClick's equality gate).
                    if (current == null || !current.equals(picked)) {
                        selectionConsumer.accept(picked);
                    }
                }
                open = false;
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                open = false;
                return true;
            }
            default -> {
                return false; // other keys fall through to vanilla
            }
        }
    }

    /**
     * Index the keyboard highlight seeds to on its first arrow press after the
     * popover opens: the current selection's row if present, else the first
     * row.
     */
    private int initialHighlight() {
        T current = selectionSupplier.get();
        if (current != null) {
            int idx = items.indexOf(current);
            if (idx >= 0) return idx;
        }
        return 0;
    }

    /**
     * Scrolls the popover so the keyboard-highlighted row stays within the
     * visible window. Reuses the same scroll-offset clamping as wheel scroll
     * ({@link #mouseScrolled}).
     */
    private void ensureHighlightVisible() {
        if (highlightedIndex < 0) return;
        int visible = visibleRowCount();
        int first = clampScrollOffset(scrollOffset);
        if (highlightedIndex < first) {
            scrollOffset = clampScrollOffset(highlightedIndex);
        } else if (highlightedIndex >= first + visible) {
            scrollOffset = clampScrollOffset(highlightedIndex - visible + 1);
        }
    }

    // ── Trigger screen-position cache ──────────────────────────────────
    //
    // mouseClicked / mouseScrolled don't receive the panel content origin
    // (only render does — via RenderContext.originX/originY). Cache the
    // trigger's screen-space top-left during render so input-handling
    // methods can reconstruct popover bounds without re-deriving panel
    // layout state.
    //
    // Race-safety: render fires on the render thread; input fires on the
    // input thread. The cache is updated each frame; staleness window is
    // one frame, which is sub-perceptible (~16ms at 60 Hz). volatile so
    // input-thread reads see the latest write.

    private volatile int lastTriggerScreenX = 0;
    private volatile int lastTriggerScreenY = 0;

    // ── Builder ────────────────────────────────────────────────────────

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public static final class Builder<T> {
        private int childX = 0;
        private int childY = 0;
        private int triggerWidth = -1;
        private int triggerHeight = -1;
        private @Nullable List<T> items = null;
        private @Nullable Function<T, Component> labelFn = null;
        private @Nullable Supplier<@Nullable T> selectionSupplier = null;
        private @Nullable Consumer<T> selectionConsumer = null;
        private int maxVisibleItems = DEFAULT_MAX_VISIBLE;
        private @Nullable Function<T, Component> itemTooltipFn = null;
        private ControlStyle controlStyle = ControlStyle.MK;
        private @Nullable BooleanSupplier disabledWhen = null;

        private Builder() {}

        /**
         * Phase 18s follow-up — selects the trigger's visual style.
         * {@link ControlStyle#MK} (default) uses MenuKit's RAISED-panel
         * look; {@link ControlStyle#VANILLA} uses Minecraft's standard
         * widget/button sprite atlas (square corners, gray gradient).
         * The popover always stays RAISED regardless — popover is a
         * container for choices, not itself a button.
         */
        public Builder<T> style(ControlStyle style) {
            this.controlStyle = (style != null) ? style : ControlStyle.MK;
            return this;
        }

        /** Panel-local position. Default (0, 0). */
        public Builder<T> at(int childX, int childY) {
            this.childX = childX;
            this.childY = childY;
            return this;
        }

        /**
         * Required: trigger width × height in pixels. Vanilla button-style
         * default is 120×20. Popover width matches trigger width.
         */
        public Builder<T> triggerSize(int width, int height) {
            this.triggerWidth = width;
            this.triggerHeight = height;
            return this;
        }

        /**
         * Required: the items to display in the popover. Defensively copied
         * at build time — mutating the supplied list post-build does not
         * affect the dropdown. Empty list throws at {@link #build()}.
         */
        public Builder<T> items(List<T> items) {
            this.items = Objects.requireNonNull(items, "items must not be null");
            return this;
        }

        /**
         * Required: function to produce the displayed text for an item.
         * Used both for the trigger (with current selection) and for each
         * popover row.
         */
        public Builder<T> label(Function<T, Component> labelFn) {
            this.labelFn = Objects.requireNonNull(labelFn, "labelFn must not be null");
            return this;
        }

        /**
         * Required: lens pair for the current selection. Library reads
         * supplier each frame to render the trigger label; library calls
         * consumer when the user picks an item from the popover.
         *
         * <p>Selection identity uses {@code T.equals()}. {@code null}
         * supplier values OK (renders empty trigger). Consumer is invoked
         * only when the picked item differs from the current selection.
         */
        public Builder<T> selection(Supplier<@Nullable T> supplier, Consumer<T> consumer) {
            this.selectionSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
            this.selectionConsumer = Objects.requireNonNull(consumer, "consumer must not be null");
            return this;
        }

        /**
         * Optional: cap the number of popover rows visible at once.
         * When {@code items.size() > maxVisibleItems}, an internal
         * scrollbar appears on the popover's right edge. Default 8.
         */
        public Builder<T> maxVisibleItems(int n) {
            if (n <= 0) {
                throw new IllegalArgumentException(
                        "maxVisibleItems must be positive, got " + n);
            }
            this.maxVisibleItems = n;
            return this;
        }

        /**
         * Optional: per-item tooltip function. When set, hovering a
         * popover row queues {@code fn.apply(item)} as the next-frame
         * tooltip — companion to the trigger-level {@link Dropdown#tooltip}
         * helper. Trigger and item tooltips are mutually exclusive in
         * time (trigger fires only when popover is closed; item fires
         * only when open), so they coexist without competing.
         *
         * <p>Returning {@code null} from {@code fn} for a given item
         * suppresses the tooltip for that row.
         */
        public Builder<T> itemTooltip(Function<T, Component> fn) {
            this.itemTooltipFn = Objects.requireNonNull(fn, "itemTooltipFn must not be null");
            return this;
        }

        /**
         * Optional disabled predicate (Phase 3b — Item 8). When it returns
         * true, the trigger renders disabled (VANILLA → {@code
         * widget/button_disabled} sprite; MK → {@link PanelStyle#DARK} panel)
         * and all interaction is ignored — clicks/scroll/keys no-op and any
         * open popover force-closes. Per-frame predicate shape, matching
         * Button/Toggle's {@code disabledWhen}. Default: always enabled.
         */
        public Builder<T> disabledWhen(BooleanSupplier disabledWhen) {
            this.disabledWhen = Objects.requireNonNull(disabledWhen, "disabledWhen must not be null");
            return this;
        }

        public Dropdown<T> build() {
            if (triggerWidth <= 0 || triggerHeight <= 0) {
                throw new IllegalStateException(
                        "Dropdown.Builder: .triggerSize(w, h) must be called with positive values; "
                        + "got width=" + triggerWidth + ", height=" + triggerHeight);
            }
            if (items == null) {
                throw new IllegalStateException(
                        "Dropdown.Builder: .items(list) is required");
            }
            if (items.isEmpty()) {
                // Per round-1 verdict (M18 contract): empty items list
                // throws at build time. Empty-state-popover is a feature-
                // defer (would need a separate "no items" empty state +
                // disabled-trigger semantic).
                throw new IllegalStateException(
                        "Dropdown.Builder: .items(list) must contain at least one element; "
                        + "empty-state popover is not supported in v1");
            }
            if (labelFn == null) {
                throw new IllegalStateException(
                        "Dropdown.Builder: .label(fn) is required");
            }
            if (selectionSupplier == null || selectionConsumer == null) {
                throw new IllegalStateException(
                        "Dropdown.Builder: .selection(supplier, consumer) is required");
            }
            return new Dropdown<>(this);
        }

        /**
         * Layout terminal (Phase 3b — Item 6). Returns an {@link ElementSpec}
         * for use in {@link com.trevorschoeny.menukit.core.layout.Row} /
         * {@link com.trevorschoeny.menukit.core.layout.Column}.
         *
         * <p><b>Reported dimensions are the TRIGGER footprint</b>
         * ({@code .triggerSize(w, h)}), not the popover. By design the popover
         * overflows the trigger's layout bounds — it renders via
         * {@link Dropdown#renderOverlay} on the overlay pass and claims its own
         * input region via {@link Dropdown#getActiveOverlayBounds}, exactly
         * like its hit-test/overlay model. So Row/Column reserves only the
         * trigger footprint; the open popover paints on top of whatever follows
         * it in the layout, which is the intended behavior (popovers are
         * transient and z-ordered above siblings).
         *
         * <p>The layout helper calls {@link ElementSpec#at(int, int)}, which
         * re-runs this builder's configuration positioned at the computed
         * coordinates.
         */
        public ElementSpec spec() {
            if (triggerWidth <= 0 || triggerHeight <= 0) {
                throw new IllegalStateException(
                        "Dropdown.Builder.spec(): .triggerSize(w, h) must be called with positive values; "
                        + "got width=" + triggerWidth + ", height=" + triggerHeight);
            }
            if (items == null || items.isEmpty()) {
                throw new IllegalStateException(
                        "Dropdown.Builder.spec(): .items(list) is required and must be non-empty");
            }
            if (labelFn == null) {
                throw new IllegalStateException("Dropdown.Builder.spec(): .label(fn) is required");
            }
            if (selectionSupplier == null || selectionConsumer == null) {
                throw new IllegalStateException(
                        "Dropdown.Builder.spec(): .selection(supplier, consumer) is required");
            }
            // Snapshot config; each at(x,y) rebuilds a fresh, correctly-
            // positioned Dropdown. (childX/childY fixed at construction per
            // THESIS Principle 4 — ElementSpec is the deferred-position path.)
            final int w = triggerWidth, h = triggerHeight;
            final List<T> it = items;
            final Function<T, Component> lf = labelFn;
            final Supplier<@Nullable T> ss = selectionSupplier;
            final Consumer<T> sc = selectionConsumer;
            final int mvi = maxVisibleItems;
            final Function<T, Component> ttf = itemTooltipFn;
            final ControlStyle cs = controlStyle;
            final BooleanSupplier dw = disabledWhen;
            return new ElementSpec() {
                @Override public int width()  { return w; }
                @Override public int height() { return h; }
                @Override public PanelElement at(int x, int y) {
                    Builder<T> b = Dropdown.<T>builder().at(x, y).triggerSize(w, h)
                            .items(it).label(lf).selection(ss, sc)
                            .maxVisibleItems(mvi).style(cs);
                    if (ttf != null) b.itemTooltip(ttf);
                    if (dw != null) b.disabledWhen(dw);
                    return b.build();
                }
            };
        }
    }
}
