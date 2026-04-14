package com.trevorschoeny.menukit.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A visual or interactive element within a {@link Panel}. Elements are
 * positioned absolutely within the panel's content area (after padding)
 * using {@code childX}/{@code childY} coordinates.
 *
 * <p>Panel elements are a decorative/interactive layer on top of the slot
 * grid layout. Slot groups determine the panel's size; elements are
 * positioned within that space but don't affect it.
 *
 * <p>Elements are per-panel-instance — each panel constructs its own
 * elements via the builder. Elements aren't shared between panels.
 *
 * <p>Core implementations: {@link Button}, {@link TextLabel}.
 * Consumer mods can implement this interface for custom element types
 * (icons, progress bars, etc.).
 *
 * @see Panel              The container that holds elements
 * @see Button             Interactive button element
 * @see TextLabel          Static or dynamic text element
 */
public interface PanelElement {

    // ── Position & Bounds ──────────────────────────────────────────────
    // Coordinates are relative to the panel's content area (after padding).
    // The screen converts these to screen-space for rendering and hit testing.

    /** X position within the panel's content area. */
    int getChildX();

    /** Y position within the panel's content area. */
    int getChildY();

    /** Width in pixels. */
    int getWidth();

    /** Height in pixels. */
    int getHeight();

    // ── Visibility ─────────────────────────────────────────────────────
    // Elements can be conditionally shown. The screen checks this before
    // rendering or routing clicks — invisible elements are fully inert.
    // Panel-level visibility is checked separately (hidden panels skip
    // all element processing, like they skip slot rendering).

    /**
     * Returns whether this element is currently visible. Invisible elements
     * are not rendered and don't receive clicks. Default: always visible.
     *
     * <p>Override to gate visibility on runtime state (e.g., "show this
     * button only when the extras panel is visible").
     */
    default boolean isVisible() { return true; }

    // ── Rendering ──────────────────────────────────────────────────────

    /**
     * Renders this element. Called during the panel background pass
     * (screen space), after slot backgrounds.
     *
     * @param graphics      the graphics context
     * @param contentX      screen-space X of the panel's content area origin
     * @param contentY      screen-space Y of the panel's content area origin
     * @param mouseX        screen-space mouse X
     * @param mouseY        screen-space mouse Y
     */
    void render(GuiGraphics graphics, int contentX, int contentY,
                int mouseX, int mouseY);

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * Called when the mouse is clicked. Returns true if this element
     * consumed the click (preventing further dispatch to slots or vanilla).
     *
     * <p>The {@code button} parameter is the mouse button: 0=left, 1=right,
     * 2=middle. Core {@link Button} only consumes left-click; custom
     * elements can handle any button.
     *
     * @param mouseX  screen-space mouse X
     * @param mouseY  screen-space mouse Y
     * @param button  mouse button (0=left, 1=right, 2=middle)
     * @return true if consumed, false to let the click fall through
     */
    boolean mouseClicked(double mouseX, double mouseY, int button);
}
