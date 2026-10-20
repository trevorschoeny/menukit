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
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import java.util.function.BooleanSupplier;

/**
 * The container region's machinery (7.4.0; the public face is
 * {@code api.panel.ContainerRegion}). On a container screen it makes two panels: the
 * <b>container list</b>, one panel with its own frame holding every section any mod
 * declares in one {@link ScrollContainer}, and below it, {@link #PANEL_GAP} apart, the
 * <b>inventory panel</b>, its own frame around the player's inventory, which never moves.
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
 *   <li><b>The container list</b> holds every section; its slots draw in vanilla's slot
 *       pass, over it, clipped to its viewport. Its scrollbar is inside its frame, and
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
 * screen's origin would move everything vanilla placed from it; the player's inventory
 * slots and both labels are moved back by as much, so on screen nothing vanilla placed
 * moves. Before the screen initialises again (a resize), the bounds go back to vanilla's,
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

    /** Room left above the container list at the screen's top. */
    public static final int TOP_MARGIN = 4;
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

    /** One declared section (ContainerRegion.section). */
    public record Section(String id, PanelElement content, ScreenMatcher matcher, int order, BooleanSupplier visibleWhen) {}

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
        AbstractPanelElement holder = Flow.builder().vertical().gap(0, 0).add(section.content())
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
                .add(new Backdrop()).add(view)
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

    /** A section shows on the region's screen when it targets it and its own condition holds. */
    private static boolean shownOnCurrentScreen(Section s) {
        return state.screen.get() instanceof AbstractContainerScreen<?> acs
                && s.matcher().matches(acs) && s.visibleWhen().getAsBoolean();
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
     * Grow first, then scroll, from the screen's own bounds as vanilla laid them out: the
     * inventory panel stays where vanilla put the inventory part; the container list sits
     * {@link #PANEL_GAP} above it, as tall as its content, up to the screen's top margin;
     * never shorter than the screen's own container part (from vanilla's {@code topPos}
     * down), even when that leaves no margin (a tiny window), in which case it scrolls.
     *
     * @param vanilla         the screen's bounds as vanilla computed them
     * @param inventoryLabelY vanilla's "Inventory" label, from vanilla's {@code topPos}
     * @param content         the content's height
     */
    public static Geometry layout(Reference vanilla, int inventoryLabelY, int content) {
        int left = vanilla.leftPos(), imageWidth = vanilla.imageWidth();
        int imageBottom = vanilla.topPos() + vanilla.imageHeight();
        int inventoryTop = vanilla.topPos() + inventoryLabelY - INVENTORY_LABEL_TOP;
        int bottom = inventoryTop - PANEL_GAP;
        int ownPart = bottom - vanilla.topPos();
        int height = Math.max(ownPart, Math.min(VIEW_TOP + content + VIEW_BOTTOM, bottom - TOP_MARGIN));
        int viewHeight = height - VIEW_TOP - VIEW_BOTTOM;
        boolean scrollable = content > viewHeight;
        int width = imageWidth + (scrollable ? LANE : 0);
        int top = bottom - height;
        return new Geometry(left, top, width, height, bottom, top + VIEW_TOP, viewHeight, content, scrollable,
                new Reference(left, inventoryTop, imageWidth, imageBottom - inventoryTop),
                new Reference(left, top, Math.max(width, imageWidth), imageBottom - top));
    }

    /**
     * How far vanilla's own placements (the inventory's slots, the labels) move when the
     * screen's origin goes from {@code vanilla}'s to {@code bounds}'s: back by as much, so
     * on screen they stay where vanilla put them. {@code [dx, dy]}.
     */
    public static int[] counterShift(Reference vanilla, Reference bounds) {
        return new int[]{vanilla.leftPos() - bounds.leftPos(), vanilla.topPos() - bounds.topPos()};
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
        /** The union written as the screen's bounds, or {@code null} while they are vanilla's. */
        @Nullable Reference applied;
        /** How far vanilla's own placements are moved now ({@link #counterShift}). */
        int shiftX, shiftY;
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
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> frame(acs, mx, my));
            ScreenEvents.remove(screen).register(s -> {
                if (state.screen.get() != s) return;
                restoreBounds(acs);   // the menu can outlive the screen
                reset(null);
            });
            // Lay out now, so every other mod's init listener and widget reads the union.
            frame(acs, -1, -1);
        });
    }

    /** For MenuKit's checks: the region as if {@code screen} had just opened with these vanilla bounds. */
    public static void openForChecks(@Nullable Screen screen, @Nullable Reference vanilla, int inventoryLabelY) {
        reset(screen);
        state.vanilla = vanilla;
        state.vanillaInventoryLabelY = inventoryLabelY;
    }

    private static void reset(@Nullable Screen screen) {
        state.screen = new WeakReference<>(screen);
        state.scroll = 0;
        state.geometry = null;
        state.vanilla = null;
        state.applied = null;
        state.shiftX = state.shiftY = 0;
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
     */
    static void frame(AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        Reference vanilla = state.vanilla;
        if (panel == null || vanilla == null || state.screen.get() != screen) return;
        Geometry g = step(vanilla, state.vanillaInventoryLabelY, !screen.getMenu().getCarried().isEmpty(),
                mouseX, mouseY, Util.getMillis());
        if (g == null) restoreBounds(screen);
        else if (!g.bounds().equals(state.applied)) applyBounds(screen, g.bounds());
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
            tops[i] = CONTENT_TOP + holder.getChildY();
            bottoms[i] = tops[i] + holder.getHeight();
        }
        if (shown == 0) { state.geometry = null; return null; }
        int content = CONTENT_TOP + columnHeight + CONTENT_BOTTOM;

        if (state.tops.length == n) state.scroll = keepInPlace(state.scroll, state.tops, state.bottoms, tops);
        state.tops = tops;
        state.bottoms = bottoms;

        Geometry g = layout(vanilla, inventoryLabelY, content);
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
     * Writes {@code bounds} as the screen's own, and moves vanilla's own placements (the
     * player's inventory slots, the title and "Inventory" labels) back by as much as the
     * origin moved, so none of them moves on screen. Only on a change of layout.
     */
    private static void applyBounds(AbstractContainerScreen<?> screen, Reference bounds) {
        Reference vanilla = state.vanilla;
        if (vanilla == null) return;
        int[] shift = counterShift(vanilla, bounds);
        int dx = shift[0] - state.shiftX, dy = shift[1] - state.shiftY;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        acc.mk$setLeftPos(bounds.leftPos());
        acc.mk$setTopPos(bounds.topPos());
        acc.mk$setImageWidth(bounds.imageWidth());
        acc.mk$setImageHeight(bounds.imageHeight());
        if (dx != 0 || dy != 0) {
            for (Slot slot : screen.getMenu().slots) {
                if (!stays(slot)) continue;
                SlotAccessor pos = (SlotAccessor) slot;
                pos.mk$setX(slot.x + dx);
                pos.mk$setY(slot.y + dy);
            }
            acc.mk$setTitleLabelX(acc.mk$getTitleLabelX() + dx);
            acc.mk$setTitleLabelY(acc.mk$getTitleLabelY() + dy);
            acc.mk$setInventoryLabelX(acc.mk$getInventoryLabelX() + dx);
            acc.mk$setInventoryLabelY(acc.mk$getInventoryLabelY() + dy);
        }
        state.shiftX = shift[0];
        state.shiftY = shift[1];
        state.applied = bounds;
    }

    /** Back to vanilla's own bounds and placements, if they are not already. */
    private static void restoreBounds(AbstractContainerScreen<?> screen) {
        if (state.applied == null || state.vanilla == null) return;
        applyBounds(screen, state.vanilla);
        state.applied = null;
    }

    /**
     * Whether a slot is one of vanilla's that stays where vanilla put it: the player's
     * inventory, which never moves. A container's own slots are the region's (whoever put
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

    /**
     * The container list's frame, behind every section: vanilla's raised panel, the
     * scrollbar lane inside it while it scrolls.
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
            PanelRendering.renderPanel(ctx.graphics(), ctx.originX(), ctx.originY(), g.width(), g.height(), PanelStyle.RAISED);
        }
    }

    /**
     * The inventory panel's look: vanilla's raised panel and a slot frame under each of
     * the player's inventory slots, where vanilla's image had them. The slots themselves
     * are vanilla's, drawn over it in vanilla's pass.
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
            PanelRendering.renderPanel(ctx.graphics(), ctx.originX(), ctx.originY(), inv.imageWidth(), inv.imageHeight(),
                    PanelStyle.RAISED);
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
            for (Slot slot : acs.getMenu().slots) {
                if (stays(slot) && slot.isActive()) {
                    PanelRendering.renderSlotBackground(ctx.graphics(), acc.mk$getLeftPos() + slot.x - 1,
                            acc.mk$getTopPos() + slot.y - 1);
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
