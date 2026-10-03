package com.trevlar.menukit.api.element;

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
 * <h3>The look (6.1.0)</h3>
 *
 * {@code style} is the look the element inherits: its screen's or panel's
 * ({@code elementStyle}), or a container's own {@code style(...)}. It flows down
 * the same way, kept by {@link #at} and {@link #disabledIf}, so an element built
 * from other elements hands it on without knowing it is there. An element reads
 * {@code ControlStyle.resolve(itsOwn, ctx.style())}.
 *
 * @param graphics  the graphics handle
 * @param originX   screen-space X of the element's containing content origin
 *                  (the panel's content area after padding). The element adds
 *                  its own {@code childX} to position itself.
 * @param originY   screen-space Y of the element's containing content origin
 * @param mouseX    screen-space mouse X, or {@code -1} if no input dispatch
 * @param mouseY    screen-space mouse Y, or {@code -1} if no input dispatch
 * @param disabled  whether the element's panel or container is disabled
 * @param style     the look the element inherits ({@link ControlStyle#MK} unless its
 *                  screen, panel or a container around it says otherwise)
 */
public record RenderContext(
        GuiGraphicsExtractor graphics,
        int originX,
        int originY,
        int mouseX,
        int mouseY,
        boolean disabled,
        ControlStyle style
) {

    public RenderContext {
        Objects.requireNonNull(style, "style");
    }

    /** A context in MenuKit's look: the 6.0.0 shape. */
    public RenderContext(GuiGraphicsExtractor graphics, int originX, int originY, int mouseX, int mouseY,
                         boolean disabled) {
        this(graphics, originX, originY, mouseX, mouseY, disabled, ControlStyle.MK);
    }

    /** A context with nothing around it disabled, in MenuKit's look: the common case, and the 5.x shape. */
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
        return new RenderContext(graphics, originX, originY, mouseX, mouseY, disabled, style);
    }

    /**
     * The same frame in another look: what a container with its own
     * {@code style(...)} hands its children, so they inherit it.
     */
    public RenderContext withStyle(ControlStyle style) {
        return style == this.style ? this
                : new RenderContext(graphics, originX, originY, mouseX, mouseY, disabled, style);
    }

    /**
     * This context, disabled as well when {@code alsoDisabled} holds: what a
     * container with its own {@code disabledWhen} passes to its children. Never
     * re-enables.
     */
    public RenderContext disabledIf(boolean alsoDisabled) {
        return alsoDisabled && !disabled
                ? new RenderContext(graphics, originX, originY, mouseX, mouseY, true, style)
                : this;
    }

    /**
     * The input context for the same origin, mouse and flag: the bridge for a
     * render-time decision that needs an input question answered (whether an open
     * popover sits under the mouse, say).
     */
    public InputContext input() {
        return new InputContext(originX, originY, mouseX, mouseY, disabled);
    }
}
