package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Objects;

/**
 * Render-time context passed to every {@link PanelElement#render}. Bundles the
 * graphics handle, the element's containing content origin, the current mouse
 * position, and whether the element's surroundings are disabled.
 *
 * <p>Works across all rendering contexts:
 * <ul>
 *   <li><b>Inventory menus and standalone screens</b>: full context including
 *   live mouse coordinates for hover detection.</li>
 *   <li><b>HUDs</b>: {@code mouseX} and {@code mouseY} are {@code -1}, signalling
 *   that no input dispatch is happening. {@link #hasMouseInput()} returns
 *   {@code false}; {@link #isHovered} returns {@code false} uniformly.</li>
 * </ul>
 *
 * <p>The {@code -1} sentinel for "no input dispatch" is a deliberate convention,
 * not a bug: HUDs render without input routing per library doctrine. Consumers
 * should use {@link #hasMouseInput()} and {@link #isHovered} rather than
 * inspecting the raw coordinates.
 *
 * <h3>The disabled cascade (§0066)</h3>
 *
 * {@code disabled} is true when something around the element is disabled: its
 * panel ({@code Panel.disabledWhen}), or a container it sits in (a
 * {@link Section}, a {@link Tabs} body, a {@link ScrollContainer}, a {@link Flow})
 * whose own {@code disabledWhen} holds. An element draws its disabled look when
 * this or its own {@code disabledWhen} is true. The flag only ever turns on going
 * down: a container passes {@code ctx.disabledIf(itsOwn)} to its children, never
 * a context that re-enables them. The input twin is {@link InputContext}, which
 * carries the same flag, so what looks disabled is also inert.
 *
 * <h3>The kind of screen (6.1.0)</h3>
 *
 * {@code kind} is the kind of screen the element is on ({@link ScreenKind}): the
 * host that renders the panel sets it, from the screen it is on, and nothing
 * below changes it. It is kept by {@link #at} and {@link #disabledIf}, so an
 * element built from other elements hands it on without knowing it is there. An
 * element draws for {@code ctx.kind()}.
 *
 * @param graphics  the graphics handle
 * @param originX   screen-space X of the element's containing content origin
 *                  (the panel's content area after padding). The element adds
 *                  its own {@code childX} to position itself.
 * @param originY   screen-space Y of the element's containing content origin
 * @param mouseX    screen-space mouse X, or {@code -1} if no input dispatch
 * @param mouseY    screen-space mouse Y, or {@code -1} if no input dispatch
 * @param disabled  whether the element's panel or container is disabled
 * @param kind      the kind of screen the element is on ({@link ScreenKind#CONTAINER}
 *                  on the HUD and in a context built without one)
 */
public record RenderContext(
        GuiGraphicsExtractor graphics,
        int originX,
        int originY,
        int mouseX,
        int mouseY,
        boolean disabled,
        ScreenKind kind
) {

    public RenderContext {
        Objects.requireNonNull(kind, "kind");
    }

    /** A context on a container screen: the 6.0.0 shape. */
    public RenderContext(GuiGraphicsExtractor graphics, int originX, int originY, int mouseX, int mouseY,
                         boolean disabled) {
        this(graphics, originX, originY, mouseX, mouseY, disabled, ScreenKind.CONTAINER);
    }

    /** A context with nothing around it disabled, on a container screen: the common case, and the 5.x shape. */
    public RenderContext(GuiGraphicsExtractor graphics, int originX, int originY, int mouseX, int mouseY) {
        this(graphics, originX, originY, mouseX, mouseY, false);
    }

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

    /**
     * The same frame, with the content origin moved to {@code (originX, originY)}:
     * what a container hands the children it positions from its own top-left.
     */
    public RenderContext at(int originX, int originY) {
        return new RenderContext(graphics, originX, originY, mouseX, mouseY, disabled, kind);
    }

    /**
     * This context, disabled as well when {@code alsoDisabled} holds: what a
     * container with its own {@code disabledWhen} passes to its children. Never
     * re-enables.
     */
    public RenderContext disabledIf(boolean alsoDisabled) {
        return alsoDisabled && !disabled
                ? new RenderContext(graphics, originX, originY, mouseX, mouseY, true, kind)
                : this;
    }

    /**
     * The input context for the same origin, mouse and flag: the bridge for a
     * render-time decision that needs an input question answered (whether an open
     * popover sits under the mouse, say). It keeps the kind.
     */
    public InputContext input() {
        return new InputContext(originX, originY, mouseX, mouseY, disabled, kind);
    }
}
