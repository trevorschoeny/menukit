package com.trevorschoeny.menukit.core;

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
 * <p>Plus {@link #CENTER} for centered-in-frame placement (Phase 14d-1
 * addition for modal dialogs). CENTER is a single-position anchor; multiple
 * panels in CENTER overlap rather than stacking.
 *
 * <p><b>Flow direction</b> — stacking grows away from the anchor end:
 * <ul>
 *   <li>{@link #LEFT_ALIGN_TOP} / {@link #RIGHT_ALIGN_TOP} — flow down
 *   <li>{@link #LEFT_ALIGN_BOTTOM} / {@link #RIGHT_ALIGN_BOTTOM} — flow up
 *   <li>{@link #TOP_ALIGN_LEFT} / {@link #BOTTOM_ALIGN_LEFT} — flow right
 *   <li>{@link #TOP_ALIGN_RIGHT} / {@link #BOTTOM_ALIGN_RIGHT} — flow left
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
     * <p>TOP / BOTTOM regions flow horizontally (panels arranged left-to-right
     * or right-to-left above/below the menu frame). LEFT / RIGHT regions flow
     * vertically. CENTER does not stack — value is conventionally {@code false}.
     */
    public boolean isHorizontalFlow() {
        return switch (this) {
            case TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT,
                 BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT -> true;
            case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM,
                 RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM -> false;
            case CENTER -> false;
        };
    }
}
