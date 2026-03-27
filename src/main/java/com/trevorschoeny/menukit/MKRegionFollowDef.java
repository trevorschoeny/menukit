package com.trevorschoeny.menukit;

import org.jspecify.annotations.Nullable;

/**
 * Specifies that a panel should automatically track a named {@link MKRegion}'s
 * (or {@link MKRegionGroup}'s) bounding box each frame.
 *
 * <p>When the named region/group is present in the active menu, the panel
 * positions itself relative to the target with the given pixel gap.
 * When the target is absent (e.g., a chest region when only the player
 * inventory is open), the panel is suppressed via
 * {@link MenuKit#isPanelSuppressed(String)}.
 *
 * <p>Two positioning modes:
 * <ul>
 *   <li><b>Legacy (center-on-side)</b>: {@code placement} is null, uses
 *       {@code direction} to center on one side of the region.</li>
 *   <li><b>8-placement</b>: {@code placement} is non-null, uses
 *       {@link MKRegionPlacement} for precise edge + alignment positioning
 *       with per-placement stacking.</li>
 * </ul>
 *
 * <p>Created by {@link MKPanel.Builder#followsRegion} or
 * {@link MKPanel.Builder#followsGroup}.
 *
 * <p>Part of the <b>MenuKit</b> framework API.
 */
public record MKRegionFollowDef(
        String regionName,                      // which named region or group to track
        MKAnchor direction,                      // which side (legacy center-on-side mode)
        int gap,                                 // pixel gap between the target edge and panel edge
        boolean isGroup,                         // true = regionName refers to an MKRegionGroup
        @Nullable MKRegionPlacement placement,   // non-null = use 8-placement logic instead of legacy
        int offsetX,                             // fine-tuning pixel offset after placement
        int offsetY                              // fine-tuning pixel offset after placement
) {
    /**
     * Legacy constructor for backward compatibility.
     * Creates a single-region, center-on-side follow def.
     */
    public MKRegionFollowDef(String regionName, MKAnchor direction, int gap) {
        this(regionName, direction, gap, false, null, 0, 0);
    }

    /** Constructor without offset (backward compat). */
    public MKRegionFollowDef(String regionName, MKAnchor direction, int gap,
                              boolean isGroup, @Nullable MKRegionPlacement placement) {
        this(regionName, direction, gap, isGroup, placement, 0, 0);
    }
}
