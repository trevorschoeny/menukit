package com.trevlar.menukit.core;

/**
 * A region <b>outside</b> a reference rectangle: pick a side, pick which end to
 * align to, and stack away from that end. Eleven of them.
 *
 * <p>This names the <em>vocabulary</em>, not the reference. The reference — which
 * rectangle you are measured against — is decided by the call site: a
 * {@code ScreenPanelAdapter} means the container menu's frame, a
 * {@code SlotGroupPanelAdapter} means the slot group it targets. One enum serves
 * both, because "to the right of the box, aligned to its top" means the same thing
 * whichever box it is. (Before 5.0.0 this was two identical enums,
 * {@code MenuRegion} and {@code SlotGroupRegion}, differing only in the reference
 * they implied — which read as though a slot group had a region of its own.)
 *
 * <p>Its counterpart is {@link InsideRegion}, the nine spots <em>on</em> a
 * rectangle. The two are shaped differently and do not merge: outside is
 * parameterized by side and alignment end, inside by a three-by-three grid, and
 * "outside the top-left corner" is not well defined.
 *
 * <p><b>Coverage.</b> Eight edge regions — each of the four sides combined with
 * two alignment ends — plus three centered anchors. {@code SIDE_ALIGN_END} reads
 * as: "on {@code SIDE} of the reference, aligned to {@code END}, stacking away
 * from {@code END}."
 *
 * <p><b>{@link #CENTER} is the deliberate exception</b>: it centers a panel
 * <em>inside</em> the reference rather than outside it, which is what a modal
 * dialog wants. It is the one inside-placement in an outside vocabulary, kept here
 * because modal placement has always lived on this enum. It does not stack.
 *
 * <p><b>Flow direction</b> — stacking grows away from the anchor end:
 * <ul>
 *   <li>{@link #LEFT_ALIGN_TOP} / {@link #RIGHT_ALIGN_TOP} — flow down
 *   <li>{@link #LEFT_ALIGN_BOTTOM} / {@link #RIGHT_ALIGN_BOTTOM} — flow up
 *   <li>{@link #TOP_ALIGN_LEFT} / {@link #BOTTOM_ALIGN_LEFT} — flow right
 *   <li>{@link #TOP_ALIGN_RIGHT} / {@link #BOTTOM_ALIGN_RIGHT} — flow left
 *   <li>{@link #TOP_CENTER} — flow up, above the reference, centered
 *   <li>{@link #BOTTOM_CENTER} — flow down, below the reference, centered
 *   <li>{@link #CENTER} — no stacking
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
     * Above the menu's container frame, centered on the horizontal axis
     * (Phase 3b — Item 4a). The panel renders at
     * {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos - panelHeight - STACK_GAP - prefix)}, so stacking grows
     * UP (away from the menu), each sibling staying horizontally centered.
     *
     * <p>Parity counterpart to {@link InsideRegion#TOP_CENTER}, anchored to
     * the reference rectangle rather than the screen edge.
     */
    TOP_CENTER,

    /**
     * Below the menu's container frame, centered on the horizontal axis
     * (Phase 3b — Item 4a). The panel renders at
     * {@code (leftPos + (imageWidth - panelWidth) / 2,
     *         topPos + imageHeight + STACK_GAP + prefix)}, so stacking grows
     * DOWN (away from the menu), each sibling staying horizontally centered.
     *
     * <p>Parity counterpart to {@link InsideRegion#BOTTOM_CENTER}.
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
     * looks visually centered; for strict screen-window centering declare
     * {@link PanelPosition#center()} instead.
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
}
