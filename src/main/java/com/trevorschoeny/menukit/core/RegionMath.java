package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.inject.ScreenBounds;
import com.trevorschoeny.menukit.inject.ScreenOrigin;

import java.util.Optional;

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
public final class RegionMath {

    private RegionMath() {}

    // ── Shared constants ────────────────────────────────────────────────

    /** Gap between stacked panels along the flow axis (pixels, GUI-scaled). */
    public static final int STACK_GAP = 2;

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
     *                same region, plus one {@link #STACK_GAP} per preceding
     *                panel
     */
    public static Optional<ScreenOrigin> resolveMenu(
            MenuRegion region, ScreenBounds bounds,
            int pw, int ph, int prefix) {

        int leftPos = bounds.leftPos();
        int topPos = bounds.topPos();
        int imageWidth = bounds.imageWidth();
        int imageHeight = bounds.imageHeight();

        // Overflow check along the flow axis.
        int available = region.isHorizontalFlow() ? imageWidth : imageHeight;
        int selfExtent = region.isHorizontalFlow() ? pw : ph;
        if (prefix + selfExtent > available) return Optional.empty();

        ScreenOrigin origin = switch (region) {
            case RIGHT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos + imageWidth + STACK_GAP,
                    topPos + prefix);
            case RIGHT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos + imageWidth + STACK_GAP,
                    topPos + imageHeight - ph - prefix);
            case LEFT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos - pw - STACK_GAP,
                    topPos + prefix);
            case LEFT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos - pw - STACK_GAP,
                    topPos + imageHeight - ph - prefix);
            case TOP_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos - ph - STACK_GAP);
            case TOP_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos - ph - STACK_GAP);
            case BOTTOM_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos + imageHeight + STACK_GAP);
            case BOTTOM_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos + imageHeight + STACK_GAP);
        };
        return Optional.of(origin);
    }

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
     *                region, plus one {@link #STACK_GAP} per preceding panel
     */
    public static Optional<ScreenOrigin> resolveHud(
            HudRegion region, int sw, int sh,
            int pw, int ph, int prefix) {

        int inset = HudRegion.EDGE_INSET;
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
}
