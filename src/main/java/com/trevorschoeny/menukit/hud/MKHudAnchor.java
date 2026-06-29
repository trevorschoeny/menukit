package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MK;

/**
 * Screen-edge anchor positions for HUD panels.
 *
 * <p><b>Legacy positioning enum.</b>
 * {@link com.trevorschoeny.menukit.core.HudRegion} is the intended primary HUD
 * positioning system — it carries auto-stacking, {@code priority(...)},
 * fail-loud overflow, and is shared by HUD panels + notifications +
 * vanilla-screen panels through {@code RegionMath.resolveHud}. {@code MKHudAnchor}
 * predates HudRegion and is retained as the raw-anchor fallback for
 * {@code MKHudPanel}/{@code MKHudNotification}. Prefer {@code HudRegion} for new
 * code; reach for {@code MKHudAnchor} only for the vertical-center positions
 * ({@link #CENTER_LEFT}/{@link #CENTER_RIGHT}) or a one-off raw offset.
 *
 * <p>Each position anchors the panel to a screen edge or corner.
 * Offsets move the panel inward from the anchor point:
 * positive X = right, positive Y = down. For right/bottom anchors,
 * use negative offsets to move inward (e.g., {@code BOTTOM_RIGHT, -4, -24}).
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public enum MKHudAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    /**
     * Resolves this anchor + offset to absolute screen coordinates.
     *
     * @param screenW  GUI-scaled screen width
     * @param screenH  GUI-scaled screen height
     * @param panelW   the panel's computed width
     * @param panelH   the panel's computed height
     * @param offsetX  X offset from anchor (positive = right)
     * @param offsetY  Y offset from anchor (positive = down)
     * @return int[]{x, y} absolute screen position
     */
    public int[] resolve(int screenW, int screenH,
                         int panelW, int panelH,
                         int offsetX, int offsetY) {
        int x = switch (this) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (screenW - panelW) / 2 + offsetX;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenW - panelW + offsetX;
        };
        int y = switch (this) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> offsetY;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (screenH - panelH) / 2 + offsetY;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenH - panelH + offsetY;
        };
        return new int[]{x, y};
    }
}
