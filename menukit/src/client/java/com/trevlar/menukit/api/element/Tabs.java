package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.core.TabRows;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.trevlar.menukit.core.VanillaLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2f;
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
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
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
 * <h3>Selection is a lens</h3>
 *
 * The consumer owns which tab is selected: a supplier reads it every frame and
 * a callback writes it when the player clicks a tab. Selecting a tab from
 * outside is writing your own field; the strip shows it on
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
 * as a tooltip). A tab with an {@linkplain TabSpec#icon icon} is wider by the
 * icon and {@link #ICON_LABEL_GAP}; the icon sits left of the label and the two
 * are placed as one group (centred on the strip, aligned in the sidebar), the
 * selected underline under the label alone.
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
 *       its space kept, once its end is reached). The wheel over the strip scrolls
 *       it by pixels, as a {@link ScrollContainer} scrolls its body; an arrow
 *       glides it one tab along, and a newly selected tab, including one selected
 *       from outside, glides into view with the least movement.</li>
 *   <li><b>{@link Mode#SIDEBAR}</b>: a column of tabs down the left, the body to
 *       its right. The top tab turned on its side: the selected tab reaches 4 px
 *       further left and opens into the page, the others close against it with the
 *       page's edge. The column is as wide as its widest label, at most a third of
 *       the element, and every tab in it is that wide. {@link Align} places the
 *       label in the tab ({@link Align#FILL} as {@link Align#LEFT}), and labels
 *       stay put on selection so the column reads as a list. When the tabs are
 *       taller than the column, a {@link ScrollContainer}'s scrollbar runs down its
 *       left edge, the same track and handle at the same size: drag the handle, or
 *       press the track to jump there. The wheel over the column scrolls it by
 *       pixels, and a newly selected tab glides into view. The tabs give the bar
 *       its lane rather than the column widening, so the body never moves.
 *       {@link Builder#sidebarHeader} puts elements above the column (a Back
 *       button, say), in the column's width; the body still starts at the top.
 *       {@link Builder#tabColumn tabColumn(false)} draws no column at all: the
 *       column is 0 wide and the body takes the whole width, on the same page
 *       (band, insets, separators, scrollbar) the column would sit beside. A
 *       single-page settings screen that looks like its tabbed siblings.</li>
 * </ul>
 * <p>Both strips scroll by MenuKit's one scroller, {@code core.Scroller}, the
 * model and scrollbar a {@link ScrollContainer} uses: an offset in pixels, the
 * wheel's step, clamping, the drag, the handle's fixed size, and its look per
 * kind of screen. Only a jump the player did not drag (an arrow, a selection)
 * glides; the wheel and the handle move it at once, as in a body.
 *
 * <h3>On an options screen</h3>
 *
 * The strip ({@link Mode#WRAP}, {@link Mode#SIDE_SCROLL}) always draws vanilla's
 * tab sprites. On an options screen ({@link ScreenKind#OPTIONS}) it is the tab
 * bar of vanilla's Create World screen: every label white with a shadow, the
 * selected one underlined, vanilla's separator
 * along the strip's bottom on either side of the tabs, and the selected tab open
 * into the page below it, which is vanilla's darker list background down to a
 * separator at the bottom. The page insets the body as {@link ScrollContainer}'s
 * band does: {@link ScrollContainer#OPTIONS_INSET_X} from each side (the scrollbar
 * lane is the right margin when the body scrolls) and
 * {@link ScrollContainer#OPTIONS_INSET_Y} above and below, so the body is laid out
 * that much narrower. Behaviour, sizes and the arrows (vanilla's page
 * arrows) are the same on both kinds.
 *
 * <p>{@link Mode#SIDEBAR} on an options screen is that tab bar turned on its
 * side: vanilla's tab sprites drawn under a pose turned a quarter, open toward
 * the page on the right, the selected one open into it; vanilla's separator
 * down the column's edge above and below the tabs; and the page, the same list
 * background, from the column's edge to the element's right, inset as the top
 * strip's page is ({@link ScrollContainer#OPTIONS_INSET_X} from the column and
 * the right edge, {@link ScrollContainer#OPTIONS_INSET_Y} above and below).
 * Labels stay upright, white, the selected one underlined. Column width and
 * scrolling are the same on both kinds; the scrollbar is a ScrollContainer's,
 * vanilla's thin list scroller here. On a container screen
 * the sidebar draws MenuKit's own tab in vanilla's tab colours.
 *
 * <p>{@link Builder#header} puts a row across the whole element, above the strip
 * (or the sidebar column) and the body, in every mode: the menu's own title bar.
 * Its elements get the element's full width, so a {@link Flow} with a spacer at
 * each end centres a label across it. Everything else starts under it and its
 * {@link #HEADER_GAP}, and the body is that much shorter.
 *
 * <p>{@link Align#FILL} gives every tab in a row its label width plus an equal
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
 * {@link ScrollContainer} content or a {@link com.trevlar.menukit.api.layout.Column}.
 * It is laid out to the body width exactly as a panel lays out its own elements
 * (text wraps, taller elements push the ones below), and scrolls vertically when
 * taller than the body area. Each tab keeps its own scroll position: switching
 * away and back, hiding tabs, and resizing leave it where the player left it. By
 * default that is view state, kept for the life of this element, like a panel's
 * own auto-scroll offset. {@link Builder#bodyScroll} hands it to the consumer
 * instead, in pixels per tab id, as {@link ScrollContainer}'s {@code state} does:
 * a consumer that rebuilds its screen (a new {@code Tabs}) keeps the body where
 * it was, on the first frame, with no slide from the top.
 *
 * <h3>Scroll stays put</h3>
 *
 * The offset is pixels from the top, so a body that changes height (a section
 * opening, a label rewrapping) keeps what is on screen where it is; it moves only
 * when it has to, when the end comes up past it. A body that stops scrolling
 * (everything fits) is at the top, and its offset says so, so it starts from the
 * top when it next scrolls rather than jumping back to an old place. Nothing in a
 * body scrolls it to bring a control into view, and a body never glides: only a
 * strip does.
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
    /** Space between a tab's icon ({@link TabSpec#icon}) and its label. */
    public static final int ICON_LABEL_GAP = 4;
    /** Narrowest a tab gets, whatever its label. */
    public static final int MIN_TAB_WIDTH = 24;
    /** Space between the strip and the body. */
    public static final int BODY_GAP = 4;
    /** Width reserved at each end of an overflowing side-scroll strip for its arrow. */
    public static final int ARROW_WIDTH = 25;
    /** Space between the sidebar header and the tabs under it. */
    public static final int SIDEBAR_HEADER_GAP = 4;
    /** Space between the full-width header and everything under it: the same gap as the sidebar header's. */
    public static final int HEADER_GAP = SIDEBAR_HEADER_GAP;
    /** Space between the sidebar's scrollbar and its tabs. */
    private static final int SIDEBAR_SCROLLBAR_GAP = 2;

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

    // The sidebar tab on a container screen, drawn rather than blitted. Vanilla's tab sprite is a
    // nine-slice that TILES its middle, so drawn thicker than its native 24 px (as
    // a sidebar tab is: the column's whole width) it repeats its outline lines
    // across the tab. These are the sprite's own colours and structure, turned on
    // its side with the middle stretched instead. The cost: a resource pack that
    // restyles vanilla's tabs restyles the top strip and not this sidebar. (On an
    // options screen the sidebar blits the sprite itself, turned: drawTurnedTab.)
    /** How much shorter an unselected tab is than the selected one, per the sprite. */
    private static final int SIDE_INSET = 4;
    private static final int TAB_EDGE = 0xBF000000;        // outer line
    private static final int TAB_LIGHT = 0x33FFFFFF;       // inner line, and the page edge's light line
    private static final int TAB_LIGHT_HOVER = 0xFFFFFFFF; // inner line under the mouse
    private static final int TAB_FILL = 0xDB000000;        // an unselected tab's face
    private static final int TAB_CLEAR = 0;                // the selected tab shows the page through it

    // Declared structure, fixed at construction.

    private final Mode mode;
    private final Align align;
    private final List<Tab> tabs;
    private final Supplier<@Nullable String> selected;
    private final Consumer<String> onSelect;
    private final int fixedWidth;   // -1 = take the panel's width
    private final int fixedHeight;  // -1 = take the panel's height (fillHeight)
    /** Elements above the sidebar column, positioned from its top-left. Empty outside SIDEBAR. */
    private final List<PanelElement> sidebarHeader;
    /** Whether {@link Mode#SIDEBAR} draws its tab column; false lays it out 0 wide, so the page takes its place. */
    private final boolean tabColumn;
    /** Elements across the whole element, above the strip or column and the body, positioned from its top-left. */
    private final List<PanelElement> header;
    /** While the sidebar's handle is held: how far below the handle's top it was grabbed. */
    private boolean draggingScroller = false;
    private double dragGrabOffset = 0;

    // Resolved geometry

    private int resolvedWidth = -1;   // from layoutWithin; -1 until the first pass
    private int filledHeight = -1;    // from fillHeight; -1 = no viewport

    // View state, not consumer state.

    /**
     * Each tab's body scroll in pixels from the top, by tab id: the consumer's
     * ({@link Builder#bodyScroll}), else this element's own map, kept for its
     * life. Pixels, not a fraction: a body that grows while shown (a section
     * opening) keeps what is on screen where it is.
     */
    private final Function<String, @Nullable Double> bodyScrollGet;
    private final BiConsumer<String, Double> bodyScrollSet;
    /**
     * The strip's offset in pixels along its axis, when it overflows: the one on
     * screen ({@code stripScroll}) and the one it is gliding to ({@code stripTarget}).
     * The wheel and the handle set both, so they move at once; an arrow or a newly
     * selected tab sets only the target, and each frame glides the shown offset
     * toward it ({@link Scroller#glide}). {@code glideNanos} is the last frame's
     * time, 0 before the first draw, which lands the glide at once so a menu
     * opens with its selected tab already in view rather than sliding to it.
     */
    private double stripScroll = 0;
    private double stripTarget = 0;
    private long glideNanos = 0;
    /** The tab shown last frame, to scroll a newly selected tab into view. */
    private @Nullable String lastShown = null;
    /**
     * The kind of screen it was last drawn on, for measuring its natural size only
     * (a measure gets no context; drawing and input use their context's kind). Its
     * host fixes the kind for life, so after the first frame this is exact.
     */
    private ScreenKind measuredKind = ScreenKind.CONTAINER;

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
        this.tabColumn = b.tabColumn == null || b.tabColumn;
        this.header = List.copyOf(b.header);
        if (b.bodyScrollGet != null) {
            this.bodyScrollGet = b.bodyScrollGet;
            this.bodyScrollSet = b.bodyScrollSet;
        } else {
            Map<String, Double> own = new HashMap<>();
            this.bodyScrollGet = own::get;
            this.bodyScrollSet = own::put;
        }
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
        com.trevlar.menukit.api.Declarations.requireOpen("Tabs.addTo(" + menu + ")");
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

    // Strip layout

    /** One tab placed on the strip, in strip-local coordinates (side-scroll: content space). */
    private record Placed(Tab tab, int x, int y, int w, String text, boolean truncated) {}

    /**
     * The strip for one width and one visible set. In {@link Mode#SIDEBAR}
     * {@code height} is the column's full length, {@code columnWidth} its width,
     * and whether it overflows depends on the element's height, so it is asked
     * of {@link #overflows}, never read from {@code overflow}. {@code top} is
     * where everything under the full-width header starts: its height and gap,
     * or 0; the strip's and column's own coordinates start there.
     */
    private record StripLayout(List<Placed> placed, int height, boolean overflow, int contentWidth,
                               int columnWidth, int headerHeight, int top) {}

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
        int headerSig = headersSignature();
        if (strip == null || w != stripWidthKey || !mask.equals(stripVisibleKey) || headerSig != stripHeaderKey) {
            strip = layoutStrip(w, mask);
            stripWidthKey = w;
            stripVisibleKey = mask;
            stripHeaderKey = headersSignature(); // after layout, so a settled header doesn't relay
        }
        return strip;
    }

    private int stripHeaderKey = 0;

    /** Both headers' visibility and live geometry: a change relays out the strip. */
    private int headersSignature() {
        return signature(header) * 31 + signature(sidebarHeader);
    }

    private StripLayout layoutStrip(int width, BitSet mask) {
        Font font = Minecraft.getInstance().font;
        List<Tab> visible = new ArrayList<>();
        for (int i = mask.nextSetBit(0); i >= 0; i = mask.nextSetBit(i + 1)) visible.add(tabs.get(i));
        // The full-width header first, to the element's whole width; everything else starts under it.
        int headerHeight = width > 0 ? layOutHeader(header, width) : 0;
        int top = headerHeight > 0 ? headerHeight + HEADER_GAP : 0;
        if (mode == Mode.SIDEBAR) return layoutSidebar(width, visible, top);
        if (visible.isEmpty() || width <= 0) return new StripLayout(List.of(), 0, false, 0, 0, 0, top);

        int n = visible.size();
        int[] natural = new int[n];
        for (int i = 0; i < n; i++) {
            int w = font.width(visible.get(i).label) + visible.get(i).iconRoom() + 2 * LABEL_PAD;
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
                int room = widths[i] - 2 * LABEL_PAD - t.iconRoom();
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
        return new StripLayout(List.copyOf(placed), y, overflow, contentWidth, 0, 0, top);
    }

    /**
     * One column, one tab per row, all as wide as the widest label or header
     * element, capped at a third of the element; the header laid out to it.
     * With no tab column ({@link Builder#tabColumn}) it is the empty column, 0
     * wide with no tabs, the same layout a sidebar with nothing in it gets: every
     * path that reads {@code columnWidth} (the body's left and width, the page's
     * left edge, the strip's hit test) then gives the page the whole width.
     */
    private StripLayout layoutSidebar(int width, List<Tab> visible, int top) {
        Font font = Minecraft.getInstance().font;
        if (width <= 0 || !tabColumn || (visible.isEmpty() && !anyVisible(sidebarHeader))) {
            return new StripLayout(List.of(), 0, false, 0, 0, 0, top);
        }
        int cap = Math.max(Math.min(MIN_TAB_WIDTH, width), width / 3);
        int column = Math.min(Math.max(sidebarNaturalWidth(visible), naturalWidthOf(sidebarHeader)), cap);
        int headerHeight = layOutHeader(sidebarHeader, column);
        List<Placed> placed = new ArrayList<>(visible.size());
        for (int i = 0; i < visible.size(); i++) {
            Tab t = visible.get(i);
            int room = column - 2 * LABEL_PAD - SIDE_INSET - t.iconRoom();
            String text = t.label.getString();
            boolean truncated = false;
            if (font.width(text) > room) {
                text = font.plainSubstrByWidth(text, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
                truncated = true;
            }
            placed.add(new Placed(t, 0, i * TAB_HEIGHT, column, text, truncated));
        }
        return new StripLayout(List.copyOf(placed), visible.size() * TAB_HEIGHT, false, column, column,
                headerHeight, top);
    }

    /** A header laid out to {@code width} the way a panel lays out its own elements; returns its height. */
    private static int layOutHeader(List<PanelElement> elements, int width) {
        Panel.layoutElementsWithin(elements, width);
        Panel.reflowForWrap(elements);
        return Panel.contentHeightOf(elements);
    }

    /** How wide a header's visible elements want to be, from its left edge. */
    private static int naturalWidthOf(List<PanelElement> elements) {
        int w = 0;
        for (PanelElement e : elements) {
            if (e.isVisible()) w = Math.max(w, e.getChildX() + e.naturalWidth());
        }
        return w;
    }

    private static boolean anyVisible(List<PanelElement> elements) {
        for (PanelElement e : elements) if (e.isVisible()) return true;
        return false;
    }

    /** The column width every label fits in whole: the widest label and its icon, its padding, and the unselected inset. */
    private static int sidebarNaturalWidth(List<Tab> visible) {
        Font font = Minecraft.getInstance().font;
        int w = MIN_TAB_WIDTH;
        for (Tab t : visible) w = Math.max(w, font.width(t.label) + t.iconRoom() + 2 * LABEL_PAD + SIDE_INSET);
        return w;
    }

    private static int sum(int[] a, int from, int to) {
        int s = 0;
        for (int i = from; i < to; i++) s += a[i];
        return s;
    }

    // Selection (lens, no write-back)

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

    // Scroll geometry: along the strip's own axis (x for side-scroll, y for the sidebar)

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
        if (sidebar()) return Math.max(0, getHeight() - s.top - stripViewportStart(s));
        return s.overflow ? Math.max(1, getWidth() - 2 * ARROW_WIDTH) : getWidth();
    }

    /**
     * The length the strip scrolls over: its content, or, while it fits, the
     * viewport's own length, so the one scroller's range is 0 and every offset
     * clamps to the start.
     */
    private int scrollLength(StripLayout s) {
        return overflows(s) ? contentLength(s) : stripViewportLength(s);
    }

    private int maxStripScroll(StripLayout s) {
        return Scroller.maxScroll(scrollLength(s), stripViewportLength(s));
    }

    private double clampStrip(StripLayout s, double offset) {
        return Scroller.clamp(offset, scrollLength(s), stripViewportLength(s));
    }

    /** Both offsets back inside the range: the layout or the element's size changed. */
    private void clampStripScroll(StripLayout s) {
        stripScroll = clampStrip(s, stripScroll);
        stripTarget = clampStrip(s, stripTarget);
    }

    /** The wheel or the handle: straight there, the glide dropped. */
    private void setStripScroll(StripLayout s, double offset) {
        stripScroll = stripTarget = clampStrip(s, offset);
    }

    /** Glides the least distance that brings {@code tab} fully into view. */
    private void scrollIntoView(StripLayout s, Tab tab) {
        if (!overflows(s)) return;
        for (Placed p : s.placed) {
            if (p.tab != tab) continue;
            stripTarget = Scroller.intoView(stripTarget, posOf(p), lengthOf(p), contentLength(s),
                    stripViewportLength(s));
        }
    }

    /**
     * An arrow: one tab back (-1) or forward (+1), to the next tab edge that way
     * from where the strip is gliding to, so presses in a row add up. Glides.
     */
    private void stepStrip(StripLayout s, int dir) {
        if (!overflows(s)) return;
        double from = stripTarget;
        double to = dir > 0 ? maxStripScroll(s) : 0;
        if (dir > 0) {
            for (Placed p : s.placed) {
                if (posOf(p) > from) { to = posOf(p); break; }
            }
        } else {
            for (Placed p : s.placed) {
                if (posOf(p) < from) to = posOf(p);
            }
        }
        stripTarget = clampStrip(s, to);
    }

    /** One frame of the glide toward the target, timed by the clock so it runs the same at any frame rate. */
    private void glideStrip() {
        long now = System.nanoTime();
        stripScroll = glideNanos == 0 ? stripTarget : Scroller.glide(stripScroll, stripTarget, (now - glideNanos) / 1e6);
        glideNanos = now;
    }

    /**
     * Whether a point is on the strip (the sidebar column, or the rows on top),
     * {@code ly} measured from under the header, as the strip's own coordinates are.
     */
    private boolean onStrip(StripLayout s, double lx, double ly) {
        if (sidebar()) return lx >= 0 && lx < s.columnWidth && ly >= 0 && ly < getHeight() - s.top;
        return ly >= 0 && ly < s.height;
    }

    // Bodies

    /**
     * Whether the body sits on an options screen's page (the list band), inset as
     * the band insets its content: below the strip, or right of the sidebar column.
     */
    private boolean paged(ScreenKind kind) {
        return kind == ScreenKind.OPTIONS;
    }

    /**
     * The body's top-left, in element-local coordinates: below the header and the
     * strip, or right of the sidebar. On a page the gap under the strip is the
     * band's top inset.
     */
    private int bodyTop(StripLayout s, ScreenKind kind) {
        if (sidebar()) return s.top + (paged(kind) ? ScrollContainer.OPTIONS_INSET_Y : 0);
        int gap = paged(kind) ? ScrollContainer.OPTIONS_INSET_Y : BODY_GAP;
        return s.top + s.height + (s.height > 0 ? gap : 0);
    }

    private int bodyLeft(StripLayout s, ScreenKind kind) {
        if (!sidebar()) return paged(kind) ? ScrollContainer.OPTIONS_INSET_X : 0;
        // On a page the column meets the page, and the page insets the body.
        if (paged(kind)) return s.columnWidth + ScrollContainer.OPTIONS_INSET_X;
        return s.columnWidth + (s.columnWidth > 0 ? BODY_GAP : 0);
    }

    /** From the body's left to the element's right edge: the page's right inset included. */
    private int bodyOuterWidth(StripLayout s, ScreenKind kind) {
        return Math.max(1, getWidth() - bodyLeft(s, kind));
    }

    /** The width the body is laid out to when it does not scroll: inside the page's right inset too. */
    private int bodyWidth(StripLayout s, ScreenKind kind) {
        int right = paged(kind) ? ScrollContainer.OPTIONS_INSET_X : 0;
        return Math.max(1, bodyOuterWidth(s, kind) - right);
    }

    /** The shown body, laid out for the current body size and kind of screen, scrolling when it overflows. */
    private record BodyView(Tab tab, ScreenKind kind, int width, int height, int layoutSig,
                            @Nullable ScrollContainer scroll) {}

    private @Nullable BodyView body;

    private @Nullable BodyView bodyView(StripLayout s, @Nullable Tab tab, ScreenKind kind) {
        if (tab == null) return null;
        int w = bodyWidth(s, kind);
        int h = Math.max(0, getHeight() - bodyTop(s, kind));
        int sig = signature(tab.body);
        if (body != null && body.tab == tab && body.kind == kind && body.width == w && body.height == h
                && body.layoutSig == sig) {
            return body;
        }
        // Lay the body out the way a panel lays out its own elements: width down,
        // reflow, measure; and only when it overflows, again with the scrollbar
        // lane reserved, hosted in a ScrollContainer whose offset is this tab's.
        // On a page the room under the last element (the band's bottom inset) is
        // part of the height, and scrolls with it, as in vanilla's lists.
        Panel.layoutElementsWithin(tab.body, w);
        Panel.reflowForWrap(tab.body);
        int contentH = Panel.contentHeightOf(tab.body);
        int below = paged(kind) ? ScrollContainer.OPTIONS_INSET_Y : 0;
        ScrollContainer scroll = null;
        int reserve = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
        // A scrolling body reaches the element's right edge: its lane is the page's right margin.
        int outer = bodyOuterWidth(s, kind);
        if (h > 0 && contentH + below > h && outer > reserve + 1) {
            Panel.layoutElementsWithin(tab.body, outer - reserve);
            Panel.reflowForWrap(tab.body);
            contentH = Panel.contentHeightOf(tab.body);
            String id = tab.id;
            // Bandless: on an options screen the Tabs draws the page band itself, behind
            // the whole body, whether or not it scrolls, and insets the body itself.
            scroll = ScrollContainer.builder()
                    .bandless()
                    .at(0, 0)
                    .size(outer, h)
                    .content(tab.body)
                    .contentHeight(contentH + below)
                    .state(() -> bodyScroll(id), v -> bodyScrollSet.accept(id, v))
                    .build();
        } else if (h > 0 && (fixedHeight >= 0 || filledHeight >= 0) && bodyScroll(tab.id) != 0) {
            // It fits, so it is at the top: say so, as a ScrollContainer writes back its
            // clamp. Kept, an old offset would come back the moment the body scrolled
            // again (a section closed so all fits, then reopened), a jump from the top
            // to wherever it once was. Only with a real height: a body measured at its
            // natural height (no viewport yet) fits by definition and proves nothing.
            bodyScrollSet.accept(tab.id, 0.0);
        }
        // Signature after layout, so a settled body doesn't lay itself out again every frame.
        body = new BodyView(tab, kind, w, h, signature(tab.body), scroll);
        return body;
    }

    /** A tab's body scroll, 0 when nothing is stored (a consumer's map that has no entry yet). */
    private double bodyScroll(String id) {
        Double v = bodyScrollGet.apply(id);
        return v == null ? 0.0 : v;
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

    // Size

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
            row += Math.max(font.width(t.label) + t.iconRoom() + 2 * LABEL_PAD, MIN_TAB_WIDTH);
            for (PanelElement e : t.body) {
                if (e.isVisible()) widestBody = Math.max(widestBody, e.getChildX() + e.naturalWidth());
            }
        }
        // A sidebar sits beside the body; a strip sits above it.
        // The header spans both, so it only ever widens the whole.
        boolean paged = paged(measuredKind);
        if (paged) widestBody += 2 * ScrollContainer.OPTIONS_INSET_X;
        if (sidebar()) {
            // No tab column: 0 wide and no gap, as bodyLeft lays it out.
            int column = tabColumn ? Math.max(sidebarNaturalWidth(visible), naturalWidthOf(sidebarHeader)) : 0;
            int gap = paged || column == 0 ? 0 : BODY_GAP;
            return Math.max(column + gap + widestBody, naturalWidthOf(header));
        }
        return Math.max(Math.max(row, widestBody), naturalWidthOf(header));
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
        ScreenKind kind = measuredKind;
        int w = bodyWidth(s, kind);
        int key = naturalKey(w);
        if (naturalHeightCache >= 0 && key == naturalHeightKey) return naturalHeightCache;
        int tallest = 0;
        for (Tab t : tabs) {
            if (!t.isVisible()) continue;
            Panel.layoutElementsWithin(t.body, w);
            Panel.reflowForWrap(t.body);
            tallest = Math.max(tallest, Panel.contentHeightOf(t.body));
        }
        // Under the header: the sidebar column (its own header, then every tab) beside the body, or the body.
        int column = s.top + stripViewportStart(s) + s.height;
        int below = paged(kind) ? ScrollContainer.OPTIONS_INSET_Y : 0;
        int bodyBottom = bodyTop(s, kind) + tallest + below;
        naturalHeightCache = sidebar() ? Math.max(column, bodyBottom) : bodyBottom;
        naturalHeightKey = naturalKey(w); // after layout, so a settled body doesn't re-measure
        return naturalHeightCache;
    }

    private int naturalKey(int width) {
        int key = ((width * 31 + visibleMask().hashCode()) * 31 + headersSignature()) * 31 + measuredKind.ordinal();
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

    // Rendering

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        measuredKind = ctx.kind();

        queueTooltip(ctx); // element-level tooltip first, so a tab's own wins

        StripLayout s = strip();
        Tab shown = shownTab();
        clampStripScroll(s);
        if (shown != null && !Objects.equals(shown.id, lastShown)) {
            scrollIntoView(s, shown);     // clicked, or selected from outside
        }
        lastShown = shown == null ? null : shown.id;
        glideStrip();                     // toward an arrow's or a selection's target

        // The header, then the strip under it. A disabled one draws as if nothing were hovered.
        RenderContext stripCtx = disabled
                ? new RenderContext(g, ctx.originX(), ctx.originY(), -1, -1, true, ctx.kind()) : ctx;
        ChildDispatch.render(header, childContext(stripCtx.at(sx, sy)));
        // An options screen's page: the list band from the strip's bottom down (or
        // from the sidebar column's right edge across), with the footer separator
        // under it across the whole element. The strip's or column's own line is
        // its edge toward the tabs.
        if (ctx.kind() == ScreenKind.OPTIONS) {
            int pageLeft = sx + (sidebar() ? s.columnWidth : 0);
            int pageTop = sy + s.top + (sidebar() ? 0 : s.height);
            int bottom = sy + getHeight();
            VanillaLook.listBackground(g, pageLeft, pageTop, sx + getWidth() - pageLeft, bottom - pageTop,
                    pageLeft, pageTop);
            VanillaLook.footerSeparator(g, sx, bottom, getWidth());
        }
        if (sidebar()) {
            renderSidebar(stripCtx, s, shown, sx, sy + s.top);
        } else {
            renderStrip(stripCtx, s, shown, sx, sy + s.top);
        }

        // The body, everything in it disabled with the Tabs.
        BodyView b = bodyView(s, shown, ctx.kind());
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
        return childContext(ctx.at(ctx.originX() + childX + bodyLeft(s, ctx.kind()),
                ctx.originY() + childY + bodyTop(s, ctx.kind())));
    }

    /** The body's input context, the same way. */
    private InputContext bodyInput(InputContext in, StripLayout s) {
        return in.at(in.originX() + childX + bodyLeft(s, in.kind()), in.originY() + childY + bodyTop(s, in.kind()))
                .disabledIf(ownDisabled());
    }

    /** The full-width header's input context: origin at the element's top-left. */
    private InputContext headerInput(InputContext in) {
        return in.at(in.originX() + childX, in.originY() + childY).disabledIf(ownDisabled());
    }

    /** The sidebar header's input context: origin at the column's top-left, under the full-width header. */
    private InputContext columnInput(InputContext in) {
        return in.at(in.originX() + childX, in.originY() + childY + strip().top).disabledIf(ownDisabled());
    }

    /** The strip across the top ({@link Mode#WRAP}, {@link Mode#SIDE_SCROLL}). */
    private void renderStrip(RenderContext ctx, StripLayout s, @Nullable Tab shown, int sx, int sy) {
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        int viewLeft = sx + stripViewportStart(s);
        int viewRight = viewLeft + stripViewportLength(s);
        int shift = s.overflow ? (int) stripScroll : 0;
        boolean vanilla = ctx.kind() == ScreenKind.OPTIONS;
        if (vanilla) stripSeparators(g, s, sx, sy, viewLeft, viewRight, shift);
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
            // Vanilla's selected tab opens into the page: the page's background inside
            // its frame (MenuTabButton.renderMenuBackground), here the list band, tiled
            // from screen coordinates as the band below is, so they meet with no seam.
            if (vanilla && isSelected) {
                VanillaLook.listBackground(g, tx + 2, ty + 2, p.w - 4, TAB_HEIGHT - 2, tx + 2, ty + 2);
            }
            // Vanilla's rule (MenuTabButton.renderLabel): an unselected tab's sprite
            // starts its visible body 3 px down, so its label centers in the box below
            // that inset, not over the full height. Same arithmetic as vanilla's
            // scrolling-string centering, so both states match vanilla to the pixel.
            int labelTop = ty + (isSelected ? 0 : UNSELECTED_LABEL_INSET);
            int textY = Text.centeredTextY(labelTop, ty + TAB_HEIGHT);
            // Vanilla's labels are white whether selected or not (MenuTabButton: active
            // white, inactive LIGHT_GRAY) and the selected one is underlined; MenuKit's
            // dims the unselected ones instead.
            int color = vanilla
                    ? (p.tab.standIn ? COLOR_UNSELECTED : COLOR_SELECTED)
                    : p.tab.standIn
                            ? (isSelected ? COLOR_STAND_IN_SELECTED : COLOR_STAND_IN_UNSELECTED)
                            : (isSelected ? COLOR_SELECTED : COLOR_UNSELECTED);
            // The icon (if any), a gap and the label, centred as one group the way
            // centeredText centres a label alone, which is what a tab with no icon is,
            // to the pixel. The icon centres in the same box as the label.
            int textW = font.width(p.text);
            int groupX = TabRows.centredStart(tx, p.w, p.tab.iconRoom() + textW);
            if (p.tab.icon != null) {
                p.tab.icon.draw(g, groupX, labelTop + (ty + TAB_HEIGHT - labelTop - p.tab.icon.size) / 2);
            }
            int textX = groupX + p.tab.iconRoom();
            g.text(font, p.text, textX, textY, color);
            if (vanilla && isSelected) {
                // MenuTabButton.renderFocusUnderline: 1 px, the label's colour, as wide
                // as the label (at most the tab less 4), 2 px above the tab's bottom.
                // Under the label only: beside an icon the label is off the tab's centre.
                int lineW = Math.min(textW, p.w - 4);
                int lineX = p.tab.icon == null ? tx + (p.w - lineW) / 2 : textX;
                int lineY = ty + TAB_HEIGHT - 2;
                g.fill(lineX, lineY, lineX + lineW, lineY + 1, color);
            }
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

    /**
     * An options screen's line along the strip's bottom, either side of the last
     * row's tabs (MenuTabBar draws {@code Screen.HEADER_SEPARATOR} left of the first
     * tab and right of the last): with the selected tab open above the page, the
     * line and the page read as one surface. Under an overflowing row's arrows only,
     * since the tabs fill the viewport there.
     */
    private void stripSeparators(net.minecraft.client.gui.GuiGraphicsExtractor g, StripLayout s, int sx, int sy,
                                 int viewLeft, int viewRight, int shift) {
        if (s.placed.isEmpty()) return;
        int lastRowY = s.placed.get(s.placed.size() - 1).y;
        int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
        for (Placed p : s.placed) {
            if (p.y != lastRowY) continue;
            first = Math.min(first, viewLeft + p.x - shift);
            last = Math.max(last, viewLeft + p.x - shift + p.w);
        }
        first = Math.max(first, viewLeft);
        last = Math.min(last, viewRight);
        int lineY = sy + s.height - VanillaLook.SEPARATOR_HEIGHT;
        if (first > sx) VanillaLook.headerSeparator(g, sx, lineY, first - sx);
        int right = sx + getWidth();
        if (last < right) VanillaLook.headerSeparator(g, last, lineY, right - last);
    }

    /** The column down the left ({@link Mode#SIDEBAR}): header, tabs, and a scrollbar when they overflow. */
    private void renderSidebar(RenderContext ctx, StripLayout s, @Nullable Tab shown, int sx, int sy) {
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        int column = s.columnWidth;
        if (column <= 0) return;

        // Header: ordinary elements, positioned from the column's top-left.
        ChildDispatch.render(sidebarHeader, childContext(ctx.at(sx, sy)));

        // The tabs, clipped to the column under the header. When they overflow,
        // the scrollbar takes a lane on the column's left (the side away from the
        // page) and the tabs narrow into the rest, so the body does not move.
        boolean overflow = overflows(s);
        int viewTop = sy + stripViewportStart(s);
        int viewLength = stripViewportLength(s);
        int viewBottom = viewTop + viewLength;
        boolean vanilla = ctx.kind() == ScreenKind.OPTIONS;
        if (overflow) {
            if (ctx.hasMouseInput()) followScrollerDrag(s, ctx.mouseY() - viewTop);
        } else {
            draggingScroller = false;
        }
        int shift = overflow ? (int) stripScroll : 0;
        int lane = scrollbarLane(s, ctx.kind());
        int tabX = sx + lane, tabW = column - lane;
        if (vanilla) columnSeparators(g, s, sx + column, sy, viewTop, viewBottom, shift);
        g.enableScissor(sx, viewTop, sx + column, viewBottom);
        Placed hoveredTruncated = null;
        for (Placed p : s.placed) {
            int ty = viewTop + p.y - shift;
            if (ty + TAB_HEIGHT <= viewTop || ty >= viewBottom) continue;
            boolean isSelected = p.tab == shown;
            boolean hovered = ctx.hasMouseInput() && !draggingScroller
                    && ctx.mouseX() >= tabX && ctx.mouseX() < tabX + tabW
                    && ctx.mouseY() >= Math.max(ty, viewTop) && ctx.mouseY() < Math.min(ty + TAB_HEIGHT, viewBottom);
            if (vanilla) {
                Identifier sprite = isSelected
                        ? (hovered ? SPRITE_SELECTED_HOVER : SPRITE_SELECTED)
                        : (hovered ? SPRITE_TAB_HOVER : SPRITE_TAB);
                drawTurnedTab(g, sprite, tabX, ty, tabW, TAB_HEIGHT);
                // Open into the page, as the top strip's selected tab is: the page's
                // background inside its frame, out to the column's edge where the page starts.
                if (isSelected) {
                    VanillaLook.listBackground(g, tabX + 2, ty + 2, tabW - 2, TAB_HEIGHT - 4, tabX + 2, ty + 2);
                }
            } else {
                drawSideTab(g, tabX, ty, tabW, TAB_HEIGHT, isSelected, hovered);
            }
            // Cut again for the narrower tab when the lane is taken; laid out without it.
            String text = p.text;
            boolean truncated = p.truncated;
            if (lane > 0) {
                int room = tabW - 2 * LABEL_PAD - SIDE_INSET - p.tab.iconRoom();
                String full = p.tab.label.getString();
                if (font.width(full) > room) {
                    text = font.plainSubstrByWidth(full, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
                    truncated = true;
                }
            }
            // Labels line up down the column whatever is selected, so it reads as a
            // list: the selected tab grows toward the left, its label does not move.
            // The icon (if any), a gap and the label are placed as one group; with no
            // icon the group is the label, where it always was.
            int textW = font.width(text);
            int faceLeft = tabX + SIDE_INSET;
            int side = switch (align) {
                case LEFT, FILL -> -1;
                case CENTER -> 0;
                case RIGHT -> 1;
            };
            int groupX = TabRows.sidebarStart(faceLeft, tabX + tabW, LABEL_PAD, p.tab.iconRoom() + textW, side);
            if (p.tab.icon != null) p.tab.icon.draw(g, groupX, ty + (TAB_HEIGHT - p.tab.icon.size) / 2);
            int textX = groupX + p.tab.iconRoom();
            int textY = Text.centeredTextY(ty, ty + TAB_HEIGHT);
            // On an options screen the labels are vanilla's, as on the top strip: white
            // (a stand-in's light grey), the selected one underlined.
            int color = vanilla
                    ? (p.tab.standIn ? COLOR_UNSELECTED : COLOR_SELECTED)
                    : p.tab.standIn
                            ? (isSelected ? COLOR_STAND_IN_SELECTED : COLOR_STAND_IN_UNSELECTED)
                            : (isSelected ? COLOR_SELECTED : COLOR_UNSELECTED);
            g.text(font, text, textX, textY, color);
            if (vanilla && isSelected) {
                // The top strip's underline, the label's colour and width, kept off the
                // frame's inner line along the tab's bottom (2 px up), so 3 px up here.
                g.fill(textX, ty + TAB_HEIGHT - 3, textX + textW, ty + TAB_HEIGHT - 2, color);
            }
            if (hovered && truncated) hoveredTruncated = p;
        }
        g.disableScissor();
        if (overflow) {
            // A ScrollContainer's scrollbar, from the one scroller: the track the
            // column's length under the header, the same fixed-size handle as a body's
            // (not one sized to the share in view, which on a column a tab or two too
            // tall was nearly the track's length), in the kind of screen's look.
            Scroller.draw(g, ctx.kind(), sx, viewTop, viewLength, scrollerTop(s), true, false);
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

    // The sidebar on an options screen: vanilla's sprites, turned.

    // Vanilla's tab sprite (widget/tab*.png.mcmeta): 130 x 24, nine-sliced with a
    // 2 px border left, top and right and none at the bottom, which is the edge
    // that opens into the page. Its rows, top to bottom: 4 clear rows on an
    // unselected tab (the inset), the frame's edge and inner lines, the face, and
    // at the page the hover line (rows 21), then the page edge's light and dark
    // lines (22, 23), which the selected tab leaves open.
    private static final int TAB_SPRITE_W = 130;
    private static final int TAB_SPRITE_H = 24;
    private static final int TAB_SPRITE_BORDER = 2;
    /** The rows before the face (inset, edge, inner line) and from the hover line on, drawn 1:1. */
    private static final int TAB_SPRITE_HEAD = 6;
    private static final int TAB_SPRITE_TAIL = 3;

    /**
     * A sidebar tab on an options screen: vanilla's own tab sprite drawn under a
     * pose turned a quarter, its open edge (the sprite's bottom) facing the page
     * on the right. {@code w} is the tab's thickness across the column, {@code h}
     * its length down it.
     *
     * <p>In the turned frame the sprite is drawn {@code h} long and {@code w}
     * thick. Vanilla's nine-slice cannot draw it that way itself: it tiles the
     * sprite's middle rows, so a tab thicker than 24 px would repeat the inset and
     * the page-edge lines across its face. So the sprite is sliced here, from its
     * own texels: along its length exactly as vanilla's nine-slice does (both end
     * borders 1:1, the first {@code h - 4} middle columns), and across its
     * thickness the head and tail rows 1:1 with the face rows stretched between
     * them (they are one colour, so stretched reads the same as tiled). A resource
     * pack that restyles vanilla's tabs restyles these too.
     */
    private static void drawTurnedTab(net.minecraft.client.gui.GuiGraphicsExtractor g, Identifier id,
                                      int x, int y, int w, int h) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.GUI).getSprite(id);
        int face = Math.max(0, w - TAB_SPRITE_HEAD - TAB_SPRITE_TAIL);
        int head = Math.min(TAB_SPRITE_HEAD, w);
        turned(g, x, y, h, () -> {
            // Each band across the thickness: sprite rows [v0, v1) drawn from local y0 to y1.
            spriteBand(g, sprite, 0, head, 0, head, h);
            spriteBand(g, sprite, TAB_SPRITE_HEAD, TAB_SPRITE_H - TAB_SPRITE_TAIL, head, head + face, h);
            spriteBand(g, sprite, TAB_SPRITE_H - TAB_SPRITE_TAIL, TAB_SPRITE_H, head + face,
                    Math.min(w, head + face + TAB_SPRITE_TAIL), h);
        });
    }

    /**
     * Sprite rows {@code [v0, v1)} drawn between local {@code y0} and {@code y1},
     * {@code length} long: the sprite's end columns 1:1 at each end and its middle
     * columns from the left between them, as vanilla's nine-slice draws a tab.
     */
    private static void spriteBand(net.minecraft.client.gui.GuiGraphicsExtractor g, TextureAtlasSprite sprite,
                                   int v0, int v1, int y0, int y1, int length) {
        if (y1 <= y0) return;
        int b = Math.min(TAB_SPRITE_BORDER, length / 2);
        int mid = Math.min(length - 2 * b, TAB_SPRITE_W - 2 * TAB_SPRITE_BORDER);
        spritePart(g, sprite, 0, b, v0, v1, 0, b, y0, y1);
        spritePart(g, sprite, TAB_SPRITE_BORDER, TAB_SPRITE_BORDER + mid, v0, v1, b, length - b, y0, y1);
        spritePart(g, sprite, TAB_SPRITE_W - b, TAB_SPRITE_W, v0, v1, length - b, length, y0, y1);
    }

    /** The sprite's texels {@code [u0, u1) x [v0, v1)} (in its 130 x 24 units) drawn to {@code [x0, x1) x [y0, y1)}. */
    private static void spritePart(net.minecraft.client.gui.GuiGraphicsExtractor g, TextureAtlasSprite sprite,
                                   int u0, int u1, int v0, int v1, int x0, int x1, int y0, int y1) {
        if (x1 <= x0 || y1 <= y0) return;
        g.blit(sprite.atlasLocation(), x0, y0, x1, y1,
                sprite.getU((float) u0 / TAB_SPRITE_W), sprite.getU((float) u1 / TAB_SPRITE_W),
                sprite.getV((float) v0 / TAB_SPRITE_H), sprite.getV((float) v1 / TAB_SPRITE_H));
    }

    /**
     * Runs {@code draw} in a frame turned a quarter: local x runs up the screen
     * from {@code (x, y + length)}, local y to the right from {@code x}. So a
     * sprite's top edge lands on the left and its bottom edge on the right. An
     * exact matrix, not a rotation by an angle, so every pixel stays on the grid.
     * Only blits that take no scissor draw right in it: the scissor stack maps
     * a rectangle by two corners, which a turned pose flips, so the clip is set
     * on screen before turning, as the column's is.
     */
    private static void turned(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, int length,
                               Runnable draw) {
        g.pose().pushMatrix();
        g.pose().mul(new Matrix3x2f(0, -1, 1, 0, x, y + length));
        draw.run();
        g.pose().popMatrix();
    }

    /**
     * The options screen's line along the column's edge toward the page, above
     * and below the tabs in view: the top strip's line either side of its tabs
     * ({@link #stripSeparators}), turned, so the header separator's light line
     * faces the column and its dark line the page, as the tabs' own edge lines do.
     */
    private void columnSeparators(net.minecraft.client.gui.GuiGraphicsExtractor g, StripLayout s, int right,
                                  int columnTop, int viewTop, int viewBottom, int shift) {
        int bottom = columnTop + getHeight() - s.top;
        int first = viewBottom, last = viewTop;
        if (!s.placed.isEmpty()) {
            first = Math.max(viewTop, viewTop + s.placed.get(0).y - shift);
            last = Math.min(viewBottom, viewTop + s.placed.get(s.placed.size() - 1).y + TAB_HEIGHT - shift);
        }
        int lineX = right - VanillaLook.SEPARATOR_HEIGHT;
        if (s.placed.isEmpty() || first >= last) {
            verticalSeparator(g, lineX, columnTop, bottom - columnTop);
            return;
        }
        verticalSeparator(g, lineX, columnTop, first - columnTop);
        verticalSeparator(g, lineX, last, bottom - last);
    }

    private static void verticalSeparator(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, int length) {
        if (length > 0) turned(g, x, y, length, () -> VanillaLook.headerSeparator(g, 0, 0, length));
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

    // Sidebar scrollbar

    /**
     * The lane the scrollbar takes on the column's left while the tabs overflow,
     * else 0: the track as wide as the kind of screen draws it, and a gap.
     */
    private int scrollbarLane(StripLayout s, ScreenKind kind) {
        return sidebar() && overflows(s) ? Scroller.trackWidth(kind) + SIDEBAR_SCROLLBAR_GAP : 0;
    }

    /** The handle's top from the column's view top: the track is the view's length. */
    private double scrollerTop(StripLayout s) {
        int view = stripViewportLength(s);
        return Scroller.thumbTop(stripScroll, scrollLength(s), view, view);
    }

    /** While the handle is held, scroll so it stays under the mouse where it was grabbed. */
    private void followScrollerDrag(StripLayout s, double mouseFromViewTop) {
        if (!draggingScroller) return;
        int view = stripViewportLength(s);
        setStripScroll(s, Scroller.dragTo(mouseFromViewTop, dragGrabOffset, scrollLength(s), view, view));
    }

    private void renderArrows(RenderContext ctx, StripLayout s, int sx, int sy) {
        var g = ctx.graphics();
        int ay = sy + (TAB_HEIGHT - ARROW_SPRITE_H) / 2;
        int backX = sx + (ARROW_WIDTH - ARROW_SPRITE_W) / 2;
        int fwdX = sx + getWidth() - ARROW_WIDTH + (ARROW_WIDTH - ARROW_SPRITE_W) / 2;
        // An arrow is hidden once the strip is at (or gliding to) its end; its space
        // stays, so tabs don't shift.
        if (stripTarget > 0) {
            boolean hover = ctx.isHovered(childX, childY + s.top, ARROW_WIDTH, TAB_HEIGHT);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? SPRITE_BACK_HOVER : SPRITE_BACK,
                    backX, ay, ARROW_SPRITE_W, ARROW_SPRITE_H);
        }
        if (stripTarget < maxStripScroll(s)) {
            boolean hover = ctx.isHovered(childX + getWidth() - ARROW_WIDTH, childY + s.top, ARROW_WIDTH, TAB_HEIGHT);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? SPRITE_FORWARD_HOVER : SPRITE_FORWARD,
                    fwdX, ay, ARROW_SPRITE_W, ARROW_SPRITE_H);
        }
    }

    /**
     * An outside click reaches the headers and the shown body, so an open popover
     * there (a Dropdown) closes like one anywhere else.
     */
    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(header, headerInput(in));
        ChildDispatch.notifyClickOutside(sidebarHeader, columnInput(in));
        BodyView b = liveBody(in.kind());
        if (b == null) return;
        InputContext inner = bodyInput(in, strip());
        if (b.scroll != null) b.scroll.notifyClickOutsideOverlay(inner);
        else ChildDispatch.notifyClickOutside(b.tab.body, inner);
    }

    /** The headers' and the shown body's overlays (an open Dropdown popover), unclipped, after all base renders. */
    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(header, childContext(ctx.at(ctx.originX() + childX, ctx.originY() + childY)));
        ChildDispatch.renderOverlay(sidebarHeader,
                childContext(ctx.at(ctx.originX() + childX, ctx.originY() + childY + strip().top)));
        BodyView b = liveBody(ctx.kind());
        if (b == null) return;
        RenderContext bodyCtx = bodyRender(ctx, strip());
        if (b.scroll != null) b.scroll.renderOverlay(bodyCtx);
        else ChildDispatch.renderOverlay(b.tab.body, bodyCtx);
    }

    // Input

    /**
     * The shown body, laid out for the current size as drawing lays it out, or
     * null. Input goes only to the shown tab. Laid out here too, not only when
     * drawn, so input before a first draw (a screen just rebuilt) and input right
     * after a change (a section a click just opened) meet the body as it now is.
     */
    private @Nullable BodyView liveBody(ScreenKind kind) {
        return bodyView(strip(), shownTab(), kind);
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        StripLayout s = strip();
        BodyView b = liveBody(in.kind());
        InputContext inner = b == null ? null : bodyInput(in, s);

        // An open popover in a header claims the click anywhere in its bounds, over
        // the strip and the body too; so does one in the body.
        if (claimedByOverlay(header, headerInput(in))) return ChildDispatch.mouseClicked(header, headerInput(in), button);
        if (claimedByOverlay(sidebarHeader, columnInput(in))) {
            return ChildDispatch.mouseClicked(sidebarHeader, columnInput(in), button);
        }
        if (b != null) {
            int[] ov = b.scroll != null ? b.scroll.getActiveOverlayBounds(inner)
                    : ChildDispatch.activeOverlay(b.tab.body, inner);
            if (ov != null && in.isInside(ov)) {
                return b.scroll != null ? b.scroll.mouseClicked(inner, button)
                        : ChildDispatch.mouseClicked(b.tab.body, inner, button);
            }
        }

        double lx = in.mouseX() - (in.originX() + childX), ly = in.mouseY() - (in.originY() + childY);
        // The header band, its gap included, eats its own clicks as the strip does,
        // so a click on its empty space never reaches what lies under it (a slot,
        // a vanilla button). The one exception is a click on a header element that
        // hit-tests but doesn't claim it: a widget whose click vanilla's own widget
        // routing delivers, so it must fall through. TextField and Slider were such
        // widgets until 7.2.0; on WidgetHost they claim their own clicks now, but a
        // consumer's element may still rely on vanilla's routing.
        if (ly >= 0 && ly < s.top) {
            if (disabled(in)) return true;
            return claimOrEat(header, headerInput(in), button);
        }
        ly -= s.top;                                      // from here on, from under the header
        if (onStrip(s, lx, ly)) {
            if (disabled(in)) return true;               // a disabled strip eats its clicks, picks nothing
            if (sidebar()) return clickSidebar(s, in, lx, ly, button);
            if (button != Click.LEFT) return true;        // the strip eats its own clicks
            if (s.overflow && lx < ARROW_WIDTH) { stepStrip(s, -1); return true; }
            if (s.overflow && lx >= getWidth() - ARROW_WIDTH) { stepStrip(s, +1); return true; }
            double contentX = lx - stripViewportStart(s) + (s.overflow ? (int) stripScroll : 0);  // where it is drawn
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

    /** Whether an open overlay among {@code elements} covers the click. */
    private static boolean claimedByOverlay(List<PanelElement> elements, InputContext in) {
        int[] ov = ChildDispatch.activeOverlay(elements, in);
        return ov != null && in.isInside(ov);
    }

    /**
     * A click in a header band: true when a header element claims it, false when
     * it lands on a header element that doesn't (a widget reached by vanilla's
     * widget routing once this returns false), and true again on empty band, which
     * is eaten so nothing under the Tabs sees it.
     */
    private static boolean claimOrEat(List<PanelElement> band, InputContext in, int button) {
        if (ChildDispatch.mouseClicked(band, in, button)) return true;
        return !ChildDispatch.hitTest(band, in);
    }

    /** A click in the sidebar column ({@code ly} from under the full-width header): its header, the scrollbar lane, a tab. */
    private boolean clickSidebar(StripLayout s, InputContext in, double lx, double ly, int button) {
        if (ChildDispatch.mouseClicked(sidebarHeader, columnInput(in), button)) return true;
        int viewStart = stripViewportStart(s);
        // The band above the tabs is sidebarHeader's own: eaten like the column,
        // except a click on a vanilla-backed header widget, which falls through
        // to vanilla's routing (see claimOrEat).
        if (ly < viewStart) return !ChildDispatch.hitTest(sidebarHeader, columnInput(in));
        if (button != Click.LEFT) return true;            // the column eats its own clicks
        if (ly >= viewStart + stripViewportLength(s)) return true;
        if (lx < scrollbarLane(s, in.kind())) {
            // On the handle: grab it where it was pressed. On the track: jump so the
            // handle centres on the click, and keep holding it, as a body's does.
            double fromTop = ly - viewStart;
            dragGrabOffset = Scroller.grab(fromTop, scrollerTop(s));
            draggingScroller = true;
            followScrollerDrag(s, fromTop);
            return true;
        }
        double contentY = ly - viewStart + (overflows(s) ? (int) stripScroll : 0);
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
        if (ly >= 0 && ly < s.top) return ChildDispatch.mouseScrolled(header, headerInput(in), scrollX, scrollY);
        ly -= s.top;
        if (onStrip(s, lx, ly)) {
            if (!overflows(s) || disabled(in)) return false;
            // By pixels, as a body scrolls: wheel up goes back, down goes forward. A
            // sideways wheel scrolls the row too.
            double delta = scrollY != 0 ? scrollY : scrollX;
            if (delta == 0) return false;
            setStripScroll(s, Scroller.wheel(stripScroll, delta, scrollLength(s), stripViewportLength(s)));
            return true;
        }
        BodyView b = liveBody(in.kind());
        if (b == null) return false;
        InputContext inner = bodyInput(in, s);
        if (b.scroll != null) return b.scroll.mouseScrolled(inner, scrollX, scrollY);
        return ChildDispatch.mouseScrolled(b.tab.body, inner, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        // Releases go everywhere un-hit-tested, so a pressed button always lets go.
        ChildDispatch.mouseReleased(header, headerInput(in), button);
        ChildDispatch.mouseReleased(sidebarHeader, columnInput(in), button);
        if (button == Click.LEFT) draggingScroller = false;
        BodyView b = liveBody(in.kind());
        if (b == null) return false;
        InputContext inner = bodyInput(in, strip());
        if (b.scroll != null) return b.scroll.mouseReleased(inner, button);
        ChildDispatch.mouseReleased(b.tab.body, inner, button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        // No shortcuts of its own: keys go to the headers, then the shown body.
        if (ChildDispatch.keyPressed(header, headerInput(in), keyCode, scanCode, modifiers)) return true;
        if (ChildDispatch.keyPressed(sidebarHeader, columnInput(in), keyCode, scanCode, modifiers)) return true;
        BodyView b = liveBody(in.kind());
        if (b == null) return false;
        InputContext inner = bodyInput(in, strip());
        if (b.scroll != null) return b.scroll.keyPressed(inner, keyCode, scanCode, modifiers);
        return ChildDispatch.keyPressed(b.tab.body, inner, keyCode, scanCode, modifiers);
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        int[] top = ChildDispatch.activeOverlay(header, headerInput(in));
        if (top != null) return top;
        int[] column = ChildDispatch.activeOverlay(sidebarHeader, columnInput(in));
        if (column != null) return column;
        BodyView b = liveBody(in.kind());
        if (b == null) return null;
        InputContext inner = bodyInput(in, strip());
        return b.scroll != null ? b.scroll.getActiveOverlayBounds(inner) : ChildDispatch.activeOverlay(b.tab.body, inner);
    }

    /** Every tab's body attaches, shown or not, as a hidden panel's elements do. */
    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        for (Tab t : tabs) ChildDispatch.attach(t.body, screen);
        ChildDispatch.attach(sidebarHeader, screen);
        ChildDispatch.attach(header, screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        for (Tab t : tabs) ChildDispatch.detach(t.body, screen);
        ChildDispatch.detach(sidebarHeader, screen);
        ChildDispatch.detach(header, screen);
    }

    // Tabs and builders

    /** One tab: an id, a label, a body, and whether it shows. Built with {@link Tabs#tab(String)}. */
    public static final class Tab {
        final String id;
        final Component label;
        final @Nullable BooleanSupplier visibleWhen;
        final List<PanelElement> body;
        final boolean standIn;
        final @Nullable TabIcon icon;

        private Tab(String id, Component label, @Nullable BooleanSupplier visibleWhen, List<PanelElement> body,
                    boolean standIn, @Nullable TabIcon icon) {
            this.id = id;
            this.label = label;
            this.visibleWhen = visibleWhen;
            this.body = body;
            this.standIn = standIn;
            this.icon = icon;
        }

        public String id() { return id; }
        public Component label() { return label; }

        boolean isVisible() {
            return visibleWhen == null || visibleWhen.getAsBoolean();
        }

        /** The width the icon takes before the label, its gap included; 0 with no icon. */
        int iconRoom() {
            return icon == null ? 0 : icon.size + ICON_LABEL_GAP;
        }
    }

    /** A tab's icon: a sprite, or a texture when {@code textureSize} is positive, drawn {@code size} square. */
    private record TabIcon(Supplier<@Nullable Identifier> source, int textureSize, int size) {
        void draw(GuiGraphicsExtractor g, int x, int y) {
            Icon.draw(g, source.get(), textureSize, x, y, size, size, 1.0f);
        }
    }

    /** A tab under construction. */
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
        private @Nullable TabIcon icon;

        private TabSpec(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public String id() { return id; }

        public TabSpec label(Component label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /**
         * An icon {@code drawSize} px square left of the label, {@link #ICON_LABEL_GAP}
         * from it: a standalone square texture {@code textureSize} texels on a side
         * (a mod's icon read from its jar into a {@code DynamicTexture}, say), drawn
         * whole and scaled, as {@link Icon.Builder#texture(Supplier, int)} draws it.
         * Read every frame; null draws nothing and keeps the room, so the tab does not
         * change width while its texture loads. The tab widens by the icon and gap,
         * and in every mode the icon and label are placed as one group. Default: none.
         */
        public TabSpec icon(Supplier<@Nullable Identifier> texture, int textureSize, int drawSize) {
            Objects.requireNonNull(texture, "texture");
            if (textureSize <= 0) throw new IllegalArgumentException("Tabs: tab '" + id + "' icon textureSize must be positive");
            return icon(new TabIcon(texture, textureSize, drawSize));
        }

        /** {@link #icon(Supplier, int, int)} with a GUI-atlas sprite ({@code textures/gui/sprites}) for the picture. */
        public TabSpec icon(Identifier sprite, int drawSize) {
            Objects.requireNonNull(sprite, "sprite");
            return icon(new TabIcon(() -> sprite, 0, drawSize));
        }

        private TabSpec icon(TabIcon icon) {
            if (icon.size <= 0) throw new IllegalArgumentException("Tabs: tab '" + id + "' icon drawSize must be positive");
            this.icon = icon;
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
            return new Tab(id, label, visibleWhen, built, standIn, icon);
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
        private final List<PanelElement> header = new ArrayList<>();
        private @Nullable Boolean tabColumn;   // null: not asked, which is drawn
        private @Nullable Function<String, @Nullable Double> bodyScrollGet;
        private @Nullable BiConsumer<String, Double> bodyScrollSet;

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

        /**
         * The consumer owns each tab's body scroll, in pixels from the top, keyed by
         * tab id, as {@link ScrollContainer.Builder#state} owns a container's:
         * {@code get} is read every frame (null, or a missing entry, is the top) and
         * {@code set} receives each change. A screen rebuilt with the same lens opens
         * each body where it was, on the first frame, without sliding from the top;
         * a value past the body's end is clamped and the clamp written back. Without
         * it the scroll is this element's own view state and a rebuild starts at the
         * top. The selected tab is already the consumer's ({@link #state}).
         */
        public Builder bodyScroll(Function<String, @Nullable Double> get, BiConsumer<String, Double> set) {
            this.bodyScrollGet = Objects.requireNonNull(get, "get");
            this.bodyScrollSet = Objects.requireNonNull(set, "set");
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
         * cap; the body beside it still starts at the top (under {@link #header},
         * when there is one). {@link Mode#SIDEBAR} only.
         */
        public Builder sidebarHeader(PanelElement... elements) {
            for (PanelElement e : elements) sidebarHeader.add(Objects.requireNonNull(e, "element"));
            return this;
        }

        /**
         * Whether the sidebar draws its tab column. Default true. False draws no
         * column: it is laid out 0 wide, so the body takes the element's whole width
         * on the very page a column would sit beside, with the same band, insets,
         * separators and scrollbar on an options screen, and MenuKit's own body
         * from the left edge on a container screen. For a menu with one page that
         * should look like its tabbed siblings. The {@link #header} still spans the
         * top; clicks and the wheel over where the column would be reach the body.
         * The selected tab ({@link #state}) is the one shown, so with several tabs
         * the lens still chooses which. {@link Mode#SIDEBAR} only, and not with
         * {@link #sidebarHeader}, which lives in the column.
         */
        public Builder tabColumn(boolean shown) {
            this.tabColumn = shown;
            return this;
        }

        /**
         * Elements in a row across the whole element, above the strip (or the
         * sidebar column) and the body, in every mode: a menu's title bar.
         * Positioned from the element's top-left and laid out to its full width
         * the way a panel lays out its own elements, so a {@link Flow} with a
         * spacer at each end centres a label across it. The strip, the column and
         * the body start under it and {@link #HEADER_GAP}; the body is that much
         * shorter. Hidden elements keep no room. A screen whose header names it
         * can {@code hideTitle()} so the name shows once.
         */
        public Builder header(PanelElement... elements) {
            for (PanelElement e : elements) header.add(Objects.requireNonNull(e, "element"));
            return this;
        }

        public Tabs build() {
            if (selected == null) throw new IllegalStateException("Tabs: state(get, set) is required");
            if (!sidebarHeader.isEmpty() && mode != Mode.SIDEBAR) {
                throw new IllegalStateException("Tabs: sidebarHeader(...) needs mode(Mode.SIDEBAR); there is no "
                        + "column above which to put it");
            }
            if (tabColumn != null && mode != Mode.SIDEBAR) {
                throw new IllegalStateException("Tabs: tabColumn(...) needs mode(Mode.SIDEBAR); only the sidebar "
                        + "has a tab column to show or leave out");
            }
            if (Boolean.FALSE.equals(tabColumn) && !sidebarHeader.isEmpty()) {
                throw new IllegalStateException("Tabs: sidebarHeader(...) needs the tab column, and tabColumn(false) "
                        + "leaves it out; put the elements in header(...) instead");
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
