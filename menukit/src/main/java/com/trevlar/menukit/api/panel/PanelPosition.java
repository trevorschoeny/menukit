package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.core.RegionMath;
import com.trevlar.menukit.api.panel.ScreenOrigin;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Where a panel sits: the one placement declaration every host reads.
 *
 * <p>Placement is declared once, on the panel. A host (a container screen, a
 * vanilla screen, an anchor, the HUD, a standalone {@code MKScreen}) reads
 * this record and resolves it against its own reference: the menu frame, the
 * screen, the anchor's box, the game window, or the screen's own
 * {@link Mode#MAIN main} panel. Adapters no longer take a region; the panel says
 * where it goes, and the adapter only says which screens it appears on.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link Mode#UNPLACED}: no placement declared (the default). A host throws on
 *       an unplaced panel, except the standalone-screen builders
 *       ({@code MKScreen}, {@code CustomContainerMenu}), which normalise it: the first
 *       unplaced panel becomes {@link #main()}, each later one
 *       {@code region(BOTTOM_CENTER)}.</li>
 *   <li>{@link Mode#MAIN}: a standalone screen's frame. One per screen, centred,
 *       and the reference its {@code REGION} siblings resolve against.</li>
 *   <li>{@link Mode#REGION}: outside a reference rectangle via an
 *       {@link OutsideRegion} (RIGHT_ALIGN_TOP, BOTTOM_CENTER, ...). The rectangle is
 *       the host's: the menu frame on a container screen, the anchor's box for
 *       an anchor adapter, the main panel on a standalone screen.</li>
 *   <li>{@link Mode#SCREEN_ANCHOR}: on one of the nine {@link InsideRegion} spots of
 *       the screen (or the game window, for the HUD), inset from the edges it
 *       touches.</li>
 *   <li>{@link Mode#CENTER}: a screen-centred overlay, drawn on top.</li>
 *   <li>{@link Mode#PIXEL}: the outer origin comes from a per-frame supplier, in
 *       absolute screen pixels, for positions a region cannot express.</li>
 * </ul>
 *
 * <p>Three modifiers. The first two apply to every mode:
 * <ul>
 *   <li>{@link #priority(int)}: stacking order among siblings sharing a region, and
 *       z-order within a host. Lower goes first (closer to the region's anchor
 *       edge, and underneath in z). Default {@link #DEFAULT_PRIORITY}. Ties break by
 *       the registering mod's id, then by registration order, so ordering never
 *       depends on which mod loaded first. (Replaces {@code RegionAnchor} and the
 *       enums' {@code priority(int)} methods.)</li>
 *   <li>{@link #offset(int, int)}: a pixel nudge applied after the panel is placed.
 *       It moves only this panel; siblings stack as if it were not nudged. (Replaces
 *       the HUD's {@code MKHudAnchor} offsets.)</li>
 *   <li>{@link #gap(int)} (since 7.1.0): the space before this panel in its stack, for
 *       the two stacking modes, {@link Mode#REGION} and {@link Mode#SCREEN_ANCHOR}.
 *       Unset, the host's own spacing applies: {@link RegionConstants#MENU_STACK_GAP}
 *       (2) for every region, and the context's stacking gap for a screen spot
 *       ({@link RegionConstants#SCREEN_STACK_GAP} on a vanilla screen). Other modes
 *       do not stack and ignore it.</li>
 * </ul>
 *
 * <p>Immutable: {@code priority}, {@code offset} and {@code gap} return a new record.
 */
public record PanelPosition(Mode mode,
                            @Nullable InsideRegion screenAnchor,
                            @Nullable OutsideRegion region,
                            @Nullable Supplier<ScreenOrigin> pixelOrigin,
                            int dx,
                            int dy,
                            int priority,
                            int gap) {

    /**
     * Default stacking priority. A middle value, so a consumer can move up (lower
     * number) or down (higher number) without renumbering anyone else.
     */
    public static final int DEFAULT_PRIORITY = 100;

    /**
     * The {@link #gap()} of a position that never called {@link #gap(int)}: "the
     * host's own spacing" (since 7.1.0). Not a gap a consumer can ask for, since
     * {@link #gap(int)} refuses anything negative; hosts read the gap through
     * {@link #gapOr(int)}, which turns this into their default.
     */
    public static final int HOST_GAP = -1;

    /** How a panel is positioned. */
    public enum Mode {
        /**
         * No placement declared. Hosts refuse it, since a panel with nowhere to go
         * is a declaration bug and failing at registration is better than an
         * invisible panel. The standalone-screen builders normalise it instead
         * (see the class doc).
         */
        UNPLACED,
        /**
         * A standalone screen's frame, centred on the screen window: the reference
         * every {@link #REGION} sibling resolves against. One per screen; the
         * custom screen's equivalent of a vanilla container's menu frame.
         */
        MAIN,
        /** Outside the host's reference rectangle, via an {@link OutsideRegion}. */
        REGION,
        /** On one of the nine {@link InsideRegion} spots of the screen or window. */
        SCREEN_ANCHOR,
        /**
         * Centred on the screen as an overlay and drawn on top. Position only: it
         * combines freely with {@code dimsBehind}, {@code opaque} and
         * {@code tracksAsModal}, which stay independent.
         */
        CENTER,
        /**
         * Exact pixel placement: the outer origin comes from a consumer supplier,
         * evaluated every frame, in absolute screen pixels. For positions a region
         * cannot express.
         */
        PIXEL
    }

    /** No placement. The default on {@code Panel.builder}; see {@link Mode#UNPLACED}. */
    public static final PanelPosition UNPLACED =
            new PanelPosition(Mode.UNPLACED, null, null, null, 0, 0, DEFAULT_PRIORITY, HOST_GAP);

    /**
     * A standalone screen's main panel, its frame: centred on the screen window;
     * every {@link #region} sibling anchors to its bounds. One per screen.
     */
    public static PanelPosition main() {
        return new PanelPosition(Mode.MAIN, null, null, null, 0, 0, DEFAULT_PRIORITY, HOST_GAP);
    }

    /**
     * Outside the host's reference rectangle via {@code region}: to the right of the
     * menu frame, below an anchor, above a standalone screen's main panel, left of
     * the vanilla hotbar on the HUD (since 7.1.0, through {@link RegionMath#resolveHud}),
     * and so on. Resolved by {@link RegionMath#resolveMenu}, so it is edge-aware on both
     * axes and auto-scrolls when it would overflow the screen. Siblings sharing a
     * region stack away from the anchor edge in {@link #priority} order.
     */
    public static PanelPosition region(OutsideRegion region) {
        return new PanelPosition(Mode.REGION, null, region, null, 0, 0, DEFAULT_PRIORITY, HOST_GAP);
    }

    /**
     * On a screen-edge spot: a {@code TOP_LEFT} back button, a {@code TOP_CENTER}
     * title, a HUD panel at {@code BOTTOM_RIGHT}. Inset from the edges it touches by
     * the host's inset (the HUD and vanilla screens use
     * {@link RegionConstants#EDGE_INSET}, a standalone screen's chrome
     * {@link RegionConstants#SCREEN_EDGE_MARGIN}); siblings on one spot stack.
     * Resolved by {@link RegionMath#resolveInside}.
     */
    public static PanelPosition screenAnchor(InsideRegion region) {
        return new PanelPosition(Mode.SCREEN_ANCHOR, region, null, null, 0, 0, DEFAULT_PRIORITY, HOST_GAP);
    }

    /**
     * Centred on the screen as an overlay, drawn on top: the placement for dialogs
     * and popovers. Combines freely with {@code dimsBehind}, {@code opaque} and
     * {@code tracksAsModal}.
     */
    public static PanelPosition center() {
        return new PanelPosition(Mode.CENTER, null, null, null, 0, 0, DEFAULT_PRIORITY, HOST_GAP);
    }

    /**
     * Exact pixel placement: the panel's <b>outer</b> top-left
     * (the background origin; elements render inside at origin + padding) is exactly
     * what {@code origin} supplies, in absolute screen pixels, re-evaluated every
     * frame. Returning {@code null} skips the panel that frame: not drawn, claims
     * nothing. No reactive budgets are fed: pixel placement means the consumer owns
     * the exact geometry, on-screen included.
     *
     * @param origin per-frame supplier of the panel's outer top-left; returning
     *               {@code null} skips the panel that frame
     */
    public static PanelPosition pixel(Supplier<ScreenOrigin> origin) {
        return new PanelPosition(Mode.PIXEL, null, null, origin, 0, 0, DEFAULT_PRIORITY, HOST_GAP);
    }

    /**
     * This position nudged by {@code (dx, dy)} pixels after placement (positive x
     * right, positive y down). Replaces any earlier offset. Only this panel moves;
     * siblings in the same region stack as if it had not.
     */
    public PanelPosition offset(int dx, int dy) {
        return new PanelPosition(mode, screenAnchor, region, pixelOrigin, dx, dy, priority, gap);
    }

    /**
     * This position with stacking and z-order {@code priority} (lower goes first:
     * nearer the anchor edge, and underneath). Default {@link #DEFAULT_PRIORITY}.
     */
    public PanelPosition priority(int priority) {
        return new PanelPosition(mode, screenAnchor, region, pixelOrigin, dx, dy, priority, gap);
    }

    /**
     * This position with {@code gap} pixels before the panel in its stack (since
     * 7.1.0). Replaces any earlier gap. What "before" means follows the stack:
     *
     * <ul>
     *   <li>{@link Mode#REGION}: the space between this panel and what it stacks
     *       against. That is the reference (the menu frame, an anchor's box, the
     *       hotbar on the HUD) on the side the region sits, for every panel in the
     *       region, and the sibling before it along the stack, for every panel but the
     *       first. So {@code region(RIGHT_ALIGN_TOP).gap(0)} sits flush against the
     *       frame's right edge, and a second {@code gap(0)} panel sits flush under the
     *       first. For the four side-centred regions ({@code TOP_CENTER}, {@code RIGHT_CENTER}, ...) the two are the
     *       same axis: the first panel is {@code gap} off the frame, each later one
     *       {@code gap} off the one before. Unset: {@link RegionConstants#MENU_STACK_GAP}.</li>
     *   <li>{@link Mode#SCREEN_ANCHOR}: the space between this panel and the sibling
     *       before it on the spot. The first panel on a spot keeps the host's edge
     *       inset, which is a margin from the screen edge, not a gap between panels.
     *       Unset: the host's stacking gap ({@link RegionConstants#MENU_STACK_GAP} on
     *       the HUD and a screen's chrome, {@link RegionConstants#SCREEN_STACK_GAP} on
     *       a vanilla screen).</li>
     *   <li>Every other mode does not stack and ignores it.</li>
     * </ul>
     *
     * <p>The gap belongs to the panel that declares it: the space after a panel is
     * the next panel's gap, not its own, so siblings with different gaps each keep
     * theirs. {@code gap(0)} on slot panels stacked on a slot group keeps vanilla's
     * 18 px slot pitch, while a panel after them that declares nothing still sits
     * 2 px off the last one.
     *
     * @param gap pixels before the panel, {@code 0} or more
     * @throws IllegalArgumentException when {@code gap} is negative: a panel that
     *         overlaps its neighbour is an {@link #offset(int, int)}, which says so
     */
    public PanelPosition gap(int gap) {
        if (gap < 0) {
            throw new IllegalArgumentException("PanelPosition.gap(" + gap + ") on " + describe()
                    + ": a gap is 0 or more pixels. To overlap a neighbour, use offset(dx, dy).");
        }
        return new PanelPosition(mode, screenAnchor, region, pixelOrigin, dx, dy, priority, gap);
    }

    /**
     * The gap before this panel: the one declared with {@link #gap(int)}, or
     * {@code hostDefault} when none was (since 7.1.0). Every host reads the gap
     * through here, so "unset means the host's spacing" lives in one place.
     */
    public int gapOr(int hostDefault) {
        return gap == HOST_GAP ? hostDefault : gap;
    }

    /**
     * The gap between panels a standalone screen stacks by default (the old BODY
     * column's 14px, which leaves room for a container's "Inventory" label).
     */
    public static final int STANDALONE_STACK_GAP = 14;

    /**
     * The standalone-screen default for unplaced panels, keeping the look of the old
     * BODY column: the first unplaced panel becomes {@link #main()} (unless one is
     * declared), and each later one {@code region(BOTTOM_CENTER)}, offset so it sits
     * {@link #STANDALONE_STACK_GAP} below the one before, through its {@link #gap(int)}.
     * Declared positions pass through untouched. Used by {@code MKScreen} and
     * {@code CustomContainerMenu}, the only hosts that accept an unplaced panel.
     *
     * <p>Since the gap is the panel's own, a hidden panel takes its gap with it: the
     * next one closes up to {@link #STANDALONE_STACK_GAP} below the last shown panel,
     * as the old column did. (Before 7.1.0 this was a cumulative offset, which left
     * a 12 px hole under a hidden middle panel.)
     *
     * @param declared each panel's declared position, in declaration order
     * @return the position each panel takes, same order
     */
    public static java.util.List<PanelPosition> standaloneDefaults(java.util.List<PanelPosition> declared) {
        boolean hasMain = false;
        for (PanelPosition p : declared) {
            if (p.mode() == Mode.MAIN) hasMain = true;
        }
        java.util.List<PanelPosition> out = new java.util.ArrayList<>(declared.size());
        for (PanelPosition p : declared) {
            if (p.isPlaced()) {
                out.add(p);
            } else if (!hasMain) {
                out.add(main());
                hasMain = true;
            } else {
                // BOTTOM_CENTER stacks down from the main frame; each panel's own gap is
                // the space above it, off the main frame for the first and off the
                // panel before for the rest, so every one sits 14 px below its
                // neighbour, the old column's spacing.
                out.add(region(OutsideRegion.BOTTOM_CENTER).gap(STANDALONE_STACK_GAP));
            }
        }
        return out;
    }

    /** Whether a placement was declared (anything but {@link Mode#UNPLACED}). */
    public boolean isPlaced() {
        return mode != Mode.UNPLACED;
    }

    /** A short human form for error messages: {@code REGION(RIGHT_ALIGN_TOP)} and so on. */
    public String describe() {
        return switch (mode) {
            case REGION -> "REGION(" + region + ")";
            case SCREEN_ANCHOR -> "SCREEN_ANCHOR(" + screenAnchor + ")";
            default -> mode.name();
        };
    }
}
