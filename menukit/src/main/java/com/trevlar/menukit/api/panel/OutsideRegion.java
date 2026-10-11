package com.trevlar.menukit.api.panel;

/**
 * A region <b>outside</b> a reference rectangle: pick a side, pick which end to
 * align to, and stack away from that end. Eleven of them.
 *
 * <p>This names the <em>vocabulary</em>, not the reference. The reference, which
 * rectangle you are measured against, is decided by the call site: a
 * {@code ScreenPanelAdapter} means the container menu's frame, a
 * {@code SlotGroupPanelAdapter} means the slot group it targets. One enum serves
 * both, because "to the right of the box, aligned to its top" means the same thing
 * whichever box it is. (Before 5.0.0 this was two identical enums,
 * {@code MenuRegion} and {@code SlotGroupRegion}, differing only in the reference
 * they implied, which read as though a slot group had a region of its own.)
 *
 * <p>Its counterpart is {@link InsideRegion}, the nine spots <em>on</em> a
 * rectangle. The two are shaped differently and do not merge: outside is
 * parameterized by side and alignment end, inside by a three-by-three grid, and
 * "outside the top-left corner" is not well defined.
 *
 * <p>There are eight edge regions, each of the four sides with two alignment
 * ends, plus three centered anchors. {@code SIDE_ALIGN_END} reads
 * as: "on {@code SIDE} of the reference, aligned to {@code END}, stacking away
 * from {@code END}."
 *
 * <p>{@link #CENTER} is the exception: it centers a panel inside the reference
 * rather than outside it, as a modal dialog needs. It stays on this enum because
 * modal placement has always lived here. It does not stack.
 *
 * <p>Stacking grows away from the anchor end:
 * <ul>
 *   <li>{@link #LEFT_ALIGN_TOP} / {@link #RIGHT_ALIGN_TOP}, flow down
 *   <li>{@link #LEFT_ALIGN_BOTTOM} / {@link #RIGHT_ALIGN_BOTTOM}, flow up
 *   <li>{@link #TOP_ALIGN_LEFT} / {@link #BOTTOM_ALIGN_LEFT}, flow right
 *   <li>{@link #TOP_ALIGN_RIGHT} / {@link #BOTTOM_ALIGN_RIGHT}, flow left
 *   <li>{@link #TOP_CENTER}, flow up, above the reference, centered
 *   <li>{@link #BOTTOM_CENTER}, flow down, below the reference, centered
 *   <li>{@link #CENTER}, no stacking
 * </ul>
 */
public enum OutsideRegion {
    LEFT_ALIGN_TOP,
    LEFT_ALIGN_BOTTOM,
    RIGHT_ALIGN_TOP,
    RIGHT_ALIGN_BOTTOM,
    TOP_ALIGN_LEFT,
    TOP_ALIGN_RIGHT,
    BOTTOM_ALIGN_LEFT,
    BOTTOM_ALIGN_RIGHT,

    /**
     * Above the menu's container frame, centered horizontally. The panel renders
     * at {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos - panelHeight - STACK_GAP - prefix)}, so stacking grows up,
     * away from the menu, each sibling staying centered.
     *
     * <p>The counterpart of {@link InsideRegion#TOP_CENTER}, anchored to the
     * reference rectangle rather than the screen edge.
     */
    TOP_CENTER,

    /**
     * Below the menu's container frame, centered horizontally. The panel renders
     * at {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos + imageHeight + STACK_GAP + prefix)}, so stacking grows down,
     * away from the menu, each sibling staying centered.
     *
     * <p>The counterpart of {@link InsideRegion#BOTTOM_CENTER}.
     */
    BOTTOM_CENTER,

    /**
     * Centered within the menu's container frame, the usual anchor for modal
     * dialogs. The panel renders at
     * {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos + (imageHeight - panelHeight) / 2)}.
     *
     * <p>CENTER is a single position. Several panels registered with it all
     * resolve to the same origin and overlap; for modal dialogs the consumer
     * shows only one at a time.
     *
     * <p>It centers on the menu's frame, not the game window. For most vanilla
     * menus the frame is roughly mid-screen, so it looks centered; to center on
     * the window, declare {@link PanelPosition#center()} instead.
     */
    CENTER;

    /**
     * Whether panels in this region stack along the X axis.
     *
     * <p>The TOP_ALIGN and BOTTOM_ALIGN regions flow horizontally, above or below
     * the menu frame. The LEFT and RIGHT regions flow vertically, and so do
     * {@link #TOP_CENTER} and {@link #BOTTOM_CENTER}, away from the frame.
     * CENTER does not stack and returns {@code false}.
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
}
