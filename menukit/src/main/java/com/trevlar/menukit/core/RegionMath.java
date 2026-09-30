package com.trevlar.menukit.core;

import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenOrigin;

import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Pure coordinate resolver for M5 regions. Given explicit inputs — anchor-frame
 * bounds (or screen dimensions), panel dimensions, and the current stacking
 * {@code prefix} — returns the panel's screen-space origin, or
 * {@link Optional#empty()} if the panel's extent exceeds the region's
 * available space.
 *
 * <p><b>Pure by design.</b> No registry state, no Panel references, no
 * per-frame side effects. Enables {@code /mkverify all} to exercise the math
 * with synthetic inputs without spinning up a screen or touching the
 * panel registries. See M5 design doc §9.1.
 *
 * <p>Two resolvers cover every placement: {@link #resolveMenu} for an
 * {@link OutsideRegion} around a reference rectangle, and {@link #resolveInside}
 * for an {@link InsideRegion} spot on the screen. The panel hosts
 * ({@code com.trevlar.menukit.inject.PanelHost}) supply the inputs and treat
 * {@link Optional#empty()} as "not placed this frame".
 */
@ApiStatus.Internal
public final class RegionMath {

    private RegionMath() {}

    // ── Pass 3: screen-edge available-width arithmetic ──────────────────
    //
    // Single home for "how much OUTER width may a panel occupy before it
    // crosses the screen-edge margin," given how the panel is anchored. The
    // three primitives below cover every anchor shape; each placement context
    // (menu / HUD / vanilla-screen / centered-screen / slot-group) calls the
    // one that matches its growth direction. Pure arithmetic — no pw input, so
    // there is no measure→place→re-measure circularity (verified: every
    // directional region pins one edge and grows into the budget).

    /** Room for a panel whose left edge is pinned at {@code originX} and which
     *  grows rightward, before the right screen-edge margin. */
    public static int growRightWidth(int originX, int sw, int margin) {
        return sw - margin - originX;
    }

    /** Room for a panel whose right edge is pinned at {@code rightEdgeX} and
     *  which grows leftward, before the left screen-edge margin. */
    public static int growLeftWidth(int rightEdgeX, int margin) {
        return rightEdgeX - margin;
    }

    /** Room for a panel centered on {@code centerX} that must stay clear of
     *  BOTH screen-edge margins — symmetric about the center, so the binding
     *  edge is whichever is nearer. */
    public static int centeredWidth(int centerX, int sw, int margin) {
        return 2 * Math.min(centerX - margin, sw - margin - centerX);
    }

    // ── Movement ②: vertical twins of the width primitives ──────────────
    //
    // Same shape as growRight/growLeft/centeredWidth, rotated 90°. A panel
    // anchored above/below the frame computes how much OUTER HEIGHT it may
    // occupy before crossing the top/bottom screen-edge margin; the panel
    // then auto-scrolls into that budget instead of running off-screen. Pure
    // arithmetic — each region pins one edge and grows into the budget.

    /** Room for a panel whose top edge is pinned at {@code originY} and which
     *  grows downward, before the bottom screen-edge margin. */
    public static int growDownHeight(int originY, int sh, int margin) {
        return sh - margin - originY;
    }

    /** Room for a panel whose bottom edge is pinned at {@code bottomEdgeY} and
     *  which grows upward, before the top screen-edge margin. */
    public static int growUpHeight(int bottomEdgeY, int margin) {
        return bottomEdgeY - margin;
    }

    /** Room for a panel centered on {@code centerY} that must stay clear of
     *  BOTH screen-edge margins — symmetric about the center, binding edge is
     *  whichever is nearer. */
    public static int centeredHeight(int centerY, int sh, int margin) {
        return 2 * Math.min(centerY - margin, sh - margin - centerY);
    }

    /**
     * OUTER available height (padding-inclusive) for a MenuContext region panel
     * before it crosses the screen-edge margin, given the (chrome-extended) menu
     * frame and the screen height. The height twin of {@link #availableMenuWidth}
     * — mirrors {@link #resolveMenu}'s per-region Y geometry so a too-tall panel
     * auto-scrolls into exactly the room its anchor leaves toward the screen edge,
     * rather than rendering off-screen (then getting clamped over the frame). The
     * caller subtracts the panel's 2×padding to get the content-height ceiling for
     * {@link com.trevlar.menukit.api.panel.Panel#setAvailableContentHeight}.
     *
     * <p>Like {@link #availableMenuWidth}, the stacking prefix is ignored here —
     * the budget is computed for the region's anchor edge (an over-estimate for
     * the 2nd+ panel in a vertically-stacked adaptive set; single-panel and
     * horizontal-flow regions are exact).
     */
    public static int availableMenuHeight(OutsideRegion region, Reference b,
                                          int sh, int margin) {
        int topPos = b.topPos();
        int imageHeight = b.imageHeight();
        int gap = RegionConstants.MENU_STACK_GAP;
        int frameTop = topPos;
        int frameBottom = topPos + imageHeight;
        int centerY = topPos + imageHeight / 2;
        return switch (region) {
            // Above the frame → grows UP toward the top margin.
            case TOP_CENTER, TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT ->
                    growUpHeight(frameTop - gap, margin);
            // Below the frame → grows DOWN toward the bottom margin.
            case BOTTOM_CENTER, BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT ->
                    growDownHeight(frameBottom + gap, sh, margin);
            // Pinned at the frame top → grows DOWN toward the bottom margin.
            case RIGHT_ALIGN_TOP, LEFT_ALIGN_TOP ->
                    growDownHeight(frameTop, sh, margin);
            // Pinned at the frame bottom → grows UP toward the top margin.
            case RIGHT_ALIGN_BOTTOM, LEFT_ALIGN_BOTTOM ->
                    growUpHeight(frameBottom, margin);
            // CENTER stays within the frame; frame height is the safe ceiling.
            case CENTER -> Math.min(imageHeight, centeredHeight(centerY, sh, margin));
        };
    }

    /**
     * OUTER available width (padding-inclusive) for a MenuContext region panel
     * before it crosses the screen-edge margin, given the (chrome-extended)
     * menu frame and the screen width. Mirrors {@link #resolveMenu}'s per-region
     * anchor geometry so the budget and the origin agree on where the panel
     * sits. The caller subtracts the panel's 2×padding to get the content
     * budget for {@link com.trevlar.menukit.api.panel.Panel#setAvailableContentWidth}.
     *
     * <p>Horizontal-flow regions (TOP/BOTTOM_ALIGN) ignore the stacking prefix
     * here — the budget is computed for the region's anchor edge, an
     * over-estimate for the 2nd+ panel in a horizontally-stacked adaptive set.
     * That multi-panel-horizontal-adaptive case is rare; single-panel and all
     * vertical-flow regions are exact.
     */
    public static int availableMenuWidth(OutsideRegion region, Reference b,
                                         int sw, int margin) {
        int leftPos = b.leftPos();
        int imageWidth = b.imageWidth();
        int gap = RegionConstants.MENU_STACK_GAP;
        int centerX = leftPos + imageWidth / 2;
        return switch (region) {
            case RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM ->
                    growRightWidth(leftPos + imageWidth + gap, sw, margin);
            case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM ->
                    growLeftWidth(leftPos - gap, margin);
            case TOP_ALIGN_LEFT, BOTTOM_ALIGN_LEFT ->
                    growRightWidth(leftPos, sw, margin);
            case TOP_ALIGN_RIGHT, BOTTOM_ALIGN_RIGHT ->
                    growLeftWidth(leftPos + imageWidth, margin);
            case TOP_CENTER, BOTTOM_CENTER ->
                    centeredWidth(centerX, sw, margin);
            // CENTER stays within the frame (resolveMenu rejects pw > imageWidth);
            // the frame is itself on-screen, so frame width is the safe ceiling.
            case CENTER -> Math.min(imageWidth, centeredWidth(centerX, sw, margin));
        };
    }

    /**
     * OUTER available width for a screen-edge-anchored panel (an
     * {@link InsideRegion} spot in any context). Every spot is inset from one edge
     * and should keep the same inset from the opposite edge, so the budget is the
     * screen width minus the inset on both sides.
     */
    public static int availableScreenEdgeWidth(int sw, int inset) {
        return sw - 2 * inset;
    }

    // ── InsideRegion: the one resolver, parameterised by the context's insets ──

    /**
     * How a context places {@link InsideRegion} spots: the one thing that differs
     * between the HUD, a vanilla screen and a standalone screen's chrome (§0065:
     * "one InsideRegion resolver parameterised by the context's insets").
     *
     * @param edge         inset from each screen edge a spot touches
     * @param gap          stacking gap between siblings on one spot
     * @param belowCenter  when positive, {@link InsideRegion#CENTER} flows down from
     *                     this far below the screen centre (the HUD clears the
     *                     crosshair); when zero, CENTER is centred like the other
     *                     middle-row spots
     * @param hideOverflow when true, a panel that does not fit its spot's axis is not
     *                     placed ({@link Optional#empty()}); when false it always
     *                     resolves and a too-big panel overhangs the far edge
     */
    public record Insets(int edge, int gap, int belowCenter, boolean hideOverflow) {
        /** The HUD: 4px in, 2px stacking, CENTER below the crosshair, hide on overflow. */
        public static final Insets HUD = new Insets(RegionConstants.EDGE_INSET,
                RegionConstants.MENU_STACK_GAP, RegionConstants.CENTER_CROSSHAIR_CLEARANCE, true);
        /** A vanilla non-container screen: 4px in, 4px stacking, no crosshair, hide on overflow. */
        public static final Insets SCREEN = new Insets(RegionConstants.EDGE_INSET,
                RegionConstants.SCREEN_STACK_GAP, 0, true);
        /** Screen chrome on a standalone or container screen: the safe-area margin, always placed. */
        public static final Insets CHROME = new Insets(RegionConstants.SCREEN_EDGE_MARGIN,
                RegionConstants.MENU_STACK_GAP, 0, false);
        /** A notification: HUD geometry, but always shown (a toast never silently vanishes). */
        public static final Insets NOTIFICATION = new Insets(RegionConstants.EDGE_INSET,
                RegionConstants.MENU_STACK_GAP, RegionConstants.CENTER_CROSSHAIR_CLEARANCE, false);
    }

    /**
     * Places a {@code pw x ph} panel (padding-inclusive) on an {@link InsideRegion}
     * spot of an {@code sw x sh} screen, {@code prefix} pixels along the spot's
     * stacking axis from its anchor edge. Every spot stacks vertically: the top row
     * grows down, the bottom row grows up, the middle row grows down from its
     * centred start.
     *
     * <p>The X axis: LEFT spots pin to the inset, CENTER spots centre, RIGHT spots
     * pin to the right inset. The Y axis mirrors it; the middle row is centred on the
     * screen, except {@link InsideRegion#CENTER} with {@code insets.belowCenter() > 0}
     * (the HUD), which starts that far below the centre so it clears the crosshair.
     *
     * <p>Returns empty only when {@code insets.hideOverflow()} and the stack would run
     * past the spot's available height (screen height minus both insets, halved for
     * the middle row).
     */
    public static Optional<ScreenOrigin> resolveInside(InsideRegion region,
            int sw, int sh, int pw, int ph, int prefix, Insets insets) {
        int e = insets.edge();
        boolean hudCenter = region == InsideRegion.CENTER && insets.belowCenter() > 0;
        if (insets.hideOverflow()) {
            int available = switch (region) {
                case TOP_LEFT, TOP_CENTER, TOP_RIGHT,
                     BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> sh - 2 * e;
                case LEFT_CENTER, RIGHT_CENTER -> sh / 2 - e;
                case CENTER -> hudCenter ? sh / 2 - insets.belowCenter() - e : sh / 2 - e;
            };
            if (prefix + ph > available) return Optional.empty();
        }
        int x = switch (region) {
            case TOP_LEFT, LEFT_CENTER, BOTTOM_LEFT -> e;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (sw - pw) / 2;
            case TOP_RIGHT, RIGHT_CENTER, BOTTOM_RIGHT -> sw - pw - e;
        };
        int y = switch (region) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> e + prefix;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> sh - ph - e - prefix;
            case LEFT_CENTER, RIGHT_CENTER -> (sh - ph) / 2 + prefix;
            case CENTER -> hudCenter
                    ? sh / 2 + insets.belowCenter() + prefix
                    : (sh - ph) / 2 + prefix;
        };
        return Optional.of(new ScreenOrigin(x, y));
    }

    // ── Shared constants ────────────────────────────────────────────────
    //
    // Phase 3b (Item 4c): the stacking gap was hoisted to the single shared
    // source {@link RegionConstants}. The menu + slot-group + HUD contexts
    // all stack at {@link RegionConstants#MENU_STACK_GAP}; this class reads
    // that constant directly at each use site below.

    // ── MenuContext ─────────────────────────────────────────────────────

    /**
     * Resolves a MenuContext region panel's origin. Returns
     * {@link Optional#empty()} when {@code prefix + panel_extent} exceeds
     * the region's available space (menu height for side regions, menu
     * width for top/bottom regions).
     *
     * @param region  the MenuContext region the panel belongs to
     * @param bounds  the vanilla menu's container-frame bounds this frame
     * @param pw      the panel's width (from {@link Panel#getWidth()})
     * @param ph      the panel's height (from {@link Panel#getHeight()})
     * @param prefix  total axial extent of visible preceding panels in the
     *                same region, plus one {@link RegionConstants#MENU_STACK_GAP} per preceding
     *                panel
     * @param sw      GUI-scaled screen width (Pass 3 — for the screen safe-area
     *                overflow gate; edge regions extend toward the screen edge,
     *                not the menu edge)
     * @param sh      GUI-scaled screen height
     */
    public static Optional<ScreenOrigin> resolveMenu(
            OutsideRegion region, Reference bounds,
            int pw, int ph, int prefix, int sw, int sh) {

        int leftPos = bounds.leftPos();
        int topPos = bounds.topPos();
        int imageWidth = bounds.imageWidth();
        int imageHeight = bounds.imageHeight();

        // CENTER is in-frame by design (modal dialogs centered in the menu) — it
        // must fit the frame, so keep the frame gate. Edge regions are gated
        // AFTER the origin is computed, against the SCREEN safe area (below) —
        // their whole point is to extend past the narrow menu frame toward the
        // screen edge, so the menu width/height is the wrong ceiling (Pass-3 fix:
        // BOTTOM_ALIGN_RIGHT was silently hiding any panel wider than 176px).
        if (region == OutsideRegion.CENTER) {
            if (pw > imageWidth || ph > imageHeight) return Optional.empty();
        }

        ScreenOrigin origin = switch (region) {
            case RIGHT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos + imageWidth + RegionConstants.MENU_STACK_GAP,
                    topPos + prefix);
            case RIGHT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos + imageWidth + RegionConstants.MENU_STACK_GAP,
                    topPos + imageHeight - ph - prefix);
            case LEFT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos - pw - RegionConstants.MENU_STACK_GAP,
                    topPos + prefix);
            case LEFT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos - pw - RegionConstants.MENU_STACK_GAP,
                    topPos + imageHeight - ph - prefix);
            case TOP_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos - ph - RegionConstants.MENU_STACK_GAP);
            case TOP_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos - ph - RegionConstants.MENU_STACK_GAP);
            case BOTTOM_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos + imageHeight + RegionConstants.MENU_STACK_GAP);
            case BOTTOM_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos + imageHeight + RegionConstants.MENU_STACK_GAP);
            // TOP_CENTER / BOTTOM_CENTER (Phase 3b — Item 4a): centered on the
            // horizontal axis, stacking vertically away from the frame. X is
            // the same frame-centering math as CENTER; Y mirrors the
            // TOP_ALIGN / BOTTOM_ALIGN edge math (above/below the frame with
            // the stack-gap, offset by the vertical prefix).
            case TOP_CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos - ph - RegionConstants.MENU_STACK_GAP - prefix);
            case BOTTOM_CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos + imageHeight + RegionConstants.MENU_STACK_GAP + prefix);
            // CENTER: centered within the menu's container frame. Single-position
            // anchor — multiple panels in CENTER overlap (consumer is expected
            // to gate visibility so only one is up at a time, e.g., modal dialogs).
            case CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos + (imageHeight - ph) / 2);
        };

        // Screen safe-area CONFORMANCE for edge regions (Pass 3, primitive (a)):
        // a panel that would cross the SCREEN_EDGE_MARGIN is slid back into the
        // safe area (clamped) rather than hidden — "every panel conforms to the
        // screen edges; no panel renders outside the safe area." This may
        // overlap the menu frame (correct: a wide/tall edge panel that doesn't
        // fit beside the narrow frame belongs on-screen, over the frame, not
        // gone). Hidden ONLY if the panel is larger than the safe area itself —
        // then there's genuinely nowhere on-screen to put it.
        if (region != OutsideRegion.CENTER) {
            int m = RegionConstants.SCREEN_EDGE_MARGIN;
            if (pw > sw - 2 * m || ph > sh - 2 * m) return Optional.empty();
            int cx = Math.max(m, Math.min(origin.x(), sw - m - pw));
            int cy = Math.max(m, Math.min(origin.y(), sh - m - ph));
            origin = new ScreenOrigin(cx, cy);
        }
        return Optional.of(origin);
    }
}
