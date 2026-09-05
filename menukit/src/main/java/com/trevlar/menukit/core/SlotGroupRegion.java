package com.trevlar.menukit.core;

/**
 * Named regions for positioning decoration panels around the bounding box
 * of a slot group. Parallel to {@link MenuRegion} — same eight values, same
 * semantics, same flow directions — but anchors to a slot group's bounding
 * rectangle rather than a vanilla menu's container frame.
 *
 * <p>See {@code Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md} §5 for
 * context model and §5.5 for why this enum is a separate type from
 * {@link MenuRegion} despite having identical members.
 *
 * <p><b>Flow direction</b> — stacking grows away from the anchor end,
 * identical to {@link MenuRegion}:
 * <ul>
 *   <li>{@link #LEFT_ALIGN_TOP} / {@link #RIGHT_ALIGN_TOP} — flow down
 *   <li>{@link #LEFT_ALIGN_BOTTOM} / {@link #RIGHT_ALIGN_BOTTOM} — flow up
 *   <li>{@link #TOP_ALIGN_LEFT} / {@link #BOTTOM_ALIGN_LEFT} — flow right
 *   <li>{@link #TOP_ALIGN_RIGHT} / {@link #BOTTOM_ALIGN_RIGHT} — flow left
 *   <li>{@link #TOP_CENTER} — flow up (above the group bounds, centered)
 *   <li>{@link #BOTTOM_CENTER} — flow down (below the group bounds, centered)
 *   <li>{@link #CENTER} — no stacking (single-position anchor)
 * </ul>
 *
 * <p>The three centered anchors ({@link #TOP_CENTER} / {@link #BOTTOM_CENTER}
 * / {@link #CENTER}) were added in Phase 3b (Item 4a) for parity with
 * {@link MenuRegion}; they resolve against the slot-group bounding rectangle
 * with the same math the menu uses against the container frame.
 */
public enum SlotGroupRegion {
    LEFT_ALIGN_TOP,
    LEFT_ALIGN_BOTTOM,
    RIGHT_ALIGN_TOP,
    RIGHT_ALIGN_BOTTOM,
    TOP_ALIGN_LEFT,
    TOP_ALIGN_RIGHT,
    BOTTOM_ALIGN_LEFT,
    BOTTOM_ALIGN_RIGHT,

    /**
     * Above the slot-group bounding rectangle, centered on the horizontal
     * axis. Stacks UP (away from the group); each sibling stays centered.
     * Parity with {@link MenuRegion#TOP_CENTER}.
     */
    TOP_CENTER,

    /**
     * Below the slot-group bounding rectangle, centered on the horizontal
     * axis. Stacks DOWN (away from the group); each sibling stays centered.
     * Parity with {@link MenuRegion#BOTTOM_CENTER}.
     */
    BOTTOM_CENTER,

    /**
     * Centered within the slot-group bounding rectangle. Single-position
     * anchor — multiple panels in CENTER overlap. Parity with
     * {@link MenuRegion#CENTER}.
     */
    CENTER;

    /**
     * Returns true if panels in this region stack along the X axis —
     * matches {@link MenuRegion#isHorizontalFlow()} semantics. The centered
     * anchors flow vertically ({@link #TOP_CENTER} / {@link #BOTTOM_CENTER})
     * or not at all ({@link #CENTER}), so all three return {@code false}.
     */
    public boolean isHorizontalFlow() {
        return switch (this) {
            case TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT,
                 BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT -> true;
            case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM,
                 RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM -> false;
            case TOP_CENTER, BOTTOM_CENTER -> false;
            case CENTER -> false;
        };
    }

    /**
     * Returns a {@link RegionAnchor} pairing this region with an explicit
     * stacking priority. Brings SlotGroupRegion to parity with
     * {@link MenuRegion#priority(int)} / {@link HudRegion#priority(int)} /
     * {@link ScreenRegion#priority(int)}.
     *
     * <p>Pass the result to a
     * {@link com.trevlar.menukit.inject.SlotGroupPanelAdapter} via its
     * {@code RegionAnchor} constructor — e.g.
     * {@code new SlotGroupPanelAdapter(panel,
     * SlotGroupRegion.RIGHT_ALIGN_TOP.priority(50))}. Lower priority renders
     * first (closer to the region's anchor edge); default is
     * {@link RegionAnchor#DEFAULT_PRIORITY}. Sibling panels in the same
     * (category, region) pair stack in {@code (priority, modId, registration)}
     * order, identical to the other three region contexts.
     *
     * @see RegionAnchor
     * @see com.trevlar.menukit.inject.SlotGroupPanelAdapter
     */
    public RegionAnchor<SlotGroupRegion> priority(int priority) {
        return new RegionAnchor<>(this, priority);
    }
}
