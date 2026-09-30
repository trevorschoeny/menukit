package com.trevlar.menukit.core;

/**
 * Input-time context passed to every {@link PanelElement} input method, the input
 * twin of {@link RenderContext} (§0066): where the element's containing content
 * origin is on screen, where the mouse is, and whether the element's
 * surroundings are disabled.
 *
 * <h3>Why input receives its context</h3>
 *
 * Before 6.0.0, {@code mouseClicked(mouseX, mouseY, button)} had no origin, so
 * an element that needed to know where it was (a {@link Dropdown} finding its
 * popover, a {@link ScrollContainer} finding its scrollbar) cached the origin
 * from its last {@code render}, and simple controls cached {@code hovered} from
 * the last frame to gate their clicks. Each cache was a frame stale and each
 * container re-derived its children's origins its own way. Now the dispatcher
 * (the panel's host, or a container through {@link ChildDispatch}) hands every
 * element the same facts render gets, for the moment of the event. No element
 * caches its origin or its hover state.
 *
 * <p>Coordinates are screen space, as before: {@code mouseX}/{@code mouseY} are
 * the event's position, {@code originX}/{@code originY} the content origin the
 * element's {@code childX}/{@code childY} are relative to. A container passes
 * its children {@link #at} their own origin.
 *
 * <h3>Disabled</h3>
 *
 * When {@code disabled} is true the dispatcher does not deliver clicks, wheel or
 * keys at all (a disabled panel or container is inert inside, though it still
 * claims its area). Releases and outside clicks still arrive with the flag set,
 * so a drag or an open popover can end. An element also checks its own
 * {@code disabledWhen}.
 *
 * @param originX  screen-space X of the element's containing content origin
 * @param originY  screen-space Y of the element's containing content origin
 * @param mouseX   screen-space mouse X of the event
 * @param mouseY   screen-space mouse Y of the event
 * @param disabled whether the element's panel or container is disabled
 */
public record InputContext(int originX, int originY, double mouseX, double mouseY, boolean disabled) {

    /** Whether the mouse is inside the box at {@code (childX, childY)} of this origin. */
    public boolean isOver(int childX, int childY, int width, int height) {
        double sx = originX + childX;
        double sy = originY + childY;
        return mouseX >= sx && mouseX < sx + width && mouseY >= sy && mouseY < sy + height;
    }

    /** Whether the mouse is inside {@code element}'s layout bounds at this origin. */
    public boolean isOver(PanelElement element) {
        return isOver(element.getChildX(), element.getChildY(), element.getWidth(), element.getHeight());
    }

    /** Whether the mouse is inside a screen-space rectangle {@code [x, y, w, h]}. */
    public boolean isInside(int[] rect) {
        return mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    /** The same event with the content origin moved: what a container hands its children. */
    public InputContext at(int originX, int originY) {
        return new InputContext(originX, originY, mouseX, mouseY, disabled);
    }

    /** This context, disabled as well when {@code alsoDisabled} holds. Never re-enables. */
    public InputContext disabledIf(boolean alsoDisabled) {
        return alsoDisabled && !disabled ? new InputContext(originX, originY, mouseX, mouseY, true) : this;
    }
}
