package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * The container region's machinery (7.4.0; the public face is
 * {@code api.panel.ContainerRegion}). On a container screen it makes two panels: the
 * <b>container list</b>, one panel with its own frame holding every section any mod
 * declares in one {@link ScrollContainer}, and below it, {@link #PANEL_GAP} apart, the
 * <b>inventory panel</b>, its own frame around the player's inventory, which never scrolls.
 * The list grows first and scrolls only when there is no room left; while the two panels
 * fit, they are centred on the screen as one block ({@link #layout}).
 *
 * <h3>Built from what is there</h3>
 * Both panels are <b>background panels</b> ({@code Panel.Builder.background()}, 7.4.0): they
 * draw as the screen's own background, where vanilla draws the screen's image, so
 * everything else on the screen draws over them (vanilla's labels and slots, the region's
 * own slots, every other mod's widgets, every other panel), and they claim nothing, so
 * another mod's widget anywhere on them stays live and takes its clicks first. While the
 * region is up, vanilla's own image ({@link #replacesImage}) and title ({@link #ownsTitle})
 * are left out: the panels and the sections' own titles are in their place.
 * <ul>
 *   <li><b>The container list</b> holds every section; its slots draw right after it, in
 *       the background pass, clipped to its viewport, so other mods' widgets cover them. Its scrollbar is inside its frame, and
 *       scrolled content clips at the frame's own bottom edge.</li>
 *   <li><b>The inventory panel</b> is a frame and a slot frame under each of the player's
 *       inventory slots, where vanilla drew them; vanilla's own inventory slots and
 *       "Inventory" label stay live and where vanilla put them.</li>
 *   <li><b>The scroll area</b> is a {@link ScrollContainer} built {@code wheelLast()}: its
 *       children still get the wheel, but it never scrolls itself on one. This class
 *       scrolls it, after everyone else ({@link #wheel}).</li>
 *   <li><b>Slots</b> clip at its edges ({@code SlotClip}), and a row out of view is not
 *       rendered at any depth ({@code ChildDispatch.render}), so its library parks it.</li>
 * </ul>
 *
 * <h3>The screen's bounds are the two panels' union</h3>
 * Vanilla's {@code leftPos}, {@code topPos}, {@code imageWidth} and {@code imageHeight}
 * are set to the rectangle around both panels, scrollbar lane included, whenever that
 * rectangle changes (screen init, resize, a section opening or closing), never on a
 * scroll ({@link #applyBounds}). So everything that measures the screen sees both
 * panels: MenuKit's own menu-frame panels, other mods' buttons by the screen's edges,
 * a recipe viewer keeping clear of it, and vanilla's outside-click check, so a click in
 * the gap with an item held stays inside the screen and drops nothing. Moving the
 * screen's origin would move everything vanilla placed from it; the title is moved back by
 * as much, and the player's inventory slots and "Inventory" label by as much less the
 * distance the inventory panel went down, so they go with it. Before the screen initialises again (a resize), the bounds go back to vanilla's,
 * so vanilla centres its own image as always.
 *
 * <h3>Anchors</h3>
 * {@link #CONTAINERS} is the container list's rectangle and {@link #INVENTORY} the
 * inventory panel's, for an {@code AnchorPanelAdapter}: a mod's buttons go beside the
 * panel they act on.
 *
 * <h3>The wheel, last</h3>
 * Three places, one rule: the region scrolls on a turn nobody else used.
 * <ol>
 *   <li>{@code MouseHandler} HEAD ({@link ScreenPanelRegistry#dispatchCoveredScroll}): over a
 *       panel that claims the point, that panel's elements get the turn, and one they do not
 *       use is eaten (what the panel covers stays inert) and offered to the region straight
 *       away. The region's own panels claim nothing, so over them the turn goes on.</li>
 *   <li>The screen's own {@code mouseScrolled}, and every other mod's handler on it.</li>
 *   <li>Fabric's {@code afterMouseScroll}, in a phase ordered after the default one, so
 *       after every listener registered in it, whenever registered
 *       ({@link ScreenPanelRegistry#listenLast}): an unconsumed turn goes to the background
 *       panels' elements, then scrolls the region, wherever the mouse is.</li>
 * </ol>
 * A mod that uses the turn in step 2 or in its own {@code afterMouseScroll} listener (Mouse
 * Tweaks over any slot) answers "used", so the region does not move.
 *
 * <p>Render thread only. One container screen is open at a time; its state is reset when
 * another opens (a resize re-initialises the same screen and keeps it).
 */
@ApiStatus.Internal
public final class ContainerRegions {

    private ContainerRegions() {}

    // Layout, in GUI pixels. The container list's numbers are from its own top-left.

    /** Room left above the container list at the screen's top, once it has grown up to it. */
    public static final int TOP_MARGIN = 4;
    /** Room left below the inventory panel at the screen's bottom, once the list has pushed it down to it. */
    public static final int BOTTOM_MARGIN = 4;
    /** From the list's left edge to its content: vanilla's grid frame (slot x 8, frame x 7). */
    public static final int INSET_X = 7;
    /** From the list's top edge to the viewport's: its frame's top border. */
    public static final int VIEW_TOP = 4;
    /** From the viewport's bottom to the list's bottom edge: its frame's bottom border, where scrolled content clips. */
    public static final int VIEW_BOTTOM = 4;
    /** The first section's top inside the content, so its title sits where vanilla's does (6 down). */
    public static final int CONTENT_TOP = 2;
    /** Below the last section, inside the content: with {@link #VIEW_BOTTOM}, vanilla's 7 px from a grid to the frame's edge. */
    public static final int CONTENT_BOTTOM = 3;
    /** Between sections: vanilla's room from a grid to the "Inventory" label below it. */
    public static final int GAP = 3;
    /** Between the two panels: MenuKit's gap between panels stacked on a menu. */
    public static final int PANEL_GAP = RegionConstants.MENU_STACK_GAP;
    /** From the inventory panel's top edge to vanilla's "Inventory" label: where a container's title sits in its frame. */
    public static final int INVENTORY_LABEL_TOP = 6;
    /** A section's width before the scrollbar: nine slots. */
    public static final int CONTENT_WIDTH = 9 * 18;
    /** The scrollbar lane at the right: the gutter and the track. */
    public static final int LANE = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
    /** A slot's cell. */
    private static final int CELL = 18;

    // Carrying an item to an edge.

    /** Inside the viewport, how far from an edge the scroll starts, slow, reaching full speed at the edge. */
    public static final int EDGE_IN = 8;
    /** Past an edge, how far it keeps scrolling at full speed (below: short of the inventory's first row). */
    public static final int EDGE_PAST = 12;
    /** Full speed, in pixels a second. */
    public static final double EDGE_SPEED = 120;

    /** The container list's panel id. */
    public static final String PANEL_ID = "menukit:container_region";
    /** The inventory panel's panel id. */
    public static final String INVENTORY_PANEL_ID = "menukit:container_region_inventory";
    /** The after-init phase the bounds are set in: before the default one, so other mods' init listeners see them. */
    private static final Identifier FIRST = Identifier.fromNamespaceAndPath("menukit", "container_region_bounds");

    // Anchors: each panel's rectangle on the screen the region is on.

    /** The container list's rectangle, scrollbar lane included while it scrolls. */
    public static final Anchor CONTAINERS = Anchor.of(Identifier.fromNamespaceAndPath("menukit", "container_region"),
            screen -> {
                Geometry g = geometry(screen);
                return g == null ? null : g.list();
            });

    /** The inventory panel's rectangle. */
    public static final Anchor INVENTORY = Anchor.of(Identifier.fromNamespaceAndPath("menukit", "container_region_inventory"),
            screen -> {
                Geometry g = geometry(screen);
                return g == null ? null : g.inventory();
            });

    // Declarations

    /**
     * One declared section (ContainerRegion.section). {@code tint} is its background tint,
     * ARGB, read every frame; {@code 0} draws none. {@code texture} is the container
     * texture it stands for (vanilla's chest by default): its block of the list and its
     * slot frames take that texture's look under the active resource packs.
     */
    public record Section(String id, PanelElement content, ScreenMatcher matcher, int order, BooleanSupplier visibleWhen,
                          IntSupplier tint, Identifier texture) {

        /** A section in vanilla's chest's look. */
        public Section(String id, PanelElement content, ScreenMatcher matcher, int order, BooleanSupplier visibleWhen,
                       IntSupplier tint) {
            this(id, content, matcher, order, visibleWhen, tint, PackLook.CHEST);
        }
    }

    /** A section as placed: what it was declared as, and the element that holds it in the scroll area. */
    private record Placed(Section section, AbstractPanelElement holder) {}

    private static final List<Placed> SECTIONS = new CopyOnWriteArrayList<>();
    private static @Nullable Panel panel;
    private static @Nullable ScreenPanelAdapter adapter;
    /** The inventory panel: made with the first section, on the same screens. */
    private static @Nullable Panel inventoryPanel;
    /** The column the sections stack in, inside the scroll area. */
    private static @Nullable Flow column;
    /** The scroll area, for the checks. */
    private static @Nullable ScrollContainer scrollArea;

    /**
     * {@code ContainerRegion.Builder.declare}: adds a section and builds the container
     * list's panel again around every section so far. Declarations are open (checked by
     * the caller), so the previous adapter can be withdrawn; no screen is open yet.
     */
    public static synchronized void declare(Section section) {
        for (Placed p : SECTIONS) {
            if (p.section().id().equals(section.id())) {
                throw new IllegalStateException("ContainerRegion section '" + section.id() + "' is already declared");
            }
        }
        AbstractPanelElement holder = Flow.builder().vertical().gap(0, 0).add(new TitleInset(section)).add(section.content())
                .visibleWhen(() -> shownOnCurrentScreen(section)).build();
        SECTIONS.add(new Placed(section, holder));
        SECTIONS.sort(Comparator.comparingInt((Placed p) -> p.section().order()).thenComparing(p -> p.section().id()));

        if (adapter != null) adapter.unregister();
        List<PanelElement> holders = new ArrayList<>();
        for (Placed p : SECTIONS) holders.add(p.holder());
        // One column stacks the shown sections, GAP apart; a hidden one takes no room.
        column = Flow.builder().vertical().gap(0, GAP).addAll(holders).build();
        // The content: the column, CONTENT_TOP down, with CONTENT_BOTTOM under its last grid.
        PanelElement content = Flow.builder().vertical().gap(0, 0).at(0, CONTENT_TOP)
                .add(column).add(new Pad(CONTENT_BOTTOM)).build();
        scrollArea = ScrollContainer.builder().size(CONTENT_WIDTH + LANE, 1)
                .fillHeight().wheelLast()
                .state(() -> state.scroll, v -> state.scroll = v)
                .content(List.of(content)).build();
        // The viewport takes the panel's height below VIEW_TOP, less the frame's bottom border.
        PanelElement view = Flow.builder().vertical().gap(0, 0).at(INSET_X, VIEW_TOP)
                .add(scrollArea).add(new Pad(VIEW_BOTTOM)).build();
        // Both panels are the screen's background: under everything, claiming nothing.
        panel = Panel.builder(PANEL_ID).style(PanelStyle.NONE).background()
                .position(PanelPosition.pixel(ContainerRegions::origin))
                .add(new Backdrop()).add(view).add(new Tints())
                .build()
                .visibleWhen(() -> state.geometry != null);
        adapter = new ScreenPanelAdapter(panel, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
        if (inventoryPanel == null) {
            inventoryPanel = Panel.builder(INVENTORY_PANEL_ID).style(PanelStyle.NONE).background()
                    .position(PanelPosition.pixel(ContainerRegions::inventoryOrigin))
                    .add(new InventoryFrame())
                    .build()
                    .visibleWhen(() -> state.geometry != null && state.applied != null);
            new ScreenPanelAdapter(inventoryPanel, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
        }
    }

    /** Whether any section is declared for {@code screen}: the region's panel is put on it. */
    private static boolean targets(AbstractContainerScreen<?> screen) {
        for (Placed p : SECTIONS) if (p.section().matcher().matches(screen)) return true;
        return false;
    }

    /**
     * A section shows on the region's screen when it targets it, its own condition holds
     * and its content is visible. So content that hides itself takes its section with it:
     * a slot library's element for a group that is not on this menu, or whose slots are
     * all hidden, shows no section, with no condition of the section's own.
     */
    private static boolean shownOnCurrentScreen(Section s) {
        return state.screen.get() instanceof AbstractContainerScreen<?> acs
                && s.matcher().matches(acs) && s.visibleWhen().getAsBoolean() && s.content().isVisible();
    }

    /** The container list's panel, or {@code null} with no section declared. */
    public static @Nullable Panel panel() {
        return panel;
    }

    /** The inventory panel, or {@code null} with no section declared. */
    public static @Nullable Panel inventoryPanel() {
        return inventoryPanel;
    }

    /** The container list's scroll area, or {@code null} with no section declared. */
    public static @Nullable ScrollContainer scrollArea() {
        return scrollArea;
    }

    // Geometry: pure, so the checks can ask it.

    /**
     * Where the two panels are on a screen this frame.
     *
     * @param x          the container list's left edge (the screen's own {@code leftPos})
     * @param top        its top edge
     * @param width      its width: the screen's image width, and the scrollbar lane while it scrolls
     * @param height     its height, from {@code top} to {@code bottom}
     * @param bottom     its bottom edge, {@link #PANEL_GAP} above the inventory panel
     * @param viewTop    the viewport's top edge
     * @param viewHeight the viewport's height, down to {@link #VIEW_BOTTOM} above {@code bottom}
     * @param content    the height of everything in it
     * @param scrollable whether the content is taller than the viewport
     * @param inventory  the inventory panel: where vanilla put the inventory part, from
     *                   {@link #INVENTORY_LABEL_TOP} above its label to the image's bottom
     * @param bounds     the screen's bounds: the rectangle around both panels
     */
    public record Geometry(int x, int top, int width, int height, int bottom, int viewTop, int viewHeight,
                           int content, boolean scrollable, Reference inventory, Reference bounds) {

        /** The viewport's bottom edge: where scrolled content clips. */
        public int viewBottom() { return viewTop + viewHeight; }

        /** The container list's rectangle. */
        public Reference list() { return new Reference(x, top, width, height); }
    }

    /**
     * Grow first, then scroll, centred while it fits (Trev, 2026-10-20, after rounds 3 and
     * 5), from the screen's own bounds as vanilla laid them out. The container list starts
     * as the screen's own container part, its top at vanilla's {@code topPos}, and grows
     * with its content. The two panels are one block, and the room left free around it is
     * shared evenly above and below (to {@link #TOP_MARGIN} and {@link #BOTTOM_MARGIN}),
     * as far as each side has room:
     * <ul>
     *   <li>while the block fits centred, it is centred: each pixel the list grows moves
     *       its top up half a pixel and the inventory panel down half a pixel, rounded as
     *       vanilla rounds its own centring;</li>
     *   <li>where one side has less room than the other (a window too short for vanilla's
     *       own margins, a screen vanilla does not centre), that side's panel holds and the
     *       other moves until the block is centred: the list's top holds and the inventory
     *       panel moves down, or the other way round. A side with no room at all never
     *       moves, so the other takes all the growth up to its margin;</li>
     *   <li>only when both margins are reached does the list <b>scroll</b>.</li>
     * </ul>
     * Each pixel of growth moves each panel by at most one pixel and never back, so a box
     * opening never makes the block jump, at any window height. With vanilla's image
     * centred the block stays centred right up to both margins at once.
     * Never shorter than the screen's own container part, and neither panel ever moves the
     * other way from where vanilla put it: on a window with no room (a tiny one) the list
     * keeps vanilla's height and scrolls.
     *
     * @param vanilla         the screen's bounds as vanilla computed them
     * @param inventoryLabelY vanilla's "Inventory" label, from vanilla's {@code topPos}
     * @param content         the content's height
     * @param screenHeight    the screen's height, for the room below the inventory panel
     */
    public static Geometry layout(Reference vanilla, int inventoryLabelY, int content, int screenHeight) {
        int left = vanilla.leftPos(), imageWidth = vanilla.imageWidth();
        int imageBottom = vanilla.topPos() + vanilla.imageHeight();
        int vanillaInventoryTop = vanilla.topPos() + inventoryLabelY - INVENTORY_LABEL_TOP;
        int ownPart = vanillaInventoryTop - PANEL_GAP - vanilla.topPos();
        // The room each way, never negative: the inventory down to the bottom margin, the
        // list's top up to the top margin.
        int roomBelow = Math.max(0, screenHeight - BOTTOM_MARGIN - imageBottom);
        int roomAbove = Math.max(0, vanilla.topPos() - TOP_MARGIN);
        int grow = Math.max(0, Math.min(VIEW_TOP + content + VIEW_BOTTOM - ownPart, roomBelow + roomAbove));
        // The room still free once grown, split evenly: the list's top keeps the lower half
        // above it (floor, as vanilla centres), the inventory the rest below. Clamped so that
        // neither panel moves back, nor past its margin.
        int up = Math.max(0, Math.min(grow, roomAbove - Math.floorDiv(roomAbove + roomBelow - grow, 2)));
        int down = grow - up;
        int height = ownPart + grow;
        int top = vanilla.topPos() - up;
        int inventoryTop = vanillaInventoryTop + down;
        int bottom = inventoryTop - PANEL_GAP;
        int viewHeight = height - VIEW_TOP - VIEW_BOTTOM;
        boolean scrollable = content > viewHeight;
        int width = imageWidth + (scrollable ? LANE : 0);
        int inventoryBottom = imageBottom + down;
        return new Geometry(left, top, width, height, bottom, top + VIEW_TOP, viewHeight, content, scrollable,
                new Reference(left, inventoryTop, imageWidth, inventoryBottom - inventoryTop),
                new Reference(left, top, Math.max(width, imageWidth), inventoryBottom - top));
    }

    /**
     * How far vanilla's own placements move, from the screen's origin, when it goes from
     * {@code vanilla}'s to {@code bounds}'s: back by as much as the origin moved, so on screen
     * the title stays where vanilla put it ({@code [dx, dy]}); the inventory's slots and its
     * label also go down with the inventory panel, by as much as the bounds' bottom (the
     * panel's) moved down from vanilla's ({@code inventoryDy}). {@code [dx, dy, inventoryDy]}.
     */
    public static int[] counterShift(Reference vanilla, Reference bounds) {
        int dy = vanilla.topPos() - bounds.topPos();
        int down = (bounds.topPos() + bounds.imageHeight()) - (vanilla.topPos() + vanilla.imageHeight());
        return new int[]{vanilla.leftPos() - bounds.leftPos(), dy, dy + down};
    }

    /**
     * How fast carrying an item at {@code (mouseX, mouseY)} scrolls the region, in pixels a
     * second, negative upward: nothing over the region's middle, then faster nearer an edge
     * ({@link #EDGE_IN} inside it), full speed at the edge and {@link #EDGE_PAST} beyond it.
     * Below, that stops short of the inventory's first row. Nothing outside the region's
     * columns or when nothing scrolls.
     */
    public static double edgeSpeed(Geometry g, double mouseX, double mouseY) {
        if (!g.scrollable() || mouseX < g.x() || mouseX >= g.x() + g.width()) return 0;
        double intoTop = g.viewTop() + EDGE_IN - mouseY;              // > 0 near and past the top edge
        if (intoTop > 0 && intoTop <= EDGE_IN + EDGE_PAST) return -EDGE_SPEED * Math.min(1, intoTop / EDGE_IN);
        double intoBottom = mouseY - (g.viewBottom() - EDGE_IN);      // > 0 near and past the bottom edge
        if (intoBottom > 0 && intoBottom <= EDGE_IN + EDGE_PAST) return EDGE_SPEED * Math.min(1, intoBottom / EDGE_IN);
        return 0;
    }

    /**
     * The scroll that shows a slot whose cell starts {@code cellTop} pixels down the
     * content, whole, with the least movement: the scroll as it is when it shows already.
     */
    public static double revealScroll(double scroll, int cellTop, Geometry g) {
        return Scroller.intoView(scroll, cellTop, CELL, g.content(), g.viewHeight());
    }

    /**
     * Keeps what is on screen in place when sections above it change: the topmost section
     * that reached into the view before (and is still shown) moves the scroll by as much as
     * it moved. Tops of {@code -1} are hidden sections. At the top it stays at the top.
     */
    public static double keepInPlace(double scroll, int[] oldTop, int[] oldBottom, int[] newTop) {
        if (scroll <= 0) return scroll;
        for (int i = 0; i < oldTop.length; i++) {
            if (oldTop[i] < 0 || newTop[i] < 0 || oldBottom[i] <= scroll) continue;
            return scroll + (newTop[i] - oldTop[i]);
        }
        return scroll;
    }

    // The open screen's state

    /** The open screen's region: its scroll, this frame's geometry, what it learned. */
    private static final class State {
        WeakReference<Screen> screen = new WeakReference<>(null);
        double scroll;
        @Nullable Geometry geometry;
        /** The screen's own bounds and "Inventory" label as vanilla laid them out at its last init. */
        @Nullable Reference vanilla;
        int vanillaInventoryLabelY;
        /** The screen's height at its last init: the room the region can grow into. */
        int screenHeight;
        /** The union written as the screen's bounds, or {@code null} while they are vanilla's. */
        @Nullable Reference applied;
        /** How far vanilla's own placements are moved now ({@link #counterShift}): the title, and the inventory. */
        int shiftX, shiftY, inventoryShiftY;
        /** Each section's top and bottom in the content last frame, -1 while hidden. */
        int[] tops = new int[0], bottoms = new int[0];
        /** Where each slot the region has shown sits in the content: its cell's top. */
        final Map<Slot, Integer> cellTops = new IdentityHashMap<>();
        long lastFrame = -1;
        /** A quick move landed out of view: the slot to show, and how the search for it goes. */
        @Nullable Reveal reveal;
    }

    /**
     * Showing where a quick move landed. Known (the region has shown that slot before), it
     * is one scroll. Not yet known (a section below the fold never drawn), the region pages
     * down a viewport a frame until the slot is drawn, then shows it; if no page has it, it
     * goes back to where it was.
     */
    private record Reveal(Slot slot, int page, double from) {}

    private static final State state = new State();

    /** Called once from MenuKit's client init. */
    public static void init() {
        // Before a screen initialises again (a resize), its bounds go back to vanilla's, so
        // vanilla centres its own image from its own size.
        ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> acs && state.screen.get() == screen) restoreBounds(acs);
        });
        ScreenEvents.AFTER_INIT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE);
        ScreenEvents.AFTER_INIT.register(FIRST, (client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs) || adapter == null || !adapter.matches(acs)) return;
            if (state.screen.get() != screen) reset(screen);
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
            state.vanilla = new Reference(acc.mk$getLeftPos(), acc.mk$getTopPos(), acc.mk$getImageWidth(), acc.mk$getImageHeight());
            state.vanillaInventoryLabelY = acc.mk$getInventoryLabelY();
            state.screenHeight = screen.height;   // a resize initialises again, so the region re-runs
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> frame(acs, mx, my, false));
            ScreenEvents.remove(screen).register(s -> {
                if (state.screen.get() != s) return;
                restoreBounds(acs);   // the menu can outlive the screen
                reset(null);
            });
            // Lay out now, so every other mod's init listener and widget reads the union.
            frame(acs, -1, -1, true);
        });
    }

    /** For MenuKit's checks: the region as if {@code screen} had just opened with these vanilla bounds, {@code screenHeight} tall. */
    public static void openForChecks(@Nullable Screen screen, @Nullable Reference vanilla, int inventoryLabelY,
                                     int screenHeight) {
        reset(screen);
        state.vanilla = vanilla;
        state.vanillaInventoryLabelY = inventoryLabelY;
        state.screenHeight = screenHeight;
    }

    private static void reset(@Nullable Screen screen) {
        state.screen = new WeakReference<>(screen);
        state.scroll = 0;
        state.geometry = null;
        state.vanilla = null;
        state.applied = null;
        state.shiftX = state.shiftY = state.inventoryShiftY = 0;
        state.tops = new int[0];
        state.bottoms = new int[0];
        state.cellTops.clear();
        state.lastFrame = -1;
        state.reveal = null;
    }

    /** The region on {@code screen} this frame, or {@code null} when it is not there (no section shown). */
    public static @Nullable Geometry geometry(Screen screen) {
        return state.screen.get() == screen ? state.geometry : null;
    }

    /** The open region's scroll, in pixels. */
    public static double scroll() {
        return state.scroll;
    }

    /** Sets the open region's scroll (for checks and a mod's own buttons); clamped next frame. */
    public static void setScroll(double pixels) {
        state.scroll = pixels;
    }

    private static @Nullable ScreenOrigin origin() {
        Geometry g = state.geometry;
        return g == null ? null : new ScreenOrigin(g.x(), g.top());
    }

    private static @Nullable ScreenOrigin inventoryOrigin() {
        Geometry g = state.geometry;
        return g == null ? null : new ScreenOrigin(g.inventory().leftPos(), g.inventory().topPos());
    }

    /**
     * Before each frame of a screen the region is on (and once at its init): lay the
     * region out from the screen's vanilla bounds, and set the screen's bounds to the two
     * panels' union when that changed, or back to vanilla's when no section is shown.
     *
     * <h3>A layout change while the screen is open lays the screen out again</h3>
     * At init the bounds are simply written: every mod's init listener runs after, and
     * reads them. A change found on a later frame (a section opening or closing, the list
     * starting or stopping to scroll) re-runs the screen's layout the way a window resize
     * does ({@link #relayout}), so every other mod's widget placed at init against the
     * screen's bounds is placed again, through its own init hook, where it now belongs.
     * Only on such a change, never on a scroll (scrolling never moves the bounds), and at
     * most once a frame: however many sections changed since the last frame, this frame
     * sees one new layout and lays the screen out once.
     *
     * @param atInit whether this is the screen's own init (or a resize's), which lays out anyway
     */
    static void frame(AbstractContainerScreen<?> screen, int mouseX, int mouseY, boolean atInit) {
        Reference vanilla = state.vanilla;
        if (panel == null || vanilla == null || state.screen.get() != screen) return;
        Geometry g = step(vanilla, state.vanillaInventoryLabelY, !screen.getMenu().getCarried().isEmpty(),
                mouseX, mouseY, Util.getMillis());
        Reference want = g == null ? null : g.bounds();
        if (Objects.equals(want, state.applied)) return;
        if (!atInit) relayout(screen);
        // A screen whose re-init did not come back through the region (so the bounds are
        // still not these) gets them written here: never a layout every frame.
        if (Objects.equals(want, state.applied)) return;
        if (want == null) restoreBounds(screen);
        else applyBounds(screen, want);
    }

    /** How many times the region has laid a screen out again, for the checks. */
    private static int relayouts = 0;

    /** How many times the region has laid an open screen out again since the game started (for the checks). */
    public static int relayouts() {
        return relayouts;
    }

    /**
     * Lays {@code screen} out again exactly as a window resize does, at its own size:
     * {@code Screen.resize}, so Fabric's {@code BEFORE_INIT} and {@code AFTER_INIT} run
     * around it (the region puts vanilla's bounds back before, and the union after, in
     * its phase ahead of everyone else's), the screen's widgets are cleared and every mod
     * adds its own again from its init hook, and whatever a screen keeps across a resize
     * it keeps here too. What a resize keeps:
     * <ul>
     *   <li>the screen itself, so its menu, the item on the cursor, a drag across slots and
     *       the recipe book's state (its search text included) stay;</li>
     *   <li>the region's own state, keyed by the screen: the scroll, where each section
     *       was, a reveal under way. Sections' open state is their mods' own;</li>
     *   <li>what a screen carries over in its own {@code resize}: the anvil's name, the
     *       creative inventory's search and its scroll.</li>
     * </ul>
     * What a resize does not keep, this does not either: which widget had focus (vanilla
     * clears it and focuses the screen's first field again), and the text of a field
     * another mod creates anew at init and does not carry over itself.
     */
    public static void relayout(AbstractContainerScreen<?> screen) {
        relayouts++;
        screen.resize(screen.width, screen.height);
    }

    /**
     * One frame of the open region, from the screen's vanilla bounds and label:
     * stack the shown sections, work out where the panels are, keep the view in place if
     * sections above it changed, then carry on a reveal and scroll for an item carried to
     * an edge. {@code carrying} is whether an item is on the cursor.
     *
     * @return this frame's geometry, or {@code null} with no section shown
     */
    public static @Nullable Geometry step(Reference vanilla, int inventoryLabelY, boolean carrying,
                                          int mouseX, int mouseY, long now) {
        if (panel == null) return null;

        // Where each shown section sits in the content (the column stacks them).
        int n = SECTIONS.size();
        int[] tops = new int[n], bottoms = new int[n];
        int columnHeight = column.getHeight(), shown = 0;   // lays the column out first
        for (int i = 0; i < n; i++) {
            AbstractPanelElement holder = SECTIONS.get(i).holder();
            if (!holder.isVisible()) { tops[i] = bottoms[i] = -1; continue; }
            shown++;
            // The holder is placed in the content's coordinates, CONTENT_TOP included.
            tops[i] = holder.getChildY();
            bottoms[i] = tops[i] + holder.getHeight();
        }
        if (shown == 0) { state.geometry = null; return null; }
        int content = CONTENT_TOP + columnHeight + CONTENT_BOTTOM;

        if (state.tops.length == n) state.scroll = keepInPlace(state.scroll, state.tops, state.bottoms, tops);
        state.tops = tops;
        state.bottoms = bottoms;

        Geometry g = layout(vanilla, inventoryLabelY, content, state.screenHeight);
        state.geometry = g;
        if (panel.getPinnedWidth() != g.width() || panel.getPinnedHeight() != g.height()) panel.size(g.width(), g.height());

        double seconds = state.lastFrame < 0 ? 0 : Math.min(0.1, (now - state.lastFrame) / 1000.0);
        state.lastFrame = now;

        stepReveal(g);
        if (carrying) state.scroll += edgeSpeed(g, mouseX, mouseY) * seconds;
        state.scroll = Scroller.clamp(state.scroll, g.content(), g.viewHeight());
        return g;
    }

    // The screen's bounds

    /**
     * Writes {@code bounds} as the screen's own, and moves vanilla's own placements back by
     * as much as the origin moved ({@link #counterShift}): the title stays where vanilla put
     * it on screen, and the player's inventory slots and "Inventory" label go with the
     * inventory panel. Only on a change of layout.
     */
    private static void applyBounds(AbstractContainerScreen<?> screen, Reference bounds) {
        Reference vanilla = state.vanilla;
        if (vanilla == null) return;
        int[] shift = counterShift(vanilla, bounds);
        int dx = shift[0] - state.shiftX, dy = shift[1] - state.shiftY, invDy = shift[2] - state.inventoryShiftY;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        acc.mk$setLeftPos(bounds.leftPos());
        acc.mk$setTopPos(bounds.topPos());
        acc.mk$setImageWidth(bounds.imageWidth());
        acc.mk$setImageHeight(bounds.imageHeight());
        if (dx != 0 || invDy != 0) {
            for (Slot slot : screen.getMenu().slots) {
                if (!stays(slot)) continue;
                SlotAccessor pos = (SlotAccessor) slot;
                pos.mk$setX(slot.x + dx);
                pos.mk$setY(slot.y + invDy);
            }
            acc.mk$setInventoryLabelX(acc.mk$getInventoryLabelX() + dx);
            acc.mk$setInventoryLabelY(acc.mk$getInventoryLabelY() + invDy);
        }
        acc.mk$setTitleLabelX(acc.mk$getTitleLabelX() + dx);
        acc.mk$setTitleLabelY(acc.mk$getTitleLabelY() + dy);
        state.shiftX = shift[0];
        state.shiftY = shift[1];
        state.inventoryShiftY = shift[2];
        state.applied = bounds;
    }

    /** Back to vanilla's own bounds and placements, if they are not already. */
    private static void restoreBounds(AbstractContainerScreen<?> screen) {
        if (state.applied == null || state.vanilla == null) return;
        applyBounds(screen, state.vanilla);
        state.applied = null;
    }

    /**
     * Whether a slot is one of vanilla's that MenuKit places with the inventory panel: the
     * player's inventory, which never scrolls. A container's own slots are the region's (whoever put
     * the region on the screen presents them in a section, and their library places
     * them); a created slot is its library's.
     */
    private static boolean stays(Slot slot) {
        return slot.container instanceof Inventory && !SlotScreenDispatcher.fireIsCreated(slot);
    }

    /**
     * Whether the region's panels stand in for {@code screen}'s own image now: while its
     * bounds are the union, vanilla's container texture would be drawn around the union's
     * centre, so the background pass leaves it out ({@link ScreenBackground}). Everything
     * else in the screen's background still draws, every other mod's included.
     */
    public static boolean replacesImage(Screen screen) {
        return state.applied != null && state.geometry != null && state.screen.get() == screen;
    }

    /**
     * Whether the region titles {@code screen}'s containers now, so vanilla's own title is
     * left out: it would draw over the container list, where the first section's title is.
     */
    public static boolean ownsTitle(Screen screen) {
        return replacesImage(screen);
    }

    /**
     * At the head of the frame's contents ({@link ContainerScreenLayers#frameStart}), after
     * the background pass placed the region's slots: every slot the region placed this
     * frame, and where its cell sits in the content, is noted, so a quick move into it
     * later can be shown at once. Positions and scroll are this frame's.
     */
    static void placed(AbstractContainerScreen<?> screen) {
        Geometry g = state.geometry;
        if (panel == null || g == null || state.screen.get() != screen) return;
        int topPos = ((AbstractContainerScreenAccessor) screen).mk$getTopPos();
        for (Slot slot : PresentedSlots.below(panel)) state.cellTops.put(slot, cellTop(topPos, slot.y, g, state.scroll));
    }

    /** Where a slot placed at {@code slotY} (vanilla's, from {@code topPos}) sits in the content: its cell's top. */
    public static int cellTop(int topPos, int slotY, Geometry g, double scroll) {
        return (int) Math.round(topPos + slotY - 1 - g.viewTop() + scroll);
    }

    // The wheel

    /**
     * The region's turn of the wheel, last: scrolls it {@code Scroller}'s step a notch.
     *
     * @return whether it moved (the turn is used)
     */
    public static boolean wheel(Screen screen, double scrollY) {
        Geometry g = geometry(screen);
        if (g == null || !g.scrollable() || ScreenPanelRegistry.modalUpOn(screen)) return false;
        double before = Scroller.clamp(state.scroll, g.content(), g.viewHeight());
        state.scroll = Scroller.wheel(before, scrollY, g.content(), g.viewHeight());
        return state.scroll != before;
    }

    // Quick moves

    /** Whether a region is up on the open screen: the quick-move hook takes its snapshot only then. */
    public static boolean active() {
        Screen s = state.screen.get();
        return s != null && state.geometry != null && Minecraft.getInstance().gui.screen() == s;
    }

    /** Each slot's stack before a click (copies), for {@link #quickMoved}. */
    public static List<ItemStack> snapshot(AbstractContainerMenu menu) {
        List<ItemStack> out = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) out.add(slot.getItem().copy());
        return out;
    }

    /**
     * After a quick move the client has predicted: the first slot that gained items, if it
     * is the region's and not in view, is shown. A move into the inventory gains its slots,
     * which are not the region's, so nothing scrolls.
     */
    public static void quickMoved(AbstractContainerMenu menu, List<ItemStack> before) {
        Screen screen = state.screen.get();
        Geometry g = state.geometry;
        if (!(screen instanceof AbstractContainerScreen<?> acs) || g == null || acs.getMenu() != menu) return;
        for (int i = 0; i < menu.slots.size() && i < before.size(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack was = before.get(i), now = slot.getItem();
            boolean gained = !now.isEmpty() && (was.isEmpty() || !ItemStack.isSameItemSameComponents(was, now)
                    || now.getCount() > was.getCount());
            if (!gained) continue;
            if (!regionsOwn(acs, slot)) return;         // landed in the inventory (or elsewhere in view)
            Integer cell = state.cellTops.get(slot);
            if (cell != null && revealScroll(state.scroll, cell, g) == state.scroll && PresentedSlots.lastPanel(slot) == panel
                    && onScreen(acs, slot)) return;     // landed in view
            state.reveal = new Reveal(slot, cell != null ? -1 : 0, state.scroll);
            return;
        }
    }

    /**
     * Whether a slot is the region's: one it showed, or one no panel ever showed that is not
     * on screen either (a section below the fold, never drawn; its library keeps it parked).
     * The player's inventory is on screen where vanilla put it, so it is never the region's.
     */
    private static boolean regionsOwn(AbstractContainerScreen<?> screen, Slot slot) {
        Panel last = PresentedSlots.lastPanel(slot);
        if (last != null) return last == panel;
        return !onScreen(screen, slot);
    }

    private static boolean onScreen(AbstractContainerScreen<?> screen, Slot slot) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int x = acc.mk$getLeftPos() + slot.x, y = acc.mk$getTopPos() + slot.y;
        return x > -CELL && y > -CELL && x < screen.width && y < screen.height;
    }

    /** One frame of a reveal: shown when its slot's place is known, else the next page. */
    private static void stepReveal(Geometry g) {
        Reveal r = state.reveal;
        if (r == null) return;
        Integer cell = state.cellTops.get(r.slot());
        if (cell != null) {
            state.scroll = revealScroll(state.scroll, cell, g);
            state.reveal = null;
            return;
        }
        int pageTop = r.page() * Math.max(1, g.viewHeight());
        if (r.page() < 0 || pageTop >= g.content()) {   // looked everywhere: not the region's after all
            state.scroll = r.from();
            state.reveal = null;
            return;
        }
        state.scroll = pageTop;
        state.reveal = new Reveal(r.slot(), r.page() + 1, r.from());
    }

    // The frame

    // Following resource packs (spec "Panel-look panels follow resource packs")

    /**
     * The container texture {@code screen} draws its own image from, whose look the two
     * panels take (spec part 6): the inventory panel the inventory half, as vanilla does,
     * and the container list the container half. A shulker box's screen is the shulker
     * box's; any other the chest's.
     */
    static Identifier screenTexture(@Nullable Screen screen) {
        return screen instanceof ShulkerBoxScreen ? PackLook.SHULKER_BOX : PackLook.CHEST;
    }

    /** The container list's look: its screen's container texture, the container half. */
    private static PackLook.Look listLook() {
        return PackLook.look(screenTexture(state.screen.get()), PackLook.Part.CONTAINER);
    }

    /** Section {@code s}'s look: the container texture it stands for. */
    private static PackLook.Look sectionLook(Section s) {
        return PackLook.look(s.texture(), PackLook.Part.CONTAINER);
    }

    /**
     * Whether section {@code s} is drawn in greys for its tint to colour
     * ({@link PackLook.Tint#RECOLOUR}): it is tinted and its art is coloured.
     */
    private static boolean recoloured(Section s) {
        return PackLook.Mockup.tint == PackLook.Tint.RECOLOUR && s.tint().getAsInt() != 0 && !sectionLook(s).grey();
    }

    /** The look section {@code s}'s block and slot frames are drawn in: its own, or in greys under a recolour. */
    private static PackLook.Look drawnLook(Section s) {
        return recoloured(s) ? sectionLook(s).greyed() : sectionLook(s);
    }

    /**
     * The tint drawn over section {@code s} this frame, ARGB, {@code 0} for none: over grey
     * art, and over coloured art drawn in greys; left out over the pack's own colour, unless
     * a mockup says otherwise ({@code ALWAYS}).
     */
    private static int drawnTint(Section s) {
        int argb = s.tint().getAsInt();
        if (argb != 0 && PackLook.Mockup.tint != PackLook.Tint.ALWAYS && !drawnLook(s).grey()) return 0;   // mockup: ALWAYS
        return argb;
    }

    /** Whether section {@code s} draws a block of its own over the list: its own texture's fill, or its art in greys. */
    private static boolean drawsBlock(Section s, PackLook.Look list) {
        return !s.texture().equals(list.texture()) || recoloured(s);
    }

    /**
     * Whether section {@code s} shows as a block of its own on the list (a tint, or a fill
     * unlike the list's), so its title takes vanilla's room above it ({@link #TITLE_INSET}).
     */
    private static boolean ownsBlock(Section s) {
        if (drawnTint(s) != 0) return true;
        PackLook.Look list = listLook();
        return drawsBlock(s, list) && drawnLook(s).fill() != list.fill();
    }

    /**
     * How far a section's title goes down when the section is a block of its own: with the
     * block's top {@link #TINT_TOP} above the section, its title sits 3 px inside the block,
     * as vanilla's title sits 3 px inside its art's fill (fill from row 3, title at 6).
     */
    public static final int TITLE_INSET = 2;

    /** The room above a section's title while it is a block of its own ({@link #ownsBlock}); none otherwise. */
    private record TitleInset(Section section) implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { return 0; }
        @Override public int getHeight() { return state.screen.get() != null && ownsBlock(section) ? TITLE_INSET : 0; }
        @Override public void render(RenderContext ctx) {}
    }

    /**
     * Which look a slot frame drawn in one of the region's panels takes: in the inventory
     * panel, the inventory half's; in the container list, the look of the section it is in
     * (by where it is drawn), else the list's. {@code null} for any other panel.
     */
    public static PackLook.@Nullable SlotLooks slotLooks(Panel p) {
        if (p == inventoryPanel && inventoryPanel != null) {
            PackLook.Look look = PackLook.look(screenTexture(state.screen.get()), PackLook.Part.INVENTORY);
            return (x, y) -> look;
        }
        if (p != panel || panel == null) return null;
        Geometry g = state.geometry;
        PackLook.Look list = listLook();
        if (g == null || state.tops.length != SECTIONS.size()) return (x, y) -> list;
        int[] tops = state.tops, bottoms = state.bottoms;
        double scroll = state.scroll;
        return (x, y) -> {
            for (int i = 0; i < tops.length; i++) {
                int[] r = tintRect(g, tops[i], bottoms[i], scroll);
                if (r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) return drawnLook(SECTIONS.get(i).section());
            }
            return list;
        };
    }

    /**
     * The container list's frame, behind every section: a panel surface in the look of the
     * screen's container texture under the active resource packs (MenuKit's raised panel
     * with vanilla's own), the scrollbar lane inside it while it scrolls. A section that
     * stands for another texture (a shulker box in a chest's list) gets a block of its own
     * texture's fill over its part, so a pack's purple shulker art shows as purple there.
     * A tinted section over coloured art gets its block in greys, for the tint to colour
     * ({@link PackLook.Tint#RECOLOUR}).
     */
    private static final class Backdrop implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Geometry g = state.geometry; return g == null ? 0 : g.width(); }
        @Override public int getHeight() { Geometry g = state.geometry; return g == null ? 0 : g.height(); }

        @Override
        public void render(RenderContext ctx) {
            Geometry g = state.geometry;
            if (g == null) return;
            PackLook.Look list = listLook();
            PanelRendering.renderPanelSurface(ctx.graphics(), ctx.originX(), ctx.originY(), g.width(), g.height(), list);
            if (state.tops.length != SECTIONS.size()) return;
            int dx = ctx.originX() - g.x(), dy = ctx.originY() - g.top();
            boolean clipped = false;
            for (int i = 0; i < SECTIONS.size(); i++) {
                Section s = SECTIONS.get(i).section();
                if (!drawsBlock(s, list)) continue;
                int[] r = tintRect(g, state.tops[i], state.bottoms[i], state.scroll);
                if (r == null) continue;
                if (!clipped) {
                    ctx.graphics().enableScissor(g.x() + dx, g.viewTop() + dy, g.x() + g.width() + dx, g.viewBottom() + dy);
                    clipped = true;
                }
                PackLook.drawFill(ctx.graphics(), r[0] + dx, r[1] + dy, r[2] - r[0], r[3] - r[1], drawnLook(s));
            }
            if (clipped) ctx.graphics().disableScissor();
        }
    }

    /** How far a section's tint reaches past its content: to the frame's inner edge at the sides, and into the gaps. */
    public static final int TINT_SIDE = 3, TINT_TOP = 1, TINT_BOTTOM = 1;

    /**
     * Where section {@code i}'s tint goes on screen this frame, {@code [x0, y0, x1, y1]}, or
     * {@code null} while it is hidden: its place in the content, moved by the scroll, from
     * {@link #TINT_SIDE} left of the content edge to as far right of the nine-slot width
     * (never over the scrollbar lane), {@link #TINT_TOP} above and {@link #TINT_BOTTOM} below
     * it, so two tinted sections a {@link #GAP} apart keep a line of the panel between them.
     */
    public static int @Nullable [] tintRect(Geometry g, int top, int bottom, double scroll) {
        if (top < 0) return null;
        int x = g.x() + INSET_X, y = g.viewTop() - (int) scroll;
        return new int[]{x - TINT_SIDE, y + top - TINT_TOP, x + CONTENT_WIDTH + TINT_SIDE, y + bottom + TINT_BOTTOM};
    }

    /**
     * Each section's tint, over its part of the container list: after the sections drew
     * (their titles and slot frames take the colour) and before the region's slots draw
     * their items (which keep their own), cut at the viewport's edges like the content.
     * Over grey art, and over coloured art drawn in greys ({@link PackLook.Tint#RECOLOUR}):
     * the dye's colour in the pack's own shading.
     */
    private static final class Tints implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { return 0; }
        @Override public int getHeight() { return 0; }

        @Override
        public void render(RenderContext ctx) {
            Geometry g = state.geometry;
            if (g == null || state.tops.length != SECTIONS.size()) return;
            // The panel's origin is the list's top-left; the rectangles are from the screen's.
            int dx = ctx.originX() - g.x(), dy = ctx.originY() - g.top();
            boolean clipped = false;
            for (int i = 0; i < SECTIONS.size(); i++) {
                int argb = drawnTint(SECTIONS.get(i).section());
                int[] r = argb == 0 ? null : tintRect(g, state.tops[i], state.bottoms[i], state.scroll);
                if (r == null) continue;
                if (!clipped) {
                    ctx.graphics().enableScissor(g.x() + dx, g.viewTop() + dy, g.x() + g.width() + dx, g.viewBottom() + dy);
                    clipped = true;
                }
                ctx.graphics().fill(r[0] + dx, r[1] + dy, r[2] + dx, r[3] + dy, argb);
            }
            if (clipped) ctx.graphics().disableScissor();
        }
    }

    /**
     * The inventory panel's look: a panel surface in the look of the inventory half of the
     * screen's container texture under the active resource packs (MenuKit's raised panel
     * with vanilla's own), and a slot frame under each of the player's inventory slots,
     * where vanilla's image had them, in the same look. The slots themselves are vanilla's,
     * drawn over it in vanilla's pass.
     */
    private static final class InventoryFrame implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Geometry g = state.geometry; return g == null ? 0 : g.inventory().imageWidth(); }
        @Override public int getHeight() { Geometry g = state.geometry; return g == null ? 0 : g.inventory().imageHeight(); }

        @Override
        public void render(RenderContext ctx) {
            Geometry g = state.geometry;
            if (g == null || !(state.screen.get() instanceof AbstractContainerScreen<?> acs)) return;
            Reference inv = g.inventory();
            PackLook.Look look = PackLook.look(screenTexture(acs), PackLook.Part.INVENTORY);
            PanelRendering.renderPanelSurface(ctx.graphics(), ctx.originX(), ctx.originY(), inv.imageWidth(), inv.imageHeight(),
                    look);
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
            for (Slot slot : acs.getMenu().slots) {
                if (stays(slot) && slot.isActive()) {
                    PackLook.drawSlot(ctx.graphics(), acc.mk$getLeftPos() + slot.x - 1, acc.mk$getTopPos() + slot.y - 1, look);
                }
            }
        }
    }

    /** Fixed room in a vertical stack: a frame's bottom border, the content's bottom margin. */
    private record Pad(int height) implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { return 0; }
        @Override public int getHeight() { return height; }
        @Override public void render(RenderContext ctx) {}
    }
}
