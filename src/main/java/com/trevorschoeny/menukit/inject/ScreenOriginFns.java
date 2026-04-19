package com.trevorschoeny.menukit.inject;

/**
 * Common {@link ScreenOriginFn} constructors for the positioning cases the
 * Phase 10 audit surfaced. Consumers write their own lambda for cases these
 * don't cover.
 *
 * <p>Only four constructors ship. The bar for adding more: a concrete
 * consumer case the audit missed, not hypothetical future demand.
 */
public final class ScreenOriginFns {

    private ScreenOriginFns() {}

    /**
     * Panel origin at an offset from the vanilla screen's top-left corner.
     * Panel origin = ({@code leftPos + dx}, {@code topPos + dy}).
     *
     * @param dx offset from leftPos, positive = rightward
     * @param dy offset from topPos, positive = downward
     */
    public static ScreenOriginFn fromScreenTopLeft(int dx, int dy) {
        return (bounds, screen) -> new ScreenOrigin(bounds.leftPos() + dx, bounds.topPos() + dy);
    }

    /**
     * Panel origin at an offset from the vanilla screen's top-right corner.
     * The panel's width is needed so the panel is right-anchored at its
     * top-right corner — otherwise the panel would extend rightward off the
     * frame.
     *
     * <p>Panel origin = ({@code leftPos + imageWidth - panelWidth + dx}, {@code topPos + dy}).
     *
     * @param panelWidth the panel's width in pixels
     * @param dx         offset from the right edge, positive = rightward (off the frame)
     * @param dy         offset from topPos, positive = downward
     */
    public static ScreenOriginFn fromScreenTopRight(int panelWidth, int dx, int dy) {
        return (bounds, screen) -> new ScreenOrigin(
                bounds.leftPos() + bounds.imageWidth() - panelWidth + dx,
                bounds.topPos() + dy);
    }

    /**
     * Panel origin just above a slot grid inside the vanilla screen. The
     * panel is left-aligned with the grid at the given grid coordinates,
     * placed {@code gap} pixels above it.
     *
     * <p>Panel origin = ({@code leftPos + gridX}, {@code topPos + gridY - panelHeight - gap}).
     *
     * @param gridX        X of the slot grid within the vanilla screen's content
     * @param gridY        Y of the slot grid within the vanilla screen's content
     * @param panelHeight  the panel's height in pixels
     * @param gap          gap between panel's bottom edge and grid's top edge
     */
    public static ScreenOriginFn aboveSlotGrid(int gridX, int gridY, int panelHeight, int gap) {
        return (bounds, screen) -> new ScreenOrigin(
                bounds.leftPos() + gridX,
                bounds.topPos() + gridY - panelHeight - gap);
    }

    /**
     * Panel origin just below a slot grid inside the vanilla screen. The
     * panel is left-aligned with the grid at the given grid coordinates,
     * placed {@code gap} pixels below it.
     *
     * <p>Panel origin = ({@code leftPos + gridX}, {@code topPos + gridY + gridHeight + gap}).
     *
     * @param gridX       X of the slot grid within the vanilla screen's content
     * @param gridY       Y of the slot grid within the vanilla screen's content
     * @param gridHeight  height of the slot grid in pixels
     * @param gap         gap between grid's bottom edge and panel's top edge
     */
    public static ScreenOriginFn belowSlotGrid(int gridX, int gridY, int gridHeight, int gap) {
        return (bounds, screen) -> new ScreenOrigin(
                bounds.leftPos() + gridX,
                bounds.topPos() + gridY + gridHeight + gap);
    }
}
