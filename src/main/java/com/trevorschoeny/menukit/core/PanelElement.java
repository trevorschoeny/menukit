package com.trevorschoeny.menukit.core;

/**
 * A visual or interactive element within a {@link Panel}. Elements are
 * positioned absolutely within the panel's content area (after padding)
 * using {@code childX}/{@code childY} coordinates.
 *
 * <p>Panel elements are a decorative/interactive layer on top of the panel
 * backgrounds. In inventory-menu panels they sit alongside slot groups (which
 * live on the handler, not on the panel itself); in HUD panels and
 * standalone-screen panels they are the only content.
 *
 * <p>Elements are per-panel-instance — each panel constructs its own
 * elements via its builder. Elements aren't shared between panels.
 *
 * <p>Works uniformly across all three rendering contexts (inventory menus,
 * HUDs, standalone screens). The {@link RenderContext} bundles per-frame
 * state; in contexts without input dispatch (HUDs), {@link RenderContext#hasMouseInput}
 * returns {@code false} and {@link #mouseClicked} is never called by the
 * dispatcher.
 *
 * <p>Core implementations: {@link Button}, {@link TextLabel}.
 * Consumer mods can implement this interface for custom element types
 * (icons, progress bars, etc.).
 *
 * <h3>Coordinate contract</h3>
 *
 * Two coordinate spaces are in play across the element surface:
 *
 * <ul>
 *   <li><b>Panel-local</b>: {@link #getChildX} / {@link #getChildY} specify
 *       the element's position within the panel's content area (after
 *       padding). Fixed at construction; never mutated.</li>
 *   <li><b>Screen-space</b>: absolute coordinates in the game window's
 *       GUI-scaled pixel grid. Used by {@link RenderContext#mouseX} /
 *       {@link RenderContext#mouseY} and by the {@code mouseX} / {@code mouseY}
 *       parameters of {@link #mouseClicked}. {@link RenderContext#originX} /
 *       {@link RenderContext#originY} are the screen-space origin of the
 *       panel's content area.</li>
 * </ul>
 *
 * Render code composes the two: the element paints at
 * {@code (ctx.originX() + getChildX(), ctx.originY() + getChildY())}.
 * Input code uses screen-space directly — the dispatcher hit-tests in
 * screen-space and passes screen-space {@code mouseX} / {@code mouseY} to
 * {@link #mouseClicked}. Implementations of {@link #mouseClicked} should
 * trust this and not re-compose with panel-local coords.
 *
 * <p>All three dispatchers conform to this contract: the native inventory-menu
 * dispatcher in {@code MenuKitHandledScreen}, the standalone-screen
 * dispatcher in {@code MenuKitScreen}, and the Phase 10 injection adapter
 * {@code ScreenPanelAdapter}. Consumer implementations of
 * {@link PanelElement} can rely on screen-space coords in {@link #mouseClicked}
 * regardless of which context their panel ends up in.
 *
 * @see Panel              The container that holds elements
 * @see Button             Interactive button element
 * @see TextLabel          Static or dynamic text element
 * @see RenderContext      Per-frame render state
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

    // ── Hover Convenience ──────────────────────────────────────────────

    /**
     * Returns whether the mouse is currently over this element, using the
     * element's own bounds. Returns {@code false} in contexts without input
     * dispatch (HUDs).
     *
     * <p>Convenience wrapper around {@link RenderContext#isHovered}; equivalent
     * to {@code ctx.isHovered(getChildX(), getChildY(), getWidth(), getHeight())}.
     */
    default boolean isHovered(RenderContext ctx) {
        return ctx.isHovered(getChildX(), getChildY(), getWidth(), getHeight());
    }

    // ── Rendering ──────────────────────────────────────────────────────

    /**
     * Renders this element. Called during the panel background pass
     * (screen space), after slot backgrounds.
     *
     * <p>Position the element using the context's content origin plus this
     * element's {@code childX}/{@code childY}:
     * <pre>{@code
     * int sx = ctx.originX() + getChildX();
     * int sy = ctx.originY() + getChildY();
     * }</pre>
     *
     * @param ctx per-frame render context
     */
    void render(RenderContext ctx);

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * Called when the mouse is clicked on this element. The dispatcher
     * hit-tests against the element's bounds before calling this method, so
     * implementations know the click is within bounds.
     *
     * <p>Returns true if this element consumed the click (preventing further
     * dispatch to slots or vanilla). The default returns {@code false}, so
     * render-only elements (TextLabel, Icon, ItemDisplay) don't need to
     * override it.
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
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
