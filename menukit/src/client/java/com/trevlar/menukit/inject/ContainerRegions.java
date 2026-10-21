package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.core.OptiGuiRules;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The container region's machinery (7.4.0; the public face is
 * {@code api.panel.ContainerRegion}). On a container screen it makes two panels: the
 * <b>container list</b>, one panel with its own frame holding every section any mod
 * declares in one {@link ScrollContainer}, and below it, {@link RegionLayout#PANEL_GAP}
 * apart, the <b>inventory panel</b>, its own frame around the player's inventory, which
 * never scrolls. The list grows first and scrolls only when there is no room left; while the
 * two panels fit, they are centred on the screen as one block ({@link RegionLayout#layout}).
 * This class keeps the declarations, the open screen's state, its lifecycle and bounds;
 * {@link RegionLayout} is where the panels go, {@link RegionLooks} how they follow
 * resource packs, {@link RegionTitles} what the sections are called, and
 * {@link RegionReveal} the shift-click reveal.
 *
 * <h3>Built from what is there</h3>
 * Both panels are <b>background panels</b> ({@code Panel.Builder.background()}, 7.4.0): they
 * draw as the screen's own background, where vanilla draws the screen's image, so
 * everything else on the screen draws over them (vanilla's labels and slots, the region's
 * own slots, every other mod's widgets, every other panel), and they claim nothing, so
 * another mod's widget anywhere on them stays live and takes its clicks first. While the
 * region is up, vanilla's own image and title are left out ({@link #replacesImage}): the
 * panels and the sections' own titles are in their place.
 * <ul>
 *   <li><b>The container list</b> holds every section; its slots draw right after it, in
 *       the background pass, clipped to its viewport, so other mods' widgets cover them. Its
 *       scrollbar is inside its frame, and scrolled content clips at the frame's own bottom
 *       edge.</li>
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
 * <h3>Once a frame</h3>
 * Before each frame ({@link #step}) the region works out, once, everything its elements
 * read while they lay out and draw ({@link Frame}): each section's look and the list's, which
 * sections are blocks of their own, the titles the sections show and how far the first moves
 * clear of the list's corner. Then the column lays out, and the geometry follows.
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
 * distance the inventory panel went down, so they go with it. Before the screen
 * initialises again (a resize), the bounds go back to vanilla's, so vanilla centres its own
 * image as always.
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
     * One declared section ({@code ContainerRegion.section}). {@code texture} is the
     * container texture it stands for (vanilla's chest by default): its slice of the list and
     * its slot frames take that texture's look under the active resource packs, or with
     * OptiGUI the texture its rules give the section's {@code source} (the open screen's,
     * without one). {@code title}, if any, is drawn above its content; {@code name} stands in
     * for a blank title while other sections show ({@link RegionTitles#shownTitles}).
     */
    public record Declared(String id, PanelElement content, ScreenMatcher matcher, int order, BooleanSupplier visibleWhen,
                           Identifier texture, @Nullable Supplier<Component> title, @Nullable Supplier<Component> name,
                           @Nullable Supplier<ContainerRegion.@Nullable Source> source) {}

    /** A section as placed: what it was declared as, and the element that holds it in the scroll area. */
    private record Placed(Declared section, AbstractPanelElement holder) {}

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
    public static synchronized void declare(Declared section) {
        for (Placed p : SECTIONS) {
            if (p.section().id().equals(section.id())) {
                throw new IllegalStateException("ContainerRegion section '" + section.id() + "' is already declared");
            }
        }
        // A titled section: its title where vanilla draws a container's, the content 11 px
        // below the title's top (vanilla's title at y 6, its grid at 17). The title moves
        // right clear of the list's corner when its section is the first shown, and the
        // section moves down when it is a block of its own (this frame's Frame says which).
        PanelElement body = section.title() == null ? section.content() : Flow.builder().vertical().gap(0, 2)
                .add(Flow.builder().gap(0, 0).add(new Spacer(() -> titleIndent(section), () -> 0))
                        .add(TextLabel.builder().at(1, 0).text(() -> shownTitle(section)).build()).build())
                .add(section.content()).build();
        AbstractPanelElement holder = Flow.builder().vertical().gap(0, 0)
                .add(new Spacer(() -> 0, () -> ownsBlock(section) ? RegionLayout.TITLE_INSET : 0)).add(body)
                .visibleWhen(() -> shownOnCurrentScreen(section)).build();
        SECTIONS.add(new Placed(section, holder));
        SECTIONS.sort(Comparator.comparingInt((Placed p) -> p.section().order()).thenComparing(p -> p.section().id()));

        if (adapter != null) adapter.unregister();
        List<PanelElement> holders = new ArrayList<>();
        for (Placed p : SECTIONS) holders.add(p.holder());
        // One column stacks the shown sections, GAP apart; a hidden one takes no room.
        column = Flow.builder().vertical().gap(0, RegionLayout.GAP).addAll(holders).build();
        // The content: the column, CONTENT_TOP down, with CONTENT_BOTTOM under its last grid.
        PanelElement content = Flow.builder().vertical().gap(0, 0).at(0, RegionLayout.CONTENT_TOP)
                .add(column).add(new Spacer(() -> 0, () -> RegionLayout.CONTENT_BOTTOM)).build();
        scrollArea = ScrollContainer.builder().size(RegionLayout.CONTENT_WIDTH + RegionLayout.LANE, 1)
                .fillHeight().wheelLast()
                .state(() -> state.scroll, v -> state.scroll = v)
                .content(List.of(content)).build();
        // The viewport takes the panel's height below VIEW_TOP, less the frame's bottom border.
        PanelElement view = Flow.builder().vertical().gap(0, 0).at(RegionLayout.INSET_X, RegionLayout.VIEW_TOP)
                .add(scrollArea).add(new Spacer(() -> 0, () -> RegionLayout.VIEW_BOTTOM)).build();
        // Both panels are the screen's background: under everything, claiming nothing. Each
        // says what its surface is at a point, for its slot frames and text (RegionLooks).
        panel = Panel.builder(PANEL_ID).style(PanelStyle.NONE).background()
                .position(PanelPosition.pixel(ContainerRegions::origin))
                .add(new RegionLooks.Backdrop()).add(view)
                .build()
                .visibleWhen(() -> state.geometry != null)
                .surfaceLooks(RegionLooks::listSurfaceAt);
        adapter = new ScreenPanelAdapter(panel, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
        if (inventoryPanel == null) {
            inventoryPanel = Panel.builder(INVENTORY_PANEL_ID).style(PanelStyle.NONE).background()
                    .position(PanelPosition.pixel(ContainerRegions::inventoryOrigin))
                    .add(new RegionLooks.InventoryFrame())
                    .build()
                    .visibleWhen(() -> state.geometry != null && state.applied != null)
                    .surfaceLooks(RegionLooks::inventorySurfaceAt);
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
    private static boolean shownOnCurrentScreen(Declared s) {
        return state.screen.get() instanceof AbstractContainerScreen<?> acs
                && s.matcher().matches(acs) && s.visibleWhen().getAsBoolean() && s.content().isVisible();
    }

    /** The container list's panel, or {@code null} with no section declared. */
    static @Nullable Panel listPanel() {
        return panel;
    }

    /** The container list's scroll area, or {@code null} with no section declared. */
    @VisibleForTesting
    public static @Nullable ScrollContainer scrollArea() {
        return scrollArea;
    }

    // The open screen's state

    /** The open screen's region: its scroll, this frame's geometry and looks, what it learned. */
    static final class State {
        WeakReference<Screen> screen = new WeakReference<>(null);
        double scroll;
        @Nullable Geometry geometry;
        /** What the region's elements read this frame ({@link #step}). */
        @Nullable Frame frame;
        /** The screen's own bounds and "Inventory" label as vanilla laid them out at its last init. */
        @Nullable Reference vanilla;
        int vanillaInventoryLabelY;
        /** The screen's height at its last init: the room the region can grow into. */
        int screenHeight;
        /** The union written as the screen's bounds, or {@code null} while they are vanilla's. */
        @Nullable Reference applied;
        /** How far vanilla's own placements are moved now ({@link RegionLayout#counterShift}): the title, and the inventory. */
        int shiftX, shiftY, inventoryShiftY;
        /** Each section's top and bottom in the content this frame, -1 while hidden. */
        int[] tops = new int[0], bottoms = new int[0];
        long lastFrame = -1;
        /** The container the screen was opened on ({@link #openedContainer}): its own sections' source. */
        ContainerRegion.@Nullable Source opened;
    }

    static final State state = new State();

    /**
     * What the region's elements read while they lay out and draw this frame, worked out once
     * before it ({@link #step}), per section in stacking order.
     *
     * @param looks     each section's look ({@link RegionLooks#sectionLooks}), {@code null} while hidden
     * @param list      the list's own look, the first shown section's ({@link RegionLooks#listLook})
     * @param ownsBlock whether each is a block of its own ({@link RegionLooks#ownsBlock})
     * @param inventory the inventory panel's look
     * @param titles    the title each shown, titled section draws ({@link RegionTitles#shownTitles}), else {@code null}
     * @param first     the first shown section
     * @param indent    how far right the first shown section's title goes ({@link RegionTitles#indent}); 0 with no title
     */
    record Frame(PackLook.@Nullable Look[] looks, PackLook.Look list, boolean[] ownsBlock, PackLook.Look inventory,
                 @Nullable Component[] titles, int first, int indent) {}

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
    @VisibleForTesting
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
        state.frame = null;
        state.vanilla = null;
        state.applied = null;
        state.shiftX = state.shiftY = state.inventoryShiftY = 0;
        state.tops = new int[0];
        state.bottoms = new int[0];
        state.lastFrame = -1;
        state.opened = openedContainer(screen);
        RegionReveal.reset();
        OptiGuiRules.screenClosed();   // its answers were for the screen before
    }

    /**
     * The container the player opened {@code screen} on, for the sections that are the
     * screen's own container (those with no source): the block under the crosshair as the
     * screen opened, the one the right click used, if it holds a container the size of the
     * screen's own (the menu's first slot's). So a lime box's own screen knows it is a lime
     * box, for its name, its dye and the pack's rules. {@code null} for a screen opened from
     * an item or a key, or on a double chest.
     */
    // ponytail: the crosshair at open, no use hook; a screen opened by a key while looking at another container of the same size takes that one.
    private static ContainerRegion.@Nullable Source openedContainer(@Nullable Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(screen instanceof AbstractContainerScreen<?> acs) || acs.getMenu() == null || acs.getMenu().slots.isEmpty()) return null;
        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        Container own = acs.getMenu().getSlot(0).container;
        if (!(be instanceof Container c) || own instanceof Inventory || c.getContainerSize() != own.getContainerSize()) return null;
        return ContainerRegion.Source.of(be);
    }

    /** The region on {@code screen} this frame, or {@code null} when it is not there (no section shown). */
    public static @Nullable Geometry geometry(Screen screen) {
        return state.screen.get() == screen ? state.geometry : null;
    }

    /** The open region's scroll, in pixels. */
    public static double scroll() {
        return state.scroll;
    }

    /** Sets the open region's scroll; clamped next frame. */
    @VisibleForTesting
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
    @VisibleForTesting
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
     * One frame of the open region, from the screen's vanilla bounds and label: work out
     * this frame's looks and titles ({@link Frame}), stack the shown sections, work out where
     * the panels are, keep the view in place if sections above it changed, then carry on a
     * reveal and scroll for an item carried to an edge. {@code carrying} is whether an item
     * is on the cursor.
     *
     * @return this frame's geometry, or {@code null} with no section shown
     */
    public static @Nullable Geometry step(Reference vanilla, int inventoryLabelY, boolean carrying,
                                          int mouseX, int mouseY, long now) {
        if (panel == null) return null;

        // Before anything lays out: what the elements read this frame.
        int n = SECTIONS.size();
        boolean[] shown = new boolean[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            shown[i] = SECTIONS.get(i).holder().isVisible();
            if (shown[i]) count++;
        }
        if (count == 0) {
            state.geometry = null;
            state.frame = null;
            return null;
        }
        state.frame = frameOf(shown);

        // Where each shown section sits in the content (the column stacks them).
        int[] tops = new int[n], bottoms = new int[n];
        int columnHeight = column.getHeight();   // lays the column out first
        for (int i = 0; i < n; i++) {
            AbstractPanelElement holder = SECTIONS.get(i).holder();
            if (!shown[i]) { tops[i] = bottoms[i] = -1; continue; }
            // The holder is placed in the content's coordinates, CONTENT_TOP included.
            tops[i] = holder.getChildY();
            bottoms[i] = tops[i] + holder.getHeight();
        }
        int content = RegionLayout.CONTENT_TOP + columnHeight + RegionLayout.CONTENT_BOTTOM;

        if (state.tops.length == n) state.scroll = RegionLayout.keepInPlace(state.scroll, state.tops, state.bottoms, tops);
        state.tops = tops;
        state.bottoms = bottoms;

        Geometry g = RegionLayout.layout(vanilla, inventoryLabelY, content, state.screenHeight);
        state.geometry = g;
        if (panel.getPinnedWidth() != g.width() || panel.getPinnedHeight() != g.height()) panel.size(g.width(), g.height());

        double seconds = state.lastFrame < 0 ? 0 : Math.min(0.1, (now - state.lastFrame) / 1000.0);
        state.lastFrame = now;

        state.scroll = RegionReveal.step(g, state.scroll, tops, bottoms);
        if (carrying) state.scroll += RegionLayout.edgeSpeed(g, mouseX, mouseY) * seconds;
        state.scroll = Scroller.clamp(state.scroll, g.content(), g.viewHeight());
        return g;
    }

    /** This frame's {@link Frame}, with the sections {@code shown} as they are (at least one). */
    private static Frame frameOf(boolean[] shown) {
        Screen screen = state.screen.get();
        List<Declared> sections = new ArrayList<>(SECTIONS.size());
        for (Placed p : SECTIONS) sections.add(p.section());
        PackLook.Look[] looks = RegionLooks.sectionLooks(sections, shown, state.opened);
        PackLook.Look list = RegionLooks.listLook(looks, screen);
        // No screen (MenuKit's checks of the bare layout): no block of its own, no indent.
        boolean[] owns = new boolean[looks.length];
        if (screen != null) for (int i = 0; i < owns.length; i++) owns[i] = RegionLooks.ownsBlock(looks[i], list);

        // The titles: each shown, titled section's own, or its name where it comes out blank beside others.
        List<Integer> titled = new ArrayList<>();
        List<Component> titles = new ArrayList<>();
        List<@Nullable Component> names = new ArrayList<>();
        int first = -1;
        for (int i = 0; i < sections.size(); i++) {
            if (!shown[i]) continue;
            if (first < 0) first = i;
            Declared s = sections.get(i);
            if (s.title() == null) continue;
            titled.add(i);
            titles.add(s.title().get());
            names.add(s.name() != null ? s.name().get() : s.source() == null ? RegionTitles.openedName(state.opened) : null);
        }
        Component[] shownTitles = new Component[sections.size()];
        List<Component> out = RegionTitles.shownTitles(titles, names);
        for (int k = 0; k < titled.size(); k++) shownTitles[titled.get(k)] = out.get(k);
        int indent = screen == null || RegionTitles.blank(shownTitles[first]) ? 0 : RegionTitles.indent(list.corner());
        return new Frame(looks, list, owns, PackLook.inventory(RegionLooks.screenTexture(screen)), shownTitles, first, indent);
    }

    /** The title section {@code s} draws this frame: its own, or while it comes out blank and other sections show, its name. */
    private static Component shownTitle(Declared s) {
        Frame f = state.frame;
        int i = indexOf(s);
        Component t = f == null || i < 0 ? null : f.titles()[i];
        return t != null ? t : Objects.requireNonNull(s.title()).get();
    }

    /** The room left of section {@code s}'s title this frame: the first shown section's indent. */
    private static int titleIndent(Declared s) {
        Frame f = state.frame;
        return f != null && f.first() == indexOf(s) ? f.indent() : 0;
    }

    /** Whether section {@code s} is a block of its own this frame, so its title takes vanilla's room above it. */
    private static boolean ownsBlock(Declared s) {
        Frame f = state.frame;
        int i = indexOf(s);
        return f != null && i >= 0 && f.ownsBlock()[i];
    }

    /** Where {@code s} stacks, or {@code -1} (a frame from before it was declared has no entry for it). */
    private static int indexOf(Declared s) {
        Frame f = state.frame;
        int n = f == null ? 0 : f.looks().length;
        for (int i = 0; i < SECTIONS.size() && i < n; i++) if (SECTIONS.get(i).section() == s) return i;
        return -1;
    }

    // The screen's bounds

    /**
     * Writes {@code bounds} as the screen's own, and moves vanilla's own placements back by
     * as much as the origin moved ({@link RegionLayout#counterShift}): the title stays where
     * vanilla put it on screen, and the player's inventory slots and "Inventory" label go with
     * the inventory panel. Only on a change of layout.
     */
    private static void applyBounds(AbstractContainerScreen<?> screen, Reference bounds) {
        Reference vanilla = state.vanilla;
        if (vanilla == null) return;
        int[] shift = RegionLayout.counterShift(vanilla, bounds);
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
     * player's inventory, which never scrolls. A container's own slots are the region's
     * (whoever put the region on the screen presents them in a section, and their library
     * places them); a created slot is its library's.
     */
    static boolean stays(Slot slot) {
        return slot.container instanceof Inventory && !SlotScreenDispatcher.fireIsCreated(slot);
    }

    /**
     * Whether the region's panels stand in for {@code screen}'s own image and title now: while
     * its bounds are the union, vanilla's container texture would be drawn around the union's
     * centre, so the background pass leaves it out ({@link ScreenBackground}), and vanilla's
     * title would draw over the first section's, so it is left out too. Everything else in
     * the screen's background still draws, every other mod's included.
     */
    public static boolean replacesImage(Screen screen) {
        return state.applied != null && state.geometry != null && state.screen.get() == screen;
    }

    /**
     * At the head of the frame's contents ({@link ContainerScreenLayers#frameStart}), after
     * the background pass placed the region's slots: every slot the region placed this
     * frame, and where it sits, is noted, so a quick move into it later can be shown at once.
     */
    static void placed(AbstractContainerScreen<?> screen) {
        Geometry g = state.geometry;
        if (panel == null || g == null || state.screen.get() != screen) return;
        int topPos = ((AbstractContainerScreenAccessor) screen).mk$getTopPos();
        RegionReveal.placed(PresentedSlots.below(panel), topPos, g, state.scroll, state.tops, state.bottoms);
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

    /** Room in a stack, its size read as it lays out: a frame's border, a margin, a title's indent or the room above it. */
    private record Spacer(IntSupplier width, IntSupplier height) implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { return width.getAsInt(); }
        @Override public int getHeight() { return height.getAsInt(); }
        @Override public void render(RenderContext ctx) {}
    }
}
