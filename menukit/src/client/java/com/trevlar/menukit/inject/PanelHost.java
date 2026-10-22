package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.element.ChildDispatch;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.api.panel.WrapsPanelAdapter;
import com.trevlar.menukit.core.RegionMath;
import com.trevlar.menukit.core.VanillaLook;
import com.trevlar.menukit.api.element.RenderContext;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.HumanoidArm;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One place panels live. Each of the five contexts makes a host: a container
 * screen, a non-container vanilla screen, an anchor, the HUD, and a standalone
 * screen ({@code MKScreen}, a library's own screen). The contexts stay
 * distinct, but stacking, budgets, rendering, claims and dispatch are written once,
 * here.
 *
 * <h2>What a host owns</h2>
 * <ul>
 *   <li><b>Entries, sorted.</b> One {@link Entry} per (panel, host, mode scope): the
 *       panel, the mode it is for, the position the host resolves, the content padding,
 *       and the sort key {@code (priority, modId, registration sequence)}. Sorted order is
 *       both the stacking order within a region and the z-order, topmost last. Nothing
 *       depends on which mod loaded first.</li>
 *   <li><b>The mode, every frame</b> (7.4.0): an entry shows only while its scope is the
 *       panel's effective one ({@link #effectiveScope}): the current mode's placement if the
 *       panel has one, else its {@code BOTH} one. So a panel with a survival placement here
 *       and a creative one in an anchor's host is in exactly one of them at a time.</li>
 *   <li><b>Origin resolution</b> ({@link #layout}): each shown entry's position is
 *       resolved against this frame's {@link Frame}: the reference rectangle (menu
 *       frame, anchor box, the HUD's hotbar, or the standalone screen's own main panel) and the
 *       screen size. One budget function feeds each panel the room it has before it
 *       is measured; one stacking prefix per region; one resolver per reference kind
 *       ({@link RegionMath#resolveMenu}, {@link RegionMath#resolveInside}).</li>
 *   <li><b>Render in layers</b> ({@link #render}): FLOW, POPOVER, OVERLAY, per
 *       {@link LayerPlan}, every panel drawn for the host's {@link ScreenKind}
 *       (its background, and every element through {@link RenderContext#kind()}).</li>
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

    /**
     * Panels with surface looks of their own (7.4.0): what each point of the panel is, for its
     * slot frames and its text, in place of the host's (one look for a whole Panel-look raised
     * panel). Only MenuKit's own panels have them, the container region's two, set from this
     * package ({@link #surfaceLooks(Panel, PackLook.SurfaceLooks)}), so they are no part of
     * {@code Panel}'s API. Weak: a panel built again lets the old one go.
     */
    private static final Map<Panel, PackLook.SurfaceLooks> SURFACE_LOOKS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Gives {@code panel} its own surface looks ({@link #SURFACE_LOOKS}). */
    static void surfaceLooks(Panel panel, PackLook.SurfaceLooks looks) {
        SURFACE_LOOKS.put(panel, Objects.requireNonNull(looks, "looks"));
    }

    // Types

    /** The five contexts. Each names what its host places against. */
    public enum Kind {
        /** A vanilla (or modded) container screen: REGION resolves around the menu frame. */
        CONTAINER("container screen"),
        /** A non-container vanilla screen (Options, title, ...): no frame, screen spots only. */
        VANILLA_SCREEN("vanilla screen"),
        /** One anchor on any screen: REGION resolves around the rectangle its library supplies. */
        ANCHOR("anchor"),
        /**
         * The in-game HUD: screen spots on the game window, and (since 7.1.0) REGION
         * around the vanilla hotbar; render only, no input.
         */
        HUD("HUD"),
        /** A standalone screen ({@code MKScreen}, a library's own screen): its own main panel is the frame. */
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
     * One panel in this host for one mode scope. The same {@link Panel} may sit in several
     * hosts (an anchor adapter targeting two anchors is in two anchor hosts, a panel with a
     * survival screen placement and a creative anchor placement is in the screen's host and
     * the anchor's), each with its own entry: registries are keyed by (panel, host), not by
     * panel identity. A panel may have an entry per scope in one host; at most one of them
     * shows at a time ({@link #isShown(Entry)}).
     *
     * @param panel    the panel
     * @param scope    the mode this entry is the panel's placement for, or {@code null} for a
     *                 position the caller gave rather than one of the panel's placements (the
     *                 7.3.0 forms), which shows in every mode as it always did
     * @param position the position this host resolves (the panel's own, or the
     *                 standalone normalization of an unplaced one)
     * @param padding  content padding between the panel's outer edge and its elements
     * @param modId    the registering mod: the tie-break after priority
     * @param seq      registration sequence: the last tie-break
     */
    public record Entry(Panel panel, @Nullable ModeScope scope, PanelPosition position, int padding, String modId, int seq) {}

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
     * @param sw          GUI-scaled screen width
     * @param sh          GUI-scaled screen height
     * @param reference   the rectangle REGION resolves around (menu frame, anchor
     *                    box), or {@code null} when the context has none. A standalone
     *                    host computes its own from its main panel.
     * @param screenFrame the screen's frame, which a side-region panel's frame band
     *                    runs to ({@link RegionMath#frameBandHeight}): the container
     *                    screen's {@code leftPos, topPos, imageWidth, imageHeight} for
     *                    a container or anchor host, so an anchor panel wraps
     *                    where the screen ends rather than where its anchor does;
     *                    {@code null} where there is no frame (a standalone host uses
     *                    its main frame instead).
     * @param offhand     the HUD only (since 7.1.0): the side vanilla draws the offhand
     *                    slot on beside the hotbar this frame, or {@code null} when it
     *                    draws none. {@link RegionMath#resolveHud} starts that side's
     *                    regions beyond the slot. Always {@code null} elsewhere.
     */
    public record Frame(int sw, int sh, @Nullable Reference reference, @Nullable Reference screenFrame,
                        @Nullable HumanoidArm offhand) {
        /** A frame with no offhand slot beside its reference (every host but the HUD). */
        public Frame(int sw, int sh, @Nullable Reference reference, @Nullable Reference screenFrame) {
            this(sw, sh, reference, screenFrame, null);
        }

        /** A frame whose reference is also the screen's frame (or a context with neither). */
        public Frame(int sw, int sh, @Nullable Reference reference) {
            this(sw, sh, reference, reference);
        }
    }

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
     * the point. A slot library's screen answers for its own slots, so its opaque
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

    // State

    private final Kind kind;
    /**
     * The kind of screen this host's panels are on (6.1.0): fixed when the host is
     * made, from the screen it serves ({@link ScreenKind#of}), so every panel on a
     * screen, its own and injected ones alike, draws for the same kind.
     */
    private final ScreenKind screenKind;
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

    /**
     * Non-null while {@link #layout()} is resolving entries: the placements this pass
     * has made so far. A panel's position can read another panel's {@link
     * Panel#placedBounds()} (that method's own example: a close button pinned to an
     * item panel's corner), which calls back into this same host's layout mid-pass. A
     * re-entrant call must never start a second pass (that pass would reach the same
     * panel, ask the same question, and recurse without end), so it answers from this
     * field instead of recomputing.
     */
    private @Nullable List<Placed> layoutInProgress;
    /** The last completed pass's placements, read by a re-entrant call for a panel this
     *  pass hasn't reached yet, so {@code placedBounds()} returns last frame's rectangle
     *  rather than {@code null} for one frame. */
    private List<Placed> lastPlaced = List.of();
    /** The last completed pass's main frame, paired with {@link #lastPlaced}. */
    private @Nullable Reference lastMain;

    private PanelHost(Kind kind, ScreenKind screenKind, Supplier<@Nullable Frame> frames, RegionMath.Insets insets) {
        this.kind = kind;
        this.screenKind = Objects.requireNonNull(screenKind, "screenKind");
        this.frames = frames;
        this.insets = insets;
    }

    // Each factory fixes the look (ScreenKind) by the one rule: a vanilla container
    // screen is PANEL; an anchor takes the look of the screen it is on; the HUD keeps the look it always had
    // (PANEL); any other screen is what ScreenKind.of says for it (DEFAULT for a
    // vanilla screen, the declared look for an MKScreen); a standalone screen passes
    // the look it declared.

    /** A container screen's host: REGION around the (chrome-extended) menu frame. */
    public static PanelHost container(Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.CONTAINER, ScreenKind.PANEL, frames, RegionMath.Insets.CHROME);
    }

    /**
     * A non-container screen's host: screen spots, 4px in. {@code screenKind} is the
     * screen's ({@link ScreenKind#of}): the Default look for any vanilla screen, an
     * {@code MKScreen}'s declared look for a panel injected into one.
     */
    public static PanelHost vanillaScreen(ScreenKind screenKind, Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.VANILLA_SCREEN, screenKind, frames, RegionMath.Insets.SCREEN);
    }

    /** One anchor's host: REGION around the anchor's box, in the look of the screen it is on. */
    public static PanelHost anchor(ScreenKind screenKind, Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.ANCHOR, screenKind, frames, RegionMath.Insets.CHROME);
    }

    /** The HUD's host: screen spots on the game window; render only. Draws as it always has (the Panel look). */
    public static PanelHost hud(Supplier<@Nullable Frame> frames) {
        return new PanelHost(Kind.HUD, ScreenKind.PANEL, frames, RegionMath.Insets.HUD);
    }

    /**
     * The HUD's frame on an {@code sw x sh} GUI: the window, with the vanilla hotbar as
     * the reference a REGION panel is placed around (since 7.1.0). No screen frame: a
     * side panel beside the hotbar has no frame band to wrap at. No offhand slot drawn
     * beside the hotbar.
     */
    public static Frame hudFrame(int sw, int sh) {
        return hudFrame(sw, sh, null);
    }

    /**
     * The HUD's frame with the offhand slot vanilla draws beside the hotbar on
     * {@code offhand}'s side this frame ({@code null}: none drawn). Regions on that
     * side then start beyond the slot ({@link RegionMath#resolveHud}).
     */
    public static Frame hudFrame(int sw, int sh, @Nullable HumanoidArm offhand) {
        return new Frame(sw, sh, RegionMath.hotbar(sw, sh), null, offhand);
    }

    /** A standalone screen's host: its main panel is the frame; {@code screenKind} is what the screen declared. */
    public static PanelHost standalone(ScreenKind screenKind, Supplier<@Nullable Frame> frames, TitleBand titleBand) {
        PanelHost host = new PanelHost(Kind.STANDALONE, screenKind, frames, RegionMath.Insets.CHROME);
        host.titleBand = titleBand;
        return host;
    }

    /** Which context this host is. */
    public Kind kind() { return kind; }

    /** The kind of screen this host's panels are on, which decides how they and their elements draw. */
    public ScreenKind screenKind() { return screenKind; }

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
     * slot library's screen panel also holds slots). Default: the panel's own.
     */
    public PanelHost contentSize(Function<Panel, int[]> size) { this.contentSize = size; return this; }

    /** Standalone: which live slots each panel owns (see {@link SlotOwnership}). */
    public PanelHost slotOwnership(SlotOwnership ownership) { this.slotOwnership = ownership; return this; }

    /** Standalone: drawn between a panel's background and its elements. */
    public PanelHost decoration(Decoration decoration) { this.decoration = decoration; return this; }

    // Entries

    /**
     * The origin a {@code Placement.at} position carries: its anchor's box's top-left, which
     * the host fills in ({@link #fromBox}). Asked directly it gives nothing, so the panel is
     * skipped rather than put anywhere else.
     */
    public static final java.util.function.Supplier<ScreenOrigin> BOX_ORIGIN = () -> null;

    /** Whether {@code position} is a {@code Placement.at} placement's: its origin is its anchor's box's top-left. */
    public static boolean fromBox(PanelPosition position) {
        return position.pixelOrigin() == BOX_ORIGIN;
    }

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
    public Entry add(Panel panel, PanelPosition position, int padding, String modId, int seq) {
        return add(panel, null, position, padding, modId, seq);
    }

    /**
     * Adds a panel's screen placements (those with no anchor), one entry per mode scope; a
     * panel that declares no placement at all is added unplaced, which this host rejects as
     * {@link #add(Panel, ModeScope, PanelPosition, int, String, int)} does.
     */
    public void addPlacements(Panel panel, int padding, String modId, int seq) {
        if (panel.placements().isEmpty()) {
            add(panel, ModeScope.BOTH, PanelPosition.UNPLACED, padding, modId, seq);
            return;
        }
        for (Map.Entry<ModeScope, Placement> p : panel.placements().entrySet()) {
            if (p.getValue().anchor() == null) add(panel, p.getKey(), p.getValue().position(), padding, modId, seq);
        }
    }

    /** Adds a panel's placements on {@code anchor}, one entry per mode scope: this must be that anchor's host. */
    public void addAnchored(Panel panel, Anchor anchor, int padding, String modId, int seq) {
        for (Map.Entry<ModeScope, Placement> p : panel.placements().entrySet()) {
            if (p.getValue().anchor() == anchor) add(panel, p.getKey(), p.getValue().position(), padding, modId, seq);
        }
    }

    /**
     * Adds a panel for one mode scope ({@code null}: a position the caller gave, shown in
     * every mode), in sorted place.
     *
     * @throws IllegalArgumentException if this context cannot place the position
     */
    public synchronized Entry add(Panel panel, @Nullable ModeScope scope, PanelPosition position, int padding,
                                  String modId, int seq) {
        requireSupported(kind, panel, position);
        // The padding is measured against MenuKit's own frame; a thicker frame on this
        // kind of screen (vanilla's popup frame) widens it by the difference.
        Entry entry = new Entry(panel, scope, position, padding + panel.frameExtra(screenKind), modId, seq);
        if (position.mode() == PanelPosition.Mode.MAIN) requireOneMain(entry);
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
     * {@link #requireSupported} for each of {@code panel}'s placements: its screen placements
     * by {@code kind}, its anchored ones by an anchor's rule. A panel with no placement is
     * unplaced, which no adapter's context takes.
     */
    public static void requirePlacements(Kind kind, Panel panel) {
        if (panel.placements().isEmpty()) {
            requireSupported(kind, panel, PanelPosition.UNPLACED);
            return;
        }
        for (Placement p : panel.placements().values()) {
            requireSupported(p.anchor() == null ? kind : Kind.ANCHOR, panel, p.position());
        }
    }

    /**
     * The placement rule per context. Every context takes PIXEL and CENTER (an
     * overlay floats centered on the screen wherever it is hosted); the rest depend
     * on what the context has to place against.
     */
    public static void requireSupported(Kind kind, Panel panel, PanelPosition position) {
        PanelPosition.Mode mode = position.mode();
        boolean ok = switch (mode) {
            case UNPLACED -> false;
            case PIXEL, CENTER -> true;
            case MAIN -> kind == Kind.STANDALONE;
            case REGION -> kind != Kind.VANILLA_SCREEN;
            case SCREEN_ANCHOR -> kind != Kind.ANCHOR;
        };
        if (mode == PanelPosition.Mode.REGION && position.region() == null) ok = false;
        if (mode == PanelPosition.Mode.SCREEN_ANCHOR && position.screenAnchor() == null) ok = false;
        if (mode == PanelPosition.Mode.PIXEL && position.pixelOrigin() == null) ok = false;
        if (!ok) {
            String hint = switch (kind) {
                case CONTAINER -> "region(OutsideRegion), screenAnchor(InsideRegion), center() or pixel(...)";
                case VANILLA_SCREEN -> "screenAnchor(InsideRegion), center() or pixel(...)";
                case HUD -> "screenAnchor(InsideRegion), region(OutsideRegion) (around the hotbar), center() or pixel(...)";
                case ANCHOR -> "region(OutsideRegion), center() or pixel(...)";
                case STANDALONE -> "main(), region(OutsideRegion), screenAnchor(InsideRegion), center() or pixel(...)";
            };
            throw new IllegalArgumentException("MenuKit: panel '" + panel.id() + "' is "
                    + position.describe() + ", which a " + kind.label() + " cannot place. "
                    + "Declare .in(ModeScope, Placement.screen(PanelPosition." + hint + ")) on the panel.");
        }
    }

    // Shown and claim rules

    /**
     * The one "is this panel shown" answer: its own visibility (imperative or
     * {@code visibleWhen}). A hidden panel is skipped before layout, so it is never
     * measured, never placed, and its pixel supplier never runs.
     */
    private static boolean isShown(Panel panel) {
        return panel.isVisible();
    }

    /** The "is this entry shown" answer: its panel is, and the entry is for the mode now ({@link #forNow}). */
    public static boolean isShown(Entry e) {
        return forNow(e) && isShown(e.panel());
    }

    /** Whether an entry is for the mode now: {@link #shownIn} the current mode. */
    private static boolean forNow(Entry e) {
        return shownIn(e, currentMode());
    }

    /**
     * Whether an entry shows in {@code mode} ({@code null}: no player): its scope is its panel's
     * placement for that mode ({@link #effectiveScope(Panel, ModeScope)}), or it has none (a
     * caller's position, shown in every mode).
     */
    private static boolean shownIn(Entry e, @Nullable ModeScope mode) {
        return e.scope() == null || e.scope() == effectiveScope(e.panel(), mode);
    }

    // The mode (7.4.0)

    private static final java.util.function.Supplier<@Nullable ModeScope> PLAYER_MODE = () -> {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : ModeScope.of(mc.player);
    };
    /** The current mode, read on every call. Swapped by MenuKit's checks, like {@code PresentedSlots}' drawer. */
    private static java.util.function.Supplier<@Nullable ModeScope> mode = PLAYER_MODE;

    /** For MenuKit's checks: swaps where the current mode comes from, and back with {@code null}. */
    @org.jetbrains.annotations.VisibleForTesting
    public static void modeForChecks(java.util.function.@Nullable Supplier<@Nullable ModeScope> m) {
        mode = m == null ? PLAYER_MODE : m;
    }

    /**
     * The mode now: the player's ({@link ModeScope#of}), or {@code null} with no player. Ask a
     * scope about it with {@link ModeScope#covers}: the one place the mode is read, so the
     * checks swap it here.
     */
    public static @Nullable ModeScope currentMode() {
        return mode.get();
    }

    /**
     * Which of {@code panel}'s placements applies now: the current mode's if it declared
     * one, else {@link ModeScope#BOTH}, which is also the answer for a panel with no
     * placements at all (it is {@code BOTH} by nature). A panel whose only placements are
     * for another mode gets {@code BOTH} too, which it has no entry for, so it doesn't show.
     */
    public static ModeScope effectiveScope(Panel panel) {
        return effectiveScope(panel, currentMode());
    }

    /** {@link #effectiveScope(Panel)} in {@code mode} ({@code null}: no player): that mode's placement if declared, else BOTH. */
    private static ModeScope effectiveScope(Panel panel, @Nullable ModeScope mode) {
        return mode != null && panel.placements().containsKey(mode) ? mode : ModeScope.BOTH;
    }

    // Player-moved panels (7.4.0)

    /**
     * Whether the player may move this entry's panel: not one marked {@code fixed()}, nor one
     * that isn't part of the screen's layout: a background panel, an overlay (centered, dim or
     * modal, which every MenuKit dialog is), a popover, or a standalone screen's main panel.
     */
    public static boolean movable(Entry e) {
        Panel p = e.panel();
        PanelPosition.Mode mode = e.position().mode();
        return !p.isFixed() && !p.isBackground() && !p.isPopover() && !p.isOverlayPositioned()
                && mode != PanelPosition.Mode.MAIN && mode != PanelPosition.Mode.CENTER;
    }

    /** Where the player put this entry's panel ({@link PanelPositions#lookup}), or {@code null}: its declaration, or not movable. */
    public static PanelPositions.@Nullable Spot movedTo(Entry e) {
        return movable(e) ? PanelPositions.lookup(e.panel().id()) : null;
    }

    /**
     * The claim rule: does this placed panel take the point, so whatever lies
     * beneath it is inert?
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
     * A {@link Panel#isBackground() background} panel claims nothing at all: it is
     * drawn under everything on the screen, so nothing on the screen is beneath it.
     *
     * <p>The active overlay of the topmost modal is the one exception, and it is not
     * decided here: a shown modal claims every point on its surface, which
     * {@link ScreenPanelRegistry} applies before it asks any host.
     */
    public boolean claimsPoint(Placed placed, double mouseX, double mouseY) {
        Panel panel = placed.panel();
        // A background panel (7.4.0) is under everything on the screen, so it takes no
        // point from anything: whatever lies over it stays live.
        if (panel.isBackground()) return false;
        InputContext in = input(placed, mouseX, mouseY);
        List<PanelElement> shown = shownElements(panel);
        if (ChildDispatch.overlayOwner(shown, in) != null) return true;
        if (panel.isOpaque()) {
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
     * a slot (a created slot in an element, or a standalone screen's own slots) claims its
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
     * The input context for a placed panel's elements: its content origin,
     * the event's point, whether the panel is disabled (the cascade), and this
     * host's kind of screen, as its render context carries.
     */
    public InputContext input(Placed placed, double mouseX, double mouseY) {
        return new InputContext(placed.contentX(), placed.contentY(), mouseX, mouseY, placed.panel().isDisabled(),
                screenKind);
    }

    /** The panel's shown elements (the one isShown for elements). */
    private static List<PanelElement> shownElements(Panel panel) {
        List<PanelElement> all = panel.getElements();
        List<PanelElement> out = new ArrayList<>(all.size());
        for (PanelElement el : all) {
            if (el.isVisible()) out.add(el);
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

    /**
     * The topmost placed panel of any layer that claims the point, or {@code null}:
     * the layers top down (OVERLAY, POPOVER, FLOW), the one order
     * {@link LayerPlan#topDown} gives. A standalone screen asks this of its own host.
     */
    public @Nullable Placed claimAt(double mouseX, double mouseY) {
        for (LayerPlan.Drawn d : LayerPlan.topDown(List.of(this))) {
            if (claimsPoint(d.placed(), mouseX, mouseY)) return d.placed();
        }
        return null;
    }

    /** Whether any shown panel matches ({@code Panel::dimsBehind}, {@code Panel::tracksAsModal}). */
    public boolean anyShown(Predicate<Panel> test) {
        for (Entry e : entries) {
            if (isShown(e) && test.test(e.panel())) return true;
        }
        return false;
    }

    /** The topmost shown {@code tracksAsModal} panel, or {@code null}. */
    public @Nullable Panel topmostModal() {
        List<Entry> es = entries;
        for (int i = es.size() - 1; i >= 0; i--) {
            Panel p = es.get(i).panel();
            if (isShown(es.get(i)) && p.tracksAsModal()) return p;
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

    // Layout

    /** This frame's placements, bottom to top. */
    public List<Placed> placed() {
        return layout().placed();
    }

    /**
     * Resolves every shown entry against this frame. Pure apart from feeding each
     * panel its size budget and this host's kind of screen (a panel measures itself
     * against the room it has, and an auto-scrolling one in the Default look lays
     * its content inside the list band's inset) and a one-time overflow warning.
     *
     * <p>Re-entrant: a panel's position can read another panel's {@link
     * Panel#placedBounds()}, which calls back into {@link #placed()} and so here,
     * while this very call is still resolving entries. Rather than start a second
     * pass (which would reach the same panel, ask the same question, and recurse
     * without end), a re-entrant call answers from what this pass has placed so
     * far, falling back to the last completed pass for an entry not yet reached.
     */
    public Layout layout() {
        if (layoutInProgress != null) return new Layout(mergeWithLastPlaced(layoutInProgress), lastMain);
        Frame frame = frames.get();
        if (frame == null || entries.isEmpty()) return Layout.EMPTY;
        int sw = frame.sw(), sh = frame.sh();
        Reference reference = frame.reference();
        for (Entry e : entries) e.panel().setScreenKind(screenKind);

        // The frame a side-region panel's band runs to: the screen's frame, or a
        // standalone host's main frame once it is resolved below.
        Reference screenFrame = frame.screenFrame();
        List<Placed> out = new ArrayList<>();
        layoutInProgress = out; // from here, a re-entrant call sees every entry placed so far
        Reference main;
        try {
            main = null;
            Placed mainPlaced = null;
            if (kind == Kind.STANDALONE) {
                Entry mainEntry = mainEntry();
                if (mainEntry != null) {
                    MainFrame mf = resolveMain(mainEntry, sw, sh);
                    main = mf.frame();
                    reference = main;
                    screenFrame = main;
                    if (isShown(mainEntry)) mainPlaced = mf.placed();
                }
            }

            // Where each region's stack ends so far this frame (OutsideRegion and
            // InsideRegion keys never collide: different enum types). A region with no
            // entry has nothing stacked in it yet, so its next panel is the first.
            Map<Object, Integer> stackEnd = new HashMap<>();
            for (Entry e : entries) {
                if (!isShown(e)) continue;
                // An adapter's panel with nothing to show (every element hidden or empty,
                // such as slot elements whose slots this menu lacks) is not placed: no
                // empty frame, no claim (7.3.0).
                if (placesAdapters() && e.panel().showsNothing()) continue;
                if (mainPlaced != null && e == mainPlaced.entry()) {
                    out.add(mainPlaced);
                    continue;
                }
                // A panel the player moved (7.4.0) sits where they put it, but only where its
                // declaration would place it this frame (its anchor present, room for it), and
                // out of its region's flow: its declared place is worked out against a copy of
                // the stacks, so the siblings after it close up.
                PanelPositions.Spot moved = movedTo(e);
                Placed p = place(e, sw, sh, reference, screenFrame, frame.offhand(),
                        moved == null ? stackEnd : new HashMap<>(stackEnd));
                if (p != null && moved != null) p = new Placed(e, moved.left(p.w(), sw), moved.top(p.h(), sh), p.w(), p.h());
                if (p != null) out.add(p);
            }
        } finally {
            layoutInProgress = null;
        }
        Layout result = new Layout(List.copyOf(out), main);
        lastPlaced = result.placed();
        lastMain = main;
        return result;
    }

    /**
     * {@code pass}, the placements made so far in an in-progress {@link #layout()}
     * call, plus, for any panel {@code pass} hasn't reached yet, that panel's
     * placement from the last completed pass, so a re-entrant {@link
     * Panel#placedBounds()} reads a real rectangle from a frame ago rather than
     * {@code null}, for every panel but the one asking (which has none yet either way).
     */
    private List<Placed> mergeWithLastPlaced(List<Placed> pass) {
        if (lastPlaced.isEmpty()) return pass;
        List<Placed> merged = new ArrayList<>(pass);
        Set<Panel> placedThisPass = new HashSet<>();
        for (Placed p : pass) placedThisPass.add(p.panel());
        for (Placed p : lastPlaced) {
            if (placedThisPass.add(p.panel())) merged.add(p);
        }
        return merged;
    }

    /** Whether this host places panels mods declared through an adapter (a screen's, an anchor's). */
    private boolean placesAdapters() {
        return kind == Kind.CONTAINER || kind == Kind.VANILLA_SCREEN || kind == Kind.ANCHOR;
    }

    /** Resolves one entry, or {@code null} when it has no place this frame. */
    private @Nullable Placed place(Entry e, int sw, int sh, @Nullable Reference reference,
                                   @Nullable Reference screenFrame, @Nullable HumanoidArm offhand,
                                   Map<Object, Integer> stackEnd) {
        Panel panel = e.panel();
        PanelPosition pos = e.position();
        int pad = e.padding();
        int x, y;
        int[] size;

        if (panel.isOverlayPositioned()) {
            // The single overlay rule: whatever its declared mode, an overlay (a
            // center() panel, or any dim or modal panel) floats centered on the screen
            // window and draws on top. It grows to the screen width, not an anchor's.
            feedWidth(panel, RegionMath.availableScreenEdgeWidth(sw, RegionConstants.SCREEN_EDGE_MARGIN), pad);
            panel.setFrameHeight(-1);
            size = measure(panel, pad);
            x = (sw - size[0]) / 2;
            y = (sh - size[1]) / 2;
        } else {
            switch (pos.mode()) {
                case PIXEL -> {
                    // The consumer owns the geometry: natural size, no budget, the
                    // supplier's point verbatim. Null = no anchor this frame. A
                    // Placement.at position takes its point from the reference (its
                    // anchor's box), the same way: no stacking, no budget, no clamp.
                    ScreenOrigin o;
                    if (fromBox(pos)) {
                        if (reference == null) return null;
                        o = new ScreenOrigin(reference.leftPos(), reference.topPos());
                    } else {
                        o = Objects.requireNonNull(pos.pixelOrigin()).get();
                    }
                    if (o == null) return null;
                    panel.setFrameHeight(-1);
                    size = measure(panel, pad);
                    x = o.x();
                    y = o.y();
                }
                case REGION -> {
                    if (reference == null) return null;
                    OutsideRegion region = Objects.requireNonNull(pos.region());
                    // The panel's own gap: off the reference for every panel, off the
                    // sibling before it for every panel but the first. Every region
                    // context (menu, anchor, HUD) defaults to MENU_STACK_GAP.
                    int gap = pos.gapOr(RegionConstants.MENU_STACK_GAP);
                    int before = RegionMath.stackPrefix(region, stackEnd.get(region), gap);
                    feedRegionBudget(panel, region, reference, pad, sw, sh, gap);
                    feedFrameBand(panel, region, reference, screenFrame, before, pad);
                    size = measure(panel, pad);
                    // On the HUD the reference is the hotbar, flush with the window's
                    // bottom: no safe-area clamp, which would lift a panel aligned to
                    // the hotbar's bottom off it, and the side the offhand slot is
                    // drawn on starts beyond the slot (RegionMath.resolveHud).
                    var o = kind == Kind.HUD
                            ? RegionMath.resolveHud(region, reference, offhand, size[0], size[1], before, gap, sw, sh)
                            : RegionMath.resolveMenu(region, reference, size[0], size[1], before, gap, sw, sh);
                    if (o.isEmpty()) {
                        warnOnce(e, kind == Kind.HUD
                                ? "runs off the window in OutsideRegion." + region + " around the hotbar"
                                : "is larger than the screen's safe area in OutsideRegion." + region);
                        return null;
                    }
                    int axial = region.isHorizontalFlow() ? size[0] : size[1];
                    // The stack now ends past this panel. The next panel's gap is its
                    // own, added when it is placed, so a gap(0) panel sits flush on
                    // this one whatever gap this one declared.
                    stackEnd.put(region, RegionMath.stackEnd(region, before, axial, gap));
                    x = o.get().x();
                    y = o.get().y();
                }
                case SCREEN_ANCHOR -> {
                    InsideRegion spot = Objects.requireNonNull(pos.screenAnchor());
                    feedWidth(panel, RegionMath.availableScreenEdgeWidth(sw, insets.edge()), pad);
                    panel.setFrameHeight(-1);
                    size = measure(panel, pad);
                    // The first panel on a spot starts at the edge inset (prefix 0);
                    // each later one its own gap past the sibling before it, the
                    // host's stacking gap when it declares none (4 px on a vanilla
                    // screen, 2 px on the HUD and a screen's chrome).
                    Integer end = stackEnd.get(spot);
                    int before = end == null ? 0 : end + pos.gapOr(insets.gap());
                    var o = RegionMath.resolveInside(spot, sw, sh, size[0], size[1], before, insets);
                    if (o.isEmpty()) {
                        warnOnce(e, "overflows InsideRegion." + spot + " (" + before + "px of siblings above it)");
                        return null;
                    }
                    stackEnd.put(spot, before + size[1]);
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

    /**
     * The MAIN entry of a standalone host for the mode now: one a mode at most (see
     * {@link #requireOneMain}), the first whose scope is its panel's effective one, else the first.
     */
    private @Nullable Entry mainEntry() {
        Entry first = null;
        for (Entry e : entries) {
            if (e.position().mode() != PanelPosition.Mode.MAIN) continue;
            if (forNow(e)) return e;
            if (first == null) first = e;
        }
        return first;
    }

    /**
     * One main panel a mode. Refuses {@code added}, a MAIN entry, when some concrete mode
     * (SURVIVAL or CREATIVE) would show both it and a MAIN entry already here ({@link #shownIn}:
     * each panel's own placement for that mode first, then its BOTH one). So two BOTH mains
     * clash, and a BOTH main clashes with another panel's SURVIVAL main unless the BOTH one's
     * panel has a SURVIVAL placement of its own; one panel's mains never clash with each other,
     * since one panel shows one placement at a time. Only the two modes count: with no player
     * the first main in order frames the screen.
     *
     * @throws IllegalArgumentException naming both panels and the mode
     */
    private void requireOneMain(Entry added) {
        for (ModeScope mode : List.of(ModeScope.SURVIVAL, ModeScope.CREATIVE)) {
            if (!shownIn(added, mode)) continue;
            for (Entry e : entries) {
                if (e.position().mode() != PanelPosition.Mode.MAIN || !shownIn(e, mode)) continue;
                throw new IllegalArgumentException("MenuKit: panels '" + e.panel().id() + "' and '" + added.panel().id()
                        + "' are both main() in " + mode + " (each panel's " + mode + " placement, else its BOTH one); "
                        + "a screen has one main panel in each mode. Give one of them a placement of its own for " + mode
                        + " that isn't main().");
            }
        }
    }

    private record MainFrame(Reference frame, Placed placed) {}

    /** Title strip at the top of a container frame, and the band reserved above a standalone one. */
    private static final int TITLE_STRIP = 14;

    /**
     * The main frame: the main panel centered on the screen, with the title's room
     * reserved per {@link TitleBand}. The main panel gets the centered width budget and
     * (when {@link #autoFitMain} allows) a height budget, so it wraps and
     * auto-scrolls rather than running off the screen.
     *
     * <p>The main panel makes room for the panels placed outside it: both budgets
     * leave out the bands its shown {@link OutsideRegion} panels need (see
     * {@link #outsideBands}), and the frame is pushed off a screen edge only as far
     * as a band needs. So a panel above a full-height main panel always fits; the
     * main panel shrinks instead. A main panel with room to spare stays centered.
     */
    private MainFrame resolveMain(Entry main, int sw, int sh) {
        Panel panel = main.panel();
        int pad = main.padding();
        int m = RegionConstants.SCREEN_EDGE_MARGIN;
        int[] band = outsideBands(sw, sh); // top, bottom, left, right
        panel.setFrameHeight(-1); // the main panel is the frame; nothing beside it to wrap at
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
     * panel; the side-centered regions (TOP_CENTER, LEFT_CENTER, ...) stack away from the
     * frame and need their sum plus gaps. Each side takes its largest region. Overlays and in-frame CENTER
     * panels need no band.
     *
     * <p>Known limit: panels are measured against the screen-wide budget here, before
     * the frame exists; {@link #place} re-feeds the anchor budget. A top or bottom panel that
     * wraps taller under the narrower anchor budget can still miss its band. Measure
     * twice (frame, then bands) if that ever shows up.
     */
    private int[] outsideBands(int sw, int sh) {
        int m = RegionConstants.SCREEN_EDGE_MARGIN;
        Map<OutsideRegion, Integer> need = new HashMap<>();
        for (Entry e : entries) {
            Panel p = e.panel();
            if (e.position().mode() != PanelPosition.Mode.REGION || !isShown(e) || p.isOverlayPositioned()) continue;
            if (movedTo(e) != null) continue; // moved by the player: it needs no room beside the frame
            OutsideRegion r = Objects.requireNonNull(e.position().region());
            if (r == OutsideRegion.CENTER) continue;
            boolean sideways = switch (r) {
                case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM, LEFT_CENTER,
                     RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM, RIGHT_CENTER -> true;
                default -> false;
            };
            feedWidth(p, RegionMath.availableScreenEdgeWidth(sw, m), e.padding());
            int[] size = measure(p, e.padding());
            int across = sideways ? size[0] : size[1];
            // Each panel's own gap, as place() will put it: off the frame, and for
            // a side-centered region also off the panel before it.
            int gap = e.position().gapOr(RegionConstants.MENU_STACK_GAP);
            if (RegionMath.gapAlongStack(r)) {
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
                case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM, LEFT_CENTER -> left = Math.max(left, v);
                case RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM, RIGHT_CENTER -> right = Math.max(right, v);
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
                                         int pad, int sw, int sh, int gap) {
        int m = RegionConstants.SCREEN_EDGE_MARGIN;
        panel.setAvailableContentWidth(RegionMath.availableMenuWidth(region, frame, sw, m, gap) - 2 * pad);
        panel.setAvailableContentHeight(RegionMath.availableMenuHeight(region, frame, sh, m, gap) - 2 * pad);
    }

    /**
     * The frame band for a REGION panel: the content height from its top (its
     * siblings above it counted) down to where the screen's frame ends, for a side
     * region; {@code -1} for every other region or with no screen frame. One feed for
     * every host that places regions, so a container-screen panel, an anchor panel
     * and a panel beside a standalone screen's main frame all wrap at the frame the
     * same way. Fed before measuring, like the budgets: the panel's size depends on it.
     */
    private static void feedFrameBand(Panel panel, OutsideRegion region, Reference reference,
                                      @Nullable Reference screenFrame, int before, int pad) {
        int band = screenFrame == null ? -1 : RegionMath.frameBandHeight(region, reference, screenFrame, before);
        panel.setFrameHeight(band < 0 ? -1 : Math.max(0, band - 2 * pad));
    }

    private void warnOnce(Entry e, String what) {
        if (!warned.add(e)) return;
        LOGGER.warn("[PanelHost] Panel '{}' on the {} {}; not placed until it fits.",
                e.panel().id(), kind.label(), what);
    }

    // Render

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
     * Renders this host's placed panels of {@code layer}, in its sorted order. A
     * surface with several hosts draws through {@link LayerPlan#render} instead, which
     * interleaves the hosts in the one draw order.
     */
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                       LayerPlan.Layer layer, PointerPolicy pointer) {
        for (LayerPlan.Drawn d : LayerPlan.drawOrder(List.of(this), layer)) {
            render(graphics, mouseX, mouseY, d.placed(), pointer);
        }
    }

    /**
     * Renders one placed panel: background, the host's decoration, elements (base
     * then overlay pass), then the panel-level tooltip. A panel the pointer policy
     * marks inert renders with the {@code -1} mouse sentinel, so its elements show no
     * hover and queue no tooltip. The HUD host is never live.
     */
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                       Placed p, PointerPolicy pointer) {
        Panel panel = p.panel();
        boolean live = kind != Kind.HUD && pointer.live(this, p);
        int mx = live ? mouseX : -1;
        int my = live ? mouseY : -1;
        // The panel's background for the kind of screen: MenuKit's sprites in the
        // Panel look, vanilla's popup frame (and friends) in the Default
        // look, so a dialog in the Default look reads as vanilla's own.
        // A raised panel in the Panel look is a panel surface: it follows the resource
        // pack's container art, the chest's (controls keep their own sprite); the HUD's panels and
        // every other style keep their sprites. Its slot frames and text follow it too; a
        // panel with surface looks of its own (the container region's, per section) says
        // which look each point takes.
        boolean surface = screenKind == ScreenKind.PANEL && kind != Kind.HUD && panel.getStyle() == PanelStyle.RAISED;
        PackLook.SurfaceLooks surfaceLooks = SURFACE_LOOKS.get(panel);
        if (surfaceLooks == null && surface) {
            PackLook.Look look = PackLook.panel();
            surfaceLooks = (sx, sy) -> look;
        }
        if (panel.getStyle() != PanelStyle.NONE) {
            if (screenKind == ScreenKind.DEFAULT) {
                VanillaLook.panelBackground(graphics, p.x(), p.y(), p.w(), p.h(), panel.getStyle());
            } else if (surface) {
                PackLook.drawPanel(graphics, p.x(), p.y(), p.w(), p.h(), PackLook.panel());
            } else {
                PanelRendering.renderPanel(graphics, p.x(), p.y(), p.w(), p.h(), panel.getStyle());
            }
        }
        if (decoration != null) decoration.draw(graphics, p);
        RenderContext ctx = new RenderContext(graphics, p.contentX(), p.contentY(), mx, my, panel.isDisabled(),
                screenKind);
        if (live) liveRenderDepth++;
        PresentedSlots.beginPanel(panel);
        PackLook.SurfaceLooks surfaceBefore = PackLook.beginSurface(surfaceLooks);
        try {
            // Base pass, then the slots this panel shows on a container screen (7.3.0:
            // a panel's slots draw right after it, over its chrome and under the next
            // panel), then the overlay pass (an open dropdown on top of every sibling
            // and of the slots), through the one child dispatch.
            List<PanelElement> shown = shownElements(panel);
            ChildDispatch.render(shown, ctx);
            PresentedSlots.drawAfter(graphics, panel);
            ChildDispatch.renderOverlay(shown, ctx);
            panel.maybeQueueTooltip(graphics, p.x(), p.y(), p.w(), p.h(), mx, my, ctx.hasMouseInput());
        } finally {
            PresentedSlots.endPanel();
            PackLook.endSurface(surfaceBefore);
            if (live) liveRenderDepth--;
        }
    }

    // Input

    /** Routes a click to this host's panels alone; see {@link #mouseClicked(List, double, double, int, boolean)}. */
    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean modalUp) {
        return mouseClicked(List.of(this), mouseX, mouseY, button, modalUp);
    }

    /**
     * Routes a click to the panels of {@code hosts} (bottom to top), in the one draw
     * order read top down ({@link LayerPlan#topDown}: OVERLAY, POPOVER, FLOW, upper
     * host before lower): an element's open overlay takes it exclusively, then the
     * first element whose hit test contains the point and consumes it (both through
     * {@link ChildDispatch}, with each panel's own input context). So a click on a
     * popover reaches it before any FLOW panel under it, whichever host holds either.
     * With a modal up only modal panels are eligible. The HUD routes none.
     *
     * @return whether an element consumed the click
     */
    public static boolean mouseClicked(List<PanelHost> hosts, double mouseX, double mouseY,
                                       int button, boolean modalUp) {
        List<LayerPlan.Drawn> order = routable(hosts, modalUp);
        // Every panel's open overlays first: an open dropdown is on top of every panel.
        for (LayerPlan.Drawn d : order) {
            if (d.host().clickOverlay(d.placed(), mouseX, mouseY, button)) return true;
        }
        for (LayerPlan.Drawn d : order) {
            if (d.host().mouseClicked(d.placed(), mouseX, mouseY, button)) return true;
        }
        return false;
    }

    /**
     * The panels pointer input may reach, top down: the draw order reversed, without
     * the HUD (render only) and, with a modal up, without the panels that are not
     * modal.
     */
    private static List<LayerPlan.Drawn> routable(List<PanelHost> hosts, boolean modalUp) {
        List<LayerPlan.Drawn> out = new ArrayList<>();
        for (LayerPlan.Drawn d : LayerPlan.topDown(hosts)) {
            if (d.host().kind == Kind.HUD) continue;
            if (modalUp && !d.placed().panel().tracksAsModal()) continue;
            out.add(d);
        }
        return out;
    }

    /** Routes a click to one claimed panel. @return whether an element consumed it */
    public boolean mouseClicked(Placed target, double mouseX, double mouseY, int button) {
        if (kind == Kind.HUD) return false;
        return ChildDispatch.mouseClicked(shownElements(target.panel()), input(target, mouseX, mouseY), button);
    }

    /** An open overlay of one of the panel's elements under the point takes the click, exclusively. */
    private boolean clickOverlay(Placed p, double mouseX, double mouseY, int button) {
        List<PanelElement> shown = shownElements(p.panel());
        InputContext in = input(p, mouseX, mouseY);
        if (ChildDispatch.overlayOwner(shown, in) == null) return false;
        ChildDispatch.mouseClicked(shown, in, button);
        return true;
    }

    /** Routes a scroll to this host's panels alone; see {@link #mouseScrolled(List, double, double, double, double, boolean)}. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 boolean modalUp) {
        return mouseScrolled(List.of(this), mouseX, mouseY, scrollX, scrollY, modalUp);
    }

    /** Routes a scroll in the draw order read top down, like {@link #mouseClicked(List, double, double, int, boolean)}. */
    public static boolean mouseScrolled(List<PanelHost> hosts, double mouseX, double mouseY,
                                        double scrollX, double scrollY, boolean modalUp) {
        for (LayerPlan.Drawn d : routable(hosts, modalUp)) {
            if (d.host().mouseScrolled(d.placed(), mouseX, mouseY, scrollX, scrollY)) return true;
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
        for (Panel panel : panels()) {
            List<PanelElement> elements = panel.getElements();
            ChildDispatch.detach(elements, screen);
            ChildDispatch.attach(elements, screen);
        }
    }

    /** Element lifecycle on close. */
    public void detach(Screen screen) {
        for (Panel panel : panels()) ChildDispatch.detach(panel.getElements(), screen);
    }

    /** Each panel once, though it may have an entry per mode scope. */
    private Set<Panel> panels() {
        Set<Panel> out = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Entry e : entries) out.add(e.panel());
        return out;
    }

    // Registering mod, the tie-break after priority

    /**
     * The mod that called into MenuKit: the first stack frame outside MenuKit's own
     * packages and outside any class marked {@link WrapsPanelAdapter} (a library's own
     * adapter wrapper, such as SlotKit's {@code SlotGroupPanelAdapter}), matched to its
     * Fabric mod by where its class was loaded from. Called at registration only, never
     * per frame. Falls back to the caller's package so the sort key stays total.
     */
    public static String captureCallerModId() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> !c.getPackageName().startsWith("com.trevlar.menukit"))
                        .filter(c -> !c.isAnnotationPresent(WrapsPanelAdapter.class))
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
