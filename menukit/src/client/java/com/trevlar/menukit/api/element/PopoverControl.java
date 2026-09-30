package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * What {@link Dropdown} and {@link DropdownMulti} share (6.0.0 plan, decision 5):
 * a trigger that opens a popover list of items. Before 6.0.0 the two carried
 * the same thousand lines twice; the base holds the trigger, the popover's
 * geometry and drawing, its open, scroll and keyboard-highlight state, and the
 * routing of clicks, wheel and keys, so a fix lands in both. A subclass says
 * only what differs: what the trigger reads, which rows are selected, what
 * picking a row does, and (the multi-select) pinned action rows above the list
 * and a check-mark column.
 *
 * <h3>View state, not consumer state</h3>
 *
 * Whether the popover is open, how far it is scrolled, and which row the arrow
 * keys highlight are the element's own view state, like a {@link Tabs} body's
 * scroll: nothing a consumer would want to own. The selection is always the
 * consumer's, through the subclass's lens.
 *
 * <h3>Input receives its context (§0066)</h3>
 *
 * The popover's place is computed from the {@link InputContext} or
 * {@link RenderContext} it is asked with (the trigger's origin), not from a
 * position cached at the last render.
 *
 * @param <T> the item type
 */
abstract class PopoverControl<T> extends AbstractPanelElement {

    /** Pixels per popover row. */
    static final int ROW_HEIGHT = 14;
    /** Rows visible before the popover scrolls. */
    static final int DEFAULT_MAX_VISIBLE = 8;
    private static final int POPOVER_TEXT_PAD_X = 4;
    private static final int TRIGGER_TEXT_PAD_X = 4;
    private static final int CHEVRON_RESERVED_W = 10;
    private static final int SCROLLBAR_W = 4;
    /** Space under the pinned rows (1px line and 1px air). */
    private static final int SEPARATOR_HEIGHT = 2;

    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;
    private static final int COLOR_ROW_SELECTED = 0x60FFFFFF;
    private static final int COLOR_SCROLLBAR_THUMB = 0xFFC6C6C6;
    private static final int COLOR_SEPARATOR = 0xFF606060;

    protected final List<T> items;
    protected final Function<T, Component> label;
    private final int maxVisibleItems;
    private final @Nullable Function<T, Component> itemTooltip;
    private final ControlStyle style;

    // ── View state ─────────────────────────────────────────────────────
    private boolean open = false;
    private int scrollOffset = 0;
    /** The arrow keys' row, -1 until the first arrow press after opening. */
    private int highlighted = -1;
    /** When the popover opened: long row labels start their scroll at the beginning. */
    private long openedAt = 0L;

    protected PopoverControl(Builder<T, ?, ?> b) {
        super(b);
        this.items = List.copyOf(b.items);
        this.label = b.label;
        this.maxVisibleItems = b.maxVisibleItems;
        this.itemTooltip = b.itemTooltip;
        this.style = b.style;
    }

    // ── What a subclass says ───────────────────────────────────────────

    /** The trigger's text this frame. */
    protected abstract Component triggerText();

    /** Whether {@code item} is selected (drawn highlighted). */
    protected abstract boolean isSelected(T item);

    /** A row picked by click, Enter or Space. A single-select also closes. */
    protected abstract void pick(T item);

    /** Rows pinned above the list (a multi-select's Select all); default none. */
    protected int pinnedRowCount() { return 0; }

    /** A pinned row's label. */
    protected Component pinnedLabel(int index) { throw new IndexOutOfBoundsException(index); }

    /** A pinned row picked by click. */
    protected void pickPinned(int index) {}

    /** Width kept at the left of each row for a check mark; default none. */
    protected int checkColumnWidth() { return 0; }

    /** Draws a selected row's check mark in the check column; default nothing. */
    protected void drawCheck(GuiGraphicsExtractor g, int x, int rowY) {}

    /** Closes the popover (a single-select after a pick). */
    protected final void close() {
        open = false;
    }

    // ── Size: the trigger; its label wraps (the one wrap helper) ───────

    private int triggerTextWidth() {
        return Math.max(1, width - CHEVRON_RESERVED_W - 2 * TRIGGER_TEXT_PAD_X);
    }

    private int triggerLines() {
        return Text.lineCount(triggerText(), Text.wrapWidth(triggerText(), triggerTextWidth()));
    }

    /** Symmetric padding above and below the text: the single-line slack, split. */
    private int triggerVPad() {
        return Math.max(0, (height - Minecraft.getInstance().font.lineHeight) / 2);
    }

    /** The trigger's height: the authored box, grown when its label wraps. */
    @Override
    public int getHeight() {
        int lines = triggerLines();
        if (lines <= 1) return height;
        return Math.max(height, triggerVPad() * 2 + lines * Minecraft.getInstance().font.lineHeight);
    }

    @Override
    public int extraLayoutHeight() {
        return triggerLines() > 1 ? getHeight() - height : 0;
    }

    @Override public boolean isInteractive() { return true; }

    // ── Popover geometry ───────────────────────────────────────────────

    private int visibleRowCount() {
        return Math.min(items.size(), maxVisibleItems);
    }

    private boolean scrollable() {
        return items.size() > maxVisibleItems;
    }

    private int pinnedHeight() {
        int n = pinnedRowCount();
        return n * ROW_HEIGHT + (n > 0 ? SEPARATOR_HEIGHT : 0);
    }

    private int popoverHeight() {
        return 2 + pinnedHeight() + visibleRowCount() * ROW_HEIGHT;
    }

    private int clampScroll(int offset) {
        return Mth.clamp(offset, 0, Math.max(0, items.size() - visibleRowCount()));
    }

    /**
     * The popover's {@code [x, y, w, h]} for a trigger at {@code (tx, ty)}: below
     * the trigger, flipped above when there is no room below, kept on screen
     * horizontally (vanilla's command-suggestion rule).
     */
    private int[] popoverBounds(int tx, int ty) {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        int w = width, h = popoverHeight();
        int x = Mth.clamp(tx, 0, Math.max(0, screenW - w));
        int below = ty + getHeight();
        int y = below + h <= screenH ? below : Math.max(0, ty - h);
        return new int[]{x, y, w, h};
    }

    /** The open popover's bounds, where this element is placed in {@code in}; null when closed. */
    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return open ? popoverBounds(in.originX() + childX, in.originY() + childY) : null;
    }

    // ── Render ─────────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int tx = ctx.originX() + childX;
        int ty = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        if (disabled) open = false;
        boolean hovered = !disabled && ctx.isHovered(childX, childY, width, getHeight());
        var g = ctx.graphics();
        int h = getHeight();

        // Background: vanilla's button sprite, or MenuKit's raised panel; pressed
        // while open (the popover is the live surface then, so no hover highlight).
        if (style == ControlStyle.VANILLA) {
            ControlStyle.renderVanillaButton(g, tx, ty, width, h, !disabled, hovered && !open);
            if (open) ControlStyle.renderVanillaPressedOverlay(g, tx, ty, width, h);
        } else {
            PanelRendering.renderPanel(g, tx, ty, width, h, disabled ? PanelStyle.DARK : PanelStyle.RAISED);
            if (hovered && !open) g.fill(tx + 1, ty + 1, tx + width - 1, ty + h - 1, ElementConstants.HOVER_OVERLAY);
        }

        // Label: left, wrapping onto more lines when too long (the trigger grows);
        // the chevron stays beside the first line.
        Font font = Minecraft.getInstance().font;
        int color = disabled ? ElementConstants.TEXT_DISABLED : ElementConstants.TEXT_LIGHT;
        Component text = triggerText();
        int textX = tx + TRIGGER_TEXT_PAD_X;
        int firstLineY = Text.centeredTextY(ty, ty + height);
        int wrap = Text.wrapWidth(text, triggerTextWidth());
        if (wrap > 0) {
            Text.drawWrapped(g, text, wrap, textX, firstLineY, 0, color, true);
        } else {
            Text.render(g, text, TextAlignment.LEFT, textX, textX + triggerTextWidth(), ty, ty + height, color, true);
        }
        Component chevron = Component.literal(open ? "▲" : "▼");
        int chevX = tx + width - CHEVRON_RESERVED_W + (CHEVRON_RESERVED_W - font.width(chevron)) / 2 - 1;
        g.text(font, chevron, chevX, firstLineY, color, true);

        // The trigger's tooltip only while closed: open, the rows are the surface.
        if (hovered && !open) queueTooltip(ctx);
    }

    /** The popover, on the overlay pass: on top of every sibling whatever the declaration order. */
    @Override
    public void renderOverlay(RenderContext ctx) {
        if (!open) return;
        var g = ctx.graphics();
        int[] p = popoverBounds(ctx.originX() + childX, ctx.originY() + childY);
        int px = p[0], py = p[1], pw = p[2], ph = p[3];
        if (style == ControlStyle.VANILLA) {
            ControlStyle.renderVanillaPopoverBackground(g, px, py, pw, ph);
        } else {
            PanelRendering.renderPanel(g, px, py, pw, ph, PanelStyle.RAISED);
        }

        int textX = px + 1 + POPOVER_TEXT_PAD_X;
        int fullRowRight = px + pw - 1;

        // Pinned rows (italic, no check column), then the separator.
        int pinned = pinnedRowCount();
        for (int i = 0; i < pinned; i++) {
            int rowY = py + 1 + i * ROW_HEIGHT;
            if (rowHovered(ctx, px, fullRowRight, rowY)) g.fill(px + 1, rowY, fullRowRight, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
            Component italic = Component.empty().append(pinnedLabel(i)).withStyle(net.minecraft.ChatFormatting.ITALIC);
            Text.renderFromOpenTime(g, italic, TextAlignment.LEFT, textX, fullRowRight - POPOVER_TEXT_PAD_X,
                    rowY, rowY + ROW_HEIGHT, ElementConstants.TEXT_LIGHT, true, openedAt);
        }
        if (pinned > 0) {
            int sepY = py + 1 + pinned * ROW_HEIGHT;
            g.fill(px + POPOVER_TEXT_PAD_X, sepY, px + pw - POPOVER_TEXT_PAD_X, sepY + 1, COLOR_SEPARATOR);
        }

        // The items, scrolled.
        boolean scrollable = scrollable();
        int rowRight = fullRowRight - (scrollable ? SCROLLBAR_W : 0);
        int check = checkColumnWidth();
        int first = clampScroll(scrollOffset);
        int last = Math.min(items.size(), first + visibleRowCount());
        int rowsTop = py + 1 + pinnedHeight();
        for (int i = first; i < last; i++) {
            int rowY = rowsTop + (i - first) * ROW_HEIGHT;
            T item = items.get(i);
            boolean hover = rowHovered(ctx, px, fullRowRight, rowY);
            if (hover || i == highlighted) g.fill(px + 1, rowY, rowRight, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
            if (hover && itemTooltip != null) {
                Component tip = itemTooltip.apply(item);
                if (tip != null) MKTooltip.queue(g, tip, ctx.mouseX(), ctx.mouseY());
            }
            if (isSelected(item)) {
                g.fill(px + 1, rowY, rowRight, rowY + ROW_HEIGHT, COLOR_ROW_SELECTED);
                drawCheck(g, textX, rowY);
            }
            Text.renderFromOpenTime(g, label.apply(item), TextAlignment.LEFT, textX + check,
                    rowRight - POPOVER_TEXT_PAD_X, rowY, rowY + ROW_HEIGHT, ElementConstants.TEXT_LIGHT, true, openedAt);
        }

        // Scrollbar over the items' region: a thumb sized to the share in view.
        if (scrollable) {
            int trackX = fullRowRight - SCROLLBAR_W;
            int trackH = visibleRowCount() * ROW_HEIGHT;
            PanelRendering.renderInsetRect(g, trackX, rowsTop, SCROLLBAR_W, trackH);
            int visible = visibleRowCount();
            int thumbH = Math.max(8, trackH * visible / items.size());
            int range = items.size() - visible;
            int thumbY = rowsTop + (range > 0 ? (trackH - thumbH) * first / range : 0);
            g.fill(trackX + 1, thumbY, trackX + SCROLLBAR_W - 1, thumbY + thumbH, COLOR_SCROLLBAR_THUMB);
        }
    }

    private static boolean rowHovered(RenderContext ctx, int px, int right, int rowY) {
        return ctx.hasMouseInput() && ctx.mouseX() >= px + 1 && ctx.mouseX() < right
                && ctx.mouseY() >= rowY && ctx.mouseY() < rowY + ROW_HEIGHT;
    }

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * Left click: in the open popover, a row (the dispatcher gave it to this
     * element exclusively); on the trigger, open or close.
     */
    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (button != Click.LEFT || disabled(in)) return false;
        int[] p = getActiveOverlayBounds(in);
        if (p != null && in.isInside(p)) {
            clickPopover(in, p);
            return true;
        }
        if (open) {
            open = false;
        } else {
            open = true;
            scrollOffset = 0;
            highlighted = -1;
            openedAt = Util.getMillis();
        }
        return true;
    }

    private void clickPopover(InputContext in, int[] p) {
        int px = p[0], py = p[1], pw = p[2];
        if (scrollable() && in.mouseX() >= px + pw - 1 - SCROLLBAR_W) return;   // the scrollbar lane: no pick
        int y = (int) (in.mouseY() - py - 1);
        int pinnedRows = pinnedRowCount() * ROW_HEIGHT;
        if (y < pinnedRows) {
            pickPinned(y / ROW_HEIGHT);
            return;
        }
        int inRows = y - pinnedHeight();
        if (inRows < 0) return;                                                // the separator
        int index = clampScroll(scrollOffset) + inRows / ROW_HEIGHT;
        if (index >= 0 && index < items.size()) pick(items.get(index));
    }

    /** A click outside both the popover and the trigger closes it, as every native popup does. */
    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        if (!open) return;
        int[] p = getActiveOverlayBounds(in);
        boolean inPopover = p != null && in.isInside(p);
        boolean onTrigger = in.isOver(childX, childY, width, getHeight());
        if (!inPopover && !onTrigger) open = false;
    }

    /** The wheel over the open popover scrolls its items one row per notch. */
    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        if (!open || disabled(in)) return false;
        if (!scrollable()) return true;
        scrollOffset = clampScroll(clampScroll(scrollOffset) + (scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0));
        return true;
    }

    /**
     * The keyboard, while open: Up and Down move the highlighted row (the first
     * press lands on the first selected row), Enter or Space picks it, Escape
     * closes the popover without closing the screen. A closed control takes no
     * keys, so it never swallows a sibling's or vanilla's.
     */
    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        if (!open || items.isEmpty() || disabled(in)) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                highlighted = highlighted < 0 ? firstSelected() : Math.min(items.size() - 1, highlighted + 1);
                keepHighlightVisible();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                highlighted = highlighted < 0 ? firstSelected() : Math.max(0, highlighted - 1);
                keepHighlightVisible();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (highlighted >= 0 && highlighted < items.size()) pick(items.get(highlighted));
                else if (keyCode != GLFW.GLFW_KEY_SPACE) open = false;
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                open = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private int firstSelected() {
        for (int i = 0; i < items.size(); i++) if (isSelected(items.get(i))) return i;
        return 0;
    }

    private void keepHighlightVisible() {
        int first = clampScroll(scrollOffset);
        int visible = visibleRowCount();
        if (highlighted < first) scrollOffset = clampScroll(highlighted);
        else if (highlighted >= first + visible) scrollOffset = clampScroll(highlighted - visible + 1);
    }

    // ── Builder ────────────────────────────────────────────────────────

    /**
     * What both dropdowns' builders share: {@code size} (the trigger; the
     * popover is as wide), the items, their row label, the visible-row cap, a
     * per-row tooltip, and the style.
     */
    public abstract static class Builder<T, E extends PopoverControl<T>, B extends Builder<T, E, B>>
            extends AbstractPanelElement.Builder<E, B> {

        protected List<T> items = List.of();
        protected Function<T, Component> label = v -> Component.literal(String.valueOf(v));
        protected int maxVisibleItems = DEFAULT_MAX_VISIBLE;
        protected @Nullable Function<T, Component> itemTooltip;
        protected ControlStyle style = ControlStyle.MK;

        protected Builder() {}

        /** Required: the trigger's size in pixels (vanilla's buttons are 20 tall). The popover is as wide. */
        @Override
        public B size(int width, int height) {
            return super.size(width, height);
        }

        /** Required: the items, in popover order. Copied. */
        public B items(List<T> items) {
            this.items = List.copyOf(Objects.requireNonNull(items, "items"));
            return self();
        }

        /** The text for an item's row (and, for a single-select, the trigger). Default its {@code toString}. */
        public B label(Function<T, Component> label) {
            this.label = Objects.requireNonNull(label, "label");
            return self();
        }

        /** Rows shown before the popover scrolls. Default 8. */
        public B maxVisibleItems(int n) {
            if (n <= 0) throw new IllegalArgumentException("maxVisibleItems must be positive, got " + n);
            this.maxVisibleItems = n;
            return self();
        }

        /** A tooltip for a hovered row; a {@code null} result shows none. */
        public B itemTooltip(Function<T, Component> tooltip) {
            this.itemTooltip = Objects.requireNonNull(tooltip, "tooltip");
            return self();
        }

        /** The trigger's look: {@link ControlStyle#MK} (default) or vanilla's button. The popover is a raised panel either way. */
        public B style(ControlStyle style) {
            this.style = Objects.requireNonNull(style, "style");
            return self();
        }

        /** Validation both share. */
        protected final void requireCommon() {
            require(width > 0 && height > 0, "size(w, h) is required");
            require(!items.isEmpty(), "items(...) needs at least one item");
        }
    }
}
