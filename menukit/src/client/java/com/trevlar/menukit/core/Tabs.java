package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A tab strip and the area below it that shows the selected tab's body. One
 * element holds both, so the body's top edge follows the strip's height by
 * construction: a strip that wraps from one row to three pushes the body down
 * with no cross-panel coordination.
 *
 * <pre>{@code
 * Tabs tabs = Tabs.builder()
 *         .mode(Tabs.Mode.WRAP)
 *         .align(Tabs.Align.FILL)
 *         .state(() -> state.tab, id -> state.tab = id)
 *         .tab("general", Component.literal("General"), generalBody)
 *         .tab(Tabs.tab("pockets")
 *                 .label(Component.literal("Pockets"))
 *                 .visibleWhen(() -> config.showMaxTabs)
 *                 .body(pocketsBody))
 *         .build();
 * }</pre>
 *
 * <h3>Selection is a lens (§0026)</h3>
 *
 * The consumer owns which tab is selected: a supplier reads it every frame and
 * a callback writes it when the player clicks a tab.
 * Selecting a tab from outside is writing your own field; the strip shows it on
 * the next frame. When the selected tab is hidden, the nearest visible tab is
 * <em>shown</em> (the next one, else the previous) and <b>nothing is written</b>,
 * so a tab that reappears while the field still names it takes the selection
 * back.
 *
 * <h3>Visibility</h3>
 *
 * Each tab's {@code visibleWhen} is read every frame. A change relays out the
 * strip; in wrap mode that can change the number of rows, and the body moves
 * with it.
 *
 * <h3>Layout</h3>
 *
 * A tab is its label's width plus padding, at least {@link #MIN_TAB_WIDTH}, and
 * never wider than the strip (a longer label is cut with "..." and shown whole
 * as a tooltip).
 * <ul>
 *   <li><b>{@link Mode#WRAP}</b>: the fewest rows the width allows, with the tabs
 *       split across them so the rows are about equally full, in order. A row's
 *       tabs never depend on which tab is selected, so clicking never moves a
 *       tab; only a width or visibility change does. {@link Align} applies per
 *       row, and {@link Align#FILL} stretches every row, the last one included,
 *       which balancing keeps from looking like a stub.</li>
 *   <li><b>{@link Mode#SIDE_SCROLL}</b>: one row. While it fits, {@link Align}
 *       applies as for one wrap row. Once it overflows it scrolls from the left
 *       edge, alignment has no effect, and arrows appear at both ends (each hidden,
 *       its space kept, once its end is reached). The wheel over the strip and the
 *       arrows move one tab at a time, and a newly selected tab, including one
 *       selected from outside, scrolls into view with the least movement.</li>
 *   <li><b>{@link Mode#SIDEBAR}</b>: a column of tabs down the left, the body to
 *       its right. The top tab turned on its side: the selected tab reaches 4 px
 *       further left and opens into the page, the others close against it with the
 *       page's edge. The column is as wide as its widest label, at most a third of
 *       the element, and every tab in it is that wide. {@link Align} places the
 *       label in the tab ({@link Align#FILL} as {@link Align#LEFT}), and labels
 *       stay put on selection so the column reads as a list. When the tabs are
 *       taller than the column, vanilla's thin list scrollbar runs down its left
 *       edge: drag the handle, or click the track to jump there. The wheel over
 *       the column moves one tab at a time, and a newly selected tab scrolls into
 *       view. The tabs give the bar its lane rather than the column widening, so
 *       the body never moves.
 *       {@link Builder#sidebarHeader} puts elements above the column (a Back
 *       button, say), in the column's width; the body still starts at the top.</li>
 * </ul>
 * {@link Align#FILL} gives every tab in a row its label width plus an equal
 * share of the row's leftover space: every label always fits, and short labels
 * are not squeezed next to long ones.
 *
 * <h3>Size</h3>
 *
 * By default a Tabs fills its panel: its width is the panel's content width and
 * its height comes down from the panel's viewport through {@link #fillHeight}
 * (the screen's MAIN panel has one on a standalone screen). Where there is no
 * viewport it takes its natural height, the strip plus the tallest visible body.
 * {@link Builder#size(int, int)} fixes both instead, for a panel that has no
 * height budget.
 *
 * <h3>Bodies</h3>
 *
 * A body is a list of elements positioned from the body's top-left, the same as
 * {@link ScrollContainer} content or a {@link com.trevlar.menukit.core.layout.Column}.
 * It is laid out to the body width exactly as a panel lays out its own elements
 * (text wraps, taller elements push the ones below), and scrolls vertically when
 * taller than the body area. Each tab keeps its own scroll position for the life
 * of this element: switching away and back, hiding tabs, and resizing leave it
 * where the player left it. That is view state, like a panel's own auto-scroll
 * offset, not consumer state.
 *
 * <h3>Tabs from other mods</h3>
 *
 * A menu with a {@linkplain Builder#menu name} takes tabs from other mods:
 * {@link #addTo(Identifier, TabSpec)} at their init, with a body factory so each
 * body is built fresh, against that mod's own config, every time the owner builds.
 * The owner's builder order is the menu's order. A contribution takes the slot of
 * an owner tab marked {@link TabSpec#standIn() standIn()} with the same id, or
 * places itself {@link TabSpec#after after} or {@link TabSpec#before before} a tab;
 * contributions that share an anchor are ordered by id, so the result does not
 * depend on which mod initialised first. See {@link TabMenus} for the full rule.
 *
 * <p>Known limit, shared with hidden panels: a widget-wrapping element (a
 * {@link TextField}) in a tab that is not shown is still registered with the
 * screen, so it can keep keyboard focus. See {@link TextField}.
 */
public final class Tabs extends AbstractPanelElement {

    /** How the strip lays out when the tabs don't fit one row. */
    public enum Mode {
        /** Break into as many rows as the width needs. */
        WRAP,
        /** Stay one row and scroll horizontally. */
        SIDE_SCROLL,
        /** A column down the left, the body to its right; scrolls vertically when too tall. */
        SIDEBAR
    }

    /** Where tabs sit within a row; in {@link Mode#SIDEBAR}, where the label sits within its tab. */
    public enum Align { LEFT, CENTER, RIGHT, FILL }

    /** Height of one row of tabs. */
    public static final int TAB_HEIGHT = 20;

    /** How far down an unselected tab's visible body starts, per vanilla's tab sprite. */
    private static final int UNSELECTED_LABEL_INSET = 3;
    /** Padding on each side of a label. */
    public static final int LABEL_PAD = 8;
    /** Narrowest a tab gets, whatever its label. */
    public static final int MIN_TAB_WIDTH = 24;
    /** Space between the strip and the body. */
    public static final int BODY_GAP = 4;
    /** Width reserved at each end of an overflowing side-scroll strip for its arrow. */
    public static final int ARROW_WIDTH = 25;
    /** Space between the sidebar header and the tabs under it. */
    public static final int SIDEBAR_HEADER_GAP = 4;
    /** Width of the sidebar's scrollbar: vanilla's list scrollbar ({@code AbstractScrollArea.SCROLLBAR_WIDTH}). */
    public static final int SIDEBAR_SCROLLBAR_WIDTH = 6;
    /** Space between the sidebar's scrollbar and its tabs. */
    private static final int SIDEBAR_SCROLLBAR_GAP = 2;
    /** Shortest the handle gets, and how far short of the track it stops, per vanilla's list. */
    private static final int SCROLLER_MIN_HEIGHT = 32;
    private static final int SCROLLER_TRACK_MARGIN = 8;
    private static final Identifier SPRITE_SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SPRITE_SCROLLER_TRACK = Identifier.withDefaultNamespace("widget/scroller_background");

    private static final String ELLIPSIS = "...";
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_UNSELECTED = 0xFFA0A0A0;
    // A stand-in's label is dimmed: the tab is there to say its mod is missing.
    private static final int COLOR_STAND_IN_SELECTED = 0xFF909090;
    private static final int COLOR_STAND_IN_UNSELECTED = 0xFF606060;

    // Vanilla's own tab and page-arrow sprites (Create World's tab bar).
    private static final Identifier SPRITE_TAB = Identifier.withDefaultNamespace("widget/tab");
    private static final Identifier SPRITE_TAB_HOVER = Identifier.withDefaultNamespace("widget/tab_highlighted");
    private static final Identifier SPRITE_SELECTED = Identifier.withDefaultNamespace("widget/tab_selected");
    private static final Identifier SPRITE_SELECTED_HOVER =
            Identifier.withDefaultNamespace("widget/tab_selected_highlighted");
    private static final Identifier SPRITE_BACK = Identifier.withDefaultNamespace("widget/page_backward");
    private static final Identifier SPRITE_BACK_HOVER = Identifier.withDefaultNamespace("widget/page_backward_highlighted");
    private static final Identifier SPRITE_FORWARD = Identifier.withDefaultNamespace("widget/page_forward");
    private static final Identifier SPRITE_FORWARD_HOVER = Identifier.withDefaultNamespace("widget/page_forward_highlighted");
    private static final int ARROW_SPRITE_W = 23;
    private static final int ARROW_SPRITE_H = 13;

    // The sidebar tab, drawn rather than blitted. Vanilla's tab sprite is a
    // nine-slice that TILES its middle, so drawn thicker than its native 24 px (as
    // a sidebar tab is: the column's whole width) it repeats its outline lines
    // across the tab. These are the sprite's own colours and structure, turned on
    // its side with the middle stretched instead. The cost: a resource pack that
    // restyles vanilla's tabs restyles the top strip and not the sidebar.
    /** How much shorter an unselected tab is than the selected one, per the sprite. */
    private static final int SIDE_INSET = 4;
    private static final int TAB_EDGE = 0xBF000000;        // outer line
    private static final int TAB_LIGHT = 0x33FFFFFF;       // inner line, and the page edge's light line
    private static final int TAB_LIGHT_HOVER = 0xFFFFFFFF; // inner line under the mouse
    private static final int TAB_FILL = 0xDB000000;        // an unselected tab's face
    private static final int TAB_CLEAR = 0;                // the selected tab shows the page through it

    // ── Declared structure (fixed at construction, §0022) ──────────────────

    private final Mode mode;
    private final Align align;
    private final List<Tab> tabs;
    private final Supplier<@Nullable String> selected;
    private final Consumer<String> onSelect;
    private final int fixedWidth;   // -1 = take the panel's width
    private final int fixedHeight;  // -1 = take the panel's height (fillHeight)
    /** Elements above the sidebar column, positioned from its top-left. Empty outside SIDEBAR. */
    private final List<PanelElement> sidebarHeader;
    /** While the sidebar's handle is held: how far below the handle's top it was grabbed. */
    private boolean draggingScroller = false;
    private double dragGrabOffset = 0;

    // ── Resolved geometry ──────────────────────────────────────────────────

    private int resolvedWidth = -1;   // from layoutWithin; -1 until the first pass
    private int filledHeight = -1;    // from fillHeight; -1 = no viewport

    // ── View state (not consumer state) ────────────────────────────────────

    /**
     * Each tab's body scroll in pixels from the top, kept for the life of this
     * element. Pixels, not a fraction: a body that grows while shown (a section
     * opening) keeps what is on screen where it is.
     */
    private final Map<String, Double> bodyScroll = new HashMap<>();
    /** Side-scroll strip offset in pixels, when the row overflows. */
    private int stripScroll = 0;
    /** The tab shown last frame, to scroll a newly selected tab into view. */
    private @Nullable String lastShown = null;

    private Tabs(Builder b) {
        super(b);
        this.mode = b.mode;
        this.align = b.align;
        this.tabs = List.copyOf(b.resolveTabs());
        this.selected = b.selected;
        this.onSelect = b.onSelect;
        this.fixedWidth = b.fixedWidth;
        this.fixedHeight = b.fixedHeight;
        this.sidebarHeader = List.copyOf(b.sidebarHeader);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Starts a tab with an id; give it a label and a body. */
    public static TabSpec tab(String id) {
        return new TabSpec(id);
    }

    /**
     * Adds a tab to the menu named {@code menu}, from a mod that does not own it.
     * Call at your mod's init. The tab shows every time the owner builds the menu,
     * from the next build on, and never if nobody builds it.
     *
     * <p>The body must be a factory ({@link TabSpec#body(Supplier)}): it is built
     * each time the menu opens, against your own config. Place the tab by giving
     * it the id of one of the owner's stand-ins, or with {@link TabSpec#after} or
     * {@link TabSpec#before}; with neither it goes at the end.
     *
     * @throws IllegalStateException if another tab added to this menu has the
     *         same id, or the body is a finished list, or the tab is a stand-in
     */
    public static void addTo(Identifier menu, TabSpec tab) {
        com.trevlar.menukit.window.Declarations.requireOpen("Tabs.addTo(" + menu + ")");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(tab, "tab");
        if (tab.label == null) throw new IllegalStateException("Tabs: tab '" + tab.id + "' has no label");
        if (tab.bodyFactory == null) {
            throw new IllegalStateException("Tabs: tab '" + tab.id + "' added to " + menu + " needs a body "
                    + "factory, body(() -> elements), because it is built every time the menu opens");
        }
        if (tab.standIn) {
            throw new IllegalStateException("Tabs: tab '" + tab.id + "' added to " + menu + " is marked "
                    + "standIn(); only the menu's owner declares stand-ins");
        }
        TabMenus.add(menu, tab);
    }

    /** The ids of this element's tabs, in strip order, hidden ones included. */
    public List<String> tabIds() {
        List<String> ids = new ArrayList<>(tabs.size());
        for (Tab t : tabs) ids.add(t.id);
        return ids;
    }

    /** Whether the tab with this id shows on the strip right now (its {@code visibleWhen}, read once). */
    public boolean isTabVisible(String id) {
        for (Tab t : tabs) if (t.id.equals(id)) return t.isVisible();
        return false;
    }

    /** Whether the tab with this id is a stand-in nothing has replaced. */
    public boolean isStandIn(String id) {
        for (Tab t : tabs) if (t.id.equals(id)) return t.standIn;
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Strip layout
    // ════════════════════════════════════════════════════════════════════════

    /** One tab placed on the strip, in strip-local coordinates (side-scroll: content space). */
    private record Placed(Tab tab, int x, int y, int w, String text, boolean truncated) {}

    /**
     * The strip for one width and one visible set. In {@link Mode#SIDEBAR}
     * {@code height} is the column's full length, {@code columnWidth} its width,
     * and whether it overflows depends on the element's height, so it is asked
     * of {@link #overflows}, never read from {@code overflow}.
     */
    private record StripLayout(List<Placed> placed, int height, boolean overflow, int contentWidth,
                               int columnWidth, int headerHeight) {}

    private @Nullable StripLayout strip;
    private int stripWidthKey = -1;
    private @Nullable BitSet stripVisibleKey;

    private BitSet visibleMask() {
        BitSet mask = new BitSet(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).isVisible()) mask.set(i);
        }
        return mask;
    }

    /** The strip for the current width and visible set, recomputed only when either changes. */
    private StripLayout strip() {
        int w = getWidth();
        BitSet mask = visibleMask();
        int headerSig = signature(sidebarHeader);
        if (strip == null || w != stripWidthKey || !mask.equals(stripVisibleKey) || headerSig != stripHeaderKey) {
            strip = layoutStrip(w, mask);
            stripWidthKey = w;
            stripVisibleKey = mask;
            stripHeaderKey = signature(sidebarHeader); // after layout, so a settled header doesn't relay
        }
        return strip;
    }

    private int stripHeaderKey = 0;

    private StripLayout layoutStrip(int width, BitSet mask) {
        Font font = Minecraft.getInstance().font;
        List<Tab> visible = new ArrayList<>();
        for (int i = mask.nextSetBit(0); i >= 0; i = mask.nextSetBit(i + 1)) visible.add(tabs.get(i));
        if (mode == Mode.SIDEBAR) return layoutSidebar(width, visible);
        if (visible.isEmpty() || width <= 0) return new StripLayout(List.of(), 0, false, 0, 0, 0);

        int n = visible.size();
        int[] natural = new int[n];
        for (int i = 0; i < n; i++) {
            int w = font.width(visible.get(i).label) + 2 * LABEL_PAD;
            natural[i] = Math.min(Math.max(w, MIN_TAB_WIDTH), width); // never wider than the strip
        }

        List<int[]> rows; // each row: {firstIndex, endIndexExclusive}
        boolean overflow = false;
        if (mode == Mode.WRAP) {
            rows = TabRows.balanced(natural, width);
        } else {
            rows = List.of(new int[]{0, n});
            overflow = sum(natural, 0, n) > width;
        }

        List<Placed> placed = new ArrayList<>(n);
        int y = 0;
        int contentWidth = 0;
        for (int[] row : rows) {
            int from = row[0], to = row[1];
            int[] widths = java.util.Arrays.copyOfRange(natural, from, to);
            int x;
            if (overflow) {
                x = 0;                                    // scrolls from the left; no alignment
            } else {
                int slack = width - sum(widths, 0, widths.length);
                x = switch (align) {
                    case LEFT, FILL -> 0;
                    case CENTER -> slack / 2;
                    case RIGHT -> slack;
                };
                if (align == Align.FILL && slack > 0) {
                    // An equal share of the leftover to every tab, the remainder a
                    // pixel each to the first tabs, so the row ends flush.
                    int share = slack / widths.length, extra = slack % widths.length;
                    for (int i = 0; i < widths.length; i++) widths[i] += share + (i < extra ? 1 : 0);
                }
            }
            for (int i = 0; i < widths.length; i++) {
                Tab t = visible.get(from + i);
                String text = t.label.getString();
                boolean truncated = false;
                int room = widths[i] - 2 * LABEL_PAD;
                if (font.width(text) > room) {
                    text = font.plainSubstrByWidth(text, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
                    truncated = true;
                }
                placed.add(new Placed(t, x, y, widths[i], text, truncated));
                x += widths[i];
            }
            contentWidth = Math.max(contentWidth, x);
            y += TAB_HEIGHT;
        }
        return new StripLayout(List.copyOf(placed), y, overflow, contentWidth, 0, 0);
    }

    /**
     * One column, one tab per row, all as wide as the widest label or header
     * element, capped at a third of the element; the header laid out to it.
     */
    private StripLayout layoutSidebar(int width, List<Tab> visible) {
        Font font = Minecraft.getInstance().font;
        if (width <= 0 || (visible.isEmpty() && !anyVisible(sidebarHeader))) {
            return new StripLayout(List.of(), 0, false, 0, 0, 0);
        }
        int cap = Math.max(Math.min(MIN_TAB_WIDTH, width), width / 3);
        int column = Math.min(Math.max(sidebarNaturalWidth(visible), headerNaturalWidth()), cap);
        // The header is laid out the way a panel lays out its own elements, to the column's width.
        Panel.layoutElementsWithin(sidebarHeader, column);
        Panel.reflowForWrap(sidebarHeader);
        int headerHeight = Panel.contentHeightOf(sidebarHeader);
        List<Placed> placed = new ArrayList<>(visible.size());
        int room = column - 2 * LABEL_PAD - SIDE_INSET;
        for (int i = 0; i < visible.size(); i++) {
            Tab t = visible.get(i);
            String text = t.label.getString();
            boolean truncated = false;
            if (font.width(text) > room) {
                text = font.plainSubstrByWidth(text, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
                truncated = true;
            }
            placed.add(new Placed(t, 0, i * TAB_HEIGHT, column, text, truncated));
        }
        return new StripLayout(List.copyOf(placed), visible.size() * TAB_HEIGHT, false, column, column,
                headerHeight);
    }

    private int headerNaturalWidth() {
        int w = 0;
        for (PanelElement e : sidebarHeader) {
            if (e.isVisible()) w = Math.max(w, e.getChildX() + e.naturalWidth());
        }
        return w;
    }

    private static boolean anyVisible(List<PanelElement> elements) {
        for (PanelElement e : elements) if (e.isVisible()) return true;
        return false;
    }

    /** The column width every label fits in whole: the widest label, its padding, and the unselected inset. */
    private static int sidebarNaturalWidth(List<Tab> visible) {
        Font font = Minecraft.getInstance().font;
        int w = MIN_TAB_WIDTH;
        for (Tab t : visible) w = Math.max(w, font.width(t.label) + 2 * LABEL_PAD + SIDE_INSET);
        return w;
    }

    private static int sum(int[] a, int from, int to) {
        int s = 0;
        for (int i = from; i < to; i++) s += a[i];
        return s;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Selection (lens, no write-back)
    // ════════════════════════════════════════════════════════════════════════

    /** The tab to show: the selected one if visible, else the nearest visible, else none. */
    private @Nullable Tab shownTab() {
        String want = selected.get();
        int at = -1;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id.equals(want)) { at = i; break; }
        }
        if (at >= 0 && tabs.get(at).isVisible()) return tabs.get(at);
        if (at >= 0) {
            for (int i = at + 1; i < tabs.size(); i++) if (tabs.get(i).isVisible()) return tabs.get(i);
            for (int i = at - 1; i >= 0; i--) if (tabs.get(i).isVisible()) return tabs.get(i);
            return null;
        }
        for (Tab t : tabs) if (t.isVisible()) return t;
        return null;
    }

    private void select(Tab tab) {
        onSelect.accept(tab.id);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Scroll geometry: along the strip's own axis (x for side-scroll, y for the sidebar)
    // ════════════════════════════════════════════════════════════════════════

    private boolean sidebar() {
        return mode == Mode.SIDEBAR;
    }

    /** Whether the strip is longer than its room and scrolls. */
    private boolean overflows(StripLayout s) {
        return sidebar() ? s.height > stripViewportLength(s) : s.overflow;
    }

    /** Where the sidebar's tabs start: under the header and its gap, or at the top. */
    private int sidebarTabsY(StripLayout s) {
        return s.headerHeight > 0 ? s.headerHeight + SIDEBAR_HEADER_GAP : 0;
    }

    /** The strip's full content length along its axis. */
    private int contentLength(StripLayout s) {
        return sidebar() ? s.height : s.contentWidth;
    }

    /** Where a tab starts along the axis, and how long it is. */
    private int posOf(Placed p) {
        return sidebar() ? p.y : p.x;
    }

    private int lengthOf(Placed p) {
        return sidebar() ? TAB_HEIGHT : p.w;
    }

    /**
     * Start of the tab viewport along the axis: after the back arrow when a
     * side-scroll row overflows; in the sidebar, under the header.
     */
    private int stripViewportStart(StripLayout s) {
        if (sidebar()) return sidebarTabsY(s);
        return s.overflow ? ARROW_WIDTH : 0;
    }

    private int stripViewportLength(StripLayout s) {
        if (sidebar()) return Math.max(0, getHeight() - stripViewportStart(s));
        return s.overflow ? Math.max(1, getWidth() - 2 * ARROW_WIDTH) : getWidth();
    }

    private int maxStripScroll(StripLayout s) {
        return overflows(s) ? Math.max(0, contentLength(s) - stripViewportLength(s)) : 0;
    }

    private void clampStripScroll(StripLayout s) {
        stripScroll = Math.max(0, Math.min(stripScroll, maxStripScroll(s)));
    }

    /** Scrolls the least distance that brings {@code tab} fully into view. */
    private void scrollIntoView(StripLayout s, Tab tab) {
        if (!overflows(s)) return;
        for (Placed p : s.placed) {
            if (p.tab != tab) continue;
            int view = stripViewportLength(s);
            if (posOf(p) < stripScroll) stripScroll = posOf(p);
            else if (posOf(p) + lengthOf(p) > stripScroll + view) stripScroll = posOf(p) + lengthOf(p) - view;
        }
        clampStripScroll(s);
    }

    /** One tab back (-1) or forward (+1): snaps to the next tab edge in that direction. */
    private void stepStrip(StripLayout s, int dir) {
        if (!overflows(s)) return;
        if (dir > 0) {
            for (Placed p : s.placed) {
                if (posOf(p) > stripScroll) { stripScroll = posOf(p); break; }
            }
        } else {
            int target = 0;
            for (Placed p : s.placed) {
                if (posOf(p) < stripScroll) target = posOf(p);
            }
            stripScroll = target;
        }
        clampStripScroll(s);
    }

    /** Whether an element-local point is on the strip (the sidebar column, or the rows on top). */
    private boolean onStrip(StripLayout s, double lx, double ly) {
        if (sidebar()) return lx >= 0 && lx < s.columnWidth && ly >= 0 && ly < getHeight();
        return ly >= 0 && ly < s.height;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Bodies
    // ════════════════════════════════════════════════════════════════════════

    /** The body's top-left, in element-local coordinates: below the strip, or right of the sidebar. */
    private int bodyTop(StripLayout s) {
        if (sidebar()) return 0;
        return s.height + (s.height > 0 ? BODY_GAP : 0);
    }

    private int bodyLeft(StripLayout s) {
        if (!sidebar()) return 0;
        return s.columnWidth + (s.columnWidth > 0 ? BODY_GAP : 0);
    }

    private int bodyWidth(StripLayout s) {
        return Math.max(1, getWidth() - bodyLeft(s));
    }

    /** The shown body, laid out for the current body size, scrolling when it overflows. */
    private record BodyView(Tab tab, int width, int height, int layoutSig, @Nullable ScrollContainer scroll) {}

    private @Nullable BodyView body;

    private @Nullable BodyView bodyView(StripLayout s, @Nullable Tab tab) {
        if (tab == null) return null;
        int w = bodyWidth(s);
        int h = Math.max(0, getHeight() - bodyTop(s));
        int sig = signature(tab.body);
        if (body != null && body.tab == tab && body.width == w && body.height == h && body.layoutSig == sig) {
            return body;
        }
        // Lay the body out the way a panel lays out its own elements: width down,
        // reflow, measure; and only when it overflows, again with the scrollbar
        // lane reserved, hosted in a ScrollContainer whose offset is this tab's.
        Panel.layoutElementsWithin(tab.body, w);
        Panel.reflowForWrap(tab.body);
        int contentH = Panel.contentHeightOf(tab.body);
        ScrollContainer scroll = null;
        int reserve = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
        if (h > 0 && contentH > h && w > reserve + 1) {
            Panel.layoutElementsWithin(tab.body, w - reserve);
            Panel.reflowForWrap(tab.body);
            contentH = Panel.contentHeightOf(tab.body);
            String id = tab.id;
            scroll = ScrollContainer.builder()
                    .at(0, 0)
                    .size(w, h)
                    .content(tab.body)
                    .contentHeight(contentH)
                    .state(() -> bodyScroll.getOrDefault(id, 0.0), v -> bodyScroll.put(id, v))
                    .build();
        }
        // Signature AFTER layout, so a settled body doesn't re-lay itself out every frame.
        body = new BodyView(tab, w, h, signature(tab.body), scroll);
        return body;
    }

    /** Visibility and live geometry of a body's elements: changes when one resizes or toggles. */
    private static int signature(List<PanelElement> elements) {
        int sig = 1;
        for (PanelElement e : elements) {
            boolean vis = e.isVisible();
            sig = sig * 31 + (vis ? 1 : 0);
            if (vis) {
                sig = sig * 31 + e.getChildX() + e.getWidth();
                sig = sig * 31 + e.getChildY() + e.getHeight();
            }
        }
        return sig;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Size (PanelElement reactive sizing)
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public int getWidth() {
        if (fixedWidth >= 0) return fixedWidth;
        return resolvedWidth >= 0 ? resolvedWidth : naturalWidth();
    }

    @Override
    public int getHeight() {
        if (fixedHeight >= 0) return fixedHeight;
        if (filledHeight >= 0) return filledHeight;
        return naturalHeight();
    }

    /**
     * What it wants with room to spare: every visible tab on one row, or the
     * widest body, whichever is wider. The panel clamps this to the screen, so a
     * strip wider than the screen wraps (or scrolls) at the screen's width.
     */
    @Override
    public int naturalWidth() {
        if (fixedWidth >= 0) return fixedWidth;
        Font font = Minecraft.getInstance().font;
        int row = 0, widestBody = 0;
        List<Tab> visible = new ArrayList<>();
        for (Tab t : tabs) {
            if (!t.isVisible()) continue;
            visible.add(t);
            row += Math.max(font.width(t.label) + 2 * LABEL_PAD, MIN_TAB_WIDTH);
            for (PanelElement e : t.body) {
                if (e.isVisible()) widestBody = Math.max(widestBody, e.getChildX() + e.naturalWidth());
            }
        }
        // A sidebar sits beside the body; a strip sits above it.
        if (sidebar()) {
            int column = Math.max(sidebarNaturalWidth(visible), headerNaturalWidth());
            return column + BODY_GAP + widestBody;
        }
        return Math.max(row, widestBody);
    }

    // Natural height is asked for often (every panel pass), and measuring it lays
    // out every visible body, so it is cached against what it depends on: the
    // width, the visible set, and the bodies' own live geometry.
    private int naturalHeightCache = -1;
    private int naturalHeightKey = 0;

    /**
     * The strip plus the tallest visible body (the sidebar: the taller of the
     * column and that body), at the current width: steady across tab switches.
     */
    private int naturalHeight() {
        StripLayout s = strip();
        int w = bodyWidth(s);
        int key = naturalKey(w);
        if (naturalHeightCache >= 0 && key == naturalHeightKey) return naturalHeightCache;
        int tallest = 0;
        for (Tab t : tabs) {
            if (!t.isVisible()) continue;
            Panel.layoutElementsWithin(t.body, w);
            Panel.reflowForWrap(t.body);
            tallest = Math.max(tallest, Panel.contentHeightOf(t.body));
        }
        // The sidebar column: header, then every tab.
        int column = stripViewportStart(s) + s.height;
        naturalHeightCache = sidebar() ? Math.max(column, tallest) : bodyTop(s) + tallest;
        naturalHeightKey = naturalKey(w); // after layout, so a settled body doesn't re-measure
        return naturalHeightCache;
    }

    private int naturalKey(int width) {
        int key = (width * 31 + visibleMask().hashCode()) * 31 + signature(sidebarHeader);
        for (Tab t : tabs) {
            if (t.isVisible()) key = key * 31 + signature(t.body);
        }
        return key;
    }

    /** Width flows down: a Tabs is as wide as the room its panel gives it. */
    @Override
    public void layoutWithin(int budget) {
        if (fixedWidth < 0) resolvedWidth = Math.max(1, budget);
    }

    @Override
    public void fillWidth(int width) {
        if (fixedWidth < 0) resolvedWidth = Math.max(1, width);
    }

    @Override
    public boolean fillsHeight() {
        return fixedHeight < 0;
    }

    @Override
    public void fillHeight(int height) {
        filledHeight = height;
    }

    @Override public boolean isInteractive() { return true; }

    // ════════════════════════════════════════════════════════════════════════
    // Render
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);

        queueTooltip(ctx); // element-level tooltip first, so a tab's own wins

        StripLayout s = strip();
        Tab shown = shownTab();
        clampStripScroll(s);
        if (shown != null && !Objects.equals(shown.id, lastShown)) {
            scrollIntoView(s, shown);     // clicked, or selected from outside
        }
        lastShown = shown == null ? null : shown.id;

        // ── Strip ── (a disabled strip draws as if nothing were hovered)
        RenderContext stripCtx = disabled ? new RenderContext(g, ctx.originX(), ctx.originY(), -1, -1, true) : ctx;
        if (sidebar()) {
            renderSidebar(stripCtx, s, shown, sx, sy);
        } else {
            renderStrip(stripCtx, s, shown, sx, sy);
        }

        // ── Body ── (everything in it disabled with the Tabs)
        BodyView b = bodyView(s, shown);
        if (b == null) return;
        RenderContext bodyCtx = bodyRender(ctx, s);
        if (b.scroll != null) {
            b.scroll.render(bodyCtx);
        } else {
            int bx = bodyCtx.originX(), by = bodyCtx.originY();
            if (b.height > 0) g.enableScissor(bx, by, bx + b.width, by + b.height);
            ChildDispatch.render(b.tab.body, bodyCtx);
            if (b.height > 0) g.disableScissor();
        }
    }

    /** The body's render context: origin at the body's top-left, the disabled cascade added. */
    private RenderContext bodyRender(RenderContext ctx, StripLayout s) {
        return ctx.at(ctx.originX() + childX + bodyLeft(s), ctx.originY() + childY + bodyTop(s))
                .disabledIf(ownDisabled());
    }

    /** The body's input context, the same way. */
    private InputContext bodyInput(InputContext in, StripLayout s) {
        return in.at(in.originX() + childX + bodyLeft(s), in.originY() + childY + bodyTop(s))
                .disabledIf(ownDisabled());
    }

    /** The sidebar header's input context: origin at the column's top-left. */
    private InputContext columnInput(InputContext in) {
        return in.at(in.originX() + childX, in.originY() + childY).disabledIf(ownDisabled());
    }

    /** The strip across the top ({@link Mode#WRAP}, {@link Mode#SIDE_SCROLL}). */
    private void renderStrip(RenderContext ctx, StripLayout s, @Nullable Tab shown, int sx, int sy) {
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        int viewLeft = sx + stripViewportStart(s);
        int viewRight = viewLeft + stripViewportLength(s);
        int shift = s.overflow ? stripScroll : 0;
        if (s.overflow) g.enableScissor(viewLeft, sy, viewRight, sy + s.height);
        Placed hoveredTruncated = null;
        for (Placed p : s.placed) {
            int tx = viewLeft + p.x - shift;
            int ty = sy + p.y;
            if (tx + p.w <= viewLeft || tx >= viewRight) continue;
            boolean isSelected = p.tab == shown;
            boolean hovered = ctx.hasMouseInput()
                    && ctx.mouseX() >= Math.max(tx, viewLeft) && ctx.mouseX() < Math.min(tx + p.w, viewRight)
                    && ctx.mouseY() >= ty && ctx.mouseY() < ty + TAB_HEIGHT;
            Identifier sprite = isSelected
                    ? (hovered ? SPRITE_SELECTED_HOVER : SPRITE_SELECTED)
                    : (hovered ? SPRITE_TAB_HOVER : SPRITE_TAB);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tx, ty, p.w, TAB_HEIGHT);
            // Vanilla's rule (MenuTabButton.renderLabel): an unselected tab's sprite
            // starts its visible body 3 px down, so its label centers in the box below
            // that inset, not over the full height. Same arithmetic as vanilla's
            // scrolling-string centering, so both states match vanilla to the pixel.
            int labelTop = ty + (isSelected ? 0 : UNSELECTED_LABEL_INSET);
            int textY = MKText.centeredTextY(labelTop, ty + TAB_HEIGHT);
            int color = p.tab.standIn
                    ? (isSelected ? COLOR_STAND_IN_SELECTED : COLOR_STAND_IN_UNSELECTED)
                    : (isSelected ? COLOR_SELECTED : COLOR_UNSELECTED);
            g.centeredText(font, p.text, tx + p.w / 2, textY, color);
            if (hovered && p.truncated) hoveredTruncated = p;
        }
        if (s.overflow) {
            g.disableScissor();
            renderArrows(ctx, s, sx, sy);
        }
        if (hoveredTruncated != null) {
            MKTooltip.queue(g, hoveredTruncated.tab.label, ctx.mouseX(), ctx.mouseY());
        }
    }

    /** The column down the left ({@link Mode#SIDEBAR}): header, tabs, and a scrollbar when they overflow. */
    private void renderSidebar(RenderContext ctx, StripLayout s, @Nullable Tab shown, int sx, int sy) {
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        int column = s.columnWidth;
        if (column <= 0) return;

        // Header: ordinary elements, positioned from the column's top-left.
        ChildDispatch.render(sidebarHeader, ctx.at(sx, sy).disabledIf(ownDisabled()));

        // The tabs, clipped to the column under the header. When they overflow,
        // the scrollbar takes a lane on the column's left (the side away from the
        // page) and the tabs narrow into the rest, so the body does not move.
        boolean overflow = overflows(s);
        int viewTop = sy + stripViewportStart(s);
        int viewLength = stripViewportLength(s);
        int viewBottom = viewTop + viewLength;
        if (overflow) {
            followScrollerDrag(s, ctx.mouseY() - viewTop);
        } else {
            draggingScroller = false;
        }
        int shift = overflow ? stripScroll : 0;
        int lane = scrollbarLane(s);
        int tabX = sx + lane, tabW = column - lane;
        g.enableScissor(sx, viewTop, sx + column, viewBottom);
        Placed hoveredTruncated = null;
        for (Placed p : s.placed) {
            int ty = viewTop + p.y - shift;
            if (ty + TAB_HEIGHT <= viewTop || ty >= viewBottom) continue;
            boolean isSelected = p.tab == shown;
            boolean hovered = ctx.hasMouseInput() && !draggingScroller
                    && ctx.mouseX() >= tabX && ctx.mouseX() < tabX + tabW
                    && ctx.mouseY() >= Math.max(ty, viewTop) && ctx.mouseY() < Math.min(ty + TAB_HEIGHT, viewBottom);
            drawSideTab(g, tabX, ty, tabW, TAB_HEIGHT, isSelected, hovered);
            // Cut again for the narrower tab when the lane is taken; laid out without it.
            String text = p.text;
            boolean truncated = p.truncated;
            if (lane > 0) {
                int room = tabW - 2 * LABEL_PAD - SIDE_INSET;
                String full = p.tab.label.getString();
                if (font.width(full) > room) {
                    text = font.plainSubstrByWidth(full, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
                    truncated = true;
                }
            }
            // Labels line up down the column whatever is selected, so it reads as a
            // list: the selected tab grows toward the left, its label does not move.
            int textW = font.width(text);
            int faceLeft = tabX + SIDE_INSET;
            int textX = switch (align) {
                case LEFT, FILL -> faceLeft + LABEL_PAD;
                case CENTER -> faceLeft + (tabW - SIDE_INSET - textW) / 2;
                case RIGHT -> tabX + tabW - LABEL_PAD - textW;
            };
            int textY = MKText.centeredTextY(ty, ty + TAB_HEIGHT);
            int color = p.tab.standIn
                    ? (isSelected ? COLOR_STAND_IN_SELECTED : COLOR_STAND_IN_UNSELECTED)
                    : (isSelected ? COLOR_SELECTED : COLOR_UNSELECTED);
            g.text(font, text, textX, textY, color);
            if (hovered && truncated) hoveredTruncated = p;
        }
        g.disableScissor();
        if (overflow) {
            // Vanilla's list scrollbar: its track, and a handle sized to the share of
            // the column in view.
            int handleH = scrollerHeight(s);
            int handleY = viewTop + scrollerOffset(s, handleH);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_SCROLLER_TRACK, sx, viewTop,
                    SIDEBAR_SCROLLBAR_WIDTH, viewLength);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE_SCROLLER, sx, handleY,
                    SIDEBAR_SCROLLBAR_WIDTH, handleH);
        }
        if (hoveredTruncated != null) {
            MKTooltip.queue(g, hoveredTruncated.tab.label, ctx.mouseX(), ctx.mouseY());
        }
    }

    /**
     * One sidebar tab: vanilla's tab sprite turned on its side, open toward the
     * page on the right. Along the tab's thickness (left to right) the sprite's
     * rows: the unselected inset, the outer cap line, the inner cap line, the
     * face (stretched), and at the page the inner line, then the page edge's
     * light and dark lines, which the selected tab leaves open. Across its length
     * (top to bottom) each row is edge, inner line, face, inner line, edge.
     */
    private static void drawSideTab(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, int w, int h,
                                    boolean selected, boolean hovered) {
        int L = hovered ? TAB_LIGHT_HOVER : TAB_LIGHT;
        int E = TAB_EDGE, P = TAB_LIGHT;
        int face = selected ? TAB_CLEAR : TAB_FILL;
        int cx = x + (selected ? 0 : SIDE_INSET);
        int stretch = x + w - cx - 2 - 3;          // cap lines before, three page-side lines after
        int[][] rows = selected
                ? new int[][]{{E, E, E}, {E, L, L}, {E, L, face}, {E, L, face}, {P, L, TAB_CLEAR}, {E, E, TAB_CLEAR}}
                : new int[][]{{E, E, E}, {E, L, L}, {E, L, face}, hovered ? new int[]{E, L, L} : new int[]{E, L, face},
                              {P, P, P}, {E, E, E}};
        int[] thickness = {1, 1, Math.max(0, stretch), 1, 1, 1};
        for (int i = 0; i < rows.length; i++) {
            int t = thickness[i];
            if (t > 0) drawSideTabRow(g, cx, y, t, h, rows[i]);
            cx += t;
        }
    }

    /** One row of the sprite, {@code t} px thick: {edge, inner, face} mirrored across the tab's length. */
    private static void drawSideTabRow(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, int t, int h,
                                       int[] row) {
        fillIfVisible(g, x, y, x + t, y + 1, row[0]);
        fillIfVisible(g, x, y + 1, x + t, y + 2, row[1]);
        fillIfVisible(g, x, y + 2, x + t, y + h - 2, row[2]);
        fillIfVisible(g, x, y + h - 2, x + t, y + h - 1, row[1]);
        fillIfVisible(g, x, y + h - 1, x + t, y + h, row[0]);
    }

    private static void fillIfVisible(net.minecraft.client.gui.GuiGraphicsExtractor g, int x0, int y0, int x1, int y1,
                                      int color) {
        if ((color >>> 24) != 0 && x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, color);
    }

    // ── Sidebar scrollbar ──────────────────────────────────────────────────

    /** The lane the scrollbar takes on the column's left: its width and gap while the tabs overflow, else 0. */
    private int scrollbarLane(StripLayout s) {
        return sidebar() && overflows(s) ? SIDEBAR_SCROLLBAR_WIDTH + SIDEBAR_SCROLLBAR_GAP : 0;
    }

    /** The handle's height: the share of the column in view, clamped as vanilla's list clamps it. */
    private int scrollerHeight(StripLayout s) {
        int view = stripViewportLength(s);
        int content = Math.max(1, contentLength(s));
        int h = (int) ((float) view * view / content);
        return Math.max(1, Math.min(Math.max(h, SCROLLER_MIN_HEIGHT), Math.max(1, view - SCROLLER_TRACK_MARGIN)));
    }

    /** The handle's top, from the viewport's top, for the current scroll. */
    private int scrollerOffset(StripLayout s, int handleH) {
        int max = maxStripScroll(s);
        int travel = stripViewportLength(s) - handleH;
        return max <= 0 || travel <= 0 ? 0 : (int) ((long) stripScroll * travel / max);
    }

    /** While the handle is held, scroll so it stays under the mouse where it was grabbed. */
    private void followScrollerDrag(StripLayout s, double mouseFromViewTop) {
        if (!draggingScroller) return;
        int handleH = scrollerHeight(s);
        int travel = stripViewportLength(s) - handleH;
        if (travel <= 0) return;
        double top = Math.max(0, Math.min(travel, mouseFromViewTop - dragGrabOffset));
        stripScroll = (int) Math.round(top * maxStripScroll(s) / travel);
        clampStripScroll(s);
    }

    private void renderArrows(RenderContext ctx, StripLayout s, int sx, int sy) {
        var g = ctx.graphics();
        int ay = sy + (TAB_HEIGHT - ARROW_SPRITE_H) / 2;
        int backX = sx + (ARROW_WIDTH - ARROW_SPRITE_W) / 2;
        int fwdX = sx + getWidth() - ARROW_WIDTH + (ARROW_WIDTH - ARROW_SPRITE_W) / 2;
        // An arrow is hidden once its end is reached; its space stays, so tabs don't shift.
        if (stripScroll > 0) {
            boolean hover = ctx.isHovered(childX, childY, ARROW_WIDTH, TAB_HEIGHT);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? SPRITE_BACK_HOVER : SPRITE_BACK,
                    backX, ay, ARROW_SPRITE_W, ARROW_SPRITE_H);
        }
        if (stripScroll < maxStripScroll(s)) {
            boolean hover = ctx.isHovered(childX + getWidth() - ARROW_WIDTH, childY, ARROW_WIDTH, TAB_HEIGHT);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? SPRITE_FORWARD_HOVER : SPRITE_FORWARD,
                    fwdX, ay, ARROW_SPRITE_W, ARROW_SPRITE_H);
        }
    }

    /**
     * An outside click reaches the header and the shown body, so an open popover
     * there (a Dropdown) closes like one anywhere else.
     */
    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(sidebarHeader, columnInput(in));
        BodyView b = liveBody();
        if (b == null) return;
        InputContext inner = bodyInput(in, strip());
        if (b.scroll != null) b.scroll.notifyClickOutsideOverlay(inner);
        else ChildDispatch.notifyClickOutside(b.tab.body, inner);
    }

    /** The header's and the shown body's overlays (an open Dropdown popover), unclipped, after all base renders. */
    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(sidebarHeader,
                ctx.at(ctx.originX() + childX, ctx.originY() + childY).disabledIf(ownDisabled()));
        BodyView b = liveBody();
        if (b == null) return;
        RenderContext bodyCtx = bodyRender(ctx, strip());
        if (b.scroll != null) b.scroll.renderOverlay(bodyCtx);
        else ChildDispatch.renderOverlay(b.tab.body, bodyCtx);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Input
    // ════════════════════════════════════════════════════════════════════════

    /** The shown body as last laid out, or null. Input goes only to the shown tab. */
    private @Nullable BodyView liveBody() {
        Tab shown = shownTab();
        return (body != null && body.tab == shown) ? body : null;
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        StripLayout s = strip();
        BodyView b = liveBody();
        InputContext inner = b == null ? null : bodyInput(in, s);

        // An open popover in the body claims the click anywhere in its bounds.
        if (b != null) {
            int[] ov = b.scroll != null ? b.scroll.getActiveOverlayBounds(inner)
                    : ChildDispatch.activeOverlay(b.tab.body, inner);
            if (ov != null && in.isInside(ov)) {
                return b.scroll != null ? b.scroll.mouseClicked(inner, button)
                        : ChildDispatch.mouseClicked(b.tab.body, inner, button);
            }
        }

        double lx = in.mouseX() - (in.originX() + childX), ly = in.mouseY() - (in.originY() + childY);
        if (onStrip(s, lx, ly)) {
            if (disabled(in)) return true;               // a disabled strip eats its clicks, picks nothing
            if (sidebar()) return clickSidebar(s, in, lx, ly, button);
            if (button != Click.LEFT) return true;        // the strip eats its own clicks
            if (s.overflow && lx < ARROW_WIDTH) { stepStrip(s, -1); return true; }
            if (s.overflow && lx >= getWidth() - ARROW_WIDTH) { stepStrip(s, +1); return true; }
            double contentX = lx - stripViewportStart(s) + (s.overflow ? stripScroll : 0);
            for (Placed p : s.placed) {
                if (contentX >= p.x && contentX < p.x + p.w && ly >= p.y && ly < p.y + TAB_HEIGHT) {
                    select(p.tab);
                    return true;
                }
            }
            return true;
        }
        if (b == null) return false;
        if (b.scroll != null) return b.scroll.hitTest(inner) && b.scroll.mouseClicked(inner, button);
        return ChildDispatch.mouseClicked(b.tab.body, inner, button);
    }

    /** A click in the sidebar column: header elements, then the scrollbar lane, then a tab. */
    private boolean clickSidebar(StripLayout s, InputContext in, double lx, double ly, int button) {
        if (ChildDispatch.mouseClicked(sidebarHeader, columnInput(in), button)) return true;
        if (button != Click.LEFT) return true;            // the column eats its own clicks
        int viewStart = stripViewportStart(s);
        if (ly < viewStart || ly >= viewStart + stripViewportLength(s)) return true;
        if (lx < scrollbarLane(s)) {
            // On the handle: grab it where it was pressed. On the track: jump so the
            // handle centres on the click, and keep holding it, as vanilla's list does.
            int handleH = scrollerHeight(s);
            int handleTop = scrollerOffset(s, handleH);
            double fromTop = ly - viewStart;
            boolean onHandle = fromTop >= handleTop && fromTop < handleTop + handleH;
            dragGrabOffset = onHandle ? fromTop - handleTop : handleH / 2.0;
            draggingScroller = true;
            followScrollerDrag(s, fromTop);
            return true;
        }
        double contentY = ly - viewStart + (overflows(s) ? stripScroll : 0);
        for (Placed p : s.placed) {
            if (contentY >= p.y && contentY < p.y + TAB_HEIGHT) {
                select(p.tab);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        StripLayout s = strip();
        double lx = in.mouseX() - (in.originX() + childX), ly = in.mouseY() - (in.originY() + childY);
        if (onStrip(s, lx, ly)) {
            if (!overflows(s) || disabled(in)) return false;
            double delta = scrollY != 0 ? scrollY : scrollX;
            if (delta == 0) return false;
            stepStrip(s, delta > 0 ? -1 : +1);             // wheel up = back, down = forward
            return true;
        }
        BodyView b = liveBody();
        if (b == null) return false;
        InputContext inner = bodyInput(in, s);
        if (b.scroll != null) return b.scroll.mouseScrolled(inner, scrollX, scrollY);
        return ChildDispatch.mouseScrolled(b.tab.body, inner, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        // Releases go everywhere un-hit-tested, so a pressed button always lets go.
        ChildDispatch.mouseReleased(sidebarHeader, columnInput(in), button);
        if (button == Click.LEFT) draggingScroller = false;
        BodyView b = liveBody();
        if (b == null) return false;
        InputContext inner = bodyInput(in, strip());
        if (b.scroll != null) return b.scroll.mouseReleased(inner, button);
        ChildDispatch.mouseReleased(b.tab.body, inner, button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        // No shortcuts of its own (Trev, 2026-09-26): keys go to the header, then the shown body.
        if (ChildDispatch.keyPressed(sidebarHeader, columnInput(in), keyCode, scanCode, modifiers)) return true;
        BodyView b = liveBody();
        if (b == null) return false;
        InputContext inner = bodyInput(in, strip());
        if (b.scroll != null) return b.scroll.keyPressed(inner, keyCode, scanCode, modifiers);
        return ChildDispatch.keyPressed(b.tab.body, inner, keyCode, scanCode, modifiers);
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        int[] header = ChildDispatch.activeOverlay(sidebarHeader, columnInput(in));
        if (header != null) return header;
        BodyView b = liveBody();
        if (b == null) return null;
        InputContext inner = bodyInput(in, strip());
        return b.scroll != null ? b.scroll.getActiveOverlayBounds(inner) : ChildDispatch.activeOverlay(b.tab.body, inner);
    }

    /** Every tab's body attaches, shown or not, as a hidden panel's elements do. */
    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        for (Tab t : tabs) ChildDispatch.attach(t.body, screen);
        ChildDispatch.attach(sidebarHeader, screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        for (Tab t : tabs) ChildDispatch.detach(t.body, screen);
        ChildDispatch.detach(sidebarHeader, screen);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Tabs and builders
    // ════════════════════════════════════════════════════════════════════════

    /** One tab: an id, a label, a body, and whether it shows. Built with {@link Tabs#tab(String)}. */
    public static final class Tab {
        final String id;
        final Component label;
        final @Nullable BooleanSupplier visibleWhen;
        final List<PanelElement> body;
        final boolean standIn;

        private Tab(String id, Component label, @Nullable BooleanSupplier visibleWhen, List<PanelElement> body,
                    boolean standIn) {
            this.id = id;
            this.label = label;
            this.visibleWhen = visibleWhen;
            this.body = body;
            this.standIn = standIn;
        }

        public String id() { return id; }
        public Component label() { return label; }

        boolean isVisible() {
            return visibleWhen == null || visibleWhen.getAsBoolean();
        }
    }

    /** A tab under construction. An {@code icon(...)} can join the label here later without breaking anything. */
    public static final class TabSpec {
        private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

        private final String id;
        private @Nullable Component label;
        private @Nullable BooleanSupplier visibleWhen;
        private List<PanelElement> body = List.of();
        private @Nullable Supplier<List<PanelElement>> bodyFactory;
        private boolean standIn;
        private @Nullable String anchor;
        private boolean anchorIsBefore;

        private TabSpec(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public String id() { return id; }

        public TabSpec label(Component label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /** Read every frame; a change relays out the strip. Default: always shown. */
        public TabSpec visibleWhen(BooleanSupplier visibleWhen) {
            this.visibleWhen = Objects.requireNonNull(visibleWhen, "visibleWhen");
            return this;
        }

        /** The body: elements positioned from the body's top-left. For the owner's own tabs. */
        public TabSpec body(List<PanelElement> body) {
            this.body = List.copyOf(body);
            this.bodyFactory = null;
            return this;
        }

        /**
         * The body as a factory, run each time the menu is built, so the body's
         * elements are fresh and read the current config. Required for a tab added
         * with {@link Tabs#addTo}; fine for the owner's tabs too.
         */
        public TabSpec body(Supplier<List<PanelElement>> factory) {
            this.bodyFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        /**
         * Owner only: this tab stands in for one another mod may add. A tab added
         * to the menu with the same id takes this one's place, label and body; with
         * nothing to replace it, the tab shows with a dimmed label and this body,
         * typically a line saying which mod to install.
         *
         * <p>Only the place carries over. The replacement keeps its own
         * {@code visibleWhen}, or always shows if it has none: a stand-in's
         * visibility is about advertising the missing mod (an owner's "hide the
         * companion's tabs" switch), not about the real feature once it is there.
         */
        public TabSpec standIn() {
            this.standIn = true;
            return this;
        }

        /** For a tab added with {@link Tabs#addTo}: place it right after the tab with this id. */
        public TabSpec after(String id) {
            this.anchor = Objects.requireNonNull(id, "id");
            this.anchorIsBefore = false;
            return this;
        }

        /** For a tab added with {@link Tabs#addTo}: place it right before the tab with this id. */
        public TabSpec before(String id) {
            this.anchor = Objects.requireNonNull(id, "id");
            this.anchorIsBefore = true;
            return this;
        }

        boolean isStandIn() { return standIn; }
        @Nullable String anchor() { return anchor; }
        boolean anchorIsBefore() { return anchorIsBefore; }

        /**
         * Builds the tab, running the body factory. A factory that throws is
         * another mod's bug, and the owner's screen must still open: the tab gets a
         * one-line body saying so, and the exception is logged.
         */
        Tab build() {
            if (label == null) throw new IllegalStateException("Tabs: tab '" + id + "' has no label");
            List<PanelElement> built = body;
            if (bodyFactory != null) {
                try {
                    built = List.copyOf(bodyFactory.get());
                } catch (RuntimeException e) {
                    LOGGER.error("[MenuKit] the body of tab '{}' failed to build", id, e);
                    built = List.of(TextLabel.builder()
                            .text(Component.literal("This tab failed to build; see the log.")).build());
                }
            }
            return new Tab(id, label, visibleWhen, built, standIn);
        }
    }

    public static final class Builder extends AbstractPanelElement.Builder<Tabs, Builder> {
        private Mode mode = Mode.WRAP;
        private Align align = Align.LEFT;
        private final List<TabSpec> tabs = new ArrayList<>();
        private @Nullable Identifier menu;
        private @Nullable Supplier<@Nullable String> selected;
        private @Nullable Consumer<String> onSelect;
        private int fixedWidth = -1, fixedHeight = -1;
        private final List<PanelElement> sidebarHeader = new ArrayList<>();

        private Builder() {}

        @Override protected Builder self() { return this; }

        /** {@link Mode#WRAP} (default), {@link Mode#SIDE_SCROLL}, or {@link Mode#SIDEBAR}. */
        public Builder mode(Mode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /** Where tabs sit in a row. Default {@link Align#LEFT}. */
        public Builder align(Align align) {
            this.align = Objects.requireNonNull(align, "align");
            return this;
        }

        /**
         * Required: the selection lens. {@code get} reads which tab is selected
         * every frame (null or an unknown id shows the first visible tab), and
         * {@code set} receives the id the player picks.
         */
        public Builder state(Supplier<@Nullable String> get, Consumer<String> set) {
            this.selected = Objects.requireNonNull(get, "get");
            this.onSelect = Objects.requireNonNull(set, "set");
            return this;
        }

        /** Adds a tab that is always shown. */
        public Builder tab(String id, Component label, List<PanelElement> body) {
            return tab(Tabs.tab(id).label(label).body(body));
        }

        /** Adds a tab built with {@link Tabs#tab(String)}. The builder's order is the strip's order. */
        public Builder tab(TabSpec spec) {
            Objects.requireNonNull(spec, "spec");
            if (spec.anchor != null) {
                throw new IllegalStateException("Tabs: tab '" + spec.id + "' uses after/before, which place a "
                        + "tab added by another mod; in the owner's builder, the order you add tabs is the order");
            }
            tabs.add(spec);
            return this;
        }

        /**
         * Names this menu so other mods can add tabs to it with {@link Tabs#addTo}.
         * Every tab added to this name before {@link #build()} is in the result.
         */
        public Builder menu(Identifier name) {
            this.menu = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * A fixed box instead of filling the panel, for a panel with no height
         * budget (a panel on a vanilla screen, say).
         */
        @Override
        public Builder size(int width, int height) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Tabs: size must be positive");
            this.fixedWidth = width;
            this.fixedHeight = height;
            return super.size(width, height);
        }

        /**
         * Elements above the sidebar's tab column (a Back button, say), positioned
         * from the column's top-left and laid out to its width the way a panel
         * lays out its own elements. The column widens to fit them, within its
         * cap; the body beside it still starts at the top. {@link Mode#SIDEBAR} only.
         */
        public Builder sidebarHeader(PanelElement... elements) {
            for (PanelElement e : elements) sidebarHeader.add(Objects.requireNonNull(e, "element"));
            return this;
        }

        public Tabs build() {
            if (selected == null) throw new IllegalStateException("Tabs: state(get, set) is required");
            if (!sidebarHeader.isEmpty() && mode != Mode.SIDEBAR) {
                throw new IllegalStateException("Tabs: sidebarHeader(...) needs mode(Mode.SIDEBAR); there is no "
                        + "column above which to put it");
            }
            Set<String> ids = new HashSet<>();
            for (TabSpec t : tabs) {
                if (!ids.add(t.id)) throw new IllegalStateException("Tabs: two tabs share the id '" + t.id + "'");
            }
            return new Tabs(this);
        }

        /** The owner's tabs with any added to this menu merged in, each built now (bodies included). */
        private List<Tab> resolveTabs() {
            List<TabSpec> specs = menu == null ? tabs : TabMenus.merge(menu, tabs);
            List<Tab> out = new ArrayList<>(specs.size());
            for (TabSpec spec : specs) out.add(spec.build());
            return out;
        }
    }
}
