package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.Transition;
import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.menukit.inject.TabRegistry.Group;
import com.trevlar.menukit.inject.TabRegistry.Tab;
import com.trevlar.menukit.inject.TabRowLayout.Fit;
import com.trevlar.menukit.inject.TabRowLayout.Line;
import com.trevlar.menukit.inject.TabRowLayout.Placed;
import com.trevlar.menukit.inject.TabRowLayout.Shown;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The container screen's tab rows this frame (7.4.0; the public face is {@code api.panel.ContainerTabs}):
 * on each {@link Edge}, the declared tabs ({@link TabRegistry}) that show there now, each row's
 * page, and each group's slide. {@link ContainerRegions} asks each frame which edges have a row,
 * so the layout makes room for it ({@link RegionLayout.Shape}); how a row lays out is
 * {@link TabRowLayout}'s, how it draws {@link TabStrip}'s, and what a tab does the mod's, through
 * its callbacks, each read once a frame ({@link #choose}), a throw counting as no.
 *
 * <p><b>Two ways to stand</b> (Fluorite). While the region is up, a row stands on its panels'
 * edges, drawn in the background layer over the region's panels. On a container screen the
 * region isn't up on (the player's inventory, a shulker box, a chest with no section showing),
 * the outer two rows stand on the screen's own frame instead ({@link #bare}): the top one on
 * its top edge, the bottom one on its bottom edge, drawn just after the screen's image so a
 * selected tab opens into it. The frame and those rows are centered as one block, by the rule
 * the region's block is ({@link #centre}); the two middle edges have no gap to stand in there
 * and show nothing. So screens of one size that open in place of each other, with the same
 * rows, show one row in one place.
 *
 * <p><b>Paging carries across screens.</b> A screen opened in place of another keeps each row's
 * page; it starts over only once the player is back in the world with no screen open.
 *
 * <p>Render thread only.
 */
@ApiStatus.Internal
public final class ContainerTabRows {

    private ContainerTabRows() {}

    private static final int EDGES = Edge.values().length;

    // A screen's own frame, where the region isn't up

    /** This frame's screen frame the outer rows stand on, or {@code null} while the region is up or no screen is. */
    private static @Nullable Reference bare;

    /** Whether rows may stand on {@code screen}: every container screen but the creative inventory, which has tabs of its own. */
    static boolean carries(AbstractContainerScreen<?> screen) {
        return !(screen instanceof CreativeModeInventoryScreen);
    }

    /** Whether the outer rows stand on a screen's own frame this frame. */
    static boolean onBareFrame() {
        return bare != null;
    }

    /**
     * Hooks every container screen's frame, after the region's own frame hook, and clears the
     * rows' paging once the player is back in the world. Called once from client init.
     */
    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            // The first frame is the first one drawn: the layouts at init read a mod's tabs
            // before the packets that came with the screen (its selected tab) are in.
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> settle = false);
            if (!carries(acs)) return;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> onFrame(acs, false));
            ScreenEvents.remove(screen).register(s -> bare = null);
            onFrame(acs, true);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.gui.screen() == null) resetPaging();
        });
    }

    /**
     * One frame of {@code screen} with the region down: the tabs that show there on the outer
     * two edges, standing on the screen's own frame. With the region up, it chose the rows. Rows
     * other than the ones the frame was centered with ({@link #centre}) lay the screen out
     * again, as the region does on a layout change, so it centers anew and every mod's widget
     * follows: not at init, which has just centered it.
     */
    private static void onFrame(AbstractContainerScreen<?> screen, boolean atInit) {
        if (ContainerRegions.state.geometry != null && ContainerRegions.state.screen.get() == screen) {
            bare = null;
            return;
        }
        // The frame as it draws: moving, a moment, when the screen opened in place of another.
        bare = ScreenSwap.frame(screen);
        choose(screen);
        for (Edge edge : Edge.values()) {
            if (lineNow(edge) == null) rows[edge.ordinal()] = drawn[edge.ordinal()] = new Shown[0];
        }
        afterLayout();
        int[] c = CENTRED.get(screen);
        if (!atInit && c != null && (c[1] != height(Edge.CONTAINERS_TOP) || c[2] != height(Edge.INVENTORY_BOTTOM))) {
            ContainerRegions.relayout(screen);
        }
    }

    // A screen's own frame centered with its rows

    /** Each container screen's centering at its last init: {dy, above, below}, the frame's move down from vanilla's place and the rows it counted. */
    private static final Map<Screen, int[]> CENTRED = new WeakHashMap<>();

    /**
     * At the head of each of {@code screen}'s inits ({@code MKFrameCentringMixin}), before
     * vanilla centers it: how far its frame goes down from vanilla's place so that the frame and
     * the rows standing on it now, the top one above it and the bottom one below, are centered
     * as one block ({@link RegionLayout#frameTop}, the region's own rule). Vanilla's container
     * screen code then centers everything from a window that much taller, so the frame, its
     * image, its slots and labels, the recipe book and its button all move together, and so
     * does every widget placed from {@code topPos}. No row, or the region up on it: vanilla's
     * place. The rows are measured ({@link #settledHeights}), not chosen: nothing slides for it.
     */
    public static void centre(AbstractContainerScreen<?> screen) {
        int above = 0, below = 0;
        boolean regionUp = ContainerRegions.state.geometry != null && ContainerRegions.state.screen.get() == screen;
        if (carries(screen) && !regionUp) {
            int[] h = settledHeights(screen);
            above = h[Edge.CONTAINERS_TOP.ordinal()];
            below = h[Edge.INVENTORY_BOTTOM.ordinal()];
        }
        int imageHeight = ((AbstractContainerScreenAccessor) screen).mk$getImageHeight();
        int top = (screen.height - imageHeight) / 2;
        int dy = RegionLayout.frameTop(new Reference(0, top, 0, imageHeight), screen.height, above, below) - top;
        CENTRED.put(screen, new int[]{dy, above, below});
    }

    /** How far {@code screen}'s frame is moved down from vanilla's place for its tab rows ({@link #centre}); 0 for none. */
    public static int centring(Screen screen) {
        int[] c = CENTRED.get(screen);
        return c == null ? 0 : c[0];
    }

    // This frame's rows

    /**
     * Each edge's row this frame, in order, by {@link Edge#ordinal}: empty where none shows. A
     * closed group's sub-tabs aren't in it; the row is what pages, takes clicks and keeps a
     * selected tab in view.
     */
    private static Shown[][] rows = empty();
    /** Each edge's tabs drawn this frame: its row, and a closed group's sub-tabs too, for as long as they slide back. */
    private static Shown[][] drawn = empty();
    /**
     * Each row's first tab in view when they don't all fit, the selected tab last brought into
     * view, and whether the row keeps that tab in view: from when it starts drawing selected
     * until the arrows page it out.
     */
    private static final int[] FIRST = new int[EDGES];
    /** Each row's first tab in view, by its {@link Tab#ref}, so the page stays on it as tabs before it come and go. */
    private static final @Nullable Object[] FIRST_REF = new Object[EDGES];
    private static final @Nullable Object[] IN_VIEW = new Object[EDGES];
    private static final boolean[] KEEP = new boolean[EDGES];
    /** Each edge's row as it lays out and draws this frame, worked out the first time it is asked for. */
    private static final @Nullable View[] VIEWS = new View[EDGES];

    // Groups sliding

    /**
     * Each group's slide, by its id, so it carries over to a screen opened in place of another.
     * Eased each way, so a group closing moves from its first frame as one opening does.
     */
    private static Transition slide = Transition.eachWay(ContainerTabs.SLIDE_MILLIS, Easing.EASE_OUT);
    /**
     * The room each row's arrows take at its ends, by edge, 0 to 1 of theirs: it opens and closes
     * with the slide that makes the row page or stop paging, so the tabs move over to make room
     * rather than jump. Tabs coming or going page the row at once, as they appear at once.
     */
    private static Transition pageRoom = Transition.eachWay(ContainerTabs.SLIDE_MILLIS, Easing.EASE_OUT);
    /** Each edge's row this frame: whether a group on it is sliding. */
    private static final boolean[] SLIDING = new boolean[EDGES];
    /** How far each group showing this frame is open, 0 to 1, by its id. */
    private static final Map<String, Float> OPEN = new HashMap<>();
    /**
     * Until the first frame drawn since the player was last in the world: groups stand as they
     * are, not sliding, through the layouts at init and that frame.
     */
    private static boolean settle = true;

    /**
     * How far group {@code id} is open now, sliding toward {@code expanded}. On a screen's first
     * frame it stands at its end: opening the inventory shows its groups as they are.
     */
    private static float openness(String id, boolean expanded) {
        long now = MenuKitClock.now();
        if (!settle) return slide.toward(id, expanded, now);
        // Started a whole slide ago, so it is already there.
        slide.toward(id, expanded, now - ContainerTabs.SLIDE_MILLIS);
        return expanded ? 1f : 0f;
    }

    private static Shown[][] empty() {
        Shown[][] out = new Shown[EDGES][];
        Arrays.fill(out, new Shown[0]);
        return out;
    }

    /** The open region went (or another opened): the rows go with it. Paging stays for a screen opened in its place. */
    static void reset() {
        rows = empty();
        drawn = empty();
        Arrays.fill(VIEWS, null);
    }

    /** Back in the world: every row starts over from its first tab, and every group from where it stands. */
    private static void resetPaging() {
        Arrays.fill(FIRST, 0);
        Arrays.fill(FIRST_REF, null);
        Arrays.fill(IN_VIEW, null);
        Arrays.fill(KEEP, false);
        slide = Transition.eachWay(ContainerTabs.SLIDE_MILLIS, Easing.EASE_OUT);
        pageRoom = Transition.eachWay(ContainerTabs.SLIDE_MILLIS, Easing.EASE_OUT);
        settle = true;
    }

    /** Whether {@code t} shows on {@code screen} now: its screens take it and its {@code visibleWhen} holds. */
    private static boolean shows(Tab t, AbstractContainerScreen<?> screen) {
        return t.matcher().matches(screen) && Guard.test(t.visible(), "visibleWhen", t.id());
    }

    /** Whether {@code g}'s sub-tabs are out now, its rule read once per {@code read}. */
    private static boolean expanded(@Nullable Group g, Map<Group, Boolean> read) {
        return g != null && read.computeIfAbsent(g, k -> Guard.test(k.expanded(), "expandedWhen", k.id()));
    }

    /** {@code t} as it shows this frame: whether it is selected, disabled and pinned, each read once, a throw counting as no. */
    private static Shown read(Tab t) {
        return new Shown(t, Guard.test(t.selected(), "selectedWhen", t.id()), Guard.test(t.disabled(), "disabledWhen", t.id()),
                Guard.test(t.pinned(), "pinnedWhen", t.id()));
    }

    /**
     * This frame's rows on {@code screen}: on each edge, the tabs that show there now, in order,
     * a group's sub-tabs while it is expanded or still sliding closed, each tab's callbacks read
     * once ({@link Shown}). None with no screen (MenuKit's checks of the bare layout).
     */
    static void choose(@Nullable Screen screen) {
        Shown[][] out = empty(), drew = empty();
        OPEN.clear();
        Arrays.fill(SLIDING, false);
        Arrays.fill(VIEWS, null);
        if (screen instanceof AbstractContainerScreen<?> acs) {
            TabRegistry.sync();
            Map<Group, Boolean> expanded = new IdentityHashMap<>();
            List<List<Shown>> row = new ArrayList<>(), shown = new ArrayList<>();
            for (int i = 0; i < EDGES; i++) {
                row.add(new ArrayList<>());
                shown.add(new ArrayList<>());
            }
            for (Tab t : TabRegistry.all()) {
                int i = t.edge().ordinal();
                if (!shows(t, acs)) continue;
                boolean inRow = true;
                if (t.sub()) {
                    Group g = t.group();
                    boolean open = expanded(g, expanded);
                    float p = OPEN.computeIfAbsent(g.id(), id -> openness(id, open));
                    if (p != (open ? 1f : 0f)) SLIDING[i] = true;
                    // Closed and back under its header: not drawn.
                    if (!open && p <= 0f) continue;
                    inRow = open;
                }
                // One read for the row and the drawing alike: the layout matches the two by identity.
                Shown s = read(t);
                if (inRow) row.get(i).add(s);
                shown.get(i).add(s);
            }
            for (int i = 0; i < EDGES; i++) {
                out[i] = row.get(i).toArray(Shown[]::new);
                drew[i] = shown.get(i).toArray(Shown[]::new);
            }
        }
        rows = out;
        drawn = drew;
    }

    /**
     * Each edge's settled row's height on {@code screen} now, by {@link Edge#ordinal}: its tallest
     * tab that shows, a group's sub-tabs counted while it is expanded, or 0 with none. Measured
     * without choosing the rows, so nothing slides or pages for it: what centering asks at init.
     */
    static int[] settledHeights(AbstractContainerScreen<?> screen) {
        int[] h = new int[EDGES];
        TabRegistry.sync();
        Map<Group, Boolean> expanded = new IdentityHashMap<>();
        for (Tab t : TabRegistry.all()) {
            int i = t.edge().ordinal();
            if (t.height() <= h[i] || !shows(t, screen) || t.sub() && !expanded(t.group(), expanded)) continue;
            h[i] = t.height();
        }
        return h;
    }

    /**
     * How far {@code edge}'s row stands out from its frame this frame: its tallest tab, or 0 with
     * none. The settled row's: a group's sub-tabs sliding back are drawn, but take no room.
     */
    static int height(Edge edge) {
        int h = 0;
        for (Shown t : rows[edge.ordinal()]) h = Math.max(h, t.height());
        return h;
    }

    /** {@code edge}'s row's height for its line: its tallest tab, or a tab's with none, so an empty row's line still reads. */
    private static int rowHeight(Edge edge) {
        int h = height(edge);
        return h > 0 ? h : ContainerTabs.TAB_HEIGHT;
    }

    /** How far {@code edge}'s tabs reach out from its frame as they draw this frame, sliding ones included. */
    static int drawnHeight(Edge edge) {
        int h = rowHeight(edge);
        for (Shown t : drawn[edge.ordinal()]) h = Math.max(h, t.height());
        return h;
    }

    /** Whether {@code edge} has a row this frame. */
    static boolean hasRow(Edge edge) {
        return rows[edge.ordinal()].length > 0;
    }

    /** The ids in {@code edge}'s row this frame, in order, for the checks. A closed group's sub-tabs aren't in it. */
    @VisibleForTesting
    public static List<String> rowForChecks(Edge edge) {
        return Arrays.stream(rows[edge.ordinal()]).map(s -> s.tab().id()).toList();
    }

    /** {@code edge}'s line this frame, on the region or on the screen's own frame, for the checks. */
    @VisibleForTesting
    public static @Nullable Line lineForChecks(Edge edge) {
        return lineNow(edge);
    }

    /** The first tab in view in {@code edge}'s row, for the checks. */
    @VisibleForTesting
    public static int firstForChecks(Edge edge) {
        return FIRST[edge.ordinal()];
    }

    /** How far group {@code id} is open this frame, 0 to 1, or -1 where none of its sub-tabs shows, for the checks. */
    @VisibleForTesting
    public static float opennessForChecks(String id) {
        Float p = OPEN.get(id);
        return p == null ? -1f : p;
    }

    /** What is at {@code (mx, my)} on {@code edge}'s row this frame, as {@link TabRowLayout#hit} answers, for the checks. */
    @VisibleForTesting
    public static int hitForChecks(Edge edge, double mx, double my) {
        View v = view(edge);
        return v == null ? TabRowLayout.NONE : TabRowLayout.hit(v.line(), v.row().length, v.fit(), v.inset(), v.placed(), mx, my);
    }

    /** Where {@code edge}'s tabs draw this frame, for the checks. */
    @VisibleForTesting
    public static List<Placed> placedForChecks(Edge edge) {
        View v = view(edge);
        return v == null ? List.of() : v.placed();
    }

    // Where each row is

    /** {@code edge}'s line on {@code g}: the list's top or bottom, or the inventory panel's. */
    public static Line line(Geometry g, Edge edge) {
        Reference inv = g.inventory();
        int h = rowHeight(edge);
        return switch (edge) {
            case CONTAINERS_TOP -> new Line(g.x(), g.width(), g.top(), true, h);
            case CONTAINERS_BOTTOM -> new Line(g.x(), g.width(), g.bottom(), false, h);
            case INVENTORY_TOP -> new Line(inv.leftPos(), inv.imageWidth(), inv.topPos(), true, h);
            case INVENTORY_BOTTOM -> new Line(inv.leftPos(), inv.imageWidth(), inv.topPos() + inv.imageHeight(), false, h);
        };
    }

    /** {@code edge}'s line this frame: on the region's panels while it is up, else the screen's own frame's outer two; {@code null} where none stands. */
    static @Nullable Line lineNow(Edge edge) {
        Geometry g = ContainerRegions.state.geometry;
        if (g != null) return line(g, edge);
        Reference f = bare;
        if (f == null) return null;
        return switch (edge) {
            case CONTAINERS_TOP -> new Line(f.leftPos(), f.imageWidth(), f.topPos(), true, rowHeight(edge));
            case INVENTORY_BOTTOM -> new Line(f.leftPos(), f.imageWidth(), f.topPos() + f.imageHeight(), false, rowHeight(edge));
            default -> null;
        };
    }

    /** {@code edge}'s panel's origin: the row's top-left, a bottom row's {@link TabStrip#INTO} rows up to take its selected tab. */
    static @Nullable ScreenOrigin origin(Edge edge) {
        Line l = lineNow(edge);
        if (l == null) return null;
        return new ScreenOrigin(l.x(), l.top() ? l.rowTop() : l.y() - TabStrip.INTO);
    }

    /** The arrows' room at each end of {@code edge}'s row this frame, toward theirs while it pages ({@link #pageRoom}). */
    private static int inset(Edge edge, boolean paged) {
        long now = MenuKitClock.now();
        // No group sliding: the room is where it goes, at once.
        if (!SLIDING[edge.ordinal()]) pageRoom.toward(edge, paged, now - ContainerTabs.SLIDE_MILLIS);
        return Math.round((TabRowLayout.ARROW_WIDTH + 1) * pageRoom.toward(edge, paged, now));
    }

    /** {@code edge}'s row this frame: its line, its tabs, how it fits, the arrows' room and where each draws. */
    record View(Line line, Shown[] row, Fit fit, int inset, List<Placed> placed) {}

    /**
     * {@code edge}'s {@link View} this frame, {@code null} where none shows: worked out the first
     * time it is asked for after the rows are chosen or paged, and kept while its line stands.
     */
    static @Nullable View view(Edge edge) {
        int e = edge.ordinal();
        Line l = lineNow(edge);
        Shown[] row = rows[e];
        if (l == null || row.length == 0) return null;
        View v = VIEWS[e];
        // The line moves with the frame (a swapped screen's, mid-way): a view on another line is stale.
        if (v != null && v.line().equals(l)) return v;
        Fit f = TabRowLayout.fit(l.width(), TabRowLayout.widths(row), FIRST[e]);
        int inset = inset(edge, f.paged());
        v = new View(l, row, f, inset, TabRowLayout.place(l, drawn[e], row, f, OPEN, inset));
        VIEWS[e] = v;
        return v;
    }

    /** Where the tab kept by {@code ref} is in {@code row}, or -1. */
    private static int indexOf(Shown[] row, @Nullable Object ref) {
        if (ref != null) for (int i = 0; i < row.length; i++) if (row[i].tab().ref().equals(ref)) return i;
        return -1;
    }

    /** The first selected tab in {@code row}, or -1. */
    private static int selected(Shown[] row) {
        for (int i = 0; i < row.length; i++) if (row[i].selected()) return i;
        return -1;
    }

    /**
     * Once the region is laid out: each row's first tab is the one it draws from, and in each
     * row a tab that has started drawing selected comes into view and stays there, however
     * the frame's width changes, until the arrows page it out (paging leaves the selection
     * alone, and a tab paged out stays wherever the arrows put the row). The page follows its
     * first tab in view: tabs that come or go before it (a keyed list's, a group's opening or
     * closing) don't move the row off it, and a tab that went leaves the page at its place.
     */
    static void afterLayout() {
        Arrays.fill(VIEWS, null);
        for (Edge edge : Edge.values()) {
            Shown[] row = rows[edge.ordinal()];
            int e = edge.ordinal();
            Line l = lineNow(edge);
            if (l == null) continue;
            int[] widths = TabRowLayout.widths(row);
            // Kept as drawn: on a frame wide enough for every tab (a list that took a
            // scrollbar's lane, a wider section) the row draws from its first, and a row
            // kept on an earlier page would go back there once the frame narrows again,
            // the selected tab out of view though it never moved.
            int anchor = indexOf(row, FIRST_REF[e]);
            Fit f = TabRowLayout.fit(l.width(), widths, anchor >= 0 ? anchor : FIRST[e]);
            FIRST[e] = f.first();
            int sel = selected(row);
            Object ref = sel < 0 ? null : row[sel].tab().ref();
            if (ref != null && !ref.equals(IN_VIEW[e])) KEEP[e] = true;
            IN_VIEW[e] = ref;
            // Kept in view, not only revealed once: the frame it was revealed on can narrow
            // after (a list that takes a scrollbar's lane once a new section stacks), and the
            // row then fits a tab fewer.
            if (sel >= 0 && KEEP[e]) {
                if (sel < f.first()) FIRST[e] = sel;
                else if (sel >= f.first() + f.shown()) FIRST[e] = TabRowLayout.endingAt(l.width(), widths, sel);
                // Out from behind a pinned tab: a tab at a time away from the end it is pinned at.
                int room = TabRowLayout.room(l.width(), f), side = TabRowLayout.covered(row, FIRST[e], sel, room);
                int lastFirst = TabRowLayout.fit(l.width(), widths, row.length - 1).first();
                while (side != 0 && FIRST[e] + side >= 0 && FIRST[e] + side <= lastFirst
                        && TabRowLayout.covered(row, FIRST[e], sel, room) == side) FIRST[e] += side;
            }
            // An empty row (a frame with none showing) keeps the anchor for when they're back.
            if (row.length > 0) FIRST_REF[e] = row[FIRST[e]].tab().ref();
        }
    }

    // The row, clicked

    /**
     * A left click at {@code (mx, my)} on {@code edge}'s row: on an enabled tab it runs the
     * tab's action, on an arrow it pages, each with vanilla's click sound. Whether the click
     * was the row's: anywhere on a tab or an arrow, an item held or not, so it goes no further
     * and a held item is neither dropped nor placed. A disabled tab takes it and does nothing;
     * an action that throws is logged once.
     */
    public static boolean click(Edge edge, double mx, double my) {
        View v = view(edge);
        if (v == null) return false;
        Shown[] row = v.row();
        Fit f = v.fit();
        int e = edge.ordinal();
        int what = TabRowLayout.hit(v.line(), row.length, f, v.inset(), v.placed(), mx, my);
        if (what == TabRowLayout.NONE) return false;
        if (what == TabRowLayout.BACK || what == TabRowLayout.FORWARD) {
            FIRST[e] = what == TabRowLayout.BACK ? Math.max(0, f.first() - 1) : f.first() + 1;
            FIRST_REF[e] = row[FIRST[e]].tab().ref();
            VIEWS[e] = null;
            // Paged out of view, or behind a pinned tab, the selected tab stays out until it is selected anew.
            int sel = selected(row);
            KEEP[e] = sel >= FIRST[e] && sel < FIRST[e] + f.shown()
                    && TabRowLayout.covered(row, FIRST[e], sel, TabRowLayout.room(v.line().width(), f)) == 0;
        }
        else if (row[what].disabled()) return true;
        else Guard.run(row[what].tab().onClick(), "onClick", row[what].tab().id());
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) AbstractWidget.playButtonClickSound(mc.getSoundManager());
        return true;
    }
}
