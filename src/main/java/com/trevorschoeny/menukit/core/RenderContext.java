package com.trevorschoeny.menukit.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Render-time context passed to every {@link PanelElement#render}. Bundles the
 * graphics handle, the element's containing content origin, and the current
 * mouse position.
 *
 * <p>Works across all three rendering contexts:
 * <ul>
 *   <li><b>Inventory menus and standalone screens</b> — full context including
 *   live mouse coordinates for hover detection.</li>
 *   <li><b>HUDs</b> — {@code mouseX} and {@code mouseY} are {@code -1}, signalling
 *   that no input dispatch is happening. {@link #hasMouseInput()} returns
 *   {@code false}; {@link #isHovered} returns {@code false} uniformly.</li>
 * </ul>
 *
 * <p>The {@code -1} sentinel for "no input dispatch" is a deliberate convention,
 * not a bug — HUDs render without input routing per library doctrine. Consumers
 * should use {@link #hasMouseInput()} and {@link #isHovered} rather than
 * inspecting the raw coordinates.
 *
 * <p>This record is the single parameter for {@link PanelElement#render},
 * replacing flat parameter lists. Future additions (animation tick, focus
 * state, viewport bounds) can be added as record fields without breaking
 * element implementations.
 *
 * @param graphics  the graphics handle
 * @param originX   screen-space X of the element's containing content origin
 *                  (the panel's content area after padding). The element adds
 *                  its own {@code childX} to position itself.
 * @param originY   screen-space Y of the element's containing content origin
 * @param mouseX    screen-space mouse X, or {@code -1} if no input dispatch
 * @param mouseY    screen-space mouse Y, or {@code -1} if no input dispatch
 */
public record RenderContext(
        GuiGraphics graphics,
        int originX,
        int originY,
        int mouseX,
        int mouseY
) {

    /**
     * Returns whether mouse input is present in this render pass. HUDs return
     * {@code false}; inventory menus and standalone screens return {@code true}.
     */
    public boolean hasMouseInput() {
        return mouseX >= 0;
    }

    /**
     * Tests whether the mouse is over a bounded region within this context's
     * content origin. Returns {@code false} when no input dispatch is present
     * (HUDs), regardless of mouse coordinates.
     *
     * @param childX element's X position relative to the content origin
     * @param childY element's Y position relative to the content origin
     * @param width  element's width in pixels
     * @param height element's height in pixels
     */
    public boolean isHovered(int childX, int childY, int width, int height) {
        if (!hasMouseInput()) return false;
        int sx = originX + childX;
        int sy = originY + childY;
        return mouseX >= sx && mouseX < sx + width
                && mouseY >= sy && mouseY < sy + height;
    }
}
