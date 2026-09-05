package com.trevlar.menukit.core;

/**
 * Named regions for positioning decoration panels inside vanilla menu screens.
 * Each region anchors to the menu's container frame (leftPos/topPos/imageWidth/imageHeight)
 * and declares a flow direction for stacking multiple panels in the same region.
 *
 * <p><b>Coverage.</b> Eight edge regions — one for each of the four menu sides
 * (left, right, top, bottom) combined with two alignment ends per side
 * (top/bottom for vertical sides, left/right for horizontal sides). The
 * {@code SIDE_ALIGN_END} naming reads as: "on {@code SIDE} of the menu,
 * aligned to {@code END}, stacking away from {@code END}."
 *
 * <p>Plus three centered anchors — {@link #CENTER} for centered-in-frame
 * placement (Phase 14d-1 addition for modal dialogs), and {@link #TOP_CENTER}
 * / {@link #BOTTOM_CENTER} for above-/below-the-frame placement centered on
 * the horizontal axis (Phase 3b — Item 4a; brings MenuRegion to parity with
 * {@link HudRegion} / {@link ScreenRegion}, which each carry
 * TOP_CENTER / BOTTOM_CENTER / CENTER). {@code CENTER} is a single-position
 * anchor; {@code TOP_CENTER} / {@code BOTTOM_CENTER} stack vertically away
 * from the frame, each panel staying horizontally centered.
 *
 * <p><b>Flow direction</b> — stacking grows away from the anchor end:
 * <ul>
 *   <li>{@link #LEFT_ALIGN_TOP} / {@link #RIGHT_ALIGN_TOP} — flow down
 *   <li>{@link #LEFT_ALIGN_BOTTOM} / {@link #RIGHT_ALIGN_BOTTOM} — flow up
 *   <li>{@link #TOP_ALIGN_LEFT} / {@link #BOTTOM_ALIGN_LEFT} — flow right
 *   <li>{@link #TOP_ALIGN_RIGHT} / {@link #BOTTOM_ALIGN_RIGHT} — flow left
 *   <li>{@link #TOP_CENTER} — flow up (above the frame, centered)
 *   <li>{@link #BOTTOM_CENTER} — flow down (below the frame, centered)
 *   <li>{@link #CENTER} — no stacking (single-position anchor)
 * </ul>
 *
 * <p>See {@code Design Docs/Phase 12/M5_REGION_SYSTEM.md} for the full design
 * and {@code M5_REGION_SPECS.md} for the authoritative region catalog.
 */
public enum MenuRegion {
    LEFT_ALIGN_TOP,
    LEFT_ALIGN_BOTTOM,
    RIGHT_ALIGN_TOP,
    RIGHT_ALIGN_BOTTOM,
    TOP_ALIGN_LEFT,
    TOP_ALIGN_RIGHT,
    BOTTOM_ALIGN_LEFT,
    BOTTOM_ALIGN_RIGHT,

    /**
     * Above the menu's container frame, centered on the horizontal axis
     * (Phase 3b — Item 4a). The panel renders at
     * {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos - panelHeight - STACK_GAP - prefix)}, so stacking grows
     * UP (away from the menu), each sibling staying horizontally centered.
     *
     * <p>Parity counterpart to {@link HudRegion#TOP_CENTER} /
     * {@link ScreenRegion#TOP_CENTER}, anchored to the menu frame
     * rather than the screen edge.
     */
    TOP_CENTER,

    /**
     * Below the menu's container frame, centered on the horizontal axis
     * (Phase 3b — Item 4a). The panel renders at
     * {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos + imageHeight + STACK_GAP + prefix)}, so stacking grows
     * DOWN (away from the menu), each sibling staying horizontally centered.
     *
     * <p>Parity counterpart to {@link HudRegion#BOTTOM_CENTER} /
     * {@link ScreenRegion#BOTTOM_CENTER}.
     */
    BOTTOM_CENTER,

    /**
     * Centered within the menu's container frame. Canonical anchor for
     * modal dialogs (Phase 14d-1). The panel renders at
     * {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos + (imageHeight - panelHeight) / 2)}.
     *
     * <p>Stacking semantics: CENTER is a single-position anchor. Multiple
     * panels registered with CENTER all resolve to the same origin and
     * overlap (or, for modal dialogs, the consumer is expected to gate
     * visibility so only one is up at a time).
     *
     * <p>Centers within the menu's container frame, not the screen window.
     * For most vanilla menus the frame is roughly mid-screen so the result
     * looks visually centered; consumers wanting strict screen-window
     * centering use a lambda-anchor adapter as the escape hatch.
     */
    CENTER;

    /**
     * Returns true if panels in this region stack along the X axis.
     *
     * <p>TOP_ALIGN / BOTTOM_ALIGN regions flow horizontally (panels arranged
     * left-to-right or right-to-left above/below the menu frame). LEFT / RIGHT
     * regions flow vertically. {@link #TOP_CENTER} / {@link #BOTTOM_CENTER}
     * flow vertically (away from the frame, staying horizontally centered).
     * CENTER does not stack — value is conventionally {@code false}.
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
     * stacking priority. Use when sibling panels in the same region need
     * deterministic ordering relative to each other — pass the result
     * anywhere a {@link MenuRegion} is accepted.
     *
     * <p>Lower priority renders first (closer to the region's anchor edge).
     * Default priority (when {@code priority(int)} is not called) is
     * {@link RegionAnchor#DEFAULT_PRIORITY} (100); the registering mod's
     * modId serves as the tiebreaker for siblings sharing a priority.
     *
     * @see RegionAnchor
     */
    public RegionAnchor<MenuRegion> priority(int priority) {
        return new RegionAnchor<>(this, priority);
    }
}
