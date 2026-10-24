package com.trevlar.menukit.inject;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.Transition;
import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.core.ControlLook;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The container screen's tab rows (7.4.0; the public face is {@code api.panel.ContainerTabs}):
 * the declared tabs, this frame's row on each {@link Edge}, and each row drawn, hit and paged.
 * {@link ContainerRegions} asks each frame which edges have a row, so the layout makes room for
 * it ({@link RegionLayout.Shape}); what a tab does is the mod's, through its callbacks.
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
 * <p><b>Keyed lists</b> ({@code ContainerTabs.keyed}): each list's keys are read as the rows
 * are chosen, every frame a container screen is open, and its tabs placed among the others as
 * a kind of section's are.
 *
 * <p><b>Groups</b> (Fluorite): a keyed list with a header. Each frame the row is the header and,
 * while the group is expanded, its sub-tabs; that row is what pages, takes clicks and keeps a
 * selected tab in view, as if every group stood still. Only the drawing moves: each group's
 * sub-tabs, one strip, are drawn as far out from under the header as the group's slide has got
 * ({@link #place}), clipped at the header's left edge and drawn before it, so they come out from
 * behind it; the tabs after the strip follow its edge.
 *
 * <p><b>Pinned tabs</b> (Fluorite): a tab whose {@code sticky} holds this frame stops at
 * whichever end of the row paging would take it past, and the tabs on the far side of it go on
 * behind it, until the next group's header (at the left end) or the previous group's last tab
 * (at the right end) pushes it off ({@link #pin}). Pinning moves only the drawing, as a slide does.
 *
 * <p><b>Paging carries across screens.</b> A screen opened in place of another keeps each row's
 * page; it starts over only once the player is back in the world with no screen open.
 *
 * <p>Render thread only.
 */
@ApiStatus.Internal
public final class ContainerTabRows {

    private ContainerTabRows() {}

    // The row's measures, from vanilla's creative inventory.

    /** A tab's width: vanilla's 26 px tab. Tabs stand side by side, touching, so no background shows between them. */
    public static final int TAB_WIDTH = 26;
    /** A group's sub-tabs by default: about two-thirds of a tab, on the same line. */
    public static final int SUB_WIDTH = 18, SUB_HEIGHT = 17;
    /** The smallest a tab can be, either way: its sprite's corners and edges, kept whole. */
    public static final int MIN_SIZE = 8;
    /** Vanilla's tab sprite's size; the {@link #INTO} rows at its frame end are where a selected tab opens into the frame. */
    static final int SPRITE_WIDTH = 26, SPRITE_HEIGHT = 32, INTO = 4;
    /** The sprite's left and right edges, kept whole as a tab widens or narrows; the plain columns between repeat or are cut. */
    static final int EDGE_COLUMNS = 4;
    /** A tab's rows outside its fill, at the tip: the sprite's 2 empty rows, its outline and its 2-row highlight. */
    static final int TIP = 5;
    /** How long a group takes to slide open or closed. */
    public static final int SLIDE_MILLIS = 200;
    /** The paging arrows: vanilla's recipe book page arrows, at the row's two ends. */
    public static final int ARROW_WIDTH = 12, ARROW_HEIGHT = 17;
    /** {@link #hit} answers: nothing, the back arrow, the forward arrow (a tab is its place in the row, 0 up). */
    public static final int NONE = -1, BACK = -2, FORWARD = -3;
    /** A disabled tab: its own shape drawn again over it in black at this alpha, so it and its icon read 0.3 as bright. */
    static final int GRAYED = 0xB3000000;

    // The declared tabs

    /**
     * One declared tab ({@code ContainerTabs.tab}). {@code visible} already holds its mode
     * scope (and, for a sub-tab, its header's showing); its icon, the mod's drawing, or none.
     * {@code width} across and {@code height} out from its frame. A group's header and its sub-tabs carry the
     * {@code group}; {@code sub} marks the sub-tabs. {@code sticky}, read every frame, pins it ({@link #pin}).
     */
    public record Tab(String id, ScreenMatcher matcher, int order, Edge edge, BooleanSupplier visible,
                      BooleanSupplier selected, BooleanSupplier disabled, BooleanSupplier sticky, ContainerTabs.@Nullable Icon icon,
                      @Nullable Supplier<@Nullable Component> tooltip, Runnable onClick,
                      int width, int height, @Nullable Group group, boolean sub) {}

    /** A group of tabs (a keyed list with a header): the list's id, and whether its sub-tabs are out, read every frame. */
    public record Group(String id, BooleanSupplier expanded) {}

    private static final List<Tab> TABS = new CopyOnWriteArrayList<>();
    private static boolean panelsBuilt;

    /**
     * A tab one live key made ({@code ContainerRegion.sections}): placed among the declared
     * tabs by its order, then by its section kind's id, then by its key's place in the keys.
     */
    record Live(Tab tab, String kind, int index) {}

    private static List<Live> live = List.of();
    /** Every tab there is now, declared and live, in row order. */
    private static List<Tab> all = List.of();

    /** {@code ContainerTabs.Builder.declare}: adds a tab in its place, and the rows' panels with the first one. */
    public static synchronized void declare(Tab tab) {
        requireFree(tab.id());
        TABS.add(tab);
        merge();
        buildPanels();
    }

    /**
     * One keyed list of tabs ({@code ContainerTabs.keyed}): {@code keys} read every frame a
     * container screen is open, and {@code tab} making a key's tab, the frame it first appears.
     * With a {@code header}, a group: the header first, keys or none, its sub-tabs after it.
     */
    public record Keyed(String id, Supplier<? extends @Nullable List<?>> keys, Function<Object, Tab> tab, @Nullable Tab header) {}

    private static final List<Keyed> KEYED = new CopyOnWriteArrayList<>();
    /** Each list's live keys, in the order its keys gave them, and each one's tab. */
    private static final Map<Keyed, Map<Object, Tab>> KEYED_LIVE = new IdentityHashMap<>();
    /** The keyed lists' tabs now, placed as {@link #live} ones are: by order, the list's id, the key's place. */
    private static List<Live> keyedLive = List.of();

    /** {@code ContainerTabs.Keyed.declare}: adds a list, and the rows' panels with the first. */
    public static synchronized void declareKeyed(Keyed list) {
        requireFree(list.id());
        KEYED.add(list);
        // A header stands before any key comes: place the lists anew on the next frame.
        keyedDirty = true;
        buildPanels();
    }

    private static boolean keyedDirty;

    private static void requireFree(String id) {
        for (Tab t : TABS) if (t.id().equals(id)) throw new IllegalStateException("ContainerTabs tab '" + id + "' is already declared");
        for (Keyed k : KEYED) if (k.id().equals(id)) throw new IllegalStateException("ContainerTabs keyed '" + id + "' is already declared");
    }

    /**
     * Each keyed list's keys now: a key that is new makes its tab, one that is gone takes it
     * with it, and one that stays keeps the tab it had. The tabs re-place only when some
     * list's keys changed. A list keeps its tabs while no container screen is open, so a
     * screen opened in place of another finds the same ones.
     */
    private static void syncKeyed() {
        boolean changed = keyedDirty;
        keyedDirty = false;
        for (Keyed list : KEYED) {
            List<?> keys = list.keys().get();
            if (keys == null) keys = List.of();
            Map<Object, Tab> was = KEYED_LIVE.getOrDefault(list, Map.of());
            // ponytail: a keys list with a repeated key never matches, so it rebuilds the map each frame; the tabs are kept either way.
            if (was.size() == keys.size() && List.copyOf(was.keySet()).equals(keys)) continue;
            Map<Object, Tab> now = new LinkedHashMap<>();
            for (Object k : keys) {
                if (now.containsKey(k)) continue;
                Tab made = was.get(k);
                now.put(k, made != null ? made : list.tab().apply(k));
            }
            if (now.isEmpty()) KEYED_LIVE.remove(list);
            else KEYED_LIVE.put(list, now);
            changed = true;
        }
        if (!changed) return;
        List<Live> tabs = new ArrayList<>();
        for (Keyed list : KEYED) {
            int i = 0;
            if (list.header() != null) tabs.add(new Live(list.header(), list.id(), i++));
            for (Tab t : KEYED_LIVE.getOrDefault(list, Map.of()).values()) tabs.add(new Live(t, list.id(), i++));
        }
        keyedLive = List.copyOf(tabs);
        merge();
    }

    /** The live keys' tabs now, from the container region whenever its sections restack. */
    static void live(List<Live> tabs) {
        live = List.copyOf(tabs);
        merge();
    }

    private static void merge() {
        List<Live> every = new ArrayList<>(TABS.size() + live.size() + keyedLive.size());
        for (Tab t : TABS) every.add(new Live(t, t.id(), 0));
        every.addAll(live);
        every.addAll(keyedLive);
        every.sort(Comparator.comparingInt((Live l) -> l.tab().order()).thenComparing(Live::kind).thenComparingInt(Live::index));
        all = every.stream().map(Live::tab).toList();
    }

    /** The rows' panels, made once, with the first tab or the first kind of section with tabs. */
    static synchronized void buildPanels() {
        if (panelsBuilt) return;
        panelsBuilt = true;
        for (Edge edge : Edge.values()) {
            String id = "menukit:container_tabs_" + edge.name().toLowerCase(Locale.ROOT);
            // One background panel per edge, drawn over the region's two so a selected tab opens
            // into its frame, on the screens the region is on.
            Panel row = Panel.builder(id)
                    .style(PanelStyle.NONE).background()
                    .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> origin(edge))
                            .priority(PanelPosition.DEFAULT_PRIORITY + 1)))
                    .add(new Strip(edge))
                    .build()
                    .visibleWhen(() -> ContainerRegions.state.geometry != null && ContainerRegions.state.applied != null
                            && rows[edge.ordinal()].length > 0);
            new ScreenPanelAdapter(row, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
            if (edge != Edge.CONTAINERS_TOP && edge != Edge.INVENTORY_BOTTOM) continue;
            // And on a screen's own frame, where the region isn't up: an ordinary panel, drawn
            // after the screen's image, so a selected tab opens into it.
            Panel onFrame = Panel.builder(id + "_on_frame")
                    .style(PanelStyle.NONE)
                    .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> origin(edge))
                            .priority(PanelPosition.DEFAULT_PRIORITY + 1)))
                    .add(new Strip(edge))
                    .build()
                    .visibleWhen(() -> bare != null && rows[edge.ordinal()].length > 0);
            new ScreenPanelAdapter(onFrame, 0).onMatching(ScreenMatcher.where(ContainerTabRows::carries));
        }
    }

    // A screen's own frame, where the region isn't up

    /** This frame's screen frame the outer rows stand on, or {@code null} while the region is up or no screen is. */
    private static @Nullable Reference bare;

    /** Whether rows may stand on {@code screen}: every container screen but the creative inventory, which has tabs of its own. */
    private static boolean carries(AbstractContainerScreen<?> screen) {
        return !(screen instanceof CreativeModeInventoryScreen);
    }

    /**
     * Hooks every container screen's frame, after the region's own frame hook, and clears the
     * rows' paging once the player is back in the world. Called once from client init.
     */
    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs) || !carries(acs)) return;
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
            if (lineNow(edge) == null) rows[edge.ordinal()] = drawn[edge.ordinal()] = new Tab[0];
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
     * does every widget placed from {@code topPos}. No row, or the region up on it: vanilla's place.
     */
    public static void centre(AbstractContainerScreen<?> screen) {
        int above = 0, below = 0;
        boolean regionUp = ContainerRegions.state.geometry != null && ContainerRegions.state.screen.get() == screen;
        if (carries(screen) && !regionUp) {
            choose(screen);
            above = height(Edge.CONTAINERS_TOP);
            below = height(Edge.INVENTORY_BOTTOM);
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
    private static Tab[][] rows = empty();
    /** Each edge's tabs drawn this frame: its row, and a closed group's sub-tabs too, for as long as they slide back. */
    private static Tab[][] drawn = empty();
    /**
     * Each row's first tab in view when they don't all fit, the selected tab last brought into
     * view, and whether the row keeps that tab in view: from when it starts drawing selected
     * until the arrows page it out.
     */
    private static final int[] FIRST = new int[Edge.values().length];
    /** Each row's first tab in view, by id, so the page stays on it as tabs before it come and go. */
    private static final @Nullable String[] FIRST_ID = new String[Edge.values().length];
    private static final @Nullable String[] IN_VIEW = new String[Edge.values().length];
    private static final boolean[] KEEP = new boolean[Edge.values().length];

    // Groups sliding

    /** The clock the slides run on: vanilla's, as every MenuKit animation's. Checks stop it to step through a slide. */
    @VisibleForTesting
    public static LongSupplier clock = Util::getMillis;
    /**
     * Each group's slide, by its id, so it carries over to a screen opened in place of another.
     * Eased each way, so a group closing moves from its first frame as one opening does.
     */
    private static Transition slide = Transition.eachWay(SLIDE_MILLIS, Easing.EASE_OUT);
    /**
     * The room each row's arrows take at its ends, by edge, 0 to 1 of theirs: it opens and closes
     * with the slide that makes the row page or stop paging, so the tabs move over to make room
     * rather than jump. Tabs coming or going page the row at once, as they appear at once.
     */
    private static Transition pageRoom = Transition.eachWay(SLIDE_MILLIS, Easing.EASE_OUT);
    /** Each edge's row this frame: whether a group on it is sliding. */
    private static final boolean[] SLIDING = new boolean[Edge.values().length];
    /** How far each group showing this frame is open, 0 to 1, by its id. */
    private static final Map<String, Float> OPEN = new HashMap<>();
    /** The first frame since the player was last in the world: groups stand as they are then, not sliding. */
    private static boolean settle = true;

    /**
     * How far group {@code id} is open now, sliding toward {@code expanded}. On a screen's first
     * frame it stands at its end: opening the inventory shows its groups as they are.
     */
    private static float openness(String id, boolean expanded) {
        long now = clock.getAsLong();
        if (!settle) return slide.toward(id, expanded, now);
        // Started a whole slide ago, so it is already there.
        slide.toward(id, expanded, now - SLIDE_MILLIS);
        return expanded ? 1f : 0f;
    }

    private static Tab[][] empty() {
        Tab[][] out = new Tab[Edge.values().length][];
        Arrays.fill(out, new Tab[0]);
        return out;
    }

    /** The open region went (or another opened): the rows go with it. Paging stays for a screen opened in its place. */
    static void reset() {
        rows = empty();
        drawn = empty();
    }

    /** Back in the world: every row starts over from its first tab, and every group from where it stands. */
    private static void resetPaging() {
        Arrays.fill(FIRST, 0);
        Arrays.fill(FIRST_ID, null);
        Arrays.fill(IN_VIEW, null);
        Arrays.fill(KEEP, false);
        slide = Transition.eachWay(SLIDE_MILLIS, Easing.EASE_OUT);
        pageRoom = Transition.eachWay(SLIDE_MILLIS, Easing.EASE_OUT);
        settle = true;
    }

    /**
     * This frame's rows on {@code screen}: on each edge, the tabs that show there now, in order,
     * a group's sub-tabs while it is expanded or still sliding closed. None with no screen
     * (MenuKit's checks of the bare layout) or no region on it.
     */
    static void choose(@Nullable Screen screen) {
        Tab[][] out = empty(), drew = empty();
        OPEN.clear();
        Arrays.fill(SLIDING, false);
        if (screen instanceof AbstractContainerScreen<?> acs) {
            syncKeyed();
            // Each group's open rule, read once a frame.
            Map<Group, Boolean> expanded = new IdentityHashMap<>();
            for (int i = 0; i < out.length; i++) {
                List<Tab> row = new ArrayList<>(), shown = new ArrayList<>();
                for (Tab t : all) {
                    if (t.edge().ordinal() != i || !t.matcher().matches(acs) || !t.visible().getAsBoolean()) continue;
                    if (t.sub()) {
                        Group g = t.group();
                        boolean open = expanded.computeIfAbsent(g, k -> k.expanded().getAsBoolean());
                        float p = OPEN.computeIfAbsent(g.id(), id -> openness(id, open));
                        if (p != (open ? 1f : 0f)) SLIDING[i] = true;
                        if (open) row.add(t);
                        // Closed and back under its header: not drawn.
                        if (open || p > 0f) shown.add(t);
                    } else {
                        row.add(t);
                        shown.add(t);
                    }
                }
                out[i] = row.toArray(Tab[]::new);
                drew[i] = shown.toArray(Tab[]::new);
            }
            settle = false;
        }
        rows = out;
        drawn = drew;
    }

    /** How far {@code edge}'s row stands out from its frame this frame: its tallest tab, or 0 with none. */
    static int height(Edge edge) {
        int h = 0;
        for (Tab t : drawn[edge.ordinal()]) h = Math.max(h, t.height());
        return h;
    }

    /** {@code edge}'s row's height for its line: its tallest tab, or a tab's with none, so an empty row's line still reads. */
    private static int rowHeight(Edge edge) {
        int h = height(edge);
        return h > 0 ? h : RegionLayout.TAB_ROW;
    }

    /** The ids in {@code edge}'s row this frame, in order, for the checks. A closed group's sub-tabs aren't in it. */
    @VisibleForTesting
    public static List<String> rowForChecks(Edge edge) {
        return Arrays.stream(rows[edge.ordinal()]).map(Tab::id).toList();
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

    /** What is at {@code (mx, my)} on {@code edge}'s row this frame, as {@link #hit} answers, for the checks. */
    @VisibleForTesting
    public static int hitForChecks(Edge edge, double mx, double my) {
        View v = view(edge);
        return v == null ? NONE : hit(v.line(), v.row().length, v.fit(), v.inset(), v.placed(), mx, my);
    }

    /** Where {@code edge}'s tabs draw this frame, for the checks. */
    @VisibleForTesting
    public static List<Placed> placedForChecks(Edge edge) {
        View v = view(edge);
        return v == null ? List.of() : v.placed();
    }

    // Where each row is

    /**
     * The frame edge a row stands on: from {@code x}, {@code width} across, at {@code y}, whether
     * the tabs stand up from it ({@code top}) or hang down, and how far its tallest tab stands
     * out ({@code height}).
     */
    public record Line(int x, int width, int y, boolean top, int height) {

        /** The row's top: a row above its edge ends there, one below starts there. */
        public int rowTop() { return top ? y - height : y; }
    }

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
    private static @Nullable Line lineNow(Edge edge) {
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

    /** {@code edge}'s panel's origin: the row's top-left, a bottom row's {@link #INTO} rows up to take its selected tab. */
    private static @Nullable ScreenOrigin origin(Edge edge) {
        Line l = lineNow(edge);
        if (l == null) return null;
        return new ScreenOrigin(l.x(), l.top() ? l.rowTop() : l.y() - INTO);
    }

    // The row, pure

    /**
     * How the row lays out: the first tab in view, how many are, and whether they are paged
     * (the arrows at the row's ends take room then).
     */
    public record Fit(int first, int shown, boolean paged) {}

    /** How wide tabs {@code from} to {@code to} (exclusive) of a row of {@code widths} are, side by side. */
    public static int span(int[] widths, int from, int to) {
        int w = 0;
        for (int i = from; i < to; i++) w += widths[i];
        return w;
    }

    /** The room between the arrows on a {@code width} wide frame. */
    private static int between(int width) {
        return width - 2 * (ARROW_WIDTH + 1);
    }

    /**
     * The row of tabs {@code widths} wide on a {@code width} wide frame, from {@code first} on:
     * all of them where they fit, else paged, as many whole tabs as fit between the arrows (one
     * at least), {@code first} clamped so the last page is full.
     */
    public static Fit fit(int width, int[] widths, int first) {
        int n = widths.length;
        if (span(widths, 0, n) <= width) return new Fit(0, n, false);
        int room = between(width);
        // The last page full: the row starts no later than where its last tabs just fit.
        int last = n - 1;
        while (last > 0 && span(widths, last - 1, n) <= room) last--;
        int from = Math.max(0, Math.min(first, last));
        int shown = 1;
        while (from + shown < n && span(widths, from, from + shown + 1) <= room) shown++;
        return new Fit(from, shown, true);
    }

    /** The first tab in view that puts tab {@code at} last in view, on a {@code width} wide frame that pages. */
    static int endingAt(int width, int[] widths, int at) {
        int from = at;
        while (from > 0 && span(widths, from - 1, at + 1) <= between(width)) from--;
        return from;
    }

    /** The left and right ends of the room the tabs draw in on line {@code l}, {@code inset} in from each end: the arrows' room. */
    private static int roomLeft(Line l, int inset) { return l.x() + inset; }
    private static int roomRight(Line l, int inset) { return l.x() + l.width() - inset; }

    /** The arrows' room at each end of {@code edge}'s row this frame, toward theirs while it pages ({@link #pageRoom}). */
    private static int inset(Edge edge, boolean paged) {
        long now = clock.getAsLong();
        // No group sliding: the room is where it goes, at once.
        if (!SLIDING[edge.ordinal()]) pageRoom.toward(edge, paged, now - SLIDE_MILLIS);
        return Math.round((ARROW_WIDTH + 1) * pageRoom.toward(edge, paged, now));
    }

    /**
     * One tab where it draws this frame: its left edge, its size, its place in the row ({@code -1}
     * for a closing group's sub-tab, which takes no click), for a sub-tab the left edge it shows
     * from, its header's, so it goes under the header as it slides back, and whether it is
     * {@link #pin pinned} at either end of the row, drawn over the tabs going on behind it.
     */
    public record Placed(Tab tab, int x, int width, int height, int index, boolean sub, int clipLeft, boolean stuck) {}

    /**
     * Where the tabs {@code drew} draw on line {@code l} this frame, the row {@code row} laid
     * out by {@code f} between ends {@code inset} in, each group open {@code open} of the way (by
     * its id; 1 where absent): the row's tabs in view, the sub-tabs of a group sliding, and the
     * tabs pinned at the room's ends. A group's sub-tabs move as one, out from under the
     * header to its right as it opens, and every tab after it moves with their edge. Settled,
     * each tab not pinned is where the row's fit puts it. A tab not pinned shows whole or not
     * at all, as paging shows them: one that a slide has pushed past the room's right end is
     * left out until it is back inside.
     */
    static List<Placed> place(Line l, Tab[] drew, Tab[] row, Fit f, Map<String, Float> open, int inset) {
        int[] widths = widths(row);
        int n = drew.length;
        // Each tab's left as the row lays it out, its place in the row, and whether it pins now.
        double[] x = new double[n];
        int[] index = new int[n];
        boolean[] pins = new boolean[n];
        // From the row's first tab, in view at the room's left; tabs before it are off to the left.
        double cursor = roomLeft(l, inset) - span(widths, 0, f.first());
        int r = 0;
        for (int i = 0; i < n; ) {
            Tab t = drew[i];
            if (!t.sub()) {
                index[i] = r < row.length && row[r] == t ? r++ : -1;
                x[i] = cursor;
                pins[i] = index[i] >= 0 && t.sticky().getAsBoolean();
                cursor += t.width();
                i++;
                continue;
            }
            // A group's sub-tabs together: as wide as they are open, slid under the header by the rest.
            Group g = t.group();
            int end = i;
            double span = 0;
            while (end < n && drew[end].sub() && drew[end].group() == g) span += drew[end++].width();
            float p = open.getOrDefault(g.id(), 1f);
            double sx = cursor - (1f - p) * span;
            for (; i < end; i++) {
                Tab s = drew[i];
                index[i] = r < row.length && row[r] == s ? r++ : -1;
                x[i] = sx;
                // ponytail: a sliding group's sub-tabs move as one strip and pin once it settles, so one
                // that should pin then steps into its place at the slide's end; pin within the slide if that shows.
                pins[i] = index[i] >= 0 && p >= 1f && s.sticky().getAsBoolean();
                sx += s.width();
            }
            cursor += p * span;
        }
        int left = roomLeft(l, inset), right = roomRight(l, inset);
        boolean[] stuck = pin(drew, x, pins, left, right, cursor);
        List<Placed> out = new ArrayList<>();
        int headerX = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            Tab t = drew[i];
            int at = (int) Math.round(x[i]);
            if (!t.sub()) headerX = at;
            // Pinned, drawn while any of it is in the room; in the row, drawn while in view;
            // closing, drawn until it is back under the header.
            // A tab shows whole or not at all: one a slide has pushed past the room's end is left out.
            boolean whole = at + t.width() <= right && (t.sub() || at >= left);
            boolean shown = stuck[i] ? at + t.width() > left && at < right
                    : whole && (index[i] >= 0 ? index[i] >= f.first() && index[i] < f.first() + f.shown()
                    : open.getOrDefault(t.group().id(), 1f) > 0f);
            // A pinned sub-tab isn't clipped at its header: at the right end its header can be behind it.
            int clip = t.sub() && !stuck[i] ? headerX : Integer.MIN_VALUE;
            if (shown) out.add(new Placed(t, at, t.width(), t.height(), index[i], t.sub(), clip, stuck[i]));
        }
        return out;
    }

    /**
     * Pins tabs: of {@code tabs}, laid out from lefts {@code x}, those that {@code pins} stop at
     * {@code left} or {@code right} as they reach it, and the tabs beyond them go on behind. A
     * run is a tab and the sub-tabs of its group after it (a tab outside any group is a run of
     * its own). At the left end a run's pinned tabs stack from {@code left} in its order, and
     * its end, the next run's left or {@code end} for the last, pushes the stack off to the left
     * as it arrives, so a pinned sub-tab leaves with its group's header. The right end mirrors
     * it: the stack ends at {@code right}, still in row order, and the run's start, where the
     * previous run ends, pushes it off to the right. Moves each pinned tab's {@code x} to where
     * it stands; whether each one is stuck there, its own place past where the stack has it.
     */
    static boolean[] pin(Tab[] tabs, double[] x, boolean[] pins, double left, double right, double end) {
        boolean[] stuck = new boolean[tabs.length];
        for (int from = 0; from < tabs.length; ) {
            int to = from + 1;
            while (to < tabs.length && tabs[to].sub() && tabs[to].group() == tabs[from].group()) to++;
            double runStart = x[from], runEnd = to < tabs.length ? x[to] : end;
            // How wide the run's pinned tabs before this one are, and this one and those after it.
            double before = 0, rest = 0;
            for (int i = from; i < to; i++) if (pins[i]) rest += tabs[i].width();
            for (int i = from; i < to; i++) {
                if (!pins[i]) continue;
                // Its place in each end's stack, and where the run's edge pushing the stack off has it.
                double atLeft = left + before, leftPushed = runEnd - rest;
                double atRight = right - rest, rightPushed = runStart + before;
                before += tabs[i].width();
                rest -= tabs[i].width();
                if (x[i] < atLeft) {
                    stuck[i] = true;
                    x[i] = Math.min(atLeft, leftPushed);
                } else if (x[i] > atRight) {
                    stuck[i] = true;
                    x[i] = Math.max(atRight, rightPushed);
                }
            }
            from = to;
        }
        return stuck;
    }

    /**
     * Whether tab {@code at} of {@code row}, laid out from {@code first} in a room {@code room}
     * wide, is behind a pinned tab, wholly or in part: -1 behind one at the left end, 1 at the
     * right, 0 not. A tab pinned itself is never behind one.
     */
    static int covered(Tab[] row, int first, int at, int room) {
        int[] widths = widths(row);
        double[] x = new double[row.length];
        boolean[] pins = new boolean[row.length];
        // From the room's left end, 0; tabs before the first are left of it.
        double cursor = -span(widths, 0, first);
        for (int i = 0; i < row.length; i++) {
            x[i] = cursor;
            cursor += widths[i];
            pins[i] = row[i].sticky().getAsBoolean();
        }
        double from = x[at], to = from + widths[at];
        boolean[] stuck = pin(row, x, pins, 0, room, cursor);
        if (stuck[at]) return 0;
        for (int i = 0; i < row.length; i++) {
            if (stuck[i] && x[i] < to && x[i] + widths[i] > from) return i < at ? -1 : 1;
        }
        return 0;
    }

    /** The room the tabs of a row laid out by {@code f} draw in on a {@code width} wide frame, settled. */
    private static int room(int width, Fit f) {
        return f.paged() ? between(width) : width;
    }

    private static int[] widths(Tab[] row) {
        int[] w = new int[row.length];
        for (int i = 0; i < row.length; i++) w[i] = row[i].width();
        return w;
    }

    /**
     * What is at {@code (mx, my)} on line {@code l}, the row of {@code count} tabs laid out by
     * {@code f} and drawn at {@code placed}: a tab (its place in the row), an arrow there is one
     * for, or {@link #NONE}. A tab runs from the frame to its height out, and shows only
     * between the arrows; a pinned tab is over the rest, a header over its sub-tabs, which show
     * only right of it. Tabs touch, each from its left edge up to the next one's, so a point is
     * on one tab only.
     */
    private static int hit(Line l, int count, Fit f, int inset, List<Placed> placed, double mx, double my) {
        if (mx < l.x() || mx >= l.x() + l.width()) return NONE;
        if (f.paged() && my >= l.rowTop() && my < l.rowTop() + l.height()) {
            if (mx < l.x() + ARROW_WIDTH) return f.first() > 0 ? BACK : NONE;
            if (mx >= l.x() + l.width() - ARROW_WIDTH) return f.first() + f.shown() < count ? FORWARD : NONE;
        }
        int left = roomLeft(l, inset), right = roomRight(l, inset);
        for (int layer = 2; layer >= 0; layer--) {
            for (Placed p : placed) {
                if (layer(p) != layer || p.index() < 0) continue;
                int from = Math.max(left, Math.max(p.x(), p.clipLeft())), to = Math.min(right, p.x() + p.width());
                int near = l.top() ? l.y() - p.height() : l.y();
                if (mx >= from && mx < to && my >= near && my < near + p.height()) return p.index();
            }
        }
        return NONE;
    }

    /** Which tabs draw over which, from the bottom: sub-tabs, then the other tabs, then pinned ones. */
    private static int layer(Placed p) {
        return p.stuck() ? 2 : p.sub() ? 0 : 1;
    }

    /** {@code edge}'s row this frame: its line, its tabs, how it fits, the arrows' room and where each draws; {@code null} where none shows. */
    private record View(Line line, Tab[] row, Fit fit, int inset, List<Placed> placed) {}

    private static @Nullable View view(Edge edge) {
        Line l = lineNow(edge);
        Tab[] row = rows[edge.ordinal()];
        if (l == null || row.length == 0) return null;
        Fit f = fit(l.width(), widths(row), FIRST[edge.ordinal()]);
        int inset = inset(edge, f.paged());
        return new View(l, row, f, inset, place(l, drawn[edge.ordinal()], row, f, OPEN, inset));
    }

    /** Where the tab {@code id} is in {@code row}, or -1. */
    private static int indexOf(Tab[] row, @Nullable String id) {
        if (id != null) for (int i = 0; i < row.length; i++) if (row[i].id().equals(id)) return i;
        return -1;
    }

    /** The first selected tab in {@code row}, or -1. */
    private static int selected(Tab[] row) {
        for (int i = 0; i < row.length; i++) if (row[i].selected().getAsBoolean()) return i;
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
        for (Edge edge : Edge.values()) {
            Tab[] row = rows[edge.ordinal()];
            int e = edge.ordinal();
            Line l = lineNow(edge);
            if (l == null) continue;
            int[] widths = widths(row);
            // Kept as drawn: on a frame wide enough for every tab (a list that took a
            // scrollbar's lane, a wider section) the row draws from its first, and a row
            // kept on an earlier page would go back there once the frame narrows again,
            // the selected tab out of view though it never moved.
            int anchor = indexOf(row, FIRST_ID[e]);
            Fit f = fit(l.width(), widths, anchor >= 0 ? anchor : FIRST[e]);
            FIRST[e] = f.first();
            int sel = selected(row);
            String id = sel < 0 ? null : row[sel].id();
            if (id != null && !id.equals(IN_VIEW[e])) KEEP[e] = true;
            IN_VIEW[e] = id;
            // Kept in view, not only revealed once: the frame it was revealed on can narrow
            // after (a list that takes a scrollbar's lane once a new section stacks), and the
            // row then fits a tab fewer.
            if (sel >= 0 && KEEP[e]) {
                if (sel < f.first()) FIRST[e] = sel;
                else if (sel >= f.first() + f.shown()) FIRST[e] = endingAt(l.width(), widths, sel);
                // Out from behind a pinned tab: a tab at a time away from the end it is pinned at.
                int room = room(l.width(), f), side = covered(row, FIRST[e], sel, room);
                int lastFirst = fit(l.width(), widths, row.length - 1).first();
                while (side != 0 && FIRST[e] + side >= 0 && FIRST[e] + side <= lastFirst
                        && covered(row, FIRST[e], sel, room) == side) FIRST[e] += side;
            }
            // An empty row (a frame with none showing) keeps the anchor for when they're back.
            if (row.length > 0) FIRST_ID[e] = row[FIRST[e]].id();
        }
    }

    // The row, drawn and clicked

    /**
     * A left click at {@code (mx, my)} on {@code edge}'s row: on an enabled tab it runs the
     * tab's action, on an arrow it pages, each with vanilla's click sound. Whether the click
     * was the row's: anywhere on a tab or an arrow, an item held or not, so it goes no further
     * and a held item is neither dropped nor placed. A disabled tab takes it and does nothing.
     */
    public static boolean click(Edge edge, double mx, double my) {
        View v = view(edge);
        if (v == null) return false;
        Tab[] row = v.row();
        Fit f = v.fit();
        int e = edge.ordinal();
        int what = hit(v.line(), row.length, f, v.inset(), v.placed(), mx, my);
        if (what == NONE) return false;
        if (what == BACK || what == FORWARD) {
            FIRST[e] = what == BACK ? Math.max(0, f.first() - 1) : f.first() + 1;
            FIRST_ID[e] = row[FIRST[e]].id();
            // Paged out of view, or behind a pinned tab, the selected tab stays out until it is selected anew.
            int sel = selected(row);
            KEEP[e] = sel >= FIRST[e] && sel < FIRST[e] + f.shown() && covered(row, FIRST[e], sel, room(v.line().width(), f)) == 0;
        }
        else if (row[what].disabled().getAsBoolean()) return true;
        else row[what].onClick().run();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) AbstractWidget.playButtonClickSound(mc.getSoundManager());
        return true;
    }

    /**
     * The sprite runs that draw source span {@code [from, to)} {@code length} long: its first
     * {@code head} and last {@code tail} kept whole at the two ends, the plain run between them
     * repeated or cut to fill. Each is {source start, offset in the length, run}; runs can be
     * empty.
     */
    public static List<int[]> runs(int from, int to, int head, int tail, int length) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{from, 0, head});
        int plain = to - from - head - tail, fill = length - head - tail;
        for (int d = 0; d < fill; d += plain) out.add(new int[]{from + head, head + d, Math.min(plain, fill - d)});
        out.add(new int[]{to - tail, length - tail, tail});
        return out;
    }

    /**
     * One edge's row: vanilla's creative tabs ({@link ControlLook.Control#CONTAINER_TAB}), top
     * ones on a top edge and bottom ones on a bottom edge, each with its icon and its tooltip,
     * at its own size. A tab not selected stops at the frame, as vanilla's goes behind its
     * image; a selected one runs {@link #INTO} px into the frame, so it opens into the panel. A
     * disabled one is grayed. Past {@link #fit what fits} the row pages, a tab at a time, with
     * vanilla's page arrows at its ends. A group's sub-tabs draw under the tabs, so they slide
     * out from under their header, and pinned tabs over every other. Drawn from the screen's
     * coordinates, not the panel's.
     */
    static final class Strip implements PanelElement {
        private final Edge edge;

        Strip(Edge edge) {
            this.edge = edge;
        }

        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Line l = lineNow(edge); return l == null ? 0 : l.width(); }
        @Override public int getHeight() { return rowHeight(edge) + INTO; }
        @Override public boolean isInteractive() { return true; }

        @Override
        public void render(RenderContext ctx) {
            View v = view(edge);
            if (v == null) return;
            Line l = v.line();
            Tab[] row = v.row();
            Fit f = v.fit();
            GuiGraphicsExtractor graphics = ctx.graphics();
            int over = ctx.hasMouseInput() ? hit(l, row.length, f, v.inset(), v.placed(), ctx.mouseX(), ctx.mouseY()) : NONE;
            // Tabs show between the arrows; sub-tabs right of their header too. The band takes
            // in a selected tab's rows into the frame, either way up.
            int left = roomLeft(l, v.inset()), right = roomRight(l, v.inset());
            int bandTop = l.y() - l.height() - INTO, bandBottom = l.y() + l.height() + INTO;
            graphics.enableScissor(left, bandTop, right, bandBottom);
            for (int layer = 0; layer <= 2; layer++) {
                for (Placed p : v.placed()) {
                    if (layer(p) != layer) continue;
                    if (p.sub()) graphics.enableScissor(Math.max(left, p.clipLeft()), bandTop, right, bandBottom);
                    draw(graphics, p, l, !f.paged() && p.index() == 0);
                    if (p.sub()) graphics.disableScissor();
                }
            }
            graphics.disableScissor();
            if (f.paged()) {
                int ay = l.rowTop() + (l.height() - ARROW_HEIGHT) / 2;
                if (f.first() > 0) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(false, over == BACK),
                            l.x(), ay, ARROW_WIDTH, ARROW_HEIGHT);
                }
                if (f.first() + f.shown() < row.length) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(true, over == FORWARD),
                            l.x() + l.width() - ARROW_WIDTH, ay, ARROW_WIDTH, ARROW_HEIGHT);
                }
            }
            if (over == BACK || over == FORWARD || (over >= 0 && !row[over].disabled().getAsBoolean()
                    && !row[over].selected().getAsBoolean())) graphics.requestCursor(CursorTypes.POINTING_HAND);
            if (over >= 0 && row[over].tooltip() != null) {
                Component text = row[over].tooltip().get();
                if (text != null) MKTooltip.queue(graphics, text, ctx.mouseX(), ctx.mouseY());
            }
        }

        /** Tab {@code p} on line {@code l}: its sprite, its icon, and grayed over both when disabled. */
        private static void draw(GuiGraphicsExtractor graphics, Placed p, Line l, boolean first) {
            Tab t = p.tab();
            boolean selected = t.selected().getAsBoolean();
            Identifier sprite = ControlLook.Control.containerTab(!l.top(), selected, first);
            tab(graphics, sprite, p.x(), l, t.width(), t.height(), selected, 0xFFFFFFFF);
            icon(graphics, t, p.x(), l);
            // Grayed: the tab's own shape again in translucent black, over the tab and its icon alike.
            if (t.disabled().getAsBoolean()) tab(graphics, sprite, p.x(), l, t.width(), t.height(), selected, GRAYED);
        }

        /**
         * Tab {@code t}'s icon box, centered across it and centered in its fill, between the frame
         * and the tip's {@link #TIP} rows (on a full-size tab that is 2 px clear of the frame, as
         * vanilla's is): 16 px square on a full-size tab, smaller in proportion on a smaller
         * one (an even size, so it centers on the pixel grid), and the mod's drawing in it.
         */
        private static void icon(GuiGraphicsExtractor graphics, Tab t, int x, Line l) {
            if (t.icon() == null) return;
            float scale = Math.min(1f, Math.min((float) t.width() / TAB_WIDTH, (float) t.height() / RegionLayout.TAB_ROW));
            int size = 2 * Math.round(8f * scale);
            int clear = Math.max(0, (t.height() - TIP - size) / 2);
            int ix = x + (t.width() - size) / 2, iy = l.top() ? l.y() - clear - size : l.y() + clear;
            t.icon().draw(graphics, ix, iy, size, size);
        }

        /**
         * One tab at {@code x} on line {@code l}, {@code width} across and {@code height} out from
         * the frame, tinted {@code color}: vanilla's sprite, its corners and edges whole and its
         * plain middle repeated or cut to the size. Selected, its {@link #INTO} rows at the frame
         * end go over the frame's edge, open into it; not selected, they are left out, so it stops
         * at the edge. Vanilla's bottom tab not selected ends 4 px short of its sprite where a
         * top one ends 2 px short, so it draws 2 px longer and the two edges match.
         */
        private static void tab(GuiGraphicsExtractor graphics, Identifier sprite, int x, Line l, int width, int height,
                                boolean selected, int color) {
            // The sprite rows drawn, the rows kept whole at each end, and where they go.
            int from, to, head, tail, y, length;
            if (l.top()) {
                // The tip at the top, its rounded corners in the first 6 rows; selected, the INTO rows open into the frame.
                from = 0; to = SPRITE_HEIGHT - (selected ? 0 : INTO); head = 6; tail = selected ? INTO : 0;
                y = l.y() - height; length = height + (selected ? INTO : 0);
            } else if (selected) {
                // The frame end first, INTO rows up over the edge, the tip's 3 rows last.
                from = 0; to = SPRITE_HEIGHT; head = INTO; tail = 3;
                y = l.y() - INTO; length = height + INTO;
            } else {
                // From under the frame's edge; the tip's 3 rows and the 4 empty ones after them last.
                from = INTO; to = SPRITE_HEIGHT; head = 0; tail = 7;
                y = l.y(); length = height + 2;
            }
            for (int[] v : runs(from, to, head, tail, length)) {
                if (v[2] <= 0) continue;
                for (int[] u : runs(0, SPRITE_WIDTH, EDGE_COLUMNS, EDGE_COLUMNS, width)) {
                    if (u[2] <= 0) continue;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, SPRITE_WIDTH, SPRITE_HEIGHT, u[0], v[0],
                            x + u[1], y + v[1], u[2], v[2], color);
                }
            }
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            return button == Click.LEFT && click(edge, in.mouseX(), in.mouseY());
        }
    }
}
