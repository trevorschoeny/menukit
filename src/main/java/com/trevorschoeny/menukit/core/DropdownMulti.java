package com.trevorschoeny.menukit.core;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Multi-selection dropdown control. Sibling to {@link Dropdown}, NOT a
 * subclass — {@code Dropdown} is {@code final}, and the selection model
 * differs fundamentally (lens over {@link Set} rather than over a single
 * nullable {@code T}). Honors the "separate primitives" deferral named
 * in {@link Dropdown}'s class doc.
 *
 * <h3>Lens pattern — Supplier&lt;Set&lt;T&gt;&gt; + toggle Consumer&lt;T&gt;</h3>
 *
 * Same shape as {@link Dropdown}'s single-select lens, scaled up to a Set:
 * library reads the supplier each frame to render the trigger summary +
 * each row's selected/unselected state; library calls the consumer with
 * the clicked item, and the consumer toggles its own set membership.
 * Library never mutates the consumer's set. Selection identity via
 * {@code T.equals()} (matches {@link Set#contains}).
 *
 * <h3>Trigger label</h3>
 *
 * Single-select reused its {@code labelFn} for the trigger ("show the
 * label of the current selection"). Multi-select has no single
 * universally-correct trigger text: empty? "N selected"? Comma-joined?
 * Consumers vary. So {@link Builder#triggerLabel} is required (not
 * optional) and receives the current {@link Set} — consumer decides the
 * summary shape entirely.
 *
 * <h3>Click behavior</h3>
 *
 * <ul>
 *   <li><b>Trigger click</b> — toggles popover open/closed (same as
 *       single-select).</li>
 *   <li><b>Row click</b> — fires the toggle Consumer with that item,
 *       popover <b>stays open</b>. Distinguishing feature vs single-
 *       select (which closes on row pick). Lets the user toggle multiple
 *       items in one popover-open session.</li>
 *   <li><b>Action-row click</b> (Select all / Clear all, when configured)
 *       — fires the supplied {@link Runnable}, popover stays open. The
 *       Runnable does the bulk set op (one call vs N consumer fires).</li>
 *   <li><b>Click outside</b> — not auto-dismissed (matches single-select).
 *       Close by clicking the trigger again, or Esc-closes-screen via
 *       vanilla.</li>
 * </ul>
 *
 * <h3>Visual indicator</h3>
 *
 * Each regular row reserves a left-edge column for a vanilla
 * {@code minecraft:icon/checkmark} sprite (9×8). Selected rows render
 * the checkmark + the existing {@code COLOR_SELECTED_OVERLAY} highlight;
 * unselected rows render the column empty (preserved width keeps text
 * left-aligned across both states). Action rows skip the checkmark
 * column (they're stateless actions, not toggleable items) and render
 * their label in italic for visual differentiation.
 *
 * <h3>API surface</h3>
 *
 * <pre>{@code
 * Set<String> selected = new HashSet<>();
 *
 * DropdownMulti<String> dd = DropdownMulti.<String>builder()
 *     .at(0, 0)
 *     .triggerSize(160, 20)
 *     .items(List.of("Apple", "Banana", "Cherry", "Date"))
 *     .label(s -> Component.literal(s))
 *     .triggerLabel(set -> Component.literal(
 *         set.isEmpty() ? "None selected" : set.size() + " selected"))
 *     .selection(() -> selected, item -> {
 *         if (selected.contains(item)) selected.remove(item);
 *         else                          selected.add(item);
 *     })
 *     .selectAllRow(Component.literal("Select all"),
 *                   () -> selected.addAll(ALL_ITEMS))
 *     .clearAllRow(Component.literal("Clear all"),
 *                  selected::clear)
 *     .maxVisibleItems(8)
 *     .build();
 * }</pre>
 *
 * <h3>Render order discipline</h3>
 *
 * Same constraint as {@link Dropdown} — the popover renders via direct
 * {@code ctx.graphics()} calls inside the element's {@code render()},
 * so dropdowns must be the LAST element declared in their containing
 * panel. See {@link Dropdown}'s class doc §"Render order discipline" for
 * the full discussion.
 *
 * <h3>Cross-context applicability</h3>
 *
 * Same as {@link Dropdown}: yes for MenuContext + StandaloneContext, no
 * for SlotGroupContext + HudContext.
 *
 * @param <T> selection value type; identity via {@code T.equals()}
 */
public final class DropdownMulti<T> extends AbstractPanelElement {

    // ── Layout / render constants ──────────────────────────────────────
    // Mirrors Dropdown's constants. Kept verbatim (rather than imported
    // from a shared layout helper) per fold-on-evidence — there are
    // currently TWO dropdown variants. When a THIRD popover-list lands
    // (search-as-you-type, tree-select), refactor to a shared
    // PopoverList<T> primitive. Until then, duplication is the cheaper
    // structural cost.

    private static final int ROW_HEIGHT = 14;
    private static final int DEFAULT_MAX_VISIBLE = 8;
    private static final int POPOVER_TEXT_PAD_X = 4;
    private static final int TRIGGER_TEXT_PAD_X = 4;
    private static final int CHEVRON_RESERVED_W = 10;
    private static final int SCROLLBAR_W = 4;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HOVER_OVERLAY = 0x40FFFFFF;
    private static final int COLOR_SELECTED_OVERLAY = 0x60FFFFFF;
    private static final int COLOR_SCROLLBAR_THUMB = 0xFFC6C6C6;
    private static final int COLOR_SEPARATOR = 0xFF606060;

    // Checkmark column — vanilla minecraft:icon/checkmark is 9×8. We
    // reserve CHECKMARK_COL_W on the left of each regular row to fit
    // the sprite plus a small gap before the text starts. Action rows
    // don't draw into this column but still reserve it so text columns
    // align across regular + action rows.
    private static final int CHECKMARK_SPRITE_W = 9;
    private static final int CHECKMARK_SPRITE_H = 8;
    private static final int CHECKMARK_COL_W = CHECKMARK_SPRITE_W + 3; // sprite + 3px gap

    // Separator between pinned action rows and scrollable regular rows.
    private static final int SEPARATOR_HEIGHT = 2; // 1px line + 1px breathing

    // ── Immutable config ───────────────────────────────────────────────

    private final int childX;
    private final int childY;
    private final int triggerWidth;
    private final int triggerHeight;
    private final List<T> items;
    private final Function<T, Component> labelFn;
    private final Function<Set<T>, Component> triggerLabelFn;
    private final Supplier<Set<T>> selectionSupplier;
    private final Consumer<T> selectionConsumer;
    private final int maxVisibleItems;

    /** Optional per-item tooltip (hover-row → setTooltipForNextFrame). */
    private final @Nullable Function<T, Component> itemTooltipFn;

    /** Optional "Select all" pinned-top action row. Null if not configured. */
    private final @Nullable Component selectAllLabel;
    private final @Nullable Runnable  selectAllAction;

    /** Optional "Clear all" pinned-top action row. Null if not configured. */
    private final @Nullable Component clearAllLabel;
    private final @Nullable Runnable  clearAllAction;

    // ── Mutable state ──────────────────────────────────────────────────
    // (Mirrors Dropdown's narrow exception. open + scrollOffset are
    // internal UI state; the selection lives on the consumer's Set, not
    // here.)

    private volatile boolean open = false;
    private volatile int scrollOffset = 0;

    // Trigger screen-position cache — see same javadoc on Dropdown's
    // fields for race-safety rationale.
    private volatile int lastTriggerScreenX = 0;
    private volatile int lastTriggerScreenY = 0;

    private DropdownMulti(Builder<T> b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.triggerWidth = b.triggerWidth;
        this.triggerHeight = b.triggerHeight;
        this.items = List.copyOf(b.items);
        this.labelFn = b.labelFn;
        this.triggerLabelFn = b.triggerLabelFn;
        this.selectionSupplier = b.selectionSupplier;
        this.selectionConsumer = b.selectionConsumer;
        this.maxVisibleItems = b.maxVisibleItems;
        this.itemTooltipFn = b.itemTooltipFn;
        this.selectAllLabel = b.selectAllLabel;
        this.selectAllAction = b.selectAllAction;
        this.clearAllLabel = b.clearAllLabel;
        this.clearAllAction = b.clearAllAction;
    }

    // ── PanelElement protocol ──────────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth()  { return triggerWidth; }
    @Override public int getHeight() { return triggerHeight; }

    /**
     * Same active-overlay claim shape as {@link Dropdown}. While the
     * popover is open, declares its bounds as an exclusive modal-area
     * so clicks behind it (paint-occluded elements) stay inert.
     */
    @Override
    public int @Nullable [] getActiveOverlayBounds() {
        if (!open) return null;
        return computePopoverBounds(lastTriggerScreenX, lastTriggerScreenY);
    }

    // ── Render ─────────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int triggerX = ctx.originX() + childX;
        int triggerY = ctx.originY() + childY;

        // Cache for input-thread reads — same pattern as Dropdown.
        this.lastTriggerScreenX = triggerX;
        this.lastTriggerScreenY = triggerY;

        boolean triggerHovered = ctx.isHovered(childX, childY, triggerWidth, triggerHeight);
        renderTriggerBackground(ctx.graphics(), triggerX, triggerY, triggerHovered);
        renderTriggerContent(ctx.graphics(), triggerX, triggerY);

        if (open) {
            renderPopover(ctx, triggerX, triggerY);
        }

        // Trigger-level tooltip — only when popover is closed (popover IS
        // the interactive surface when open; competing tooltip would clutter).
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (triggerHovered && !open && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                ctx.graphics().setTooltipForNextFrame(
                        Minecraft.getInstance().font, ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Chainable configuration (Phase 18r-2: covariant returns) ───────

    @Override
    public DropdownMulti<T> tooltip(Component text) {
        super.tooltip(text);
        return this;
    }

    @Override
    public DropdownMulti<T> tooltip(@Nullable Supplier<Component> supplier) {
        super.tooltip(supplier);
        return this;
    }

    @Override
    public DropdownMulti<T> showWhen(@Nullable Supplier<Boolean> supplier) {
        super.showWhen(supplier);
        return this;
    }

    // ── Trigger paint ──────────────────────────────────────────────────

    private void renderTriggerBackground(GuiGraphics graphics, int sx, int sy, boolean hovered) {
        // Same look as Dropdown / Button: RAISED panel + translucent
        // hover highlight (suppressed when popover is open since the
        // popover itself signals interactive state).
        PanelRendering.renderPanel(graphics, sx, sy, triggerWidth, triggerHeight, PanelStyle.RAISED);
        if (hovered && !open) {
            graphics.fill(sx + 1, sy + 1, sx + triggerWidth - 1, sy + triggerHeight - 1,
                    COLOR_HOVER_OVERLAY);
        }
    }

    private void renderTriggerContent(GuiGraphics graphics, int sx, int sy) {
        Font font = Minecraft.getInstance().font;

        // Trigger label — supplier delivers the current Set<T>, the
        // consumer-supplied triggerLabelFn maps it to displayed text.
        // Set is wrapped Set.copyOf to defend against consumer mutating
        // during the labelFn call (cheap for small selection sets).
        Set<T> currentSelection = selectionSupplier.get();
        Component text = triggerLabelFn.apply(Set.copyOf(currentSelection));

        int textAreaW = triggerWidth - CHEVRON_RESERVED_W - 2 * TRIGGER_TEXT_PAD_X;
        Component drawn = (font.width(text) > textAreaW)
                ? truncateToWidth(font, text, textAreaW)
                : text;

        int textX = sx + TRIGGER_TEXT_PAD_X;
        int textY = sy + (triggerHeight - font.lineHeight) / 2;
        graphics.drawString(font, drawn, textX, textY, COLOR_TEXT, true);

        // Chevron — same convention as Dropdown.
        Component chevron = Component.literal(open ? "▲" : "▼");
        int chevW = font.width(chevron);
        int chevX = sx + triggerWidth - CHEVRON_RESERVED_W + (CHEVRON_RESERVED_W - chevW) / 2 - 1;
        int chevY = sy + (triggerHeight - font.lineHeight) / 2;
        graphics.drawString(font, chevron, chevX, chevY, COLOR_TEXT, true);
    }

    // ── Popover paint ──────────────────────────────────────────────────

    /**
     * Renders the popover with optional pinned action rows at the top
     * (Select all / Clear all), a separator below them, and the
     * scrollable regular-items region beneath.
     *
     * <p>Layout (top to bottom):
     * <pre>
     * ┌────────────────────────┐   py
     * │ Select all  (italic)   │   actionRow 0  (if configured)
     * │ Clear all   (italic)   │   actionRow 1  (if configured)
     * │ ──────────────────     │   separator    (if any action rows)
     * │ ✓ Item A               │   regular row 0  (scrollable region)
     * │   Item B               │   regular row 1
     * │   ...                  │   ...
     * └────────────────────────┘   py + popoverHeight
     * </pre>
     */
    private void renderPopover(RenderContext ctx, int triggerX, int triggerY) {
        GuiGraphics graphics = ctx.graphics();
        int[] popover = computePopoverBounds(triggerX, triggerY);
        int px = popover[0], py = popover[1], pw = popover[2], ph = popover[3];

        // Background.
        PanelRendering.renderPanel(graphics, px, py, pw, ph, PanelStyle.RAISED);

        Font font = Minecraft.getInstance().font;
        Set<T> currentSelection = selectionSupplier.get();

        int actionRowCount = actionRowCount();
        int regularRowsStartY = py + 1 + actionRowCount * ROW_HEIGHT
                + (actionRowCount > 0 ? SEPARATOR_HEIGHT : 0);

        // ── Action rows (pinned, italic, no checkmark column) ─────────
        // Hover highlight on action rows uses the same overlay as item
        // rows; click maps to the supplied Runnable.
        int actionRowsContentW = pw - 2 - 2 * POPOVER_TEXT_PAD_X;
        int actionRowYCursor = py + 1;
        if (selectAllLabel != null) {
            renderActionRow(graphics, font, ctx, px, actionRowYCursor, pw,
                    selectAllLabel, actionRowsContentW);
            actionRowYCursor += ROW_HEIGHT;
        }
        if (clearAllLabel != null) {
            renderActionRow(graphics, font, ctx, px, actionRowYCursor, pw,
                    clearAllLabel, actionRowsContentW);
            actionRowYCursor += ROW_HEIGHT;
        }

        // ── Separator ─────────────────────────────────────────────────
        if (actionRowCount > 0) {
            int sepY = py + 1 + actionRowCount * ROW_HEIGHT;
            graphics.fill(px + POPOVER_TEXT_PAD_X, sepY,
                    px + pw - POPOVER_TEXT_PAD_X, sepY + 1,
                    COLOR_SEPARATOR);
        }

        // ── Regular rows (scrollable) ─────────────────────────────────
        int visibleCount = visibleRowCount();
        int firstRow = clampScrollOffset(scrollOffset);
        int lastRow = Math.min(items.size(), firstRow + visibleCount);

        boolean scrollable = items.size() > maxVisibleItems;
        // Text content width — minus border, minus padding, minus
        // checkmark column, minus scrollbar reserve when scrollable.
        int rowsContentW = pw - 2 - 2 * POPOVER_TEXT_PAD_X - CHECKMARK_COL_W
                - (scrollable ? SCROLLBAR_W : 0);

        for (int i = firstRow; i < lastRow; i++) {
            int rowY = regularRowsStartY + (i - firstRow) * ROW_HEIGHT;
            T item = items.get(i);

            boolean rowHovered = ctx.hasMouseInput()
                    && ctx.mouseX() >= px + 1 && ctx.mouseX() < px + pw - 1
                    && ctx.mouseY() >= rowY && ctx.mouseY() < rowY + ROW_HEIGHT;
            if (rowHovered) {
                graphics.fill(px + 1, rowY,
                        px + pw - 1 - (scrollable ? SCROLLBAR_W : 0), rowY + ROW_HEIGHT,
                        COLOR_HOVER_OVERLAY);
                if (itemTooltipFn != null) {
                    Component ttText = itemTooltipFn.apply(item);
                    if (ttText != null) {
                        graphics.setTooltipForNextFrame(
                                font, ttText, ctx.mouseX(), ctx.mouseY());
                    }
                }
            }

            boolean isSelected = currentSelection.contains(item);
            if (isSelected) {
                graphics.fill(px + 1, rowY,
                        px + pw - 1 - (scrollable ? SCROLLBAR_W : 0), rowY + ROW_HEIGHT,
                        COLOR_SELECTED_OVERLAY);
                // Checkmark sprite — vertically centered in the row,
                // left-edge of the checkmark column.
                int markX = px + 1 + POPOVER_TEXT_PAD_X;
                int markY = rowY + (ROW_HEIGHT - CHECKMARK_SPRITE_H) / 2;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        Checkbox.CHECKMARK_SPRITE,
                        markX, markY, CHECKMARK_SPRITE_W, CHECKMARK_SPRITE_H);
            }

            Component itemText = labelFn.apply(item);
            Component drawn = (font.width(itemText) > rowsContentW)
                    ? truncateToWidth(font, itemText, rowsContentW)
                    : itemText;
            int textX = px + 1 + POPOVER_TEXT_PAD_X + CHECKMARK_COL_W;
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, drawn, textX, textY, COLOR_TEXT, true);
        }

        // ── Scrollbar (regular-rows region only) ──────────────────────
        if (scrollable) {
            int trackX = px + pw - 1 - SCROLLBAR_W;
            int trackY = regularRowsStartY;
            int trackH = visibleCount * ROW_HEIGHT;
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
     * Renders one pinned action row (Select all / Clear all). Italic
     * label, no checkmark column, full-width hover highlight, no
     * selection highlight (actions are stateless).
     */
    private void renderActionRow(GuiGraphics graphics, Font font, RenderContext ctx,
                                  int px, int rowY, int pw,
                                  Component label, int contentW) {
        boolean rowHovered = ctx.hasMouseInput()
                && ctx.mouseX() >= px + 1 && ctx.mouseX() < px + pw - 1
                && ctx.mouseY() >= rowY && ctx.mouseY() < rowY + ROW_HEIGHT;
        if (rowHovered) {
            graphics.fill(px + 1, rowY, px + pw - 1, rowY + ROW_HEIGHT,
                    COLOR_HOVER_OVERLAY);
        }

        MutableComponent italic = Component.empty()
                .append(label).withStyle(ChatFormatting.ITALIC);
        Component drawn = (font.width(italic) > contentW)
                ? truncateToWidth(font, italic, contentW)
                : italic;
        int textX = px + 1 + POPOVER_TEXT_PAD_X;
        int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
        graphics.drawString(font, drawn, textX, textY, COLOR_TEXT, true);
    }

    // ── Popover geometry ───────────────────────────────────────────────

    private int[] computePopoverBounds(int triggerX, int triggerY) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int popoverW = triggerWidth;
        int popoverH = popoverHeight();

        int popoverX = Mth.clamp(triggerX, 0, Math.max(0, screenW - popoverW));

        int below = triggerY + triggerHeight;
        int popoverY;
        if (below + popoverH <= screenH) {
            popoverY = below;
        } else {
            int above = triggerY - popoverH;
            popoverY = (above >= 0) ? above : 0;
        }
        return new int[]{popoverX, popoverY, popoverW, popoverH};
    }

    /**
     * Total popover pixel height — 1px top border + N action rows +
     * separator (if any action rows) + visible regular rows + 1px
     * bottom border.
     */
    private int popoverHeight() {
        int rc = actionRowCount();
        return 2                                       // borders
                + rc * ROW_HEIGHT                      // action rows
                + (rc > 0 ? SEPARATOR_HEIGHT : 0)      // separator
                + visibleRowCount() * ROW_HEIGHT;      // regular rows
    }

    private int visibleRowCount() {
        return Math.min(items.size(), maxVisibleItems);
    }

    private int actionRowCount() {
        return (selectAllLabel != null ? 1 : 0)
             + (clearAllLabel != null ? 1 : 0);
    }

    private int clampScrollOffset(int offset) {
        int visible = visibleRowCount();
        int max = Math.max(0, items.size() - visible);
        return Mth.clamp(offset, 0, max);
    }

    private Component truncateToWidth(Font font, Component text, int maxWidth) {
        // Mirrors Dropdown.truncateToWidth — simple char-by-char trim
        // with "…" suffix. Component-vs-String preserves formatting
        // because we strip to plain text first.
        String s = text.getString();
        if (font.width(s) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisW = font.width(ellipsis);
        int budget = maxWidth - ellipsisW;
        if (budget <= 0) return Component.literal(ellipsis);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int cw = font.width(ch);
            if (w + cw > budget) break;
            sb.append(ch);
            w += cw;
        }
        return Component.literal(sb.toString() + ellipsis);
    }

    // ── Input dispatch ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (open) {
            int[] popover = computePopoverBounds(lastTriggerScreenX, lastTriggerScreenY);
            int px = popover[0], py = popover[1], pw = popover[2], ph = popover[3];
            boolean inPopover = mouseX >= px && mouseX < px + pw
                    && mouseY >= py && mouseY < py + ph;
            if (inPopover) {
                return handlePopoverClick(mouseX, mouseY, popover);
            }
        }

        // Trigger click — toggle open/close.
        if (!open) {
            open = true;
            scrollOffset = 0;
        } else {
            open = false;
        }
        return true;
    }

    /**
     * Maps a click inside the popover to either an action row (Select
     * all / Clear all → fires Runnable, stays open), a regular item row
     * (→ fires toggle Consumer, stays open), or the scrollbar / separator
     * (no-op, consumed).
     */
    private boolean handlePopoverClick(double mouseX, double mouseY, int[] popover) {
        int px = popover[0], py = popover[1], pw = popover[2], ph = popover[3];

        boolean scrollable = items.size() > maxVisibleItems;
        int textRightX = px + pw - 1 - (scrollable ? SCROLLBAR_W : 0);
        if (mouseX >= textRightX) {
            // Scrollbar column — consume so it doesn't fall through to a
            // row, but don't change state. Wheel-scroll is the primary
            // scroll path (Dropdown precedent).
            return true;
        }

        int rowYRel = (int) (mouseY - py - 1);  // -1 for top border
        int actionRowCount = actionRowCount();

        // Action rows region — first N pixel-rows after the top border.
        int actionRegionEnd = actionRowCount * ROW_HEIGHT;
        if (rowYRel < actionRegionEnd) {
            int actionIdx = rowYRel / ROW_HEIGHT;
            fireActionRow(actionIdx);
            // Stay open — multi-select session continues. Fall through.
            return true;
        }

        // Separator region — pure visual; consume click.
        int regularRegionStart = actionRegionEnd + (actionRowCount > 0 ? SEPARATOR_HEIGHT : 0);
        if (rowYRel < regularRegionStart) {
            return true;
        }

        // Regular item region.
        int rowYInRegular = rowYRel - regularRegionStart;
        int rowIndex = clampScrollOffset(scrollOffset) + rowYInRegular / ROW_HEIGHT;
        if (rowIndex >= 0 && rowIndex < items.size()) {
            T picked = items.get(rowIndex);
            // Fire the toggle Consumer. Library doesn't read-then-decide
            // — consumer is the source of truth, consumer flips its own
            // set, next frame the row's selected state re-renders.
            selectionConsumer.accept(picked);
            // Stay open — distinguishing multi-select behavior.
            return true;
        }
        // Click between rows (very narrow gap) — consume, no-op.
        return true;
    }

    /**
     * Dispatches an action-row click (index relative to the configured
     * Select-all / Clear-all order). Action rows are configured at build
     * time so the index-to-runnable mapping is stable.
     */
    private void fireActionRow(int actionIdx) {
        // The action rows render in this fixed order: select-all first
        // (if set), clear-all second (if set). Walking the same order
        // here keeps the index mapping consistent with the render path.
        int cursor = 0;
        if (selectAllLabel != null) {
            if (cursor == actionIdx && selectAllAction != null) {
                selectAllAction.run();
                return;
            }
            cursor++;
        }
        if (clearAllLabel != null) {
            if (cursor == actionIdx && clearAllAction != null) {
                clearAllAction.run();
                return;
            }
        }
        // Out-of-range — silently no-op (defensive; shouldn't happen
        // given handlePopoverClick's index bounds).
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (!open) return false;
        if (items.size() <= maxVisibleItems) return true;
        int delta = (scrollY > 0) ? -1 : (scrollY < 0 ? 1 : 0);
        scrollOffset = clampScrollOffset(scrollOffset + delta);
        return true;
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public static final class Builder<T> {
        private int childX = 0;
        private int childY = 0;
        private int triggerWidth = -1;
        private int triggerHeight = -1;
        private @Nullable List<T> items = null;
        private @Nullable Function<T, Component> labelFn = null;
        private @Nullable Function<Set<T>, Component> triggerLabelFn = null;
        private @Nullable Supplier<Set<T>> selectionSupplier = null;
        private @Nullable Consumer<T> selectionConsumer = null;
        private int maxVisibleItems = DEFAULT_MAX_VISIBLE;
        private @Nullable Function<T, Component> itemTooltipFn = null;
        private @Nullable Component selectAllLabel = null;
        private @Nullable Runnable  selectAllAction = null;
        private @Nullable Component clearAllLabel = null;
        private @Nullable Runnable  clearAllAction = null;

        private Builder() {}

        /** Panel-local position. Default (0, 0). */
        public Builder<T> at(int childX, int childY) {
            this.childX = childX;
            this.childY = childY;
            return this;
        }

        /** Required: trigger width × height in pixels. Popover width matches trigger width. */
        public Builder<T> triggerSize(int width, int height) {
            this.triggerWidth = width;
            this.triggerHeight = height;
            return this;
        }

        /**
         * Required: the items to display in the popover. Defensively
         * copied at build time; mutating post-build does not affect the
         * dropdown. Empty list throws at {@link #build()}.
         */
        public Builder<T> items(List<T> items) {
            this.items = Objects.requireNonNull(items, "items must not be null");
            return this;
        }

        /**
         * Required: per-row label function. Used for each item row in
         * the popover (NOT for the trigger — see {@link #triggerLabel}).
         */
        public Builder<T> label(Function<T, Component> labelFn) {
            this.labelFn = Objects.requireNonNull(labelFn, "labelFn must not be null");
            return this;
        }

        /**
         * Required: trigger summary function. Receives the current
         * selection {@link Set} (defensively-copied snapshot) and
         * returns the trigger's displayed text. Consumers decide the
         * summary shape — "{n} selected", comma-joined labels, etc.
         */
        public Builder<T> triggerLabel(Function<Set<T>, Component> fn) {
            this.triggerLabelFn = Objects.requireNonNull(fn, "triggerLabelFn must not be null");
            return this;
        }

        /**
         * Required: lens pair. Supplier returns the consumer's current
         * selection Set (library reads each frame for trigger label +
         * row highlights); Consumer is invoked with the clicked item
         * and is expected to toggle its set membership.
         *
         * <p>Library never mutates the supplied Set. Identity uses
         * {@code T.equals()} (matches {@link Set#contains}).
         */
        public Builder<T> selection(Supplier<Set<T>> supplier, Consumer<T> consumer) {
            this.selectionSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
            this.selectionConsumer = Objects.requireNonNull(consumer, "consumer must not be null");
            return this;
        }

        /** Optional: cap visible regular rows. Default 8. */
        public Builder<T> maxVisibleItems(int n) {
            if (n <= 0) {
                throw new IllegalArgumentException(
                        "maxVisibleItems must be positive, got " + n);
            }
            this.maxVisibleItems = n;
            return this;
        }

        /** Optional: per-item tooltip — mirrors {@link Dropdown.Builder#itemTooltip}. */
        public Builder<T> itemTooltip(Function<T, Component> fn) {
            this.itemTooltipFn = Objects.requireNonNull(fn, "itemTooltipFn must not be null");
            return this;
        }

        /**
         * Optional: configure a "Select all" pinned-top action row.
         * The Runnable is invoked when the row is clicked; consumers
         * typically use it to bulk-add to their selection Set (e.g.,
         * {@code () -> selected.addAll(ALL_ITEMS)}). Library calls the
         * Runnable directly — no consumer-fires-N-times overhead.
         *
         * <p>Renders italic, with no checkmark column. Popover stays
         * open after the click (same as regular-row clicks).
         */
        public Builder<T> selectAllRow(Component label, Runnable action) {
            this.selectAllLabel = Objects.requireNonNull(label, "label must not be null");
            this.selectAllAction = Objects.requireNonNull(action, "action must not be null");
            return this;
        }

        /**
         * Optional: configure a "Clear all" pinned-top action row.
         * Same shape as {@link #selectAllRow} — Runnable does the bulk
         * clear (e.g., {@code selected::clear}).
         */
        public Builder<T> clearAllRow(Component label, Runnable action) {
            this.clearAllLabel = Objects.requireNonNull(label, "label must not be null");
            this.clearAllAction = Objects.requireNonNull(action, "action must not be null");
            return this;
        }

        public DropdownMulti<T> build() {
            if (triggerWidth <= 0 || triggerHeight <= 0) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder: .triggerSize(w, h) must be called with positive values; "
                        + "got width=" + triggerWidth + ", height=" + triggerHeight);
            }
            if (items == null) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder: .items(list) is required");
            }
            if (items.isEmpty()) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder: .items(list) must contain at least one element; "
                        + "empty-state popover is not supported");
            }
            if (labelFn == null) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder: .label(fn) is required");
            }
            if (triggerLabelFn == null) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder: .triggerLabel(fn) is required "
                        + "(consumer decides the summary shape)");
            }
            if (selectionSupplier == null || selectionConsumer == null) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder: .selection(supplier, consumer) is required");
            }
            return new DropdownMulti<>(this);
        }
    }
}
