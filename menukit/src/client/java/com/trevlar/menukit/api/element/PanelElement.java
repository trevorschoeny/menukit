package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A visual or interactive element within a {@link Panel}. Elements are
 * positioned absolutely within the panel's content area (after padding)
 * using {@code childX}/{@code childY} coordinates.
 *
 * <p>Panel elements are a decorative/interactive layer on top of the panel
 * backgrounds. In a slot library's menu panels they sit alongside slots (which
 * live on the menu, not on the panel itself); in HUD panels and
 * standalone-screen panels they are the only content.
 *
 * <p>Elements are per-panel-instance: each panel holds its own elements.
 * Elements aren't shared between panels.
 *
 * <p>MenuKit's own elements are built with builders and extend
 * {@link AbstractPanelElement}. Consumer mods can implement this
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
 * <h3>Input receives its context</h3>
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

    // Position and bounds, relative to the panel's content area (after padding).

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

    // Sizing: width flows down from the panel.

    /**
     * The element's natural (authored or intrinsic) width before any panel
     * width constraint: what it wants when the panel has room. The owning
     * {@link Panel} maxes this across its elements to compute its hug-width,
     * then feeds each element a budget via {@link #layoutWithin}. Distinct from
     * {@link #getWidth()}, which reports the resolved width. Default returns
     * {@link #getWidth()}.
     */
    default int naturalWidth() { return getWidth(); }

    /**
     * The owning {@link Panel} (or container) calls this every layout pass with
     * the horizontal pixel budget available to this element. The element resolves
     * its presentation width, and when it wraps a label its height, from its
     * authored intent, and reversibly: a later call with a larger budget restores
     * the natural extent.
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
     * pushes the elements below it down by exactly this amount. Less than 0 (an
     * element shorter than where it was placed, a {@link Tabs} fitting a shorter
     * tab) pulls them up, unless another element in its row holds them. Default
     * {@code 0}.
     */
    default int extraLayoutHeight() { return 0; }

    // Sizing: height flows down to filling elements.

    /**
     * Whether this element takes its height from its container ({@link #fillHeight}):
     * the room left below it rather than its natural height. A {@link Tabs} with no
     * fixed size, a {@code ScrollContainer.builder().fillHeight()}, and a vertical
     * {@link Flow} holding either. Default {@code false}.
     */
    default boolean fillsHeight() { return false; }

    /**
     * The height this element should occupy, from its container, or {@code -1} when
     * the container has no height to give (back to its natural height). Called only
     * when {@link #fillsHeight()} is {@code true}, every layout pass.
     *
     * <ul>
     *   <li>A <b>panel</b> gives each filling element its viewport from the element's
     *       top edge down (the screen's room for a standalone screen's main panel).
     *       Put what goes under a filling element in a vertical {@link Flow} with
     *       it.</li>
     *   <li>A <b>vertical {@link Flow}</b> gives a filling child what is left after
     *       its other children and gaps (shared when several fill).</li>
     *   <li>A <b>{@link Tabs}</b> page (7.3.0): a body with a filling element is not
     *       wrapped in the page's own scroll; the element gets the page's height from
     *       its top edge down and scrolls itself.</li>
     * </ul>
     * Default: no-op.
     */
    default void fillHeight(int height) {}

    /**
     * The height a wrapping element wraps at: the room from this element's top edge
     * down to where the frame beside its panel ends (a container screen's frame for a
     * panel in a side region; see {@code Panel.setFrameHeight}), or {@code -1} when
     * the panel has no frame beside it. A vertical {@link Flow} starts a new column
     * there. Unlike {@link #fillHeight} it is not the panel's scroll viewport: a panel
     * beside the frame still scrolls only at the screen's edge. Called on every
     * element each layout pass. Default: no-op.
     */
    default void wrapHeight(int height) {}

    /**
     * The kind of screen the owning panel is on, handed down whenever it changes,
     * before the next layout pass. For an element whose size depends on the kind (a
     * {@link Tabs} insets its body on an options screen's page), so it measures for
     * the screen it is on from the first frame. An element holding others passes it
     * on to them (a {@link ScrollContainer} in a Tabs body sizes its band's inset by
     * it). Default: no-op.
     */
    default void screenKind(ScreenKind kind) {}

    /**
     * Whether the owning panel is a dialog (a modal, {@code Panel.tracksAsModal})
     * rather than part of the screen, handed down when that is set (7.2.0). On an
     * options screen a list band (a {@link ScrollContainer}'s, a {@link Tabs} page)
     * insets its content by the full screen's {@link ScrollContainer#OPTIONS_INSET_X}
     * and {@link ScrollContainer#OPTIONS_INSET_Y}; inside a dialog the frame and its
     * padding are already the margin, so a band insets by the dialog's own gap,
     * {@link ScrollContainer#DIALOG_INSET}, instead. An element holding others passes
     * it on to them. Default: no-op.
     */
    default void inDialog(boolean inDialog) {}

    /**
     * Whether the element sits on a list band another element draws (7.3.0): a
     * {@link Tabs} page whose body fills it ({@link #fillHeight}). There a
     * {@link ScrollContainer} draws no band of its own (no second background, no
     * separators, no second inset), only its scrollbar, so the page looks the same
     * whichever side scrolls. Handed down before layout; an element holding others
     * passes it on to them. Default: no-op.
     */
    default void inBand(boolean inBand) {}

    /**
     * Returns whether this element is currently visible. Invisible elements are
     * not rendered and receive no input. Default: always visible. MenuKit's
     * elements read their builder's {@code visibleWhen} here.
     */
    default boolean isVisible() { return true; }

    /**
     * Returns whether the mouse is over this element in this render pass, using
     * the element's own bounds. {@code false} in contexts without input (HUDs).
     */
    default boolean isHovered(RenderContext ctx) {
        return ctx.isHovered(getChildX(), getChildY(), getWidth(), getHeight());
    }

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

    /**
     * A rectangle this element asks the scroll area around it to bring into view
     * (7.3.0): {@code {y, height}} in the panel's content coordinates (the space of
     * {@link #getChildY}), or {@code null} for none. Asking takes the request: the
     * element answers once per request. A {@link ScrollContainer} asks its children
     * every frame before drawing them and scrolls the least that shows the rectangle;
     * a {@link Flow} passes its children's requests on. A {@link ListView} asks when
     * its selection changes by code or by the arrow keys. Default: none.
     */
    default int @Nullable [] takeRevealRequest() { return null; }

    // Rendering

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

    // Input

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
     * {@code button} is vanilla's; compare it with {@link Click#LEFT},
     * {@link Click#RIGHT} and {@link Click#MIDDLE}. Default: false.
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
     * consumes it (keys are not pointer-localised). {@code keyCode} is
     * {@code KeyEvent.key()} (an {@code InputConstants.KEY_*} value) and
     * {@code scanCode} is {@code KeyEvent.keycode()}. Default: false.
     */
    default boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Whether this element is solid, which matters on a <em>transparent</em>
     * panel: there a panel claims only its solid elements (shown, opaque and
     * {@linkplain #isInteractive interactive}). On an opaque panel the panel
     * claims its whole rectangle. Builders set it with {@code opaque}.
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
     * presents stays live under that claim. Default {@code false}.
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
