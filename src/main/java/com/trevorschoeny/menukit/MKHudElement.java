package com.trevorschoeny.menukit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A visual element rendered inside an {@link MKHudPanel} on the game's HUD.
 *
 * <p>Implementations are stateless definitions — all dynamic data comes from
 * {@code Supplier<T>} fields evaluated at render time. The only exception is
 * {@link MKHudNotification} which tracks animation state.
 *
 * <p>Elements report their size via {@link #getWidth()} and {@link #getHeight()}
 * for panel auto-sizing and layout group calculations.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public interface MKHudElement {

    /**
     * Renders this element at the given absolute screen position.
     * The position includes the parent panel's anchor offset + padding.
     *
     * @param graphics     the GUI graphics context (same as vanilla HUD uses)
     * @param x            absolute screen X for this element's top-left
     * @param y            absolute screen Y for this element's top-left
     * @param deltaTracker tick delta for animations
     */
    void render(GuiGraphics graphics, int x, int y, DeltaTracker deltaTracker);

    /**
     * Returns the element's width in GUI-scaled pixels.
     * May evaluate suppliers for dynamic content (e.g., text width).
     */
    int getWidth();

    /**
     * Returns the element's height in GUI-scaled pixels.
     * May evaluate suppliers for dynamic content.
     */
    int getHeight();
}
