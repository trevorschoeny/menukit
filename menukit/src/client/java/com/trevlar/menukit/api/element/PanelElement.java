package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Panel;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

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
 * <p>Elements are per-panel-instance: each panel holds its own elements.
 * Elements aren't shared between panels.
 *
 * <p>MenuKit's own elements are built with builders and extend
 * {@link AbstractPanelElement} (§0066). Consumer mods can implement this
 * interface directly for custom element types.
 *
 * <h3>Coordinate contract</h3>
 *
 * Two coordinate spaces are in play across the element surface:
 *
 * <ul>
 *   <li><b>Panel-local</b>: {@link #getChildX} / {@link #getChildY} specify
 *       the element's position within the panel's content area (after
 *       padding).</li>
 *   <li><b>Screen-space</b>: absolute coordinates in the game window's
 *       GUI-scaled pixel grid. {@link RenderContext} and {@link InputContext}
 *       carry the content origin and the mouse in screen space.</li>
 * </ul>
 *
 * Render and input compose the two the same way: the element's top-left is
 * {@code (ctx.originX() + getChildX(), ctx.originY() + getChildY())}.
 *
 * <h3>Input receives its context (§0066)</h3>
 *
 * Every input method takes an {@link InputContext}, as {@link #render} takes a
 * {@link RenderContext}: the content origin, the mouse, and whether the
 * element's panel or container is disabled. An element never caches its origin
 * or its hover state from the last frame. The one dispatcher (the panel's host,
 * or a container through {@link ChildDispatch}) hit-tests before it calls
 * {@link #mouseClicked} and {@link #mouseScrolled}, and does not deliver clicks,
 * wheel or keys at all in a disabled context.
 *
 * @see Panel              The container that holds elements
 * @see RenderContext      Per-frame render state
 * @see InputContext       Per-event input state
 * @see ChildDispatch      How a container dispatches to its children
 */
public interface PanelElement {

    // ── Position & Bounds ──────────────────────────────────────────────
    // Coordinates are relative to the panel's content area (after padding).

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
     * {@code CrossAlign.FILL} column-fill primitive). After this call the
     * element's {@link #getWidth()} reports the filled width so panel auto-size,
     * hit-testing, and rendering all agree.
     *
     * <p>Default: no-op. {@link AbstractPanelElement} re-authors its width
     * here; intrinsic widgets (an icon, a checkbox square, a sprite switch) keep
     * their size.
     *
     * @param width the column's widest-child extent to stretch to, in pixels
     */
    default void fillWidth(int width) {}

    // ── Reactive sizing: width flows DOWN from the panel ───────────────

    /**
     * The element's NATURAL (authored / intrinsic) width before any panel
     * width-constraint: what it wants when the panel has room. The owning
     * {@link Panel} maxes this across its elements to compute its hug-width,
     * then feeds each element a budget via {@link #layoutWithin}. Distinct from
     * {@link #getWidth()}, which reports the resolved width. Default returns
     * {@link #getWidth()}.
     */
    default int naturalWidth() { return getWidth(); }

    /**
     * Width flows DOWN: the owning {@link Panel} (or container) calls this every
     * layout pass with the horizontal pixel budget available to this element.
     * The element resolves its presentation width, and when it wraps a label its
     * height, REVERSIBLY from its authored intent: a later call with a larger
     * budget restores the natural extent.
     *
     * <ul>
     *   <li>Text-bearing elements wrap their text to the budget, growing taller
     *       (reported through {@link #extraLayoutHeight}).</li>
     *   <li>Fixed controls (Slider, TextField, ProgressBar, a Dropdown's
     *       trigger) cap their width to the budget.</li>
     *   <li>A filling {@link Divider} takes the whole budget.</li>
     *   <li>Intrinsic widgets ignore it.</li>
     * </ul>
     *
     * <p>Default: no-op.
     *
     * @param budget horizontal pixels available to this element, in panel
     *               content space. Always {@code >= 1}.
     */
    default void layoutWithin(int budget) {}

    /**
     * Extra vertical pixels this element occupies beyond its single-line /
     * authored baseline because it wrapped or opened. The owning {@link Panel}
     * pushes the elements below it down by exactly this amount. Default
     * {@code 0}.
     */
    default int extraLayoutHeight() { return 0; }

    // ── Reactive sizing: height flows DOWN to filling elements ─────────

    /** Whether this element takes its height from the panel ({@link #fillHeight}). Default {@code false}. */
    default boolean fillsHeight() { return false; }

    /**
     * The height this element should occupy: the owning panel's viewport from
     * this element's top edge down, or {@code -1} when the panel has no viewport.
     * Called only when {@link #fillsHeight()} is {@code true}. Default: no-op.
     */
    default void fillHeight(int height) {}

    // ── Visibility ─────────────────────────────────────────────────────

    /**
     * Returns whether this element is currently visible. Invisible elements are
     * not rendered and receive no input. Default: always visible. MenuKit's
     * elements read their builder's {@code visibleWhen} here.
     */
    default boolean isVisible() { return true; }

    // ── Hover Convenience ──────────────────────────────────────────────

    /**
     * Returns whether the mouse is over this element in this render pass, using
     * the element's own bounds. {@code false} in contexts without input (HUDs).
     */
    default boolean isHovered(RenderContext ctx) {
        return ctx.isHovered(getChildX(), getChildY(), getWidth(), getHeight());
    }

    // ── Tooltip (universal hover-float contract) ───────────────────────

    /**
     * The element's hover-tooltip text supplier, or {@code null} if none.
     * {@link AbstractPanelElement} returns its builder's {@code tooltip}; a
     * direct implementor overrides this to return its own.
     */
    default @Nullable Supplier<Component> tooltipSupplier() {
        return null;
    }

    /**
     * Queues this element's hover tooltip for the current frame when the cursor
     * is over it. Routes through {@link MKTooltip}, so every element's tooltip
     * has the library's width cap and wrap. No-op without a tooltip, without
     * mouse input, or when not hovered.
     */
    default void queueTooltip(RenderContext ctx) {
        if (!ctx.hasMouseInput()) return;
        Supplier<Component> supplier = tooltipSupplier();
        if (supplier == null) return;
        if (!isHovered(ctx)) return;
        Component text = supplier.get();
        if (text == null) return;
        MKTooltip.queue(ctx.graphics(), text, ctx.mouseX(), ctx.mouseY());
    }

    // ── Rendering ──────────────────────────────────────────────────────

    /**
     * Renders this element at {@code (ctx.originX() + getChildX(),
     * ctx.originY() + getChildY())}. When {@code ctx.disabled()} is true, an
     * element draws its disabled look.
     */
    void render(RenderContext ctx);

    /**
     * Second-pass render for transient overlays that draw outside the element's
     * layout bounds (a Dropdown's popover). Runs after every element's
     * {@link #render}, so an overlay is always on top regardless of declaration
     * order. Pairs with {@link #getActiveOverlayBounds}, the input side. Default:
     * no-op.
     */
    default void renderOverlay(RenderContext ctx) {}

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * The screen-space bounds {@code [x, y, width, height]} of this element's
     * active overlay (an open popover), or {@code null} when none is open. The
     * dispatcher gives an element whose overlay contains the event exclusive
     * ownership of that click or scroll, before any hit test, and the claim rule
     * treats the overlay as claimed whatever the panel's opacity.
     *
     * @param in the element's input context (its origin, so it can place the overlay)
     */
    default int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return null;
    }

    /**
     * Tells an element with transient open state (a popover) that a click landed
     * somewhere it did not claim, so it can close. The dispatchers call it on
     * every shown element for every click, even one another element consumed;
     * each element decides for itself whether the click was outside. Default:
     * no-op.
     */
    default void notifyClickOutsideOverlay(InputContext in) {}

    /**
     * Whether this element wants the input event at the context's mouse
     * position. Default: the element's layout bounds. Override when the
     * interaction surface differs from the layout bounds (a container that only
     * claims where it has children).
     */
    default boolean hitTest(InputContext in) {
        return in.isOver(this);
    }

    /**
     * A mouse click on this element (the dispatcher hit-tested first). Returns
     * true if consumed, stopping dispatch to other elements, slots and vanilla.
     * {@code button} is GLFW's (0 left, 1 right, 2 middle). Default: false.
     */
    default boolean mouseClicked(InputContext in, int button) {
        return false;
    }

    /**
     * A wheel scroll over this element (hit-tested first). Returns true if
     * consumed. {@code scrollY} positive is up. Default: false.
     */
    default boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        return false;
    }

    /**
     * A mouse release anywhere on the screen, not hit-tested, so a drag that
     * started on the element ends wherever the cursor now is. Arrives even in a
     * disabled context. Default: false.
     */
    default boolean mouseReleased(InputContext in, int button) {
        return false;
    }

    /**
     * A key press, offered to every shown element of an active panel until one
     * consumes it (keys are not pointer-localised). GLFW codes, as vanilla's
     * {@code GuiEventListener.keyPressed}. Default: false.
     */
    default boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Whether this element is solid, which matters on a <em>transparent</em>
     * panel: there a panel claims only its solid elements (shown, opaque and
     * {@linkplain #isInteractive interactive}). On an opaque panel the panel
     * claims its whole rectangle (§0065). Builders set it with {@code opaque}.
     */
    default boolean isElementOpaque() { return true; }

    /**
     * Whether this element handles pointer input, so a point on it is a solid
     * claim on a transparent panel. Controls return {@code true}; render-only
     * decorations keep the {@code false} default, so they never eat a click they
     * do nothing with.
     */
    default boolean isInteractive() { return false; }

    /**
     * Whether this element presents a live menu slot under the event's point.
     * An opaque panel claims its whole rectangle; a slot the panel itself
     * presents stays live under that claim (§0065). Default {@code false}.
     */
    default boolean presentsSlotAt(InputContext in) { return false; }

    /**
     * The element's explicit, stable declaration id within its panel, or
     * {@code null} to use its registration position in the panel's element list
     * (the window's address for the element). Builders set it with
     * {@code declId}.
     */
    default @Nullable String declId() { return null; }

    /**
     * Screen-attach lifecycle hook, called when the containing screen reaches
     * its {@code init()}. Elements that wrap vanilla widgets register them here
     * so vanilla's focus, keyboard and narration reach them. Default: no-op.
     */
    default void onAttach(net.minecraft.client.gui.screens.Screen screen) {}

    /** Screen-detach lifecycle hook, the mirror of {@link #onAttach}. Default: no-op. */
    default void onDetach(net.minecraft.client.gui.screens.Screen screen) {}
}
