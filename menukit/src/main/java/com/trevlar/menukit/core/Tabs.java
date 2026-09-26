package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
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
 *         .selected(() -> state.tab, id -> state.tab = id)
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
 * a callback writes it when the player clicks a tab or uses the keyboard.
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
 * <h3>Keyboard</h3>
 *
 * Vanilla's tab contract: Ctrl+Tab and Ctrl+Shift+Tab cycle through the visible
 * tabs, and Ctrl+1 to Ctrl+9 (and Ctrl+0 for the tenth) jump to one. Cmd works in
 * place of Ctrl, but on macOS the system takes Cmd+Tab, so physical Ctrl is the
 * key there. No arrow keys: MenuKit has no element focus, and elements see a key
 * before a focused text field does, so arrows on the strip would steal them.
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
public final class Tabs extends AbstractPanelElement<Tabs> {

    @Override protected Tabs self() { return this; }

    /** How the strip lays out when the tabs don't fit one row. */
    public enum Mode {
        /** Break into as many rows as the width needs. */
        WRAP,
        /** Stay one row and scroll horizontally. */
        SIDE_SCROLL
    }

    /** Where tabs sit within a row. */
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

    // ── Declared structure (fixed at construction, §0022) ──────────────────

    private final Mode mode;
    private final Align align;
    private final List<Tab> tabs;
    private final Supplier<@Nullable String> selected;
    private final Consumer<String> onSelect;
    private final int fixedWidth;   // -1 = take the panel's width
    private final int fixedHeight;  // -1 = take the panel's height (fillHeight)

    // ── Resolved geometry ──────────────────────────────────────────────────

    private int resolvedWidth = -1;   // from layoutWithin; -1 until the first pass
    private int filledHeight = -1;    // from fillHeight; -1 = no viewport

    // ── View state (not consumer state) ────────────────────────────────────

    /** Each tab's body scroll, normalised 0..1, kept for the life of this element. */
    private final Map<String, Double> bodyScroll = new HashMap<>();
    /** Side-scroll strip offset in pixels, when the row overflows. */
    private int stripScroll = 0;
    /** The tab shown last frame, to scroll a newly selected tab into view. */
    private @Nullable String lastShown = null;

    // Screen-space origin from the last render; input hooks get no RenderContext
    // (the same one-frame-stale cache ScrollContainer uses).
    private int originX, originY;
    private boolean originValid = false;

    private Tabs(Builder b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.mode = b.mode;
        this.align = b.align;
        this.tabs = List.copyOf(b.resolveTabs());
        this.selected = b.selected;
        this.onSelect = b.onSelect;
        this.fixedWidth = b.width;
        this.fixedHeight = b.height;
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

    /** The strip for one width and one visible set. */
    private record StripLayout(List<Placed> placed, int height, boolean overflow, int contentWidth) {}

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
        if (strip == null || w != stripWidthKey || !mask.equals(stripVisibleKey)) {
            strip = layoutStrip(w, mask);
            stripWidthKey = w;
            stripVisibleKey = mask;
        }
        return strip;
    }

    private StripLayout layoutStrip(int width, BitSet mask) {
        Font font = Minecraft.getInstance().font;
        List<Tab> visible = new ArrayList<>();
        for (int i = mask.nextSetBit(0); i >= 0; i = mask.nextSetBit(i + 1)) visible.add(tabs.get(i));
        if (visible.isEmpty() || width <= 0) return new StripLayout(List.of(), 0, false, 0);

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
        return new StripLayout(List.copyOf(placed), y, overflow, contentWidth);
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
    // Side-scroll geometry
    // ════════════════════════════════════════════════════════════════════════

    /** Left edge of the tab viewport within the element (after the back arrow when overflowing). */
    private int stripViewportLeft(StripLayout s) {
        return s.overflow ? ARROW_WIDTH : 0;
    }

    private int stripViewportWidth(StripLayout s) {
        return s.overflow ? Math.max(1, getWidth() - 2 * ARROW_WIDTH) : getWidth();
    }

    private int maxStripScroll(StripLayout s) {
        return s.overflow ? Math.max(0, s.contentWidth - stripViewportWidth(s)) : 0;
    }

    private void clampStripScroll(StripLayout s) {
        stripScroll = Math.max(0, Math.min(stripScroll, maxStripScroll(s)));
    }

    /** Scrolls the least distance that brings {@code tab} fully into view. */
    private void scrollIntoView(StripLayout s, Tab tab) {
        if (!s.overflow) return;
        for (Placed p : s.placed) {
            if (p.tab != tab) continue;
            int view = stripViewportWidth(s);
            if (p.x < stripScroll) stripScroll = p.x;
            else if (p.x + p.w > stripScroll + view) stripScroll = p.x + p.w - view;
        }
        clampStripScroll(s);
    }

    /** One tab left (-1) or right (+1): snaps to the next tab edge in that direction. */
    private void stepStrip(StripLayout s, int dir) {
        if (!s.overflow) return;
        if (dir > 0) {
            for (Placed p : s.placed) {
                if (p.x > stripScroll) { stripScroll = p.x; break; }
            }
        } else {
            int target = 0;
            for (Placed p : s.placed) {
                if (p.x < stripScroll) target = p.x;
            }
            stripScroll = target;
        }
        clampStripScroll(s);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Bodies
    // ════════════════════════════════════════════════════════════════════════

    /** The body top, in element-local coordinates. */
    private int bodyTop(StripLayout s) {
        return s.height + (s.height > 0 ? BODY_GAP : 0);
    }

    /** The shown body, laid out for the current body size, scrolling when it overflows. */
    private record BodyView(Tab tab, int width, int height, int layoutSig, @Nullable ScrollContainer scroll) {}

    private @Nullable BodyView body;

    private @Nullable BodyView bodyView(StripLayout s, @Nullable Tab tab) {
        if (tab == null) return null;
        int w = getWidth();
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
                    .scrollOffset(() -> bodyScroll.getOrDefault(id, 0.0), v -> bodyScroll.put(id, v))
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
        for (Tab t : tabs) {
            if (!t.isVisible()) continue;
            row += Math.max(font.width(t.label) + 2 * LABEL_PAD, MIN_TAB_WIDTH);
            for (PanelElement e : t.body) {
                if (e.isVisible()) widestBody = Math.max(widestBody, e.getChildX() + e.naturalWidth());
            }
        }
        return Math.max(row, widestBody);
    }

    // Natural height is asked for often (every panel pass), and measuring it lays
    // out every visible body, so it is cached against what it depends on: the
    // width, the visible set, and the bodies' own live geometry.
    private int naturalHeightCache = -1;
    private int naturalHeightKey = 0;

    /** The strip plus the tallest visible body, at the current width: steady across tab switches. */
    private int naturalHeight() {
        StripLayout s = strip();
        int w = getWidth();
        int key = naturalKey(w);
        if (naturalHeightCache >= 0 && key == naturalHeightKey) return naturalHeightCache;
        int tallest = 0;
        for (Tab t : tabs) {
            if (!t.isVisible()) continue;
            Panel.layoutElementsWithin(t.body, w);
            Panel.reflowForWrap(t.body);
            tallest = Math.max(tallest, Panel.contentHeightOf(t.body));
        }
        naturalHeightCache = bodyTop(s) + tallest;
        naturalHeightKey = naturalKey(w); // after layout, so a settled body doesn't re-measure
        return naturalHeightCache;
    }

    private int naturalKey(int width) {
        int key = width * 31 + visibleMask().hashCode();
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
        Font font = Minecraft.getInstance().font;
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        originX = sx;
        originY = sy;
        originValid = true;

        queueTooltip(ctx); // element-level tooltip first, so a tab's own wins

        StripLayout s = strip();
        Tab shown = shownTab();
        clampStripScroll(s);
        if (shown != null && !Objects.equals(shown.id, lastShown)) {
            scrollIntoView(s, shown);     // clicked, keyed, or selected from outside
        }
        lastShown = shown == null ? null : shown.id;

        // ── Strip ──
        int viewLeft = sx + stripViewportLeft(s);
        int viewRight = viewLeft + stripViewportWidth(s);
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
            int textY = (labelTop + ty + TAB_HEIGHT - font.lineHeight) / 2 + 1;
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

        // ── Body ──
        BodyView b = bodyView(s, shown);
        if (b == null) return;
        RenderContext bodyCtx = new RenderContext(g, sx, sy + bodyTop(s), ctx.mouseX(), ctx.mouseY());
        if (b.scroll != null) {
            b.scroll.render(bodyCtx);
        } else {
            if (b.height > 0) g.enableScissor(sx, sy + bodyTop(s), sx + b.width, sy + bodyTop(s) + b.height);
            for (PanelElement e : b.tab.body) {
                if (e.isVisible()) e.render(bodyCtx);
            }
            if (b.height > 0) g.disableScissor();
        }
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

    /** The shown body's overlays (an open Dropdown popover), unclipped, after all base renders. */
    @Override
    public void renderOverlay(RenderContext ctx) {
        if (body == null || !originValid) return;
        StripLayout s = strip();
        RenderContext bodyCtx = new RenderContext(ctx.graphics(), ctx.originX() + childX,
                ctx.originY() + childY + bodyTop(s), ctx.mouseX(), ctx.mouseY());
        if (body.scroll != null) {
            body.scroll.renderOverlay(bodyCtx);
        } else {
            for (PanelElement e : body.tab.body) {
                if (e.isVisible()) e.renderOverlay(bodyCtx);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Input
    // ════════════════════════════════════════════════════════════════════════

    /** The shown body as last rendered, or null. Input goes only to the shown tab. */
    private @Nullable BodyView liveBody() {
        Tab shown = shownTab();
        return (body != null && body.tab == shown) ? body : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!originValid) return false;
        StripLayout s = strip();
        BodyView b = liveBody();

        // An open popover in the body claims the click anywhere in its bounds.
        if (b != null) {
            int[] ov = overlayBounds(b);
            if (ov != null && mouseX >= ov[0] && mouseX < ov[0] + ov[2] && mouseY >= ov[1] && mouseY < ov[1] + ov[3]) {
                return b.scroll != null ? b.scroll.mouseClicked(mouseX, mouseY, button)
                        : clickChildren(b, mouseX, mouseY, button);
            }
        }

        double lx = mouseX - originX, ly = mouseY - originY;
        if (ly >= 0 && ly < s.height) {
            if (button != 0) return true;                 // the strip eats its own clicks
            if (s.overflow && lx < ARROW_WIDTH) { stepStrip(s, -1); return true; }
            if (s.overflow && lx >= getWidth() - ARROW_WIDTH) { stepStrip(s, +1); return true; }
            int shift = s.overflow ? stripScroll : 0;
            double contentX = lx - stripViewportLeft(s) + shift;
            for (Placed p : s.placed) {
                if (contentX >= p.x && contentX < p.x + p.w && ly >= p.y && ly < p.y + TAB_HEIGHT) {
                    select(p.tab);
                    return true;
                }
            }
            return true;
        }
        if (b == null) return false;
        if (b.scroll != null) return b.scroll.mouseClicked(mouseX, mouseY, button);
        return clickChildren(b, mouseX, mouseY, button);
    }

    private boolean clickChildren(BodyView b, double mouseX, double mouseY, int button) {
        int bx = originX, by = originY + bodyTop(strip());
        for (PanelElement e : b.tab.body) {
            if (!e.isVisible()) continue;
            int ex = bx + e.getChildX(), ey = by + e.getChildY();
            if (mouseX < ex || mouseX >= ex + e.getWidth() || mouseY < ey || mouseY >= ey + e.getHeight()) continue;
            if (e.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!originValid) return false;
        StripLayout s = strip();
        double ly = mouseY - originY;
        if (ly >= 0 && ly < s.height) {
            if (!s.overflow) return false;
            double delta = scrollY != 0 ? scrollY : scrollX;
            if (delta == 0) return false;
            stepStrip(s, delta > 0 ? -1 : +1);             // wheel up = back, down = forward
            return true;
        }
        BodyView b = liveBody();
        if (b == null) return false;
        if (b.scroll != null) return b.scroll.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        for (PanelElement e : b.tab.body) {
            if (e.isVisible() && e.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        BodyView b = liveBody();
        if (b == null) return false;
        if (b.scroll != null) return b.scroll.mouseReleased(mouseX, mouseY, button);
        for (PanelElement e : b.tab.body) {
            if (e.isVisible()) e.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl or Cmd: the body's own shortcuts rarely use either with Tab or a digit.
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (ctrl) {
            List<Tab> visible = new ArrayList<>();
            for (Tab t : tabs) if (t.isVisible()) visible.add(t);
            if (!visible.isEmpty()) {
                if (keyCode == GLFW.GLFW_KEY_TAB) {
                    int at = visible.indexOf(shownTab());
                    int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
                    select(visible.get(Math.floorMod(at + step, visible.size())));
                    return true;
                }
                if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                    // Vanilla's mapping: 1..9 are the first nine tabs, 0 the tenth.
                    int index = Math.floorMod(keyCode - GLFW.GLFW_KEY_1, 10);
                    if (index < visible.size()) {
                        select(visible.get(index));
                        return true;
                    }
                }
            }
        }
        BodyView b = liveBody();
        if (b == null) return false;
        if (b.scroll != null) return b.scroll.keyPressed(keyCode, scanCode, modifiers);
        for (PanelElement e : b.tab.body) {
            if (e.isVisible() && e.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds() {
        BodyView b = liveBody();
        return b == null ? null : overlayBounds(b);
    }

    private static int @Nullable [] overlayBounds(BodyView b) {
        if (b.scroll != null) return b.scroll.getActiveOverlayBounds();
        for (PanelElement e : b.tab.body) {
            if (!e.isVisible()) continue;
            int[] ov = e.getActiveOverlayBounds();
            if (ov != null) return ov;
        }
        return null;
    }

    /** Every tab's body attaches, shown or not, as a hidden panel's elements do. */
    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        for (Tab t : tabs) for (PanelElement e : t.body) e.onAttach(screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        for (Tab t : tabs) for (PanelElement e : t.body) e.onDetach(screen);
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
                    built = List.of(new TextLabel(0, 0, Component.literal("This tab failed to build; see the log.")));
                }
            }
            return new Tab(id, label, visibleWhen, built, standIn);
        }
    }

    public static final class Builder {
        private int childX, childY;
        private Mode mode = Mode.WRAP;
        private Align align = Align.LEFT;
        private final List<TabSpec> tabs = new ArrayList<>();
        private @Nullable Identifier menu;
        private @Nullable Supplier<@Nullable String> selected;
        private @Nullable Consumer<String> onSelect;
        private int width = -1, height = -1;

        private Builder() {}

        /** Panel-local position. Default (0, 0). */
        public Builder at(int x, int y) {
            this.childX = x;
            this.childY = y;
            return this;
        }

        /** {@link Mode#WRAP} (default) or {@link Mode#SIDE_SCROLL}. */
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
         * The selection lens: {@code supplier} reads which tab is selected every
         * frame (null or an unknown id shows the first visible tab), and
         * {@code onSelect} writes the id the player picks. Required.
         */
        public Builder selected(Supplier<@Nullable String> supplier, Consumer<String> onSelect) {
            this.selected = Objects.requireNonNull(supplier, "supplier");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
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
        public Builder size(int width, int height) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Tabs: size must be positive");
            this.width = width;
            this.height = height;
            return this;
        }

        public Tabs build() {
            if (selected == null) throw new IllegalStateException("Tabs: selected(supplier, onSelect) is required");
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
