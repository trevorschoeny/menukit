package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.element.ChildDispatch;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.core.RegionMath;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.window.ClientWindowVisibility;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One place panels live (§0065). Each of the five contexts instantiates a host: a
 * container screen, a non-container vanilla screen, a slot group, the HUD, a
 * standalone screen ({@code MKScreen}, {@code CustomContainerScreen}). The five stay as
 * mental models (§0028); the five copies of stacking, budget, render, claim and
 * dispatch collapse into this class.
 *
 * <h2>What a host owns</h2>
 * <ul>
 *   <li><b>Entries, sorted.</b> One {@link Entry} per (panel, host): the panel, the
 *       position the host resolves, the content padding, and the sort key
 *       {@code (priority, modId, registration sequence)}. Sorted order is both the
 *       stacking order within a region and the z-order, topmost last. Nothing
 *       depends on which mod loaded first.</li>
 *   <li><b>Origin resolution</b> ({@link #layout}): each shown entry's position is
 *       resolved against this frame's {@link Frame}: the reference rectangle (menu
 *       frame, slot-group box, or the standalone screen's own main panel) and the
 *       screen size. One budget function feeds each panel the room it has before it
 *       is measured; one stacking prefix per region; one resolver per reference kind
 *       ({@link RegionMath#resolveMenu}, {@link RegionMath#resolveInside}).</li>
 *   <li><b>Render in layers</b> ({@link #render}): FLOW then OVERLAY, per
 *       {@link LayerPlan}.</li>
 *   <li><b>Claims</b> ({@link #claimAt}): which placed panel, if any, takes a screen
 *       point, by the one rule in {@link #claimsPoint}.</li>
 *   <li><b>Modal and dim queries</b> ({@link #topmostModal}, {@link #anyShown}).</li>
 *   <li><b>Input routing</b> ({@link #mouseClicked} and friends). The HUD host
 *       routes none.</li>
 * </ul>
 *
 * <h2>What a host does not own</h2>
 * Which hosts exist on a screen, and how their claims combine, is
 * {@link ScreenPanelRegistry}'s: it is the one place global questions ("does
 * anything claim this point?", "is a modal up?") are answered, across every host on
 * the current screen. Frame order on a container screen is {@link ContainerScreenLayers}'.
 *
 * <p>Internal: consumers declare panels through adapters, {@code HudPanel} or a
 * standalone screen; they never build a host.
 */
@ApiStatus.Internal
public final class PanelHost {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    // ── Types ──────────────────────────────────────────────────────────

    /** The five contexts. Each names what its host places against. */
    public enum Kind {
        /** A vanilla (or modded) container screen: REGION resolves around the menu frame. */
        CONTAINER("container screen"),
        /** A non-container vanilla screen (Options, title, ...): no frame, screen spots only. */
        VANILLA_SCREEN("vanilla screen"),
        /** One slot group on a container screen: REGION resolves around the group's box. */
        SLOT_GROUP("slot group"),
        /** The in-game HUD: screen spots on the game window; render only, no input. */
        HUD("HUD"),
        /** A standalone screen ({@code MKScreen}, {@code CustomContainerScreen}): its own main panel is the frame. */
        STANDALONE("standalone screen");

        private final String label;
        Kind(String label) { this.label = label; }
        /** Human name for error messages. */
        public String label() { return label; }
    }

    /** Where a standalone screen's title sits, which decides the room reserved for it. */
    public enum TitleBand {
        /** Inside the frame, above the main content (container screens). */
        IN_FRAME,
        /** At the top of the screen, outside the frame (a standalone screen's default). */
        SCREEN_TOP,
        /** Nowhere: no title, no room reserved; the frame may reach the top margin. */
        NONE
    }

    /**
     * One panel in this host. The same {@link Panel} may sit in several hosts (a
     * slot-group adapter targeting two groups is in two slot-group hosts), each with
     * its own entry: registries are keyed by (panel, host), not by panel identity.
     *
     * @param panel    the panel
     * @param position the position this host resolves (the panel's own, or the
     *                 standalone normalisation of an unplaced one)
     * @param padding  content padding between the panel's outer edge and its elements
     * @param modId    the registering mod: the tie-break after priority
     * @param seq      registration sequence: the last tie-break
     */
    public record Entry(Panel panel, PanelPosition position, int padding, String modId, int seq) {}

    /**
     * Where an entry landed this frame: its outer rectangle (padding-inclusive) in
     * absolute screen coordinates.
     */
    public record Placed(Entry entry, int x, int y, int w, int h) {
        /** The placed panel. */
        public Panel panel() { return entry.panel(); }
        /** Left edge of the element area (outer origin + padding). */
        public int contentX() { return x + entry.padding(); }
        /** Top edge of the element area (outer origin + padding). */
        public int contentY() { return y + entry.padding(); }
        /** Whether the outer rectangle contains the point. */
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /**
     * What a host places against this frame.
     *
     * @param sw        GUI-scaled screen width
     * @param sh        GUI-scaled screen height
     * @param reference the rectangle REGION resolves around (menu frame, slot-group
     *                  box), or {@code null} when the context has none. A standalone
     *                  host computes its own from its main panel.
     */
    public record Frame(int sw, int sh, @Nullable Reference reference) {}

    /**
     * One frame's placements, bottom to top, plus the main frame a standalone host
     * resolved (its {@code leftPos, topPos, width, height}; {@code null} elsewhere).
     */
    public record Layout(List<Placed> placed, @Nullable Reference main) {
        static final Layout EMPTY = new Layout(List.of(), null);
    }

    /**
     * Whether a placed panel receives the pointer this frame (live mouse coordinates
     * for hover and tooltips) or renders inert. Supplied by the caller, who knows
     * every host on the screen: a panel is live when nothing above it claims the
     * cursor and no modal it does not belong to is up.
     */
    @FunctionalInterface
    public interface PointerPolicy {
        boolean live(PanelHost host, Placed placed);
        /** Every panel live (a host alone on its surface, with no modal). */
        PointerPolicy ALL = (h, p) -> true;
    }

    /**
     * A standalone host's own slots: whether {@code panel} hosts the live slot under
     * the point. {@code CustomContainerScreen} answers for its slot groups, so its opaque
     * panels claim their rectangles without making their own slots inert.
     */
    @FunctionalInterface
    public interface SlotOwnership {
        boolean ownsSlotAt(Panel panel, double mouseX, double mouseY);
    }

    /** Drawn after a panel's background and before its elements (a standalone screen's slot frames). */
    @FunctionalInterface
    public interface Decoration {
        void draw(GuiGraphicsExtractor graphics, Placed placed);
    }

    // ── State ──────────────────────────────────────────────────────────

    private final Kind kind;
    private final Supplier<@Nullable Frame> frames;
    private final RegionMath.Insets insets;
    /**
     * Sorted by (priority, modId, seq). An immutable list swapped whole on change, so
     * a reader mid-dispatch keeps a consistent snapshot and never sees a half-sorted
     * list.
     */
    private volatile List<Entry> entries = List.of();

    // Standalone-only knobs; inert on the other kinds.
    private TitleBand titleBand = TitleBand.SCREEN_TOP;
    private Predicate<Panel> autoFitMain = p -> true;
    private @Nullable Function<Panel, int[]> contentSize;
    private @Nullable SlotOwnership slotOwnership;
    private @Nullable Decoration decoration;

    /** Entries already warned about an overflow; one warning each. */
    private final Set<Entry> warned = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private PanelHost(Kind kind, Supplier<@Nullable Frame> frames, RegionMath.Insets insets) {
        this.kind = kind;
        this.frames = frames;
        this.insets = insets;
    }

    /** A container screen's host: REGION around the (chrome-extended) menu frame. */
    public static PanelHost container(Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.CONTAINER, frames, RegionMath.Insets.CHROME);
    }

    /** A non-container vanilla screen's host: screen spots, 4px in. */
    public static PanelHost vanillaScreen(Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.VANILLA_SCREEN, frames, RegionMath.Insets.SCREEN);
    }

    /** One slot group's host: REGION around the group's box. */
    public static PanelHost slotGroup(Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.SLOT_GROUP, frames, RegionMath.Insets.CHROME);
    }

    /** The HUD's host: screen spots on the game window; render only. */
    public static PanelHost hud(Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.HUD, frames, RegionMath.Insets.HUD);
    }

    /** A standalone screen's host: its main panel is the frame. */
    public static PanelHost standalone(Supplier<@Nullable Frame> frames, TitleBand titleBand) {
        PanelHost host = new PanelHost(Kind.STANDALONE, frames, RegionMath.Insets.CHROME);
        host.titleBand = titleBand;
        return host;
    }

    /** Which context this host is. */
    public Kind kind() { return kind; }

    /** Standalone: where the title sits (and the room kept for it). */
    public PanelHost titleBand(TitleBand band) { this.titleBand = band; return this; }

    /**
     * Standalone: whether the main panel gets a height budget, so a main taller than
     * the screen auto-scrolls. A main holding vanilla slots must not (its slots are
     * placed in absolute coordinates and have no scroll hook).
     */
    public PanelHost autoFitMain(Predicate<Panel> autoFit) { this.autoFitMain = autoFit; return this; }

    /**
     * Standalone: a panel's content size ({width, height}, padding excluded) when the
     * panel's own {@code getWidth/getHeight} does not know it all (a
     * {@code CustomContainerScreen} panel also holds slot groups). Default: the panel's own.
     */
    public PanelHost contentSize(Function<Panel, int[]> size) { this.contentSize = size; return this; }

    /** Standalone: which live slots each panel owns (see {@link SlotOwnership}). */
    public PanelHost slotOwnership(SlotOwnership ownership) { this.slotOwnership = ownership; return this; }

    /** Standalone: drawn between a panel's background and its elements. */
    public PanelHost decoration(Decoration decoration) { this.decoration = decoration; return this; }

    // ── Entries ────────────────────────────────────────────────────────

    /** The next registration sequence number: the final, per-process tie-break. */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** A fresh registration sequence number. */
    public static int nextSeq() { return SEQ.getAndIncrement(); }

    private static final Comparator<Entry> ORDER = Comparator
            .comparingInt((Entry e) -> e.position().priority())
            .thenComparing(Entry::modId)
            .thenComparingInt(Entry::seq);

    /**
     * Adds a panel, in sorted place. Rejects a position this context cannot resolve
     * (and an unplaced panel) loudly: a panel with nowhere to go is a declaration
     * bug, and a registration-time error beats an invisible panel.
     *
     * @throws IllegalArgumentException if this context cannot place the position
     */
    public synchronized Entry add(Panel panel, PanelPosition position, int padding, String modId, int seq) {
        requireSupported(kind, panel, position);
        if (position.mode() == PanelPosition.Mode.MAIN && mainEntry() != null) {
            throw new IllegalArgumentException("MenuKit: panel '" + panel.id()
                    + "' is a second main() on one screen; a screen has one frame.");
        }
        Entry entry = new Entry(panel, position, padding, modId, seq);
        List<Entry> next = new ArrayList<>(entries);
        next.add(entry);
        next.sort(ORDER);
        entries = List.copyOf(next);
        return entry;
    }

    /** Removes every entry for {@code panel}. Idempotent. */
    public synchronized void remove(Panel panel) {
        List<Entry> next = new ArrayList<>(entries);
        next.removeIf(e -> e.panel() == panel);
        entries = List.copyOf(next);
    }

    /** The entries, sorted bottom to top. */
    public List<Entry> entries() { return entries; }

    /** Whether this host holds no panels. */
    public boolean isEmpty() { return entries.isEmpty(); }

    /**
     * The placement rule per context. Every context takes PIXEL and CENTER (an
     * overlay floats centred on the screen wherever it is hosted); the rest depend
     * on what the context has to place against.
     */
    public static void requireSupported(Kind kind, Panel panel, PanelPosition position) {
        PanelPosition.Mode mode = position.mode();
        boolean ok = switch (mode) {
            case UNPLACED -> false;
            case PIXEL, CENTER -> true;
            case MAIN -> kind == Kind.STANDALONE;
            case REGION -> kind == Kind.CONTAINER || kind == Kind.SLOT_GROUP || kind == Kind.STANDALONE;
            case SCREEN_ANCHOR -> kind != Kind.SLOT_GROUP;
        };
        if (mode == PanelPosition.Mode.REGION && position.region() == null) ok = false;
        if (mode == PanelPosition.Mode.SCREEN_ANCHOR && position.screenAnchor() == null) ok = false;
        if (mode == PanelPosition.Mode.PIXEL && position.pixelOrigin() == null) ok = false;
        if (!ok) {
            String hint = switch (kind) {
                case CONTAINER -> "region(OutsideRegion), screenAnchor(InsideRegion), center() or pixel(...)";
                case VANILLA_SCREEN, HUD -> "screenAnchor(InsideRegion), center() or pixel(...)";
                case SLOT_GROUP -> "region(OutsideRegion), center() or pixel(...)";
                case STANDALONE -> "main(), region(OutsideRegion), screenAnchor(InsideRegion), center() or pixel(...)";
            };
            throw new IllegalArgumentException("MenuKit: panel '" + panel.id() + "' is "
                    + position.describe() + ", which a " + kind.label() + " cannot place. "
                    + "Declare .position(PanelPosition." + hint + ") on the panel.");
        }
    }

    // ── The one isShown, the one claim rule ────────────────────────────

    /**
     * The one "is this panel shown" answer: its own visibility (imperative or
     * {@code visibleWhen}) and the engine's VISIBILITY key. A hidden panel is skipped
     * before layout, so it is never measured, never placed, and its pixel supplier
     * never runs.
     */
    public static boolean isShown(Panel panel) {
        return ClientWindowVisibility.panelShown(panel);
    }

    /**
     * The claim rule (§0065 as reworded 2026-09-27): does this placed panel
     * take the point, so whatever lies beneath it is inert?
     *
     * <ol>
     *   <li>An element's <b>active overlay</b> (an open Dropdown popover) claims its
     *       area, whatever the panel's opacity: a popover is always on top.</li>
     *   <li>An <b>opaque</b> panel claims its whole outer rectangle. No holes: an
     *       element inside it never lets a point through to what is behind the
     *       panel. (A panel's own slots stay live through
     *       {@link #yieldsToSlot}, which is about the claimant's own content, not
     *       about what lies beneath.)</li>
     *   <li>A <b>transparent</b> panel claims only its solid elements: shown, opaque
     *       and interactive ones, by their hit test. A render-only decoration on a
     *       transparent panel claims nothing, so it never eats a click it does
     *       nothing with.</li>
     * </ol>
     *
     * The active overlay of the topmost modal is the one exception, and it is not
     * decided here: a shown modal claims every point on its surface, which
     * {@link ScreenPanelRegistry} applies before it asks any host.
     */
    public static boolean claimsPoint(Placed placed, double mouseX, double mouseY) {
        Panel panel = placed.panel();
        InputContext in = input(placed, mouseX, mouseY);
        List<PanelElement> shown = shownElements(panel);
        if (ChildDispatch.overlayOwner(shown, in) != null) return true;
        if (ClientWindowVisibility.panelOpaque(panel)) {
            return placed.contains(mouseX, mouseY);
        }
        for (PanelElement el : shown) {
            if (!el.isElementOpaque() || !el.isInteractive()) continue;
            if (el.hitTest(in)) return true;
        }
        return false;
    }

    /**
     * Whether the claimant's own live slot is under the point. A panel that presents
     * a slot (a {@code SlotElement}, or a standalone screen's slot group) claims its
     * rectangle like any panel, and the claim routes the point to its own content:
     * for a slot that means vanilla's slot machinery, which then hovers, clicks and
     * tooltips that slot. Anything a different panel put there stays inert.
     */
    public boolean yieldsToSlot(Placed placed, double mouseX, double mouseY) {
        if (slotOwnership != null && slotOwnership.ownsSlotAt(placed.panel(), mouseX, mouseY)) {
            return true;
        }
        InputContext in = input(placed, mouseX, mouseY);
        for (PanelElement el : shownElements(placed.panel())) {
            if (el.presentsSlotAt(in)) return true;
        }
        return false;
    }

    /**
     * The input context for a placed panel's elements (§0066): its content origin,
     * the event's point, and whether the panel is disabled (the cascade).
     */
    public static InputContext input(Placed placed, double mouseX, double mouseY) {
        return new InputContext(placed.contentX(), placed.contentY(), mouseX, mouseY, placed.panel().isDisabled());
    }

    /** The panel's elements the window engine shows (the one isShown for elements). */
    private static List<PanelElement> shownElements(Panel panel) {
        List<PanelElement> all = panel.getElements();
        List<PanelElement> out = new ArrayList<>(all.size());
        for (PanelElement el : all) {
            if (ClientWindowVisibility.elementShown(panel, el)) out.add(el);
        }
        return out;
    }

    /** The topmost placed panel in {@code layer} that claims the point, or {@code null}. */
    public @Nullable Placed claimAt(double mouseX, double mouseY, LayerPlan.Layer layer) {
        List<Placed> placed = placed();
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placed p = placed.get(i);
            if (LayerPlan.layerOf(p.panel()) != layer) continue;
            if (claimsPoint(p, mouseX, mouseY)) return p;
        }
        return null;
    }

    /** Whether any shown panel matches ({@code Panel::dimsBehind}, {@code Panel::tracksAsModal}). */
    public boolean anyShown(Predicate<Panel> test) {
        for (Entry e : entries) {
            if (isShown(e.panel()) && test.test(e.panel())) return true;
        }
        return false;
    }

    /** The topmost shown {@code tracksAsModal} panel, or {@code null}. */
    public @Nullable Panel topmostModal() {
        List<Entry> es = entries;
        for (int i = es.size() - 1; i >= 0; i--) {
            Panel p = es.get(i).panel();
            if (isShown(p) && p.tracksAsModal()) return p;
        }
        return null;
    }

    /** This frame's placement of {@code panel}, or {@code null} when it is not placed. */
    public @Nullable Placed placedOf(Panel panel) {
        for (Placed p : placed()) {
            if (p.panel() == panel) return p;
        }
        return null;
    }

    // ── Layout ─────────────────────────────────────────────────────────

    /** This frame's placements, bottom to top. */
    public List<Placed> placed() {
        return layout().placed();
    }

    /**
     * Resolves every shown entry against this frame. Pure apart from feeding each
     * panel its size budget (a panel measures itself against the room it has) and a
     * one-time overflow warning.
     */
    public Layout layout() {
        Frame frame = frames.get();
        if (frame == null || entries.isEmpty()) return Layout.EMPTY;
        int sw = frame.sw(), sh = frame.sh();
        Reference reference = frame.reference();

        List<Placed> out = new ArrayList<>();
        Reference main = null;
        Placed mainPlaced = null;
        if (kind == Kind.STANDALONE) {
            Entry mainEntry = mainEntry();
            if (mainEntry != null) {
                MainFrame mf = resolveMain(mainEntry, sw, sh);
                main = mf.frame();
                reference = main;
                if (isShown(mainEntry.panel())) mainPlaced = mf.placed();
            }
        }

        // One stacking prefix per region (OutsideRegion and InsideRegion keys never
        // collide: different enum types).
        Map<Object, Integer> prefix = new HashMap<>();
        for (Entry e : entries) {
            if (!isShown(e.panel())) continue;
            if (mainPlaced != null && e == mainPlaced.entry()) {
                out.add(mainPlaced);
                continue;
            }
            Placed p = place(e, sw, sh, reference, prefix);
            if (p != null) out.add(p);
        }
        return new Layout(List.copyOf(out), main);
    }

    /** Resolves one entry, or {@code null} when it has no place this frame. */
    private @Nullable Placed place(Entry e, int sw, int sh, @Nullable Reference reference,
                                   Map<Object, Integer> prefix) {
        Panel panel = e.panel();
        PanelPosition pos = e.position();
        int pad = e.padding();
        int x, y;
        int[] size;

        if (panel.isOverlayPositioned()) {
            // The single overlay rule: whatever its declared mode, an overlay (a
            // center() panel, or any dim or modal panel) floats centred on the screen
            // window and draws on top. It grows to the screen width, not an anchor's.
            feedWidth(panel, RegionMath.availableScreenEdgeWidth(sw, RegionConstants.SCREEN_EDGE_MARGIN), pad);
            size = measure(panel, pad);
            x = (sw - size[0]) / 2;
            y = (sh - size[1]) / 2;
        } else {
            switch (pos.mode()) {
                case PIXEL -> {
                    // The consumer owns the geometry: natural size, no budget, the
                    // supplier's point verbatim. Null = no anchor this frame.
                    ScreenOrigin o = Objects.requireNonNull(pos.pixelOrigin()).get();
                    if (o == null) return null;
                    size = measure(panel, pad);
                    x = o.x();
                    y = o.y();
                }
                case REGION -> {
                    if (reference == null) return null;
                    OutsideRegion region = Objects.requireNonNull(pos.region());
                    feedRegionBudget(panel, region, reference, pad, sw, sh);
                    size = measure(panel, pad);
                    int before = prefix.getOrDefault(region, 0);
                    var o = RegionMath.resolveMenu(region, reference, size[0], size[1], before, sw, sh);
                    if (o.isEmpty()) {
                        warnOnce(e, "is larger than the screen's safe area in OutsideRegion." + region);
                        return null;
                    }
                    int axial = region.isHorizontalFlow() ? size[0] : size[1];
                    prefix.put(region, before + axial + RegionConstants.MENU_STACK_GAP);
                    x = o.get().x();
                    y = o.get().y();
                }
                case SCREEN_ANCHOR -> {
                    InsideRegion spot = Objects.requireNonNull(pos.screenAnchor());
                    feedWidth(panel, RegionMath.availableScreenEdgeWidth(sw, insets.edge()), pad);
                    size = measure(panel, pad);
                    int before = prefix.getOrDefault(spot, 0);
                    var o = RegionMath.resolveInside(spot, sw, sh, size[0], size[1], before, insets);
                    if (o.isEmpty()) {
                        warnOnce(e, "overflows InsideRegion." + spot + " (" + before + "px of siblings above it)");
                        return null;
                    }
                    prefix.put(spot, before + size[1] + insets.gap());
                    x = o.get().x();
                    y = o.get().y();
                }
                default -> {
                    // MAIN is placed by resolveMain; UNPLACED never enters a host;
                    // CENTER is an overlay (handled above).
                    return null;
                }
            }
        }
        // The offset nudges only this panel, after placement (siblings already
        // stacked on its un-nudged extent).
        return new Placed(e, x + pos.dx(), y + pos.dy(), size[0], size[1]);
    }

    /** The first MAIN entry of a standalone host (there is at most one; see {@link #add}). */
    private @Nullable Entry mainEntry() {
        for (Entry e : entries) {
            if (e.position().mode() == PanelPosition.Mode.MAIN) return e;
        }
        return null;
    }

    private record MainFrame(Reference frame, Placed placed) {}

    /** Title strip at the top of a container frame, and the band reserved above a standalone one. */
    private static final int TITLE_STRIP = 14;

    /**
     * The main frame: the main panel centred on the screen, with the title's room
     * reserved per {@link TitleBand}. The main panel gets the centred width budget and
     * (when {@link #autoFitMain} allows) a height budget, so it wraps and
     * auto-scrolls rather than running off the screen.
     *
     * <p>The main panel makes room for the panels placed outside it: both budgets
     * leave out the bands its shown {@link OutsideRegion} panels need (see
     * {@link #outsideBands}), and the frame is pushed off a screen edge only as far
     * as a band needs. So a panel above a full-height main panel always fits; the
     * main panel shrinks instead. A main panel with room to spare stays centred.
     */
    private MainFrame resolveMain(Entry main, int sw, int sh) {
        Panel panel = main.panel();
        int pad = main.padding();
        int m = RegionConstants.SCREEN_EDGE_MARGIN;
        int[] band = outsideBands(sw, sh); // top, bottom, left, right
        feedWidth(panel, RegionMath.availableScreenEdgeWidth(sw, m) - band[2] - band[3], pad);
        int title = titleBand == TitleBand.NONE ? 0 : TITLE_STRIP;
        if (autoFitMain.test(panel)) {
            panel.setAvailableContentHeight(sh - 2 * m - title - band[0] - band[1] - 2 * pad);
        }
        int[] size = measure(panel, pad);
        int strip = titleBand == TitleBand.IN_FRAME ? TITLE_STRIP : 0;
        int frameW = size[0];
        int frameH = size[1] + strip;
        int left = (sw - frameW) / 2;
        int top = (sh - frameH) / 2;
        // An outside band pushes the frame off that screen edge only as far as the
        // band needs. Far edges first, so when nothing fits the top/left band wins.
        if (band[1] > 0) top = Math.min(top, sh - m - band[1] - frameH);
        if (band[3] > 0) left = Math.min(left, sw - m - band[3] - frameW);
        if (band[2] > 0) left = Math.max(left, m + band[2]);
        // A standalone title draws at the screen top, outside the frame: keep a tall
        // frame below it. With no title the frame only keeps the safe-area margin.
        if (titleBand == TitleBand.SCREEN_TOP) top = Math.max(top, m + TITLE_STRIP + band[0]);
        else if (titleBand == TitleBand.NONE) top = Math.max(top, m + band[0]);
        else if (band[0] > 0) top = Math.max(top, m + band[0]);
        Reference frame = new Reference(left, top, frameW, frameH);
        Placed placed = new Placed(main, left + main.position().dx(),
                top + strip + main.position().dy(), size[0], size[1]);
        return new MainFrame(frame, placed);
    }

    /**
     * The room the shown {@link OutsideRegion} panels need beside the main frame, as
     * {top, bottom, left, right} including the frame gap. A region that flows along
     * the frame edge (TOP_ALIGN_*, LEFT_ALIGN_*, ...) needs its tallest (or widest)
     * panel; TOP_CENTER and BOTTOM_CENTER stack away from the frame and need their
     * sum plus gaps. Each side takes its largest region. Overlays and in-frame CENTER
     * panels need no band.
     *
     * <p>ponytail: panels are measured against the screen-wide budget here, before the
     * frame exists; {@link #place} re-feeds the anchor budget. A top or bottom panel that
     * wraps taller under the narrower anchor budget can still miss its band. Measure
     * twice (frame, then bands) if that ever shows up.
     */
    private int[] outsideBands(int sw, int sh) {
        int m = RegionConstants.SCREEN_EDGE_MARGIN;
        int gap = RegionConstants.MENU_STACK_GAP;
        Map<OutsideRegion, Integer> need = new HashMap<>();
        for (Entry e : entries) {
            Panel p = e.panel();
            if (e.position().mode() != PanelPosition.Mode.REGION || !isShown(p) || p.isOverlayPositioned()) continue;
            OutsideRegion r = Objects.requireNonNull(e.position().region());
            if (r == OutsideRegion.CENTER) continue;
            boolean sideways = switch (r) {
                case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM, RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM -> true;
                default -> false;
            };
            feedWidth(p, RegionMath.availableScreenEdgeWidth(sw, m), e.padding());
            int[] size = measure(p, e.padding());
            int across = sideways ? size[0] : size[1];
            if (r == OutsideRegion.TOP_CENTER || r == OutsideRegion.BOTTOM_CENTER) {
                need.merge(r, across + gap, Integer::sum);
            } else {
                need.merge(r, across + gap, Math::max);
            }
        }
        int top = 0, bottom = 0, left = 0, right = 0;
        for (var n : need.entrySet()) {
            int v = n.getValue();
            switch (n.getKey()) {
                case TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT, TOP_CENTER -> top = Math.max(top, v);
                case BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT, BOTTOM_CENTER -> bottom = Math.max(bottom, v);
                case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM -> left = Math.max(left, v);
                case RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM -> right = Math.max(right, v);
                default -> { }
            }
        }
        return new int[]{top, bottom, left, right};
    }

    /** Outer size: content (the panel's own, or the host's {@link #contentSize}) plus padding. */
    private int[] measure(Panel panel, int pad) {
        int w, h;
        if (contentSize != null) {
            int[] c = contentSize.apply(panel);
            w = c[0];
            h = c[1];
        } else {
            w = panel.getWidth();
            h = panel.getHeight();
        }
        return new int[]{w + 2 * pad, h + 2 * pad};
    }

    /** The width budget: outer room minus padding, fed before measuring. */
    private static void feedWidth(Panel panel, int outerRoom, int pad) {
        panel.setAvailableContentWidth(outerRoom - 2 * pad);
    }

    /**
     * The anchor-aware budget for a REGION panel: the room its anchor edge leaves
     * toward the screen edge, on both axes, so it wraps and auto-scrolls instead of
     * overflowing.
     */
    private static void feedRegionBudget(Panel panel, OutsideRegion region, Reference frame,
                                         int pad, int sw, int sh) {
        int m = RegionConstants.SCREEN_EDGE_MARGIN;
        panel.setAvailableContentWidth(RegionMath.availableMenuWidth(region, frame, sw, m) - 2 * pad);
        panel.setAvailableContentHeight(RegionMath.availableMenuHeight(region, frame, sh, m) - 2 * pad);
    }

    private void warnOnce(Entry e, String what) {
        if (!warned.add(e)) return;
        LOGGER.warn("[PanelHost] Panel '{}' on the {} {}; not placed until it fits.",
                e.panel().id(), kind.label(), what);
    }

    // ── Render ─────────────────────────────────────────────────────────

    /** How many live panels are rendering right now (render thread only). */
    private static int liveRenderDepth = 0;

    /**
     * Whether a panel that owns the pointer is rendering right now. Its own tooltips
     * are its content and pass the tooltip suppressor even over its own claim.
     */
    public static boolean renderingLivePanel() {
        return liveRenderDepth > 0;
    }

    /**
     * Renders the placed panels of {@code layer}: background, the host's decoration,
     * elements (base then overlay pass), then the panel-level tooltip. A panel the
     * pointer policy marks inert renders with the {@code -1} mouse sentinel, so its
     * elements show no hover and queue no tooltip. The HUD host is never live.
     */
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                       LayerPlan.Layer layer, PointerPolicy pointer) {
        for (Placed p : placed()) {
            Panel panel = p.panel();
            if (LayerPlan.layerOf(panel) != layer) continue;
            boolean live = kind != Kind.HUD && pointer.live(this, p);
            int mx = live ? mouseX : -1;
            int my = live ? mouseY : -1;
            if (panel.getStyle() != PanelStyle.NONE) {
                PanelRendering.renderPanel(graphics, p.x(), p.y(), p.w(), p.h(), panel.getStyle());
            }
            if (decoration != null) decoration.draw(graphics, p);
            RenderContext ctx = new RenderContext(graphics, p.contentX(), p.contentY(), mx, my, panel.isDisabled());
            if (live) liveRenderDepth++;
            try {
                // Base pass, then the overlay pass (an open popover on top of every
                // sibling), through the one child dispatch.
                List<PanelElement> shown = shownElements(panel);
                ChildDispatch.render(shown, ctx);
                ChildDispatch.renderOverlay(shown, ctx);
                panel.maybeQueueTooltip(graphics, p.x(), p.y(), p.w(), p.h(), mx, my, ctx.hasMouseInput());
            } finally {
                if (live) liveRenderDepth--;
            }
        }
    }

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * Routes a click to this host's panels, topmost first: an element's open
     * overlay takes it exclusively, then the first element whose hit test contains
     * the point and consumes it (both through {@link ChildDispatch}, with each
     * panel's own input context). With a modal up only modal panels are eligible.
     *
     * @return whether an element consumed the click
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean modalUp) {
        if (kind == Kind.HUD) return false;
        List<Placed> placed = placed();
        // Every panel's open overlays first: a popover is on top of every panel.
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placed p = placed.get(i);
            if (modalUp && !p.panel().tracksAsModal()) continue;
            if (clickOverlay(p, mouseX, mouseY, button)) return true;
        }
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placed p = placed.get(i);
            if (modalUp && !p.panel().tracksAsModal()) continue;
            if (ChildDispatch.mouseClicked(shownElements(p.panel()), input(p, mouseX, mouseY), button)) return true;
        }
        return false;
    }

    /** Routes a click to one claimed panel. @return whether an element consumed it */
    public boolean mouseClicked(Placed target, double mouseX, double mouseY, int button) {
        if (kind == Kind.HUD) return false;
        return ChildDispatch.mouseClicked(shownElements(target.panel()), input(target, mouseX, mouseY), button);
    }

    /** An open overlay of one of the panel's elements under the point takes the click, exclusively. */
    private static boolean clickOverlay(Placed p, double mouseX, double mouseY, int button) {
        List<PanelElement> shown = shownElements(p.panel());
        InputContext in = input(p, mouseX, mouseY);
        if (ChildDispatch.overlayOwner(shown, in) == null) return false;
        ChildDispatch.mouseClicked(shown, in, button);
        return true;
    }

    /** Routes a scroll, like {@link #mouseClicked(double, double, int, boolean)}. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 boolean modalUp) {
        if (kind == Kind.HUD) return false;
        List<Placed> placed = placed();
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placed p = placed.get(i);
            if (modalUp && !p.panel().tracksAsModal()) continue;
            if (mouseScrolled(p, mouseX, mouseY, scrollX, scrollY)) return true;
        }
        return false;
    }

    /** Routes a scroll to one claimed panel. */
    public boolean mouseScrolled(Placed target, double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (kind == Kind.HUD) return false;
        return ChildDispatch.mouseScrolled(shownElements(target.panel()), input(target, mouseX, mouseY),
                scrollX, scrollY);
    }

    /**
     * Offers a release to every shown element, not hit-tested: a drag that started
     * on an element ends there wherever the cursor now is. Not modal-filtered, and
     * not gated by disabled, so a drag begun before a modal opened (or before the
     * panel was disabled) still finishes.
     */
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (kind == Kind.HUD) return;
        for (Placed p : placed()) {
            ChildDispatch.mouseReleased(shownElements(p.panel()), input(p, mouseX, mouseY), button);
        }
    }

    /**
     * Offers a key to the shown elements, topmost panel first, until one consumes it.
     * Not hit-tested (keys are not pointer-localised). With a modal up only modal
     * panels are eligible; a disabled panel's elements take none.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers, boolean modalUp) {
        if (kind == Kind.HUD) return false;
        List<Placed> placed = placed();
        double mx = currentMouseX(), my = currentMouseY();
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placed p = placed.get(i);
            if (modalUp && !p.panel().tracksAsModal()) continue;
            if (ChildDispatch.keyPressed(shownElements(p.panel()), input(p, mx, my), keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells every shown element about a click, so an open popover the click fell
     * outside of closes even when something else consumes the click. Each element
     * self-guards.
     */
    public void notifyOutsideClick(double mouseX, double mouseY) {
        if (kind == Kind.HUD) return;
        for (Placed p : placed()) {
            ChildDispatch.notifyClickOutside(shownElements(p.panel()), input(p, mouseX, mouseY));
        }
    }

    /** The mouse position in GUI coordinates, for a key event (which carries none). */
    private static double currentMouseX() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return -1;
        return mc.mouseHandler.getScaledXPos(mc.getWindow());
    }

    private static double currentMouseY() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return -1;
        return mc.mouseHandler.getScaledYPos(mc.getWindow());
    }

    /**
     * Element lifecycle on (re)init: detach then attach every element of every entry,
     * so a widget-wrapping element (TextField, Slider, and the vanilla stand-in every
     * button, toggle, checkbox and radio registers) re-registers after a resize,
     * which clears widgets without calling {@code removed()}.
     */
    public void attach(Screen screen) {
        for (Entry e : entries) {
            List<PanelElement> elements = e.panel().getElements();
            ChildDispatch.detach(elements, screen);
            ChildDispatch.attach(elements, screen);
        }
    }

    /** Element lifecycle on close. */
    public void detach(Screen screen) {
        for (Entry e : entries) ChildDispatch.detach(e.panel().getElements(), screen);
    }

    // ── Registering mod (the tie-break after priority) ─────────────────

    /**
     * The mod that called into MenuKit: the first stack frame outside MenuKit's
     * packages, matched to its Fabric mod by where its class was loaded from. Called
     * at registration only, never per frame. Falls back to the caller's package so
     * the sort key stays total.
     */
    public static String captureCallerModId() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> !c.getPackageName().startsWith("com.trevlar.menukit"))
                        .map(PanelHost::findModIdForClass)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("zzz_unknown"));
    }

    private static String findModIdForClass(Class<?> caller) {
        try {
            var domain = caller.getProtectionDomain();
            if (domain == null || domain.getCodeSource() == null) return caller.getPackageName();
            URL url = domain.getCodeSource().getLocation();
            if (url == null) return caller.getPackageName();
            Path callerPath = Paths.get(url.toURI()).toAbsolutePath().normalize();
            // Direct match: a production jar, or a dev mod whose origin contains the class.
            for (var mod : FabricLoader.getInstance().getAllMods()) {
                for (Path modPath : mod.getOrigin().getPaths()) {
                    Path p = modPath.toAbsolutePath().normalize();
                    if (callerPath.equals(p) || callerPath.startsWith(p)) return mod.getMetadata().getId();
                }
            }
            // Dev fallback: resources and classes are sibling dirs under one 'build'.
            for (var mod : FabricLoader.getInstance().getAllMods()) {
                for (Path modPath : mod.getOrigin().getPaths()) {
                    Path build = buildAncestor(modPath.toAbsolutePath().normalize());
                    if (build != null && callerPath.startsWith(build)) return mod.getMetadata().getId();
                }
            }
        } catch (Exception ignored) {
            // fall through to the package-name fallback
        }
        return caller.getPackageName();
    }

    private static @Nullable Path buildAncestor(Path path) {
        for (Path p = path; p != null; p = p.getParent()) {
            if ("build".equals(String.valueOf(p.getFileName()))) return p;
        }
        return null;
    }
}
