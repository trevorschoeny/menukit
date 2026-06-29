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
 * dispatcher in {@code MKCHandledScreen}, the standalone-screen
 * dispatcher in {@code MKScreen}, and the Phase 10 injection adapter
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

    /**
     * Stretches this element to fill the given content width (the
     * {@code CrossAlign.FILL} / column-fill primitive, Pass 3). After this
     * call the element's {@link #getWidth()} reports the filled width so
     * panel auto-size, hit-testing, and rendering all agree — fill is NOT
     * cosmetic.
     *
     * <p>Default: no-op. Fixed-extent widgets (Button, labeled Toggle, Slider,
     * Dropdown, DropdownMulti, TextField, ProgressBar, ScrollContainer,
     * horizontal Divider) override this to set their width. Auto-content-sized
     * widgets (TextLabel, Checkbox, Radio, ItemDisplay, Icon, and a bare/sprite
     * Toggle switch) keep the default no-op — they remain intrinsic-width under
     * FILL, by design: a stretched icon/switch would distort, and text reflow is
     * owned by the panel-level screen-edge wrap, not by column fill.
     *
     * <p>This is the deliberate capability-method answer to §0047's declined
     * {@code .size()} hoist on {@link AbstractPanelElement}: it is not a
     * hoisted field (per-widget storage differs), it is an optional stretch
     * capability that each widget honors as fits its own storage.
     *
     * @param width the column's widest-child extent to stretch to, in pixels
     */
    default void fillWidth(int width) {}

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

    /**
     * Phase 18s follow-up — second-pass render hook for ELEMENTS that
     * draw transient overlays extending outside their layout bounds
     * (Dropdown popovers, future tooltip-like floats, expand-on-click
     * editors). Called by the dispatcher AFTER every element's
     * {@link #render(RenderContext)} has run, so overlays always paint
     * on top regardless of element declaration order.
     *
     * <p>Default: no-op. Most elements render entirely inside their
     * layout bounds and don't need this. Override when an element has
     * a draws-outside-bounds region that must win z-order against
     * sibling elements (e.g., an open Dropdown's popover that should
     * obscure a later-declared button below it).
     *
     * <p><b>Sibling to {@link #getActiveOverlayBounds}</b> — that
     * method is the INPUT-side primitive for exclusive overlay claim;
     * this is the RENDER-side primitive for the same conceptual
     * overlay. An element with an active overlay typically implements
     * both: getActiveOverlayBounds returns the overlay's bounds (input
     * claim), renderOverlay paints it. Together they make overlays
     * inert-on-top regardless of consumer element ordering.
     *
     * @param ctx per-frame render context, same instance passed to
     *            {@link #render(RenderContext)} on the base pass
     */
    default void renderOverlay(RenderContext ctx) {}

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * Phase 14d-5 — active overlay bounds for exclusive-claim dispatch.
     * Called by the screen dispatchers BEFORE {@link #hitTest} as the
     * first dispatch pass: any element returning a non-null rect from
     * this method gets <b>exclusive</b> ownership of clicks (and scrolls)
     * that fall inside the returned region. The dispatcher routes the
     * input solely to this element regardless of {@link #mouseClicked}'s
     * return value, then short-circuits — no other element (including
     * vanilla widgets behind) sees the click.
     *
     * <p>This is the element-level parallel to M9's panel-level modal
     * click-eat ({@code Panel.tracksAsModal}). The use case is the same:
     * an element that draws a transient interactive overlay outside its
     * layout bounds (e.g., {@link com.trevorschoeny.menukit.core.Dropdown}'s
     * popover when open) needs the overlay region to be inherently inert
     * to anything behind it. Without this primitive, a click on a
     * popover item would also fire whichever element's layout bounds
     * happened to overlap the popover's screen region (e.g., a button
     * paint-occluded by the popover but still hit-testable).
     *
     * <p>Default returns {@code null} — no active overlay. Existing
     * elements (Button, TextField, Slider, ScrollContainer, etc.) inherit
     * unchanged. Override only when you need a transient region that's
     * exclusively yours.
     *
     * <p><b>Coordinate space:</b> screen-space (same as the dispatchers
     * read mouse positions). The returned rect is consumed as
     * {@code [x, y, width, height]}.
     *
     * <p><b>When to use overlay vs hitTest:</b>
     * <ul>
     *   <li><b>Overlay</b> — the element claims an exclusive region; the
     *       region "eats" all clicks/scrolls inside it (modal-like). Use
     *       when nothing behind the region should see the input
     *       (popovers, transient action menus).</li>
     *   <li><b>hitTest</b> — the element wants to be considered for
     *       dispatch when cursor is at a non-default position; if it
     *       doesn't consume, dispatch falls through to other elements.
     *       Use when the element extends its interactive surface but
     *       isn't claiming exclusivity (extended trigger zones).</li>
     * </ul>
     *
     * @return {@code [x, y, width, height]} of the active overlay region,
     *         or {@code null} if no overlay is active
     */
    default int @org.jspecify.annotations.Nullable [] getActiveOverlayBounds() {
        return null;
    }

    /**
     * Phase 14d-5 — interaction-bounds hit test. Called by the screen
     * dispatchers (MKScreen, MKCHandledScreen, ScreenPanelAdapter)
     * before {@link #mouseClicked} and {@link #mouseScrolled} to decide
     * whether this element should receive the input event.
     *
     * <p>Default implementation tests against the element's layout bounds —
     * exactly equivalent to the inline screen-space bounds-check the
     * dispatchers performed pre-14d-5. Existing elements (Button, TextField,
     * Slider, ScrollContainer, etc.) inherit unchanged.
     *
     * <p><b>Override for elements whose interaction surface differs from
     * their layout bounds.</b> Phase 14d-5's {@code Dropdown} is the
     * canonical case: its popover renders OUTSIDE the trigger's layout
     * bounds when open, and clicks on the popover area need to reach the
     * Dropdown so it can route them internally (select-or-dismiss). Other
     * future cases: tooltips with embedded action buttons, expandable inline
     * editors, hover-with-click-targets.
     *
     * <p>The split between layout bounds and interaction bounds is
     * deliberate: panel auto-size logic uses {@code getWidth} / {@code getHeight}
     * for layout, while the dispatcher uses {@code hitTest} for input
     * routing. An element can claim a larger interaction surface (popover)
     * without disturbing layout (which would cause panel reflow).
     *
     * <p>Coordinate space: same as {@link #mouseClicked} — screen-space
     * mouse coordinates. {@code contentX} / {@code contentY} are the panel
     * content origin (panel position + padding); the element's screen-space
     * top-left is {@code contentX + getChildX(), contentY + getChildY()}.
     * The dispatcher passes its own pre-computed contentX/contentY so the
     * element doesn't need to re-derive layout state.
     *
     * @param mouseX   screen-space mouse X
     * @param mouseY   screen-space mouse Y
     * @param contentX panel content origin X (panel.x + padding)
     * @param contentY panel content origin Y (panel.y + padding)
     * @return true if this element wants to receive the input event
     */
    default boolean hitTest(double mouseX, double mouseY, int contentX, int contentY) {
        int sx = contentX + getChildX();
        int sy = contentY + getChildY();
        return mouseX >= sx && mouseX < sx + getWidth()
            && mouseY >= sy && mouseY < sy + getHeight();
    }

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

    /**
     * Called when the user scrolls the mouse wheel with the cursor over this
     * element's bounds. The container hit-tests before calling — implementations
     * know the cursor is within bounds.
     *
     * <p>Returns true if this element consumed the scroll (preventing further
     * dispatch). Default returns {@code false}, so non-scrollable elements
     * (Button, TextLabel, etc.) don't need to override it.
     *
     * <p>{@code scrollX} / {@code scrollY} are the wheel deltas — typically
     * scrollY is non-zero (vertical wheel) and scrollX is zero (horizontal
     * wheel, less common).
     *
     * <p>Added in Phase 14d-2 alongside {@code ScrollContainer}, the first
     * element that consumes scroll input. Existing elements default false
     * and continue to work unchanged.
     *
     * @param mouseX  screen-space mouse X
     * @param mouseY  screen-space mouse Y
     * @param scrollX horizontal wheel delta (typically 0)
     * @param scrollY vertical wheel delta (positive = up, negative = down)
     * @return true if consumed, false to let the scroll fall through
     */
    default boolean mouseScrolled(double mouseX, double mouseY,
                                  double scrollX, double scrollY) {
        return false;
    }

    /**
     * Called when the user releases a mouse button anywhere on the screen
     * (not hit-tested against this element's bounds — the release fires
     * regardless of cursor position, so drag-end detection works even when
     * the user drags off the element). Default returns {@code false} for
     * elements that don't track press/release state.
     *
     * <p>Added in Phase 14d-2 alongside ScrollContainer to support
     * scrollbar drag — drag is initiated in {@link #mouseClicked}, and
     * ends when {@code mouseReleased} fires (typically off-element since
     * the cursor moved during drag). Existing elements default false and
     * continue to work unchanged.
     *
     * <p>Coordinate space: screen-space, same as {@link #mouseClicked}.
     *
     * @param mouseX  screen-space mouse X at release
     * @param mouseY  screen-space mouse Y at release
     * @param button  mouse button (0=left, 1=right, 2=middle)
     * @return true if consumed (rare for release events)
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Keyboard input dispatch — completes the element input model alongside
     * {@link #mouseClicked} / {@link #mouseScrolled}. Called by the screen
     * dispatchers on a key press while the element's panel is active. Unlike
     * the mouse methods this is NOT hit-tested: keyboard events aren't
     * localized to a cursor position, so every visible element on an active
     * panel is offered the key until one consumes it.
     *
     * <p>Returns true if this element consumed the keystroke (stopping further
     * dispatch and suppressing vanilla's own handling of it). Default returns
     * {@code false}, so non-keyboard elements (Button, TextLabel, …) are
     * unaffected.
     *
     * <p>{@code keyCode} / {@code scanCode} / {@code modifiers} are GLFW codes,
     * matching vanilla's {@code GuiEventListener.keyPressed}. The canonical
     * consumer is {@link Dropdown} — arrow keys move the highlighted option,
     * Enter selects it, Escape dismisses the popover.
     *
     * @param keyCode   GLFW key code
     * @param scanCode  platform-specific scan code
     * @param modifiers GLFW modifier bitfield (shift/ctrl/alt)
     * @return true if consumed, false to let the key fall through
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Returns whether this element is interaction-opaque (M9 per-element
     * opacity). When {@code false}, clicks/hover/tooltip over this element's
     * bounds pass through an opaque panel to the slots/screen behind it (a
     * click-through "hole"); when {@code true} (default) the element inherits
     * the panel's opaque click-eating behavior.
     *
     * <p>Input-layer only — mirrors M9's panel opacity (bounding-box, not
     * visual alpha). The panel's click-consumption ({@code ScreenPanelRegistry})
     * consults this before eating a click that lands on this element's bounds.
     */
    default boolean isElementOpaque() { return true; }

    /**
     * Returns whether this element actually handles pointer input — i.e.
     * whether a click landing on it should be treated as a solid claim that
     * blocks the vanilla content behind it. Interactive elements (Button,
     * TextField, Slider, Dropdown, Toggle, Checkbox, Radio, ScrollContainer)
     * return {@code true}; render-only decorations (TextLabel, Icon, Divider,
     * ItemDisplay, ProgressBar, InfoBox) inherit the {@code false} default.
     *
     * <p><b>Why this exists (the dead-click guard).</b> On a NON-opaque panel,
     * the inertness contract only lets a point be claimed where a <em>solid</em>
     * element sits ({@code panelClaimsPoint} branch (b)). Without this flag an
     * opaque-but-render-only decoration would claim — and thus EAT — a click it
     * does nothing with, contradicting the {@code opaque(false)} "clicks pass
     * through" promise. Gating branch (b) on {@code isElementOpaque() &&
     * isInteractive()} means only elements that would actually consume the input
     * block it; decorations stay transparent to clicks. On an OPAQUE panel the
     * background already blocks the whole bounds, so this flag is moot there.
     *
     * <p>Orthogonal to {@link #isElementOpaque}: opacity is "does my visual
     * footprint occlude" (a solid Icon is opaque), interactivity is "do I
     * consume input" (the same Icon is not interactive). Both must hold for an
     * element to claim a point on a non-opaque panel.
     *
     * <p>Default {@code false} — the safe direction. A custom consumer element
     * that wants to block clicks on a transparent panel declares itself
     * interactive; the default never produces a dead-click.
     */
    default boolean isInteractive() { return false; }

    /**
     * The element's explicit, stable declaration id within its panel, or
     * {@code null} to use the default identity (its registration position in
     * the panel's element list).
     *
     * <p><b>The panel-element identity contract (THE ONE WINDOW, Address
     * keystone).</b> Every addressable thing needs a deterministic,
     * reopen-stable identity so the window can resolve it by address across
     * a screen reopen (Minecraft rebuilds the menu and all elements each
     * open). For a panel element that identity is, by default, its
     * <em>registration position</em> in the panel's immutable, consumer-built
     * element list — the same deterministic registration-order basis a created
     * slot uses for its {@code localIndex}. Position-as-identity is stable as
     * long as the consumer builds the element list in the same order each open
     * (the normal case).
     *
     * <p>This accessor is the explicit <em>override</em> for when list order is
     * not a reliable identity (e.g. a consumer who conditionally includes some
     * elements and wants a fixed handle regardless of order): supply a stable
     * string and the window keys on it instead of position. Mirrors the
     * consumer-supplied-string precedent a created slot has via its panel id.
     *
     * <p>Default {@code null} (custom consumer elements implementing this
     * interface directly inherit position-based identity). Library elements
     * extending {@code AbstractPanelElement} can set an explicit id via
     * {@code declId(String)}.
     */
    default @org.jspecify.annotations.Nullable String getElementDeclId() { return null; }

    /**
     * Phase 14d-3 — screen-attach lifecycle hook. Called when the
     * containing screen reaches its {@code init()} boundary (or when a
     * lambda-path adapter registers via {@code .activeOn}). Default
     * no-op for elements that don't need lifecycle hooks.
     *
     * <p>Use case: elements that wrap vanilla widgets (e.g., {@code TextField}
     * wraps {@link net.minecraft.client.gui.components.EditBox}) need to
     * register the wrapped widget via {@code screen.addRenderableWidget(...)}
     * so vanilla's screen widget pipeline routes charTyped/keyPressed to
     * it when focused. Without onAttach, the wrapped widget never enters
     * vanilla's input dispatch and IME / focus / tab navigation don't work.
     *
     * <p>v1 fires once per screen lifetime (at init); does NOT fire on
     * panel visibility changes mid-screen-life. Visibility-driven
     * attach/detach is deferred — see {@code DEFERRED.md} 14d-3 follow-ons.
     *
     * <p>Coordinate space note: at onAttach time the screen has been
     * laid out (super.init() ran before MenuKit's lifecycle hooks fire),
     * so panel bounds + leftPos/topPos are available. Elements that
     * register vanilla widgets typically need to update widget coords
     * per-frame in {@link #render} regardless of attach-time positions.
     *
     * @param screen the vanilla Screen the panel is attached to
     */
    default void onAttach(net.minecraft.client.gui.screens.Screen screen) {}

    /**
     * Phase 14d-3 — screen-detach lifecycle hook. Called when the
     * containing screen reaches its {@code removed()} boundary (or when
     * a lambda-path adapter calls {@code .deactivate}). Default no-op.
     *
     * <p>Mirrors {@link #onAttach}: elements that registered vanilla
     * widgets via {@code screen.addRenderableWidget} should remove them
     * via {@code screen.removeWidget(...)} so vanilla's pipeline cleans
     * up references.
     *
     * @param screen the vanilla Screen the panel was attached to
     */
    default void onDetach(net.minecraft.client.gui.screens.Screen screen) {}
}
