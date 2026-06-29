package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.inject.ScreenOrigin;
import com.trevorschoeny.menukit.inject.SlotGroupBounds;

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
     * Resolves a SlotGroupContext region panel's origin. Anchors to the
     * slot group's bounding rectangle ({@link SlotGroupBounds}) rather
     * than the screen frame, but is otherwise identical to
     * {@link RegionMath#resolveMenu} — same anchor semantics, same overflow
     * cutoff, same {@link RegionConstants#MENU_STACK_GAP} spacing.
     *
     * <p>Bounds are computed per frame by
     * {@link com.trevorschoeny.menukit.inject.SlotGroupPanelRegistry} walking
     * the slot list for the target category.
     */
    public static Optional<ScreenOrigin> resolveSlotGroup(
            SlotGroupRegion region, SlotGroupBounds bounds,
            int pw, int ph, int prefix) {

        int leftPos = bounds.leftPos();
        int topPos = bounds.topPos();
        int imageWidth = bounds.imageWidth();
        int imageHeight = bounds.imageHeight();

        // Overflow check — semantics vary by region (mirrors RegionMath.resolveMenu).
        // - CENTER doesn't stack and must fit within both axes.
        // - Edge + centered-edge regions check overflow along their flow axis,
        //   accounting for prefix from previously-stacked panels.
        if (region == SlotGroupRegion.CENTER) {
            if (pw > imageWidth || ph > imageHeight) return Optional.empty();
        } else {
            int available = region.isHorizontalFlow() ? imageWidth : imageHeight;
            int selfExtent = region.isHorizontalFlow() ? pw : ph;
            if (prefix + selfExtent > available) return Optional.empty();
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
        return Optional.of(origin);
    }
}
