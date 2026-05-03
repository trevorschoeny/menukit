package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
 * <h3>Render order discipline (sharp edge of bespoke composition)</h3>
 *
 * Because the popover renders via direct {@code ctx.graphics()} calls
 * inside Dropdown's {@link #render} (rather than as a separate render
 * pass), it follows normal element declaration order. <b>Two consumer
 * rules:</b>
 *
 * <ol>
 *   <li><b>Dropdown must be the LAST element declared</b> in any panel
 *       containing it; later elements paint on top of the popover.</li>
 *   <li><b>If multiple Dropdowns coexist in a panel, the LAST-declared
 *       one wins Z-order</b> — earlier dropdowns' popovers paint under
 *       later elements (including other Dropdowns' triggers/popovers).</li>
 * </ol>
 *
 * Future fold-on-evidence: if multi-dropdown panels become common, hoist
 * popover render to a separate {@code renderOverlay} pass on PanelElement.
 * Not in v1 — single new primitive ({@code hitTest}) is the right scope.
 *
 * <h3>Cross-context applicability</h3>
 *
 * <ul>
 *   <li><b>MenuContext (inventory menus):</b> yes — settings panels,
 *       enum selectors.</li>
 *   <li><b>StandaloneContext (MenuKitScreen):</b> yes — full-screen
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
public final class Dropdown<T> implements PanelElement {

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

    private final int childX;
    private final int childY;
    private final int triggerWidth;
    private final int triggerHeight;
    private final List<T> items;
    private final Function<T, Component> labelFn;
    private final Supplier<@Nullable T> selectionSupplier;
    private final Consumer<T> selectionConsumer;
    private final int maxVisibleItems;

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
    }

    // ── PanelElement protocol ──────────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth()  { return triggerWidth; }
    @Override public int getHeight() { return triggerHeight; }

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

        // ── Trigger ────────────────────────────────────────────────────
        // Render a Button-style raised background. Hover state computed
        // against trigger bounds (popover hover is separate). Pressed-
        // look while popover open visually communicates open state.
        boolean triggerHovered = ctx.isHovered(childX, childY, triggerWidth, triggerHeight);
        renderTriggerBackground(ctx.graphics(), triggerX, triggerY, triggerHovered);
        renderTriggerContent(ctx.graphics(), triggerX, triggerY);

        // ── Popover ────────────────────────────────────────────────────
        // Renders only when open. Direct ctx.graphics() draws — popover is
        // OUTSIDE element layout bounds so panel auto-size doesn't grow
        // with it. See render-order discipline in class JavaDoc.
        if (open) {
            renderPopover(ctx, triggerX, triggerY);
        }
    }

    private void renderTriggerBackground(GuiGraphics graphics, int sx, int sy, boolean hovered) {
        // Same look as Button: RAISED panel + translucent highlight on hover.
        // When popover is open, render with the open-look (no extra hover
        // highlight; the popover itself signals interactive state).
        PanelRendering.renderPanel(graphics, sx, sy, triggerWidth, triggerHeight, PanelStyle.RAISED);
        if (hovered && !open) {
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

        // Truncate-by-width if the text overflows the available trigger
        // space (trigger width minus chevron reservation minus padding).
        int textAreaW = triggerWidth - CHEVRON_RESERVED_W - 2 * TRIGGER_TEXT_PAD_X;
        Component drawn = (font.width(text) > textAreaW)
                ? truncateToWidth(font, text, textAreaW)
                : text;

        // Vertically center text inside trigger; left-align with text padding.
        int textX = sx + TRIGGER_TEXT_PAD_X;
        int textY = sy + (triggerHeight - font.lineHeight) / 2;
        graphics.drawString(font, drawn, textX, textY, COLOR_TEXT, true);

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

        // Background — RAISED panel for visual continuity with trigger.
        PanelRendering.renderPanel(graphics, px, py, pw, ph, PanelStyle.RAISED);

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
            }
            // Selection highlight (this item == current selection)
            if (currentSelection != null && currentSelection.equals(item)) {
                graphics.fill(px + 1, rowY,
                        px + pw - 1 - (scrollable ? SCROLLBAR_W : 0), rowY + ROW_HEIGHT,
                        COLOR_SELECTED_OVERLAY);
            }

            // Row text — truncate-by-width if it overflows the row content area.
            Component itemText = labelFn.apply(item);
            Component drawn = (font.width(itemText) > rowsContentW)
                    ? truncateToWidth(font, itemText, rowsContentW)
                    : itemText;
            int textX = px + 1 + POPOVER_TEXT_PAD_X;
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, drawn, textX, textY, COLOR_TEXT, true);
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

    /**
     * Returns the largest prefix of {@code text} that fits within
     * {@code maxWidth} pixels, with a trailing ellipsis. Matches the
     * common vanilla pattern (StringSplitter / ellipsis suffix).
     */
    private Component truncateToWidth(Font font, Component text, int maxWidth) {
        String s = text.getString();
        String ellipsis = "...";
        int ellW = font.width(ellipsis);
        if (maxWidth <= ellW) return Component.literal(ellipsis);
        // Greedy reduce
        for (int len = s.length() - 1; len >= 0; len--) {
            String prefix = s.substring(0, len) + ellipsis;
            if (font.width(prefix) <= maxWidth) {
                return Component.literal(prefix);
            }
        }
        return Component.literal(ellipsis);
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Only respond to left-click for v1 (matches Button); right/middle
        // clicks fall through unchanged.
        if (button != 0) return false;

        // Two routing cases:
        //  (a) Pass 1 (active-overlay): cursor is inside popover bounds
        //      → handle as item-pick.
        //  (b) Pass 2 (hitTest): cursor is inside trigger bounds (default
        //      hitTest = layout bounds) → handle as trigger-toggle.
        //
        // Click outside both is impossible to reach here in v1 (Dropdown's
        // hitTest is the default layout-bounds check; no extended claim).
        // Outside-click-dismiss is deferred to fold-on-evidence — vanilla
        // SuggestionsList doesn't auto-dismiss either; click-trigger-to-
        // toggle and Esc-closes-screen are the dismiss paths.

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
        } else {
            open = false;
        }
        return true;
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

        private Builder() {}

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
    }
}
