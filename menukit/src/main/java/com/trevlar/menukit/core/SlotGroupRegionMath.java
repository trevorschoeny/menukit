package com.trevlar.menukit.core;

import com.trevlar.menukit.inject.ScreenOrigin;
import com.trevlar.menukit.inject.SlotGroupBounds;

import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Pure coordinate resolver for SlotGroupContext regions.
 * Post-§0042 split companion to MenuKit's {@link RegionMath} (which
 * resolves MenuContext + HudContext regions).
 *
 * <p><b>Pure by design.</b> No registry state, no Panel references, no
 * per-frame side effects. Same shape as {@link RegionMath} — given explicit
 * inputs (slot-group bounds, panel dimensions, stacking prefix), returns
 * the panel's screen-space origin or {@link Optional#empty()} if the
 * panel's extent exceeds the slot group's available space.
 *
 * <p>Lives in MenuKit: Containers because slot-group regions reference
 * {@link SlotGroupRegion} (slot-group enum) and {@link SlotGroupBounds}
 * (slot-group bounding rectangle) — slot-related types per §0042.
 */
@ApiStatus.Internal
public final class SlotGroupRegionMath {

    private SlotGroupRegionMath() {}

    /**
     * OUTER available width for a slot-group-anchored panel before it crosses
     * the screen-edge margin (Pass 3). Mirrors
     * {@link RegionMath#availableMenuWidth} exactly — same per-region anchor
     * geometry — but anchored to the {@link SlotGroupBounds} rather than the
     * menu frame, reusing {@link RegionMath}'s shared growth-direction
     * arithmetic so the budget math lives in one place.
     */
    public static int availableSlotGroupWidth(SlotGroupRegion region,
                                              SlotGroupBounds b, int sw, int margin) {
        int leftPos = b.leftPos();
        int imageWidth = b.imageWidth();
        int gap = RegionConstants.MENU_STACK_GAP;
        int centerX = leftPos + imageWidth / 2;
        return switch (region) {
            case RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM ->
                    RegionMath.growRightWidth(leftPos + imageWidth + gap, sw, margin);
            case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM ->
                    RegionMath.growLeftWidth(leftPos - gap, margin);
            case TOP_ALIGN_LEFT, BOTTOM_ALIGN_LEFT ->
                    RegionMath.growRightWidth(leftPos, sw, margin);
            case TOP_ALIGN_RIGHT, BOTTOM_ALIGN_RIGHT ->
                    RegionMath.growLeftWidth(leftPos + imageWidth, margin);
            case TOP_CENTER, BOTTOM_CENTER ->
                    RegionMath.centeredWidth(centerX, sw, margin);
            case CENTER -> Math.min(imageWidth, RegionMath.centeredWidth(centerX, sw, margin));
        };
    }

    /**
     * Resolves a SlotGroupContext region panel's origin. Anchors to the
     * slot group's bounding rectangle ({@link SlotGroupBounds}) rather
     * than the screen frame, but is otherwise identical to
     * {@link RegionMath#resolveMenu} — same anchor semantics, same overflow
     * cutoff, same {@link RegionConstants#MENU_STACK_GAP} spacing.
     *
     * <p>Bounds are computed per frame by
     * {@link com.trevlar.menukit.inject.SlotGroupPanelRegistry} walking
     * the slot list for the target category.
     */
    public static Optional<ScreenOrigin> resolveSlotGroup(
            SlotGroupRegion region, SlotGroupBounds bounds,
            int pw, int ph, int prefix, int sw, int sh) {

        int leftPos = bounds.leftPos();
        int topPos = bounds.topPos();
        int imageWidth = bounds.imageWidth();
        int imageHeight = bounds.imageHeight();

        // CENTER is in-bounds by design; edge regions are gated AFTER the origin
        // is computed, against the SCREEN safe area (below) — mirrors
        // RegionMath.resolveMenu's Pass-3 fix. A slot-group bbox is often ONE
        // ROW (e.g. the 16px hotbar), so gating an edge panel against the bbox
        // height/width hid any normally-sized panel — the panel is meant to
        // extend past the small group toward the screen edge.
        if (region == SlotGroupRegion.CENTER) {
            if (pw > imageWidth || ph > imageHeight) return Optional.empty();
        }

        int gap = RegionConstants.MENU_STACK_GAP;
        ScreenOrigin origin = switch (region) {
            case RIGHT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos + imageWidth + gap,
                    topPos + prefix);
            case RIGHT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos + imageWidth + gap,
                    topPos + imageHeight - ph - prefix);
            case LEFT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos - pw - gap,
                    topPos + prefix);
            case LEFT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos - pw - gap,
                    topPos + imageHeight - ph - prefix);
            case TOP_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos - ph - gap);
            case TOP_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos - ph - gap);
            case BOTTOM_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos + imageHeight + gap);
            case BOTTOM_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos + imageHeight + gap);
            // Centered anchors (Phase 3b — Item 4a). X = group-bounds
            // horizontal centering; Y mirrors the TOP_ALIGN/BOTTOM_ALIGN
            // edge math for TOP_CENTER/BOTTOM_CENTER, or bounds-vertical
            // centering for CENTER.
            case TOP_CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos - ph - gap - prefix);
            case BOTTOM_CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos + imageHeight + gap + prefix);
            case CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos + (imageHeight - ph) / 2);
        };

        // Screen safe-area conformance for edge regions (mirrors
        // RegionMath.resolveMenu): clamp the resolved origin into the safe area
        // rather than hiding the panel against the (often one-row) group bbox.
        if (region != SlotGroupRegion.CENTER) {
            int m = RegionConstants.SCREEN_EDGE_MARGIN;
            if (pw > sw - 2 * m || ph > sh - 2 * m) return Optional.empty();
            int cx = Math.max(m, Math.min(origin.x(), sw - m - pw));
            int cy = Math.max(m, Math.min(origin.y(), sh - m - ph));
            origin = new ScreenOrigin(cx, cy);
        }
        return Optional.of(origin);
    }
}
