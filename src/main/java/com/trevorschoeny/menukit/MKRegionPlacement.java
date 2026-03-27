package com.trevorschoeny.menukit;

/**
 * Specifies where a panel sits relative to a region or region group's bounding box,
 * and how it aligns along that edge.
 *
 * <p>Naming convention: {@code {SIDE}_{ALIGNMENT}} where:
 * <ul>
 *   <li><b>SIDE</b> = which edge of the region the panel sits on (TOP, BOTTOM, LEFT, RIGHT)</li>
 *   <li><b>ALIGNMENT</b> = which corner of that edge the panel anchors to (LEFT/RIGHT for
 *       horizontal edges, TOP/BOTTOM for vertical edges)</li>
 * </ul>
 *
 * <p>When multiple panels share the same placement on the same region, they
 * <b>stack along the edge</b>, away from their alignment anchor:
 *
 * <pre>
 * Placement     | Stack direction
 * --------------|----------------
 * TOP_LEFT      | Rightward  ->
 * TOP_RIGHT     | Leftward   <-
 * BOTTOM_LEFT   | Rightward  ->
 * BOTTOM_RIGHT  | Leftward   <-
 * LEFT_TOP      | Downward   v
 * LEFT_BOTTOM   | Upward     ^
 * RIGHT_TOP     | Downward   v
 * RIGHT_BOTTOM  | Upward     ^
 * </pre>
 *
 * <p>Part of the <b>MenuKit</b> framework API.
 */
public enum MKRegionPlacement {
    /** Panel sits above the region, anchored to the left edge. Stacks rightward. */
    TOP_LEFT,
    /** Panel sits above the region, anchored to the right edge. Stacks leftward. */
    TOP_RIGHT,
    /** Panel sits to the right of the region, anchored to the top edge. Stacks downward. */
    RIGHT_TOP,
    /** Panel sits to the right of the region, anchored to the bottom edge. Stacks upward. */
    RIGHT_BOTTOM,
    /** Panel sits below the region, anchored to the left edge. Stacks rightward. */
    BOTTOM_LEFT,
    /** Panel sits below the region, anchored to the right edge. Stacks leftward. */
    BOTTOM_RIGHT,
    /** Panel sits to the left of the region, anchored to the top edge. Stacks downward. */
    LEFT_TOP,
    /** Panel sits to the left of the region, anchored to the bottom edge. Stacks upward. */
    LEFT_BOTTOM
}
