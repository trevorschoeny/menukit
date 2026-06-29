package com.trevorschoeny.menukit.core;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import org.lwjgl.glfw.GLFW;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.trevorschoeny.menukit.core.layout.ElementSpec;

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
public final class DropdownMulti<T> extends AbstractPanelElement<DropdownMulti<T>> {

    @Override protected DropdownMulti<T> self() { return this; }

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

    // Non-final since Pass 3 column-fill (fillWidth); the popover follows the trigger.
    private int triggerWidth;
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

    /**
     * Phase 18s follow-up — visual style for trigger background only.
     * See {@link Dropdown}'s {@code controlStyle} javadoc for rationale.
     */
    private final ControlStyle controlStyle;

    /**
     * Optional disabled predicate (Phase 3b — Item 8). Same semantics as
     * {@link Dropdown}'s: when true, the trigger renders disabled (VANILLA →
     * {@code widget/button_disabled}; MK → {@link PanelStyle#DARK}) and all
     * interaction is ignored (any open popover force-closes). Null = enabled.
     */
    private final @Nullable BooleanSupplier disabledWhen;

    // ── Mutable state ──────────────────────────────────────────────────
    // (Mirrors Dropdown's narrow exception. open + scrollOffset are
    // internal UI state; the selection lives on the consumer's Set, not
    // here.)

    private volatile boolean open = false;
    private volatile int scrollOffset = 0;

    /**
     * Keyboard-nav highlighted item index (completion of the input model;
     * mirrors {@link Dropdown}). {@code -1} = no keyboard highlight yet (reset
     * on each open); {@code 0..items.size()-1} once the user arrows in the
     * open popover. Indexes the regular-item region only — the pinned action
     * rows (Select all / Clear all) stay mouse-only.
     */
    private volatile int highlightedIndex = -1;
    /**
     * Captured {@link Util#getMillis} at the moment the popover was
     * last opened — anchors item-text scroll cycles to "beginning
     * visible" on open. See {@link Dropdown#popoverOpenMillis} for the
     * full rationale.
     */
    private volatile long popoverOpenMillis = 0L;

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

        // Disabled-state gate (Phase 3b — Item 8). Mirrors Dropdown: read once
        // per frame, force any open popover closed, suppress hover + tooltip.
        boolean disabled = isDisabled();
        if (disabled) open = false;

        boolean triggerHovered = !disabled
                && ctx.isHovered(childX, childY, triggerWidth, triggerHeight);
        renderTriggerBackground(ctx.graphics(), triggerX, triggerY, triggerHovered, disabled);
        renderTriggerContent(ctx.graphics(), triggerX, triggerY);

        // Popover moved to renderOverlay() in Phase 18s follow-up — see
        // override below. Ensures the popover always wins z-order
        // against later-declared sibling elements without consumers
        // having to manage declaration order.

        // Trigger-level tooltip — only when popover is closed (popover IS
        // the interactive surface when open; competing tooltip would clutter).
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
    // showWhen + tooltip return DropdownMulti<T> for free via the SELF self-type.
    // Position + trigger size are configured on the Builder (.at()/.triggerSize()).

    /**
     * Phase 18s follow-up — popover renders on the overlay pass so it
     * always wins z-order regardless of consumer element-declaration
     * order. See {@link Dropdown#renderOverlay} for full rationale.
     */
    @Override
    public void renderOverlay(RenderContext ctx) {
        if (!open) return;
        renderPopover(ctx, lastTriggerScreenX, lastTriggerScreenY);
    }

    // ── Trigger paint ──────────────────────────────────────────────────

    private void renderTriggerBackground(GuiGraphics graphics, int sx, int sy,
                                         boolean hovered, boolean disabled) {
        if (controlStyle == ControlStyle.VANILLA) {
            // Vanilla style — sprite atlas encodes hover/disabled state
            // directly. Disabled wins (Phase 3b — Item 8): enabled=false picks
            // widget/button_disabled; skip the pressed overlay. When popover
            // open, overlay the pressed visual. See
            // Dropdown.renderTriggerBackground for full rationale.
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
        // MK style (default): RAISED panel + translucent hover highlight
        // (suppressed when popover is open since the popover itself signals
        // interactive state). Disabled renders the DARK panel with no hover.
        PanelStyle bg = disabled ? PanelStyle.DARK : PanelStyle.RAISED;
        PanelRendering.renderPanel(graphics, sx, sy, triggerWidth, triggerHeight, bg);
        if (hovered && !open && !disabled) {
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

        // Trigger text — scroll-on-overflow via MKText (replaces
        // truncate-with-ellipsis). Matches Dropdown's trigger pattern.
        int textAreaW = triggerWidth - CHEVRON_RESERVED_W - 2 * TRIGGER_TEXT_PAD_X;
        int textAreaX = sx + TRIGGER_TEXT_PAD_X;
        MKText.render(graphics, text, net.minecraft.client.gui.TextAlignment.LEFT,
                textAreaX, textAreaX + textAreaW,
                sy, sy + triggerHeight,
                COLOR_TEXT, true);

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

        // Background — matches trigger style. MK: RAISED panel.
        // VANILLA: widget/button_disabled sprite. See
        // Dropdown.renderPopover for full rationale.
        if (controlStyle == ControlStyle.VANILLA) {
            ControlStyle.renderVanillaPopoverBackground(graphics, px, py, pw, ph);
        } else {
            PanelRendering.renderPanel(graphics, px, py, pw, ph, PanelStyle.RAISED);
        }

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
                        MKTooltip.queue(graphics, ttText, ctx.mouseX(), ctx.mouseY());
                    }
                }
            }

            // Keyboard-nav highlight — the arrow-key-selected row. Same
            // overlay as hover; gated on !rowHovered to avoid additive
            // double-draw when cursor and keyboard land on the same row.
            if (i == highlightedIndex && !rowHovered) {
                graphics.fill(px + 1, rowY,
                        px + pw - 1 - (scrollable ? SCROLLBAR_W : 0), rowY + ROW_HEIGHT,
                        COLOR_HOVER_OVERLAY);
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

            // Row text — scroll-on-overflow via MKText, anchored to
            // popoverOpenMillis so long item labels start at "text
            // beginning visible" when the popover opens.
            Component itemText = labelFn.apply(item);
            int textX = px + 1 + POPOVER_TEXT_PAD_X + CHECKMARK_COL_W;
            MKText.renderFromOpenTime(graphics, itemText, net.minecraft.client.gui.TextAlignment.LEFT,
                    textX, textX + rowsContentW,
                    rowY, rowY + ROW_HEIGHT,
                    COLOR_TEXT, true,
                    popoverOpenMillis);
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

        // Action row text — scroll-on-overflow via MKText, anchored to
        // popoverOpenMillis so long action labels start at "text
        // beginning visible" when the popover opens. Same shape as
        // regular item rows, just italic.
        MutableComponent italic = Component.empty()
                .append(label).withStyle(ChatFormatting.ITALIC);
        int textX = px + 1 + POPOVER_TEXT_PAD_X;
        MKText.renderFromOpenTime(graphics, italic, net.minecraft.client.gui.TextAlignment.LEFT,
                textX, textX + contentW,
                rowY, rowY + ROW_HEIGHT,
                COLOR_TEXT, true,
                popoverOpenMillis);
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

    // ── Input dispatch ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isDisabled()) return false; // Inert when disabled (Phase 3b — Item 8).

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
            // Keyboard highlight resets each open (see keyPressed).
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
        if (isDisabled()) return false; // Inert when disabled (Phase 3b — Item 8).
        if (!open) return false;
        if (items.size() <= maxVisibleItems) return true;
        int delta = (scrollY > 0) ? -1 : (scrollY < 0 ? 1 : 0);
        scrollOffset = clampScrollOffset(scrollOffset + delta);
        return true;
    }

    /**
     * Keyboard navigation over the regular-item region — the keyboard
     * counterpart to {@link #mouseClicked}, completing the input model and
     * mirroring {@link Dropdown}. Scoped to the OPEN popover (no focus model;
     * a closed dropdown consumes nothing). The pinned action rows stay
     * mouse-only.
     *
     * <ul>
     *   <li><b>Up / Down</b> — move the highlighted item; auto-scrolls.</li>
     *   <li><b>Enter / Space</b> — toggle the highlighted item's membership;
     *       stays open (the multi-select session continues — the keyboard
     *       analog of a row click).</li>
     *   <li><b>Escape</b> — close the popover.</li>
     * </ul>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isDisabled()) return false; // Inert when disabled (Phase 3b — Item 8).
        if (!open || items.isEmpty()) return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                highlightedIndex = (highlightedIndex < 0)
                        ? 0
                        : Math.min(items.size() - 1, highlightedIndex + 1);
                ensureHighlightVisible();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                highlightedIndex = (highlightedIndex < 0)
                        ? 0
                        : Math.max(0, highlightedIndex - 1);
                ensureHighlightVisible();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (highlightedIndex >= 0 && highlightedIndex < items.size()) {
                    // Toggle membership — stays open (multi-select session,
                    // mirroring the row-click path).
                    selectionConsumer.accept(items.get(highlightedIndex));
                }
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
     * Scrolls the popover so the keyboard-highlighted item stays within the
     * visible window. Reuses the wheel-scroll clamping.
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
        private ControlStyle controlStyle = ControlStyle.MK;
        private @Nullable BooleanSupplier disabledWhen = null;

        private Builder() {}

        /**
         * Phase 18s follow-up — selects the trigger's visual style.
         * See {@link Dropdown.Builder#style} for full rationale.
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

        /**
         * Optional disabled predicate (Phase 3b — Item 8). When it returns
         * true, the trigger renders disabled and all interaction is ignored —
         * same semantics as {@link Dropdown.Builder#disabledWhen}. Per-frame
         * predicate shape. Default: always enabled.
         */
        public Builder<T> disabledWhen(BooleanSupplier disabledWhen) {
            this.disabledWhen = Objects.requireNonNull(disabledWhen, "disabledWhen must not be null");
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

        /**
         * Layout terminal (Phase 3b — Item 6). Returns an {@link ElementSpec}
         * for use in {@link com.trevorschoeny.menukit.core.layout.Row} /
         * {@link com.trevorschoeny.menukit.core.layout.Column}.
         *
         * <p><b>Reported dimensions are the TRIGGER footprint</b>
         * ({@code .triggerSize(w, h)}), not the popover — same overflow-by-
         * design model as {@link Dropdown.Builder#spec()} (popover renders on
         * the overlay pass and claims its own input region), so Row/Column
         * reserves only the trigger footprint.
         *
         * <p>The layout helper calls {@link ElementSpec#at(int, int)}, which
         * re-runs this builder's configuration positioned at the computed
         * coordinates.
         */
        public ElementSpec spec() {
            if (triggerWidth <= 0 || triggerHeight <= 0) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder.spec(): .triggerSize(w, h) must be called with positive values; "
                        + "got width=" + triggerWidth + ", height=" + triggerHeight);
            }
            if (items == null || items.isEmpty()) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder.spec(): .items(list) is required and must be non-empty");
            }
            if (labelFn == null) {
                throw new IllegalStateException("DropdownMulti.Builder.spec(): .label(fn) is required");
            }
            if (triggerLabelFn == null) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder.spec(): .triggerLabel(fn) is required");
            }
            if (selectionSupplier == null || selectionConsumer == null) {
                throw new IllegalStateException(
                        "DropdownMulti.Builder.spec(): .selection(supplier, consumer) is required");
            }
            // Snapshot config; each at(x,y) rebuilds a fresh, correctly-
            // positioned DropdownMulti. (childX/childY fixed at construction
            // per THESIS Principle 4 — ElementSpec supplies them.)
            final int w = triggerWidth, h = triggerHeight;
            final List<T> it = items;
            final Function<T, Component> lf = labelFn;
            final Function<Set<T>, Component> tlf = triggerLabelFn;
            final Supplier<Set<T>> ss = selectionSupplier;
            final Consumer<T> sc = selectionConsumer;
            final int mvi = maxVisibleItems;
            final Function<T, Component> ttf = itemTooltipFn;
            final Component sal = selectAllLabel;
            final Runnable saa = selectAllAction;
            final Component cal = clearAllLabel;
            final Runnable caa = clearAllAction;
            final ControlStyle cs = controlStyle;
            final BooleanSupplier dw = disabledWhen;
            return new ElementSpec() {
                @Override public int width()  { return w; }
                @Override public int height() { return h; }
                @Override public PanelElement at(int x, int y) {
                    Builder<T> b = DropdownMulti.<T>builder().at(x, y).triggerSize(w, h)
                            .items(it).label(lf).triggerLabel(tlf).selection(ss, sc)
                            .maxVisibleItems(mvi).style(cs);
                    if (ttf != null) b.itemTooltip(ttf);
                    if (sal != null && saa != null) b.selectAllRow(sal, saa);
                    if (cal != null && caa != null) b.clearAllRow(cal, caa);
                    if (dw != null) b.disabledWhen(dw);
                    return b.build();
                }
            };
        }
    }
}
