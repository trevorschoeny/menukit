package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.ControlLook;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.PanelRendering;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What {@link Dropdown}, {@link DropdownMulti} and {@link MenuButton} share: a trigger
 * that opens a popover list of items. The base holds the trigger, the popover's geometry and
 * drawing, its open, scroll and keyboard-highlight state, and the routing of
 * clicks, wheel and keys, so a fix lands in both. A subclass says
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
 * <h3>Input receives its context</h3>
 *
 * The popover's place is computed from the {@link InputContext} or
 * {@link RenderContext} it is asked with (the trigger's origin), not from a
 * position cached at the last render.
 *
 * <h3>Pinned rows as values (7.3.0)</h3>
 *
 * {@code Builder.pinned(items)} pins a handful of {@code T} values above the
 * scrollable list, picked through the same {@link #pick} and shown with the same
 * {@link #isSelected} a scrollable row uses: a dropdown's fixed top-level choices
 * above its many finer-grained ones (five categories above eighty entity types).
 * Distinct from a subclass's own action rows ({@link DropdownMulti}'s Select all
 * and Clear all, which pick nothing of type {@code T}); a subclass that overrides
 * {@link #pinnedRowCount}, {@link #pinnedLabel} and {@link #pickPinned} itself
 * (as {@link DropdownMulti} does) is unaffected by {@code pinned(items)}.
 *
 * <h3>Search (7.3.0)</h3>
 *
 * {@code Builder.searchable()} adds a text field at the popover's top, below the
 * pinned rows and their divider: typing filters the scrollable rows by their
 * label text, case-insensitive "contains". The pinned rows are never filtered
 * (there are only a handful, and they are the stable top-level choices). Focused
 * automatically when the popover opens; the query resets whenever the popover
 * closes. Arrow keys, Enter and the wheel act on the filtered rows.
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
    /** The search field's height: the same as every other MenuKit search box (ItemDialog's, ListView's toolbar). */
    private static final int SEARCH_FIELD_HEIGHT = 20;
    /** Air above and below the search field, between the pinned divider and the scrollable rows. */
    private static final int SEARCH_PAD = 2;

    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;
    private static final int COLOR_ROW_SELECTED = 0x60FFFFFF;
    private static final int COLOR_SCROLLBAR_THUMB = 0xFFC6C6C6;
    private static final int COLOR_SEPARATOR = 0xFF606060;

    /** The items, read every time they are needed: a list given at build, or the consumer's supplier (7.3.0). */
    private final Supplier<? extends List<T>> items;
    protected final Function<T, Component> label;
    private final int maxVisibleItems;
    private final @Nullable Function<T, Component> itemTooltip;
    /** The builder's {@code pinned(items)} (7.3.0), empty unless a consumer gave any; see the class doc. */
    private final List<T> pinnedItems;
    private final boolean searchable;
    /** The search field (7.3.0), built once when {@code searchable()} was set, else {@code null}. */
    private final @Nullable TextField searchField;

    // View state
    private boolean open = false;
    private int scrollOffset = 0;
    /** The arrow keys' row, -1 until the first arrow press after opening. */
    private int highlighted = -1;
    /** When the popover opened: long row labels start their scroll at the beginning. */
    private long openedAt = 0L;
    /** The search field's text (7.3.0): filters the scrollable rows only; reset whenever the popover closes. */
    private String query = "";

    protected PopoverControl(Builder<T, ?, ?> b) {
        super(b);
        this.items = b.itemsLive != null ? b.itemsLive : copyOf(b.items);
        this.label = b.label;
        this.maxVisibleItems = b.maxVisibleItems;
        this.itemTooltip = b.itemTooltip;
        this.pinnedItems = b.pinnedItems;
        this.searchable = b.searchable;
        this.searchField = searchable ? TextField.builder().at(0, 0).size(10, SEARCH_FIELD_HEIGHT).fill()
                .hint(Component.translatable("menukit.list.search"))
                .label(Component.translatable("menukit.list.search"))
                .state(() -> query, q -> {
                    query = q;
                    scrollOffset = 0;    // a new search starts at the top of its results
                    highlighted = -1;
                })
                .build() : null;
    }

    /** The items now: the build-time list, or what the consumer's supplier returns this time. */
    protected final List<T> items() {
        List<T> now = items.get();
        return now != null ? now : List.of();
    }

    private static <T> Supplier<List<T>> copyOf(List<T> list) {
        List<T> copy = List.copyOf(list);
        return () -> copy;
    }

    // What a subclass says

    /** The trigger's text this frame. */
    protected abstract Component triggerText();

    /** Whether {@code item} is selected (drawn highlighted). */
    protected abstract boolean isSelected(T item);

    /** A row picked by click, Enter or Space. A single-select also closes. */
    protected abstract void pick(T item);

    /**
     * Rows pinned above the list: the builder's {@code pinned(items)} by default
     * (see the class doc), none without it. {@link DropdownMulti} overrides this
     * for its own Select all and Clear all rows instead.
     */
    protected int pinnedRowCount() { return pinnedItems.size(); }

    /** A pinned row's label: the shared {@code label} function, applied to {@code pinned(items)}'s value at {@code index}. */
    protected Component pinnedLabel(int index) { return label.apply(pinnedItems.get(index)); }

    /** A pinned row picked by click: hands {@code pinned(items)}'s value at {@code index} to {@link #pick}. */
    protected void pickPinned(int index) { pick(pinnedItems.get(index)); }

    /**
     * Whether a pinned row shows the selected highlight a scrollable row does;
     * default {@link #isSelected} of {@code pinned(items)}'s value at {@code index},
     * so a pinned category reads as picked once it is. An action row (Select all,
     * Clear all) is never "selected": {@link DropdownMulti} does not call
     * {@code pinned(...)}, so {@code pinnedItems} stays empty and this is always
     * false for it, with no override needed.
     */
    protected boolean pinnedSelected(int index) {
        return !pinnedItems.isEmpty() && isSelected(pinnedItems.get(index));
    }

    /** Width kept at the left of each row for a check mark; default none. */
    protected int checkColumnWidth() { return 0; }

    /** Draws a selected row's check mark in the check column; default nothing. */
    protected void drawCheck(GuiGraphicsExtractor g, int x, int rowY) {}

    /** Whether the trigger shows the open/closed arrow at its right; default yes. Without it the label is centred. */
    protected boolean chevron() { return true; }

    /** The popover's width; default the trigger's. A wider popover lines up its right edge with the trigger's. */
    protected int popoverWidth() { return width; }

    /** The colour of {@code item}'s row text; default the light text every row has. */
    protected int rowColor(T item) { return ElementConstants.TEXT_LIGHT; }

    /** Closes the popover (a single-select after a pick). */
    protected final void close() {
        closePopover();
    }

    // Size: the trigger's, grown when its label wraps.

    private int triggerTextWidth() {
        return Math.max(1, width - (chevron() ? CHEVRON_RESERVED_W : 0) - 2 * TRIGGER_TEXT_PAD_X);
    }

    /** The widest row label, with the row's padding and check column: what a popover sized to its rows needs. */
    protected final int widestRow() {
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (T item : items()) widest = Math.max(widest, font.width(label.apply(item)));
        for (T item : pinnedItems) widest = Math.max(widest, font.width(label.apply(item)));
        return widest + 2 * POPOVER_TEXT_PAD_X + 2 + checkColumnWidth() + (scrollable() ? SCROLLBAR_W : 0);
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

    // Search filter (7.3.0): the scrollable rows only; the pinned rows are never filtered.

    /**
     * The scrollable rows after the search filter: every item without
     * {@code searchable()} or with an empty query, else those whose label
     * contains the query, case-insensitive.
     */
    // ponytail: filters every call; fine for the tens to low hundreds of rows a
    // dropdown holds (Headcount's ~80), cache on the query if that ever changes.
    private List<T> visibleItems() {
        List<T> all = items();
        if (!searchable || query.isEmpty()) return all;
        String q = query.toLowerCase(Locale.ROOT);
        List<T> out = new ArrayList<>();
        for (T item : all) {
            if (label.apply(item).getString().toLowerCase(Locale.ROOT).contains(q)) out.add(item);
        }
        return out;
    }

    /** Whether the search found nothing: a query is typed, it matched no row, and there was something to match. */
    private boolean showsNoMatches() {
        return searchable && !query.isEmpty() && visibleItems().isEmpty() && !items().isEmpty();
    }

    /** The search field's band: the field's height with air above and below it; 0 without {@code searchable()}. */
    private int searchHeight() {
        return searchable ? SEARCH_PAD + SEARCH_FIELD_HEIGHT + SEARCH_PAD : 0;
    }

    // Popover geometry

    private int visibleRowCount() {
        int n = showsNoMatches() ? 1 : visibleItems().size();
        return Math.min(n, maxVisibleItems);
    }

    private boolean scrollable() {
        return visibleItems().size() > maxVisibleItems;
    }

    private int pinnedHeight() {
        int n = pinnedRowCount();
        return n * ROW_HEIGHT + (n > 0 ? SEPARATOR_HEIGHT : 0);
    }

    private int popoverHeight() {
        return 2 + pinnedHeight() + searchHeight() + visibleRowCount() * ROW_HEIGHT;
    }

    private int clampScroll(int offset) {
        return Mth.clamp(offset, 0, Math.max(0, visibleItems().size() - visibleRowCount()));
    }

    /**
     * The popover's {@code [x, y, w, h]} for a trigger at {@code (tx, ty)}: below
     * the trigger, flipped above when there is no room below, kept on screen
     * horizontally (vanilla's command-suggestion rule).
     */
    private int[] popoverBounds(int tx, int ty) {
        var window = Minecraft.getInstance().getWindow();
        // No window (MenuKit's build-time checks): nothing to keep it inside.
        int screenW = window != null ? window.getGuiScaledWidth() : Short.MAX_VALUE;
        int screenH = window != null ? window.getGuiScaledHeight() : Short.MAX_VALUE;
        int w = Math.max(width, popoverWidth()), h = popoverHeight();
        // As wide as the trigger, from its left; a wider one ends at the trigger's right
        // edge (a small menu button at the end of a row opens back toward the row).
        int x = Mth.clamp(w > width ? tx + width - w : tx, 0, Math.max(0, screenW - w));
        int below = ty + getHeight();
        int y = below + h <= screenH ? below : Math.max(0, ty - h);
        return new int[]{x, y, w, h};
    }

    /** The open popover's bounds, where this element is placed in {@code in}; null when closed. */
    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return open ? popoverBounds(in.originX() + childX, in.originY() + childY) : null;
    }

    // Rendering

    @Override
    public void render(RenderContext ctx) {
        int tx = ctx.originX() + childX;
        int ty = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        if (disabled) closePopover();
        boolean hovered = !disabled && ctx.isHovered(childX, childY, width, getHeight());
        var g = ctx.graphics();
        int h = getHeight();

        // Background: a raised control, open while the popover is up (the popover is the live
        // surface then, so no hover highlight; ControlLook.Press.OPEN).
        ScreenKind kind = ControlLook.kind(ctx.kind(), ControlLook.Control.BUTTON);
        ControlLook.drawRaised(ctx.kind(), g, tx, ty, width, h, !disabled, hovered, open ? ControlLook.Press.OPEN : ControlLook.Press.NONE);

        // Label: left, wrapping onto more lines when too long (the trigger grows);
        // the chevron stays beside the first line.
        Font font = Minecraft.getInstance().font;
        int color = disabled ? kind.textColor(TextRole.DISABLED) : ElementConstants.TEXT_LIGHT;
        Component text = triggerText();
        int textX = tx + TRIGGER_TEXT_PAD_X;
        int firstLineY = Text.centeredTextY(ty, ty + height);
        int wrap = Text.wrapWidth(text, triggerTextWidth());
        TextAlignment align = chevron() ? TextAlignment.LEFT : TextAlignment.CENTER;
        if (wrap > 0) {
            Text.drawWrapped(g, text, wrap, textX, firstLineY, 0, color, true);
        } else {
            Text.render(g, text, align, textX, textX + triggerTextWidth(), ty, ty + height, color, true);
        }
        if (chevron()) {
            Component chevron = Component.literal(open ? "▲" : "▼");
            // Its shadow ends TRIGGER_TEXT_PAD_X in from the right edge, as the label starts
            // that far in from the left, clear of the Panel look's wider bevel.
            int chevX = tx + width - CHEVRON_RESERVED_W + (CHEVRON_RESERVED_W - font.width(chevron)) / 2 - 2;
            g.text(font, chevron, chevX, firstLineY, color, true);
        }

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
        // The list: a flat surface in the trigger's set (ControlLook.drawList).
        ScreenKind kind = ControlLook.kind(ctx.kind(), ControlLook.Control.BUTTON);
        ControlLook.drawList(ctx.kind(), g, px, py, pw, ph);

        int textX = px + 1 + POPOVER_TEXT_PAD_X;
        int fullRowRight = px + pw - 1;

        // Pinned rows (italic, no check column, but the selected highlight a
        // pinned value earns like any other), then the separator.
        int pinned = pinnedRowCount();
        for (int i = 0; i < pinned; i++) {
            int rowY = py + 1 + i * ROW_HEIGHT;
            if (rowHovered(ctx, px, fullRowRight, rowY)) g.fill(px + 1, rowY, fullRowRight, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
            if (pinnedSelected(i)) g.fill(px + 1, rowY, fullRowRight, rowY + ROW_HEIGHT, COLOR_ROW_SELECTED);
            Component italic = Component.empty().append(pinnedLabel(i)).withStyle(net.minecraft.ChatFormatting.ITALIC);
            Text.renderFromOpenTime(g, italic, TextAlignment.LEFT, textX, fullRowRight - POPOVER_TEXT_PAD_X,
                    rowY, rowY + ROW_HEIGHT, ElementConstants.TEXT_LIGHT, true, openedAt);
        }
        if (pinned > 0) {
            int sepY = py + 1 + pinned * ROW_HEIGHT;
            g.fill(px + POPOVER_TEXT_PAD_X, sepY, px + pw - POPOVER_TEXT_PAD_X, sepY + 1, COLOR_SEPARATOR);
        }

        // The search field (7.3.0), below the pinned rows and their divider, above the scrollable rows.
        if (searchable) {
            int fx = textX;
            int fw = fullRowRight - POPOVER_TEXT_PAD_X - fx;
            int fy = py + 1 + pinnedHeight() + SEARCH_PAD;
            searchField.layoutWithin(fw);
            searchField.setChildPosition(fx - ctx.originX(), fy - ctx.originY());
            searchField.render(childContext(ctx));
        }

        // The items, scrolled — or "No matches" while the search filters out every row.
        List<T> visible = visibleItems();
        boolean scrollable = scrollable();
        int rowRight = fullRowRight - (scrollable ? SCROLLBAR_W : 0);
        int check = checkColumnWidth();
        int rowsTop = py + 1 + pinnedHeight() + searchHeight();
        if (showsNoMatches()) {
            Text.render(g, Component.translatable("menukit.list.no_matches"), TextAlignment.LEFT, textX,
                    rowRight - POPOVER_TEXT_PAD_X, rowsTop, rowsTop + ROW_HEIGHT, kind.textColor(TextRole.MUTED), true);
            return;
        }
        int first = clampScroll(scrollOffset);
        int last = Math.min(visible.size(), first + visibleRowCount());
        for (int i = first; i < last; i++) {
            int rowY = rowsTop + (i - first) * ROW_HEIGHT;
            T item = visible.get(i);
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
                    rowRight - POPOVER_TEXT_PAD_X, rowY, rowY + ROW_HEIGHT, rowColor(item), true, openedAt);
        }

        // Scrollbar over the items' region: a thumb sized to the share in view.
        if (scrollable) {
            int trackX = fullRowRight - SCROLLBAR_W;
            int trackH = visibleRowCount() * ROW_HEIGHT;
            PanelRendering.renderInsetRect(g, trackX, rowsTop, SCROLLBAR_W, trackH);
            int visibleCount = visibleRowCount();
            int thumbH = Math.max(8, trackH * visibleCount / visible.size());
            int range = visible.size() - visibleCount;
            int thumbY = rowsTop + (range > 0 ? (trackH - thumbH) * first / range : 0);
            g.fill(trackX + 1, thumbY, trackX + SCROLLBAR_W - 1, thumbY + thumbH, COLOR_SCROLLBAR_THUMB);
        }
    }

    private static boolean rowHovered(RenderContext ctx, int px, int right, int rowY) {
        return ctx.hasMouseInput() && ctx.mouseX() >= px + 1 && ctx.mouseX() < right
                && ctx.mouseY() >= rowY && ctx.mouseY() < rowY + ROW_HEIGHT;
    }

    // Input

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
            closePopover();
        } else {
            open = true;
            scrollOffset = 0;
            highlighted = -1;
            openedAt = Util.getMillis();
            if (searchable) searchField.focus();
        }
        return true;
    }

    private void clickPopover(InputContext in, int[] p) {
        int px = p[0], py = p[1], pw = p[2];
        int y = (int) (in.mouseY() - py - 1);
        int pinnedRows = pinnedRowCount() * ROW_HEIGHT;
        if (y < pinnedRows) {
            pickPinned(y / ROW_HEIGHT);
            return;
        }
        int afterPinned = y - pinnedHeight();
        if (afterPinned < 0) return;                                           // the separator
        if (searchable) {
            if (afterPinned < searchHeight()) {
                clickSearchField(in, px, py, pw);
                return;
            }
            afterPinned -= searchHeight();
        }
        if (scrollable() && in.mouseX() >= px + pw - 1 - SCROLLBAR_W) return;   // the scrollbar lane: no pick
        int index = clampScroll(scrollOffset) + afterPinned / ROW_HEIGHT;
        List<T> visible = visibleItems();
        if (index >= 0 && index < visible.size()) pick(visible.get(index));
    }

    /** Forwards a click inside the search field's band to the field itself: focus, caret, selection. */
    private void clickSearchField(InputContext in, int px, int py, int pw) {
        int fx = px + 1 + POPOVER_TEXT_PAD_X;
        int fw = (px + pw - 1) - POPOVER_TEXT_PAD_X - fx;
        int fy = py + 1 + pinnedHeight() + SEARCH_PAD;
        searchField.layoutWithin(fw);
        searchField.setChildPosition(fx - in.originX(), fy - in.originY());
        searchField.mouseClicked(in, Click.LEFT);
    }

    /** A click outside both the popover and the trigger closes it, as every native popup does. */
    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        if (!open) return;
        int[] p = getActiveOverlayBounds(in);
        boolean inPopover = p != null && in.isInside(p);
        boolean onTrigger = in.isOver(childX, childY, width, getHeight());
        if (!inPopover && !onTrigger) closePopover();
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
        if (!open || items().isEmpty() || disabled(in)) return false;
        // Everything else (Backspace, the arrow-within-text keys, paste, ...) is not
        // claimed here, so it falls through to vanilla's own focused-widget routing,
        // which is how the search field's own editing keys reach it (see the class doc).
        List<T> visible = visibleItems();
        switch (keyCode) {
            case InputConstants.KEY_DOWN -> {
                if (visible.isEmpty()) return true;   // consumed; nothing to move to while filtered to nothing
                highlighted = highlighted < 0 ? firstSelected(visible) : Math.min(visible.size() - 1, highlighted + 1);
                keepHighlightVisible();
                return true;
            }
            case InputConstants.KEY_UP -> {
                if (visible.isEmpty()) return true;
                highlighted = highlighted < 0 ? firstSelected(visible) : Math.max(0, highlighted - 1);
                keepHighlightVisible();
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
                if (highlighted >= 0 && highlighted < visible.size()) pick(visible.get(highlighted));
                else if (keyCode != InputConstants.KEY_SPACE) closePopover();
                return true;
            }
            case InputConstants.KEY_ESCAPE -> {
                closePopover();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Leaving the screen (or a list that dropped its row) closes the popover. */
    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        closePopover();
        if (searchable) searchField.onDetach(screen);
    }

    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        if (searchable) searchField.onAttach(screen);
    }

    @Override
    public void screenKind(ScreenKind kind) {
        if (searchable) searchField.screenKind(kind);
    }

    @Override
    public void inDialog(boolean inDialog) {
        if (searchable) searchField.inDialog(inDialog);
    }

    @Override
    public void inBand(boolean inBand) {
        if (searchable) searchField.inBand(inBand);
    }

    /** Closes the popover and, with {@code searchable()}, resets the query: the next open starts unfiltered. */
    private void closePopover() {
        open = false;
        if (searchable) query = "";
    }

    /**
     * As typing in the search field (7.3.0): the query now, for the checks (a real
     * popover's query comes from the field itself). A no-op without {@code searchable()}.
     */
    @ApiStatus.Internal
    public void typeQuery(String query) {
        this.query = Objects.requireNonNull(query, "query");
    }

    private int firstSelected(List<T> visible) {
        for (int i = 0; i < visible.size(); i++) if (isSelected(visible.get(i))) return i;
        return 0;
    }

    private void keepHighlightVisible() {
        int first = clampScroll(scrollOffset);
        int visible = visibleRowCount();
        if (highlighted < first) scrollOffset = clampScroll(highlighted);
        else if (highlighted >= first + visible) scrollOffset = clampScroll(highlighted - visible + 1);
    }

    /**
     * What both dropdowns' builders share: {@code size} (the trigger; the
     * popover is as wide), the items, their row label, the visible-row cap, a
     * per-row tooltip.
     */
    public abstract static class Builder<T, E extends PopoverControl<T>, B extends Builder<T, E, B>>
            extends AbstractPanelElement.Builder<E, B> {

        protected List<T> items = List.of();
        protected @Nullable Supplier<? extends List<T>> itemsLive;
        protected Function<T, Component> label = v -> Component.literal(String.valueOf(v));
        protected int maxVisibleItems = DEFAULT_MAX_VISIBLE;
        protected @Nullable Function<T, Component> itemTooltip;
        protected List<T> pinnedItems = List.of();
        protected boolean searchable = false;

        protected Builder() {}

        /** Required: the trigger's size in pixels (vanilla's buttons are 20 tall). The popover is as wide. */
        @Override
        public B size(int width, int height) {
            return super.size(width, height);
        }

        /** Required (or {@link #items(Supplier)}): the items, in popover order. Copied. */
        public B items(List<T> items) {
            this.items = List.copyOf(Objects.requireNonNull(items, "items"));
            this.itemsLive = null;
            return self();
        }

        /**
         * The items read from the consumer each time the control needs them (7.3.0), for
         * options that change while the screen is open: a list of saved worlds, the
         * groups a player has made. May be empty for a while; the trigger still shows.
         */
        public B items(Supplier<? extends List<T>> items) {
            this.itemsLive = Objects.requireNonNull(items, "items");
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

        /**
         * Rows pinned above the scrollable list, with a 1px divider under them: the
         * fixed top-level choices of a domain that also has many finer-grained values
         * below (five categories above eighty entity types). Picked through the same
         * {@code label}, {@code state} and {@code pick} as a scrollable row (so a
         * pinned value shows selected once chosen, exactly as one below it would), not
         * a separate action; for an action row (Select all, Clear all) see
         * {@link DropdownMulti#selectAllRow} and {@link DropdownMulti#clearAllRow}
         * instead. Not filtered by {@link #searchable}: there are only a handful, and
         * they are the stable choices a search over the many finer ones is for.
         */
        public B pinned(List<T> items) {
            this.pinnedItems = List.copyOf(Objects.requireNonNull(items, "items"));
            return self();
        }

        /**
         * Adds a text field at the popover's top, below the pinned rows and their
         * divider, that filters the scrollable rows by their label text,
         * case-insensitive "contains"; the pinned rows stay, unfiltered. Opt in, for a
         * popover whose rows run into the dozens where finding one by eye is not
         * realistic (Headcount's ~80 entity types). Focused automatically when the
         * popover opens; the query resets whenever the popover closes.
         */
        public B searchable() {
            this.searchable = true;
            return self();
        }

        /** Validation both share. */
        protected final void requireCommon() {
            require(width > 0 && height > 0, "size(w, h) is required");
            require(itemsLive != null || !items.isEmpty(), "items(...) needs at least one item, or a supplier");
        }
    }
}
