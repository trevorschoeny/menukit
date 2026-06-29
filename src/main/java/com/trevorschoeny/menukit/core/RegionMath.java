package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.inject.ScreenBounds;
import com.trevorschoeny.menukit.inject.ScreenOrigin;

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
 * {@code RegionRegistry}. See M5 design doc §9.1.
 *
 * <p><b>Public entry points map to a sentinel at the boundary.</b> This class
 * returns {@code Optional<ScreenOrigin>} for compositional clarity. The
 * adapter pipeline ({@link com.trevorschoeny.menukit.inject.RegionRegistry})
 * maps {@code Optional.empty()} to {@link ScreenOrigin#OUT_OF_REGION} so
 * existing {@code ScreenOriginFn} signatures stay stable. See §6.5.
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

    /**
     * OUTER available width (padding-inclusive) for a MenuContext region panel
     * before it crosses the screen-edge margin, given the (chrome-extended)
     * menu frame and the screen width. Mirrors {@link #resolveMenu}'s per-region
     * anchor geometry so the budget and the origin agree on where the panel
     * sits. The caller subtracts the panel's 2×padding to get the content
     * budget for {@link com.trevorschoeny.menukit.core.Panel#setAvailableContentWidth}.
     *
     * <p>Horizontal-flow regions (TOP/BOTTOM_ALIGN) ignore the stacking prefix
     * here — the budget is computed for the region's anchor edge, an
     * over-estimate for the 2nd+ panel in a horizontally-stacked adaptive set.
     * That multi-panel-horizontal-adaptive case is rare; single-panel and all
     * vertical-flow regions are exact.
     */
    public static int availableMenuWidth(MenuRegion region, ScreenBounds b,
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
     * OUTER available width for a screen-edge-anchored panel (HUD /
     * VanillaScreen contexts). All such regions are inset {@link
     * RegionConstants#EDGE_INSET} from one edge and should keep the same inset
     * from the opposite edge, so the budget is the screen width minus the inset
     * on both sides.
     */
    public static int availableScreenEdgeWidth(int sw, int inset) {
        return sw - 2 * inset;
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
     */
    public static Optional<ScreenOrigin> resolveMenu(
            MenuRegion region, ScreenBounds bounds,
            int pw, int ph, int prefix) {

        int leftPos = bounds.leftPos();
        int topPos = bounds.topPos();
        int imageWidth = bounds.imageWidth();
        int imageHeight = bounds.imageHeight();

        // Overflow check — semantics vary by region.
        // - CENTER doesn't stack and must fit within both axes.
        // - Edge regions (the other 8) check overflow along their flow axis,
        //   accounting for prefix from previously-stacked panels.
        if (region == MenuRegion.CENTER) {
            if (pw > imageWidth || ph > imageHeight) return Optional.empty();
        } else {
            int available = region.isHorizontalFlow() ? imageWidth : imageHeight;
            int selfExtent = region.isHorizontalFlow() ? pw : ph;
            if (prefix + selfExtent > available) return Optional.empty();
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
        return Optional.of(origin);
    }

    // Post-§0042 split: resolveSlotGroup moved to menukit-containers'
    // SlotGroupRegionMath in core/. Slot-group region resolution references
    // SlotGroupRegion (slot-group enum) and SlotGroupBounds (containers
    // type), so the math lives where its inputs live.

    // ── HUD context ─────────────────────────────────────────────────────

    /**
     * Resolves a HUD-region panel's origin. All HUD regions flow vertically;
     * overflow is measured against the region's axial capacity (screen height
     * minus edge insets, halved for center-anchored regions).
     *
     * @param region  the HUD region
     * @param sw      GUI-scaled screen width
     * @param sh      GUI-scaled screen height
     * @param pw      the panel's width
     * @param ph      the panel's height
     * @param prefix  total height of visible preceding panels in the same
     *                region, plus one {@link RegionConstants#MENU_STACK_GAP} per preceding panel
     */
    public static Optional<ScreenOrigin> resolveHud(
            HudRegion region, int sw, int sh,
            int pw, int ph, int prefix) {

        int inset = RegionConstants.EDGE_INSET;
        int crosshairClear = HudRegion.CENTER_CROSSHAIR_CLEARANCE;

        // Available vertical space along the flow axis — used for overflow.
        int available = switch (region) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT,
                 BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> sh - inset * 2;
            case LEFT_CENTER, RIGHT_CENTER -> sh / 2 - inset;
            case CENTER -> sh / 2 - crosshairClear - inset;
        };
        if (prefix + ph > available) return Optional.empty();

        ScreenOrigin origin = switch (region) {
            case TOP_LEFT -> new ScreenOrigin(
                    inset,
                    inset + prefix);
            case TOP_CENTER -> new ScreenOrigin(
                    (sw - pw) / 2,
                    inset + prefix);
            case TOP_RIGHT -> new ScreenOrigin(
                    sw - pw - inset,
                    inset + prefix);
            case LEFT_CENTER -> new ScreenOrigin(
                    inset,
                    sh / 2 + prefix);
            case RIGHT_CENTER -> new ScreenOrigin(
                    sw - pw - inset,
                    sh / 2 + prefix);
            case BOTTOM_LEFT -> new ScreenOrigin(
                    inset,
                    sh - ph - inset - prefix);
            case BOTTOM_CENTER -> new ScreenOrigin(
                    (sw - pw) / 2,
                    sh - ph - inset - prefix);
            case BOTTOM_RIGHT -> new ScreenOrigin(
                    sw - pw - inset,
                    sh - ph - inset - prefix);
            case CENTER -> new ScreenOrigin(
                    (sw - pw) / 2,
                    sh / 2 + crosshairClear + prefix);
        };
        return Optional.of(origin);
    }

    /**
     * Region-relative placement for {@link VanillaScreenRegion}s on a vanilla
     * non-container screen (Options, Controls, KeyBinds, etc.).
     *
     * <p>Parallel to {@link #resolveHud}: anchored to the screen's
     * GUI-scaled w × h, with {@link RegionConstants#EDGE_INSET} from
     * each edge. The only semantic difference vs HUD positioning is that
     * vanilla screens have NO crosshair behind them — the {@link
     * VanillaScreenRegion#CENTER} region anchors to the true screen
     * center, no crosshair clearance offset.
     *
     * <p>Returns {@link Optional#empty()} when {@code prefix + ph} exceeds
     * the available axis extent — caller treats as "this panel doesn't fit
     * in its region this frame" and skips render. {@link
     * com.trevorschoeny.menukit.inject.ScreenOrigin#OUT_OF_REGION} is the
     * adapter-side sentinel for the same condition.
     *
     * @param region  the destination region
     * @param sw      GUI-scaled screen width
     * @param sh      GUI-scaled screen height
     * @param pw      the panel's width (padding-inclusive)
     * @param ph      the panel's height (padding-inclusive)
     * @param prefix  total height of visible preceding panels in the same
     *                region, plus one {@link RegionConstants#MENU_STACK_GAP} per preceding panel
     */
    public static Optional<ScreenOrigin> resolveVanillaScreen(
            VanillaScreenRegion region, int sw, int sh,
            int pw, int ph, int prefix) {

        int inset = RegionConstants.EDGE_INSET;

        int available = switch (region) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT,
                 BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> sh - inset * 2;
            case LEFT_CENTER, RIGHT_CENTER -> sh / 2 - inset;
            case CENTER -> sh / 2 - inset;
        };
        if (prefix + ph > available) return Optional.empty();

        ScreenOrigin origin = switch (region) {
            case TOP_LEFT -> new ScreenOrigin(
                    inset,
                    inset + prefix);
            case TOP_CENTER -> new ScreenOrigin(
                    (sw - pw) / 2,
                    inset + prefix);
            case TOP_RIGHT -> new ScreenOrigin(
                    sw - pw - inset,
                    inset + prefix);
            case LEFT_CENTER -> new ScreenOrigin(
                    inset,
                    sh / 2 + prefix);
            case RIGHT_CENTER -> new ScreenOrigin(
                    sw - pw - inset,
                    sh / 2 + prefix);
            case BOTTOM_LEFT -> new ScreenOrigin(
                    inset,
                    sh - ph - inset - prefix);
            case BOTTOM_CENTER -> new ScreenOrigin(
                    (sw - pw) / 2,
                    sh - ph - inset - prefix);
            case BOTTOM_RIGHT -> new ScreenOrigin(
                    sw - pw - inset,
                    sh - ph - inset - prefix);
            case CENTER -> new ScreenOrigin(
                    (sw - pw) / 2,
                    (sh - ph) / 2 + prefix);
        };
        return Optional.of(origin);
    }
}
