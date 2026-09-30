package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.core.RegionMath;
import com.trevlar.menukit.api.panel.ScreenOrigin;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Where a panel sits: the one placement declaration every host reads (§0065).
 *
 * <p>Placement is declared once, on the panel. A host (a container screen, a
 * vanilla screen, a slot group, the HUD, a standalone {@code MKScreen}) reads
 * this record and resolves it against its own reference: the menu frame, the
 * screen, the slot group's box, the game window, or the screen's own
 * {@link Mode#MAIN main} panel. Adapters no longer take a region; the panel says
 * where it goes, and the adapter only says which screens it appears on.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link Mode#UNPLACED}: no placement declared (the default). A host rejects
 *       an unplaced panel loudly, except the standalone-screen builders
 *       ({@code MKScreen}, {@code CustomContainerMenu}), which normalise it: the first
 *       unplaced panel becomes {@link #main()}, each later one
 *       {@code region(BOTTOM_CENTER)}.</li>
 *   <li>{@link Mode#MAIN}: a standalone screen's frame: one per screen, centred,
 *       the reference its {@code REGION} siblings resolve against.</li>
 *   <li>{@link Mode#REGION}: outside a reference rectangle via an
 *       {@link OutsideRegion} (RIGHT_ALIGN_TOP, BOTTOM_CENTER, ...). The rectangle is
 *       the host's: the menu frame on a container screen, the slot group's box for
 *       a slot-group adapter, the main panel on a standalone screen.</li>
 *   <li>{@link Mode#SCREEN_ANCHOR}: on one of the nine {@link InsideRegion} spots of
 *       the screen (or the game window, for the HUD), inset from the edges it
 *       touches.</li>
 *   <li>{@link Mode#CENTER}: a screen-centred overlay, drawn on top.</li>
 *   <li>{@link Mode#PIXEL}: the outer origin comes from a per-frame supplier, in
 *       absolute screen pixels (§0057 Revision): the precision escape.</li>
 * </ul>
 *
 * <p>Two modifiers ride every mode:
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
 * </ul>
 *
 * <p>Immutable: {@code priority} and {@code offset} return a new record.
 */
public record PanelPosition(Mode mode,
                            @Nullable InsideRegion screenAnchor,
                            @Nullable OutsideRegion region,
                            @Nullable Supplier<ScreenOrigin> pixelOrigin,
                            int dx,
                            int dy,
                            int priority) {

    /**
     * Default stacking priority. A middle value, so a consumer can move up (lower
     * number) or down (higher number) without renumbering anyone else.
     */
    public static final int DEFAULT_PRIORITY = 100;

    /** How a panel is positioned. */
    public enum Mode {
        /**
         * No placement declared. Hosts refuse it (a panel with nowhere to go is a
         * declaration bug, and failing at registration beats an invisible panel);
         * the standalone-screen builders normalise it instead (see the class doc).
         */
        UNPLACED,
        /**
         * A standalone screen's frame, centred on the screen window: the reference
         * every {@link #REGION} sibling resolves against. One per screen; the
         * custom-screen analogue of a vanilla container's menu frame.
         */
        MAIN,
        /** Outside the host's reference rectangle, via an {@link OutsideRegion}. */
        REGION,
        /** On one of the nine {@link InsideRegion} spots of the screen or window. */
        SCREEN_ANCHOR,
        /**
         * Centred on the screen as an overlay and drawn on top. Position only: it
         * composes freely with {@code dimsBehind}, {@code opaque} and
         * {@code tracksAsModal} (M9: those flags stay independent).
         */
        CENTER,
        /**
         * Pixel-precision override (§0057 Revision): the outer origin comes from a
         * consumer supplier, re-evaluated every frame, in absolute screen pixels.
         * The escape for positions a region cannot say.
         */
        PIXEL
    }

    /** No placement. The default on {@code Panel.builder}; see {@link Mode#UNPLACED}. */
    public static final PanelPosition UNPLACED =
            new PanelPosition(Mode.UNPLACED, null, null, null, 0, 0, DEFAULT_PRIORITY);

    /**
     * A standalone screen's main panel, its frame: centred on the screen window;
     * every {@link #region} sibling anchors to its bounds. One per screen.
     */
    public static PanelPosition main() {
        return new PanelPosition(Mode.MAIN, null, null, null, 0, 0, DEFAULT_PRIORITY);
    }

    /**
     * Outside the host's reference rectangle via {@code region}: to the right of the
     * menu frame, below a slot group, above a standalone screen's main panel, and so
     * on. Resolved by {@link RegionMath#resolveMenu}, so it is edge-aware on both
     * axes and auto-scrolls when it would overflow the screen. Siblings sharing a
     * region stack away from the anchor edge in {@link #priority} order.
     */
    public static PanelPosition region(OutsideRegion region) {
        return new PanelPosition(Mode.REGION, null, region, null, 0, 0, DEFAULT_PRIORITY);
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
        return new PanelPosition(Mode.SCREEN_ANCHOR, region, null, null, 0, 0, DEFAULT_PRIORITY);
    }

    /**
     * Centred on the screen as an overlay, drawn on top: the placement for dialogs
     * and popovers. Composes freely with {@code dimsBehind}, {@code opaque} and
     * {@code tracksAsModal}.
     */
    public static PanelPosition center() {
        return new PanelPosition(Mode.CENTER, null, null, null, 0, 0, DEFAULT_PRIORITY);
    }

    /**
     * Pixel-precision placement (§0057 Revision): the panel's <b>outer</b> top-left
     * (the background origin; elements render inside at origin + padding) is exactly
     * what {@code origin} supplies, in absolute screen pixels, re-evaluated every
     * frame. Returning {@code null} skips the panel that frame: not drawn, claims
     * nothing. No reactive budgets are fed: pixel placement means the consumer owns
     * the exact geometry, on-screen included.
     *
     * @param origin per-frame supplier of the panel's outer top-left; {@code null}
     *               return = skip this frame
     */
    public static PanelPosition pixel(Supplier<ScreenOrigin> origin) {
        return new PanelPosition(Mode.PIXEL, null, null, origin, 0, 0, DEFAULT_PRIORITY);
    }

    /**
     * This position nudged by {@code (dx, dy)} pixels after placement (positive x
     * right, positive y down). Replaces any earlier offset. Only this panel moves;
     * siblings in the same region stack as if it had not.
     */
    public PanelPosition offset(int dx, int dy) {
        return new PanelPosition(mode, screenAnchor, region, pixelOrigin, dx, dy, priority);
    }

    /**
     * This position with stacking and z-order {@code priority} (lower goes first:
     * nearer the anchor edge, and underneath). Default {@link #DEFAULT_PRIORITY}.
     */
    public PanelPosition priority(int priority) {
        return new PanelPosition(mode, screenAnchor, region, pixelOrigin, dx, dy, priority);
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
     * {@link #STANDALONE_STACK_GAP} below the one before. Declared positions pass
     * through untouched. Used by {@code MKScreen} and {@code CustomContainerMenu}, the
     * only hosts that accept an unplaced panel.
     *
     * <p>ponytail: the offset assumes every earlier stacked panel is shown; with a
     * middle one hidden, later ones sit 12px lower than the old column did.
     * Upgrade path: a per-region stacking gap on the host, if anyone notices.
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
        int stacked = 0;
        for (PanelPosition p : declared) {
            if (p.isPlaced()) {
                out.add(p);
            } else if (!hasMain) {
                out.add(main());
                hasMain = true;
            } else {
                stacked++;
                // resolveMenu already puts MENU_STACK_GAP between siblings; make up
                // the rest of the old column's gap, once per panel above this one.
                int extra = STANDALONE_STACK_GAP - RegionConstants.MENU_STACK_GAP;
                out.add(region(OutsideRegion.BOTTOM_CENTER).offset(0, stacked * extra));
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
