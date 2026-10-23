package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.ContainerRegion;
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
import com.trevlar.menukit.core.OptiGuiRules;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.menukit.inject.RegionLayout.Shape;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The container region's machinery (7.4.0; the public face is
 * {@code api.panel.ContainerRegion}). On a container screen it makes two panels: the
 * <b>container list</b>, one panel with its own frame holding every section any mod
 * declares in one {@link ScrollContainer}, and below it, {@link RegionLayout#PANEL_GAP}
 * apart, the <b>inventory panel</b>, its own frame around the player's inventory, which
 * never scrolls. The list grows first and scrolls only when there is no room left; while the
 * two panels fit, they are centered on the screen as one block ({@link RegionLayout#layout}).
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
 *       "Inventory" label stay live and move with the panel.</li>
 *   <li><b>The scroll area</b> is a {@link ScrollContainer} in a background panel, so it
 *       gets the wheel only after everyone else, like every background panel's elements
 *       (step 3 below): over it, it scrolls itself; anywhere else this class scrolls it
 *       ({@link #wheel}). Both move the same scroll ({@code state.scroll}) the same step.</li>
 *   <li><b>Slots</b> clip at its edges ({@code SlotClip}), and a row out of view is not
 *       rendered at any depth ({@code ChildDispatch.render}); it gets {@code renderCulled}
 *       instead, so its library parks its slots rather than sending them home.</li>
 * </ul>
 *
 * <h3>Once a frame</h3>
 * Before each frame ({@link #step}) the region works out, once, everything its elements
 * read while they lay out and draw ({@link Frame}): each section's look and the list's, which
 * sections are blocks of their own, the titles the sections show and how far the first moves
 * clear of the list's corner. Then the column lays out, and the geometry follows. First of all,
 * each kind of section made per live key reads its keys ({@link #syncLive}): the sections are
 * the declared ones and one per live key, restacked only when a key comes or goes, and what the
 * region keeps per section (last frame's tops, where each slot was drawn) follows each section
 * to its new place.
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
 * distance the inventory panel went down, so they go with it. The slots are placed every
 * frame from the slots the menu holds then ({@link #placeSlots}), so one another mod puts in
 * while the screen is open goes with the panel too, and putting the bounds back moves exactly
 * the slots that were moved. Before the screen initializes again (a resize), the bounds go
 * back to vanilla's, so vanilla centers its own image as always.
 *
 * <h3>Never the player's own inventory</h3>
 * The region is never put on {@code InventoryScreen} or {@code CreativeModeInventoryScreen},
 * whatever a section's matcher says ({@link #targets}).
 *
 * <h3>Anchors</h3>
 * {@link #CONTAINERS} is the container list's rectangle and {@link #INVENTORY} the
 * inventory panel's, for an {@code AnchorPanelAdapter}: a mod's buttons go beside the
 * panel they act on. {@link #sectionAnchor} is one section's, cut at the viewport, absent
 * while the section is hidden or scrolled out of view: for a button that acts on one box.
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
 *       panels' elements (over the list, its own scroll area), then scrolls the region,
 *       wherever the mouse is.</li>
 * </ol>
 * A mod that uses the turn in step 2 or in its own {@code afterMouseScroll} listener (Mouse
 * Tweaks over any slot) answers "used", so the region does not move.
 *
 * <p>Render thread only. One container screen is open at a time; its state is reset when
 * another opens (a resize re-initializes the same screen and keeps it).
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

    /** Each section's anchor, by section id: one per id, however many mods ask for it. */
    private static final Map<String, Anchor> SECTION_ANCHORS = new ConcurrentHashMap<>();

    /**
     * {@code ContainerRegion.anchor}: the anchor for the section {@code sectionId}, made the
     * first time it is asked for. The section need not be declared yet (a mod's button may
     * declare before the mod whose section it goes by), so it is looked up by id each frame;
     * an id no section has is simply never there. Its own id is MenuKit's, so it can't clash
     * with an anchor the section's mod declares.
     *
     * @throws IllegalArgumentException when {@code sectionId} is not {@code namespace:path}
     * @throws IllegalStateException    the first time, after declarations froze
     */
    public static Anchor sectionAnchor(String sectionId) {
        Identifier id = Identifier.tryParse(Objects.requireNonNull(sectionId, "sectionId"));
        if (id == null) {
            throw new IllegalArgumentException("ContainerRegion.anchor: section id '" + sectionId + "' is not namespace:path");
        }
        return SECTION_ANCHORS.computeIfAbsent(sectionId, s -> Anchor.of(
                Identifier.fromNamespaceAndPath("menukit", "container_region/section/" + id.getNamespace() + "/" + id.getPath()),
                screen -> sectionBox(screen, s)));
    }

    /** Section {@code sectionId}'s rectangle on {@code screen} this frame, or {@code null} where it isn't showing. */
    private static @Nullable Reference sectionBox(Screen screen, String sectionId) {
        Geometry g = geometry(screen);
        if (g == null) return null;
        // This frame's tops are in sections' order; a section declared after the frame has none yet.
        for (int i = 0; i < sections.size() && i < state.tops.length; i++) {
            if (sections.get(i).section().id().equals(sectionId)) {
                return RegionLayout.sectionBox(g, state.tops[i], state.bottoms[i], state.scroll);
            }
        }
        return null;
    }

    // Declarations

    /**
     * One declared section ({@code ContainerRegion.section}). {@code texture} is the
     * container texture it stands for where its source has no block that gives one
     * ({@link RegionLooks#sectionTexture}; vanilla's chest by default): its slice of the list and
     * its slot frames take that texture's look under the active resource packs, or with
     * OptiGUI the texture its rules give the section's {@code source} (the open screen's,
     * without one). {@code title}, if any, is drawn above its content; {@code name} stands in
     * for a blank title while other sections show, ahead of its source's name ({@link RegionTitles#shownTitles}).
     * {@code onHiddenLanding}, if any, runs when a quick move lands in it while it is hidden ({@link RegionReveal}).
     */
    public record Declared(String id, PanelElement content, ScreenMatcher matcher, int order, BooleanSupplier visibleWhen,
                           Identifier texture, @Nullable Supplier<Component> title, @Nullable Supplier<Component> name,
                           @Nullable Supplier<ContainerRegion.@Nullable Source> source, @Nullable Runnable onHiddenLanding) {}

    /** A section as placed: what it was declared as, and the element that holds it in the scroll area. */
    private record Placed(Declared section, AbstractPanelElement holder) {}

    /** The sections declared at init, in stacking order. */
    private static final List<Placed> DECLARED = new CopyOnWriteArrayList<>();
    /**
     * The open screen's sections, in stacking order: the declared ones and one for each live
     * key of each {@link Family}. Every per-frame array the region keeps is indexed by it.
     */
    private static List<Placed> sections = List.of();
    private static @Nullable Panel panel;
    private static @Nullable ScreenPanelAdapter adapter;
    /** The inventory panel: made with the first section, on the same screens. */
    private static @Nullable Panel inventoryPanel;
    /** The column the sections stack in, inside the scroll area. */
    private static @Nullable RegionColumn column;
    /** The scroll area, for the checks. */
    private static @Nullable ScrollContainer scrollArea;

    /**
     * {@code ContainerRegion.Builder.declare}: adds a section in its place, and the
     * region's panels with the first one. No screen is open yet, so no live key either.
     */
    public static synchronized void declare(Declared section) {
        for (Placed p : DECLARED) {
            if (p.section().id().equals(section.id())) {
                throw new IllegalStateException("ContainerRegion section '" + section.id() + "' is already declared");
            }
        }
        DECLARED.add(new Placed(section, holderFor(section)));
        DECLARED.sort(Comparator.comparingInt((Placed p) -> p.section().order()).thenComparing(p -> p.section().id()));
        buildPanels();
        restack();
    }

    /**
     * One kind of section made per live key ({@code ContainerRegion.sections}). On a screen
     * {@code matcher} takes, {@code keys} is read once a frame; each key's section (and its
     * tab, with {@code tab}) is made the frame the key first appears and kept, the same
     * elements, for as long as the key is there on that screen. Keys are compared with
     * {@code equals}; a repeated key makes one section, the first.
     */
    public record Family(String kind, ScreenMatcher matcher, Function<AbstractContainerScreen<?>, @Nullable List<?>> keys,
                         Function<Object, Declared> section, @Nullable Function<Object, ContainerTabRows.Tab> tab) {}

    /** What one live key made: its section, placed, and its tab, if the kind has tabs. */
    private record Instance(Placed placed, ContainerTabRows.@Nullable Tab tab) {}

    private static final List<Family> FAMILIES = new CopyOnWriteArrayList<>();
    /** Each kind's live keys on the open screen, in the order its keys gave them, and what each made. */
    private static final Map<Family, Map<Object, Instance>> LIVE = new IdentityHashMap<>();

    /** {@code ContainerRegion.Sections.declare}: adds a kind, and the region's panels (and the tab rows') with the first. */
    public static synchronized void declareFamily(Family family) {
        for (Family f : FAMILIES) {
            if (f.kind().equals(family.kind())) {
                throw new IllegalStateException("ContainerRegion sections '" + family.kind() + "' is already declared");
            }
        }
        FAMILIES.add(family);
        buildPanels();
        if (family.tab() != null) ContainerTabRows.buildPanels();
    }

    /**
     * Each kind's keys on {@code screen} now (none off a container screen, or on one the kind
     * is not on): a key that is new makes its section and tab, one that is gone takes them
     * with it. Nothing is made, and nothing restacks, while every kind's keys are the ones
     * it had. Before anything else in the frame reads the sections.
     */
    private static void syncLive(@Nullable Screen screen) {
        boolean changed = false;
        for (Family f : FAMILIES) {
            List<?> keys = screen instanceof AbstractContainerScreen<?> acs && f.matcher().matches(acs) ? f.keys().apply(acs) : null;
            if (keys == null) keys = List.of();
            Map<Object, Instance> was = LIVE.getOrDefault(f, Map.of());
            // ponytail: a keys list with a repeated key never matches, so it rebuilds the map each frame; the elements are kept either way.
            if (was.size() == keys.size() && List.copyOf(was.keySet()).equals(keys)) continue;
            Map<Object, Instance> now = new LinkedHashMap<>();
            for (Object k : keys) {
                if (now.containsKey(k)) continue;
                Instance made = was.get(k);
                if (made == null) {
                    Declared d = f.section().apply(k);
                    made = new Instance(new Placed(d, holderFor(d)), f.tab() == null ? null : f.tab().apply(k));
                }
                now.put(k, made);
            }
            if (now.isEmpty()) LIVE.remove(f);
            else LIVE.put(f, now);
            changed = true;
        }
        if (changed) restack();
    }

    /**
     * The open screen's sections again, from the declared ones and every live key: stacked
     * by order, then by id (a kind's sections by its kind, then in its keys' order). What
     * the region learned about each section that stays (where it was, where its slots sit)
     * follows it to its new place; the column and the tab rows take the new set.
     */
    private static void restack() {
        record Entry(Placed placed, int order, String base, int index) {}
        List<Entry> entries = new ArrayList<>();
        for (Placed p : DECLARED) entries.add(new Entry(p, p.section().order(), p.section().id(), 0));
        List<ContainerTabRows.Live> tabs = new ArrayList<>();
        for (Family f : FAMILIES) {
            int i = 0;
            for (Instance in : LIVE.getOrDefault(f, Map.of()).values()) {
                entries.add(new Entry(in.placed(), in.placed().section().order(), f.kind(), i));
                if (in.tab() != null) tabs.add(new ContainerTabRows.Live(in.tab(), f.kind(), i));
                i++;
            }
        }
        entries.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::base).thenComparingInt(Entry::index));
        List<Placed> before = sections;
        List<Placed> next = new ArrayList<>(entries.size());
        for (Entry e : entries) next.add(e.placed());
        sections = List.copyOf(next);

        // Old place to new (-1: gone), so last frame's tops keep the view in place and the slots' cells follow.
        int[] to = new int[before.size()];
        for (int i = 0; i < to.length; i++) to[i] = sections.indexOf(before.get(i));
        if (state.tops.length == to.length) {
            int[] tops = new int[sections.size()], bottoms = new int[sections.size()];
            java.util.Arrays.fill(tops, -1);
            java.util.Arrays.fill(bottoms, -1);
            for (int i = 0; i < to.length; i++) {
                if (to[i] < 0) continue;
                tops[to[i]] = state.tops[i];
                bottoms[to[i]] = state.bottoms[i];
            }
            state.tops = tops;
            state.bottoms = bottoms;
        }
        RegionReveal.remap(to);

        List<AbstractPanelElement> holders = new ArrayList<>(sections.size());
        for (Placed p : sections) holders.add(p.holder());
        if (column != null) column.set(holders);
        ContainerTabRows.live(tabs);
    }

    /**
     * The element that holds a section in the column. A titled section: its title where
     * vanilla draws a container's, the content 11 px below the title's top (vanilla's title
     * at y 6, its grid at 17). The title moves right clear of the list's corner when its
     * section is the first shown, and the section moves down when it is a block of its own
     * (this frame's Frame says which). In the fitted frame with a tab row on top the row
     * stands in for it, and the title row goes.
     */
    private static AbstractPanelElement holderFor(Declared section) {
        PanelElement body = section.title() == null ? section.content() : Flow.builder().vertical().gap(0, 2)
                .add(Flow.builder().gap(0, 0).add(new Spacer(() -> titleIndent(section), () -> 0))
                        .add(TextLabel.builder().at(1, 0).text(() -> shownTitle(section)).build())
                        .visibleWhen(() -> state.shape.titled()).build())
                .add(section.content()).build();
        return Flow.builder().vertical().gap(0, 0)
                .add(new Spacer(() -> 0, () -> ownsBlock(section) ? RegionLayout.TITLE_INSET : 0)).add(body)
                .visibleWhen(() -> shownOnCurrentScreen(section)).build();
    }

    /** The region's two panels, made once, with the first section or kind declared. */
    private static void buildPanels() {
        if (panel != null) return;
        // One column stacks the shown sections, GAP apart; a hidden one takes no room.
        column = new RegionColumn();
        // The content: the column, with the shape's room above its first section and below its
        // last grid (CONTENT_TOP and CONTENT_BOTTOM in the list).
        PanelElement content = Flow.builder().vertical().gap(0, 0)
                .add(new Spacer(() -> 0, () -> state.shape.contentTop())).add(column)
                .add(new Spacer(() -> 0, () -> state.shape.contentBottom())).build();
        // A section is nine slots wide, so its content wraps there. The scroll area has a
        // declared width and lays no children out, so nothing else hands the content its width.
        content.layoutWithin(RegionLayout.CONTENT_WIDTH);
        scrollArea = ScrollContainer.builder().size(RegionLayout.CONTENT_WIDTH + RegionLayout.LANE, 1)
                .fillHeight()
                .state(() -> state.scroll, v -> state.scroll = v)
                .content(List.of(content)).build();
        // The viewport takes the panel's height below VIEW_TOP, less the frame's bottom border
        // (none where the list runs on into the inventory as one frame).
        PanelElement view = Flow.builder().vertical().gap(0, 0).at(RegionLayout.INSET_X, RegionLayout.VIEW_TOP)
                .add(scrollArea).add(new Spacer(() -> 0, () -> state.shape.viewBottom())).build();
        // Both panels are the screen's background: under everything, claiming nothing. Each
        // says what its surface is at a point, for its slot frames and text (RegionLooks).
        panel = Panel.builder(PANEL_ID).style(PanelStyle.NONE).background()
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(ContainerRegions::origin)))
                .add(new RegionLooks.Backdrop()).add(view)
                .build()
                .visibleWhen(() -> state.geometry != null);
        PanelHost.surfaceLooks(panel, RegionLooks::listSurfaceAt);
        adapter = new ScreenPanelAdapter(panel, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
        if (inventoryPanel == null) {
            inventoryPanel = Panel.builder(INVENTORY_PANEL_ID).style(PanelStyle.NONE).background()
                    .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(ContainerRegions::inventoryOrigin)))
                    .add(new RegionLooks.InventoryFrame())
                    .build()
                    .visibleWhen(() -> state.geometry != null && state.applied != null);
            PanelHost.surfaceLooks(inventoryPanel, RegionLooks::inventorySurfaceAt);
            new ScreenPanelAdapter(inventoryPanel, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
        }
    }

    /** One {@code ContainerRegion.fitted} declaration: the screens {@code matcher} takes, in {@code scope}'s game mode. */
    private record Fitted(ModeScope scope, ScreenMatcher matcher) {}

    private static final List<Fitted> FITTED = new CopyOnWriteArrayList<>();

    /** {@code ContainerRegion.fitted}: the screens {@code matcher} takes show the fitted frame in {@code scope}. */
    public static void declareFitted(ModeScope scope, ScreenMatcher matcher) {
        FITTED.add(new Fitted(Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(matcher, "matcher")));
    }

    /** Whether {@code screen} shows the fitted frame now: a declaration takes it in the player's mode. */
    private static boolean fitted(@Nullable Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return false;
        for (Fitted f : FITTED) if (f.scope().covers(PanelHost.currentMode()) && f.matcher().matches(acs)) return true;
        return false;
    }

    /** This frame's {@link Shape} for {@code screen}: whether it is {@link #fitted}, and its tab rows' heights now. */
    private static Shape shapeFor(@Nullable Screen screen) {
        return Shape.of(fitted(screen), ContainerTabRows.height(Edge.CONTAINERS_TOP),
                ContainerTabRows.height(Edge.CONTAINERS_BOTTOM) + ContainerTabRows.height(Edge.INVENTORY_TOP),
                ContainerTabRows.height(Edge.INVENTORY_BOTTOM));
    }

    /**
     * Whether any section, or kind of section, is declared for {@code screen}: the region's panel is put on it.
     * Never the player's own inventory, survival's or creative's: the region is for screens
     * that hold a container, and the inventory screens are the player's own (creative swaps
     * its slots on every tab, so no container list could stand there). Checked by class, not
     * by slot: the survival inventory's first slot is its crafting result, not the player's.
     * The after-init gate and both panels' adapters all ask here, so this one test keeps the
     * region off those screens entirely.
     */
    @VisibleForTesting
    public static boolean targets(AbstractContainerScreen<?> screen) {
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return false;
        for (Placed p : DECLARED) if (p.section().matcher().matches(screen)) return true;
        for (Family f : FAMILIES) if (f.matcher().matches(screen)) return true;
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

    /** The open screen's sections, in stacking order. */
    static List<Declared> declared() {
        List<Declared> out = new ArrayList<>(sections.size());
        for (Placed p : sections) out.add(p.section());
        return out;
    }

    /** Each section shown this frame, by id, and its top in the content, in stacking order, for the checks. */
    @VisibleForTesting
    public static Map<String, Integer> shownForChecks() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < sections.size() && i < state.tops.length; i++) {
            if (state.tops[i] >= 0) out.put(sections.get(i).section().id(), state.tops[i]);
        }
        return out;
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
        /** How the list stands this frame: the list or the fitted frame, and the tab rows on it. */
        Shape shape = Shape.LIST;
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
        // Before a screen initializes again (a resize), its bounds go back to vanilla's, so
        // vanilla centers its own image from its own size.
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
            state.screenHeight = screen.height;   // a resize initializes again, so the region re-runs
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
        // What the screen before had open was its own: every live key's section goes with it.
        if (!LIVE.isEmpty()) {
            LIVE.clear();
            restack();
        }
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
        state.shape = Shape.LIST;
        RegionReveal.reset();
        RegionSlots.allOn();
        ContainerTabRows.reset();
        OptiGuiRules.screenClosed();   // its answers were for the screen before
    }

    /**
     * The container the player opened {@code screen} on, for the sections that are the
     * screen's own container (those with no source): the block under the crosshair as the
     * screen opened, the one the right click used, if it holds a container the size of the
     * screen's own (the menu's first slot's) and the screen has its title. So a lime box's own
     * screen knows it is a lime box, for its name, its dye and the pack's rules. {@code null}
     * for a screen opened from an item, or by a key while looking at a container of another
     * size or title (a backpack's screen in front of a chest), and on a double chest.
     *
     * <p>The title is the container's default name: the client's block entity never knows the
     * name a player gave it, so a named container is not found, and its section takes what
     * OptiGUI picked for the screen, which is the same art; its title is its name, so it never
     * needs another.
     */
    // Read from the crosshair at open, not from a use hook, so a screen opened by a key while looking at another unnamed container of the same size and kind takes that one.
    private static ContainerRegion.@Nullable Source openedContainer(@Nullable Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(screen instanceof AbstractContainerScreen<?> acs) || acs.getMenu() == null || acs.getMenu().slots.isEmpty()) return null;
        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        Container own = acs.getMenu().getSlot(0).container;
        if (!(be instanceof Container c) || own instanceof Inventory || c.getContainerSize() != own.getContainerSize()) return null;
        if (be instanceof Nameable n && !n.getDisplayName().getContents().equals(screen.getTitle().getContents())) return null;
        return ContainerRegion.Source.of(be);
    }

    /** {@link ContainerRegion#openedOn()}: the open screen's opened-on container, while it is the region's screen. */
    public static ContainerRegion.@Nullable Source openedOn() {
        Screen open = Minecraft.getInstance().gui.screen();
        return open != null && state.screen.get() == open ? state.opened : null;
    }

    /** The region on {@code screen} this frame, or {@code null} when it is not there (no section shown). */
    public static @Nullable Geometry geometry(Screen screen) {
        return state.screen.get() == screen ? state.geometry : null;
    }

    /**
     * Whether the container list on {@code screen} scrolls this frame ({@code ContainerRegion.scrolls}):
     * {@code false} while the region is not on {@code screen}, or no section shows.
     */
    public static boolean scrolls(Screen screen) {
        Objects.requireNonNull(screen, "screen");
        Geometry g = geometry(screen);
        return g != null && g.scrollable();
    }

    /**
     * {@code ContainerRegion.contentRoom}: the content height a section on {@code screen} may
     * grow to before the fitted list would scroll, worked out from {@code screen}'s own size,
     * this frame's tab rows and whether it is {@link #fitted}, never from this frame's own
     * layout (a section reads this while building itself, ahead of that layout). 0 while the
     * region is not on {@code screen} yet (its own init, before {@link #frame} first runs).
     */
    public static int contentRoom(Screen screen) {
        Objects.requireNonNull(screen, "screen");
        if (state.screen.get() != screen || state.vanilla == null) return 0;
        return RegionLayout.maxContent(state.vanilla, state.vanillaInventoryLabelY, state.screenHeight, shapeFor(screen));
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
     * At init the bounds are just written: every mod's init listener runs after, and
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
        // The player's slots first, every frame, before anything below can return: a slot
        // another mod put into the menu since the last frame goes where the inventory is now.
        if (state.screen.get() == screen) placeSlots(screen.getMenu().slots, state.shiftX, state.inventoryShiftY);
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
        else applyBounds(screen, want, g.shape().below());
    }

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

        // Which sections show, each asked with every slot on: a hidden section's slots were off
        // last frame, and a slot library's element with all its slots off hides itself.
        Screen screen = state.screen.get();
        syncLive(screen);
        RegionSlots.allOn();
        int n = sections.size();
        boolean[] shown = new boolean[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            shown[i] = sections.get(i).holder().isVisible();
            if (shown[i]) count++;
        }
        if (count == 0) {
            state.geometry = null;
            state.frame = null;
            ContainerTabRows.choose(null);
            return null;
        }
        switchOffHidden(screen, shown);
        // Before anything lays out: what the elements read this frame, and how the list stands
        // with the tab rows on this screen.
        state.frame = frameOf(shown);
        ContainerTabRows.choose(screen);
        state.shape = shapeFor(screen);

        // Where each shown section sits in the content (the column stacks them).
        int[] tops = new int[n], bottoms = new int[n];
        int columnHeight = column.getHeight();   // lays the column out first
        for (int i = 0; i < n; i++) {
            AbstractPanelElement holder = sections.get(i).holder();
            if (!shown[i]) { tops[i] = bottoms[i] = -1; continue; }
            // The holder is placed in the column's coordinates; the column starts the shape's contentTop down the content.
            tops[i] = holder.getChildY();
            bottoms[i] = tops[i] + holder.getHeight();
        }
        int content = state.shape.contentTop() + columnHeight + state.shape.contentBottom();
        // How much wider than nine slots the widest shown section's content is (a grid with
        // its own scrollbar beside it, while that shows): the list and its scroll area widen
        // by as much. Titles are left out, as vanilla's run past the frame.
        int wider = 0;
        for (int i = 0; i < n; i++) {
            if (shown[i]) wider = Math.max(wider, sections.get(i).section().content().getWidth() - RegionLayout.CONTENT_WIDTH);
        }
        if (scrollArea.getWidth() != RegionLayout.CONTENT_WIDTH + wider + RegionLayout.LANE) {
            scrollArea.fillWidth(RegionLayout.CONTENT_WIDTH + wider + RegionLayout.LANE);
        }

        if (state.tops.length == n) state.scroll = RegionLayout.keepInPlace(state.scroll, state.tops, state.bottoms, tops);
        state.tops = tops;
        state.bottoms = bottoms;

        Geometry g = RegionLayout.layout(vanilla, inventoryLabelY, content, state.screenHeight, state.shape, wider);
        state.geometry = g;
        if (panel.getPinnedWidth() != g.width() || panel.getPinnedHeight() != g.height()) panel.size(g.width(), g.height());

        double seconds = state.lastFrame < 0 ? 0 : Math.min(0.1, (now - state.lastFrame) / 1000.0);
        state.lastFrame = now;

        state.scroll = RegionReveal.step(g, state.scroll, tops, bottoms);
        if (carrying) state.scroll += RegionLayout.edgeSpeed(g, mouseX, mouseY) * seconds;
        state.scroll = Scroller.clamp(state.scroll, g.content(), g.viewHeight());
        ContainerTabRows.afterLayout(g);
        return g;
    }

    /**
     * Every slot of a section on {@code screen} whose own condition doesn't hold this frame
     * goes off ({@link RegionSlots}). A section whose content hides itself is not hidden here:
     * its slots are not on the menu, or are off already. The screen's own container is its first
     * section with no source, shown or not, and a container's own slot no section has drawn yet
     * is its; unless that hides a section that {@code shown} says shows (its content hides with
     * its slots off: another mod's section presents those slots), and then such slots stay on
     * until a section draws them and so claims them.
     */
    private static void switchOffHidden(@Nullable Screen screen, boolean[] shown) {
        if (!(screen instanceof AbstractContainerScreen<?> acs) || acs.getMenu() == null) return;
        boolean[] hidden = new boolean[sections.size()];
        int own = -1;
        for (int i = 0; i < hidden.length; i++) {
            Declared s = sections.get(i).section();
            if (!s.matcher().matches(acs)) continue;
            if (own < 0 && s.source() == null) own = i;
            hidden[i] = !s.visibleWhen().getAsBoolean();
        }
        RegionSlots.switchOff(acs.getMenu().slots, hidden, own);
        if (own < 0) return;
        for (int i = 0; i < hidden.length; i++) {
            if (shown[i] && !sections.get(i).holder().isVisible()) {
                RegionSlots.switchOff(acs.getMenu().slots, hidden, -1);
                return;
            }
        }
    }

    /** A quick move landed in section {@code index} while it is hidden: its {@code onHiddenLanding}, if any. */
    static void landedHidden(int index) {
        Runnable r = index < sections.size() ? sections.get(index).section().onHiddenLanding() : null;
        if (r != null) r.run();
    }

    /** This frame's {@link Frame}, with the sections {@code shown} as they are (at least one). */
    private static Frame frameOf(boolean[] shown) {
        Screen screen = state.screen.get();
        List<Declared> sections = declared();
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
            if (shown[i] && first < 0) first = i;
            if (!shown[i]) continue;
            Declared s = sections.get(i);
            if (s.title() == null) continue;
            titled.add(i);
            titles.add(s.title().get());
            // Its name(...), else its container's: its source, or the block the screen was opened on.
            names.add(s.name() != null ? s.name().get() : RegionTitles.sourceName(s.source() == null ? state.opened : s.source().get()));
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
        for (int i = 0; i < sections.size() && i < n; i++) if (sections.get(i).section() == s) return i;
        return -1;
    }

    // The screen's bounds

    /**
     * Writes {@code bounds} as the screen's own, and moves vanilla's own placements back by
     * as much as the origin moved ({@link RegionLayout#counterShift}): the title stays where
     * vanilla put it on screen, and the player's inventory slots and "Inventory" label go with
     * the inventory panel. Only on a change of layout. The labels move here, by the change;
     * the slots are placed from the new shift ({@link #placeSlots}), here and every frame.
     */
    private static void applyBounds(AbstractContainerScreen<?> screen, Reference bounds, int below) {
        Reference vanilla = state.vanilla;
        if (vanilla == null) return;
        int[] shift = RegionLayout.counterShift(vanilla, bounds, below);
        int dx = shift[0] - state.shiftX, dy = shift[1] - state.shiftY, invDy = shift[2] - state.inventoryShiftY;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        acc.mk$setLeftPos(bounds.leftPos());
        acc.mk$setTopPos(bounds.topPos());
        acc.mk$setImageWidth(bounds.imageWidth());
        acc.mk$setImageHeight(bounds.imageHeight());
        acc.mk$setInventoryLabelX(acc.mk$getInventoryLabelX() + dx);
        acc.mk$setInventoryLabelY(acc.mk$getInventoryLabelY() + invDy);
        acc.mk$setTitleLabelX(acc.mk$getTitleLabelX() + dx);
        acc.mk$setTitleLabelY(acc.mk$getTitleLabelY() + dy);
        state.shiftX = shift[0];
        state.shiftY = shift[1];
        state.inventoryShiftY = shift[2];
        state.applied = bounds;
        // So every mod's init listener after this one reads the slots where they now are.
        placeSlots(screen.getMenu().slots, state.shiftX, state.inventoryShiftY);
    }

    /**
     * Back to vanilla's own bounds and placements, if they are not already. Every slot the
     * region moved goes back by exactly what it got, whether or not it is still in the menu.
     */
    private static void restoreBounds(AbstractContainerScreen<?> screen) {
        unplaceSlots();
        if (state.applied == null || state.vanilla == null) return;
        applyBounds(screen, state.vanilla, 0);
        state.applied = null;
    }

    // The player's slots, placed every frame

    /**
     * How far the region has moved each of the player's slots, {@code {dx, dy}}. Weak and
     * keyed by the slot object (a slot keeps its identity; a closed menu's slots go with it).
     * Slots come and go while a screen is open (another mod puts its own into the menu), so
     * each frame moves every slot by what it lacks, and a restore takes back exactly what was
     * given: never a slot that was not moved, never twice.
     */
    private static final Map<Slot, int[]> MOVED = new WeakHashMap<>();

    /** Moves a slot by {@code (dx, dy)}. Swapped by MenuKit's checks, which have no mixins. */
    @FunctionalInterface
    public interface SlotMover {
        void move(Slot slot, int dx, int dy);
    }

    private static final SlotMover WRITE = (slot, dx, dy) -> {
        SlotAccessor pos = (SlotAccessor) slot;
        pos.mk$setX(slot.x + dx);
        pos.mk$setY(slot.y + dy);
    };
    private static SlotMover mover = WRITE;

    /** For MenuKit's checks: swaps how a slot is moved, and back with {@code null}. */
    @VisibleForTesting
    public static void moverForChecks(@Nullable SlotMover m) {
        mover = m == null ? WRITE : m;
    }

    /**
     * Puts every slot of {@code slots} that stays with the inventory ({@link #stays}) at
     * {@code (shiftX, shiftY)} from where vanilla placed it, by moving it the difference from
     * what it already got.
     */
    @VisibleForTesting
    public static void placeSlots(List<Slot> slots, int shiftX, int shiftY) {
        for (Slot slot : slots) {
            if (!stays(slot)) continue;
            int[] got = MOVED.get(slot);
            int gx = got == null ? 0 : got[0], gy = got == null ? 0 : got[1];
            if (gx == shiftX && gy == shiftY) continue;
            mover.move(slot, shiftX - gx, shiftY - gy);
            if (shiftX == 0 && shiftY == 0) MOVED.remove(slot);
            else MOVED.put(slot, new int[]{shiftX, shiftY});
        }
    }

    /** Moves every slot the region moved back by what it got, and forgets them. */
    @VisibleForTesting
    public static void unplaceSlots() {
        for (Map.Entry<Slot, int[]> e : MOVED.entrySet()) mover.move(e.getKey(), -e.getValue()[0], -e.getValue()[1]);
        MOVED.clear();
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
     * center, so the background pass leaves it out ({@link ScreenBackground}), and vanilla's
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
