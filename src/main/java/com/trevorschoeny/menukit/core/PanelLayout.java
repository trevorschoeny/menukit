package com.trevorschoeny.menukit.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure layout utility. Resolves {@link PanelPosition} constraints across a
 * list of panels and produces per-panel {@link PanelBounds}.
 *
 * <p>Context-neutral. The caller supplies per-panel sizes (computed in a
 * context-specific way — inventory-menu handlers compute sizes from slot
 * groups; standalone screens compute sizes from element bounds) and the
 * utility applies the constraint model.
 *
 * <p>Constraint model:
 * <ul>
 *   <li><b>Body panels</b> stack vertically in a single column starting at
 *   {@code titleHeight}, separated by {@code bodyGap}.</li>
 *   <li><b>Relative panels</b> (rightOf / leftOf / above / below another panel)
 *   are positioned adjacent to their anchor panel with a separation of
 *   {@code relativeGap}.</li>
 * </ul>
 *
 * <p>Hidden panels are skipped (their bounds are not computed). Relative
 * panels whose anchor is hidden are also skipped.
 */
public final class PanelLayout {

    private PanelLayout() {} // utility — no instances

    /**
     * Resolves layout bounds for a list of panels given their computed sizes.
     *
     * @param panels       the panels in declaration order
     * @param sizes        map from panel id to {@code int[]{width, height}} —
     *                     must contain an entry for every visible panel
     * @param bodyGap      vertical gap between stacked body panels, in pixels
     * @param relativeGap  gap between a relative panel and its anchor, in pixels
     * @param titleHeight  Y offset reserved above the first body panel
     * @return ordered map from panel id to resolved bounds
     */
    public static Map<String, PanelBounds> resolve(
            List<Panel> panels,
            Map<String, int[]> sizes,
            int bodyGap,
            int relativeGap,
            int titleHeight) {

        Map<String, PanelBounds> bounds = new LinkedHashMap<>();

        // Phase 1: Position body panels — vertical stack starting at titleHeight
        int bodyY = titleHeight;
        for (Panel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.getPosition().mode() != PanelPosition.Mode.BODY) continue;

            int[] size = sizes.get(panel.getId());
            if (size == null) continue;

            bounds.put(panel.getId(), new PanelBounds(0, bodyY, size[0], size[1]));
            bodyY += size[1] + bodyGap;
        }

        // Phase 2: Position relative panels — offset from their anchor
        for (Panel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.getPosition().mode() == PanelPosition.Mode.BODY) continue;

            String anchorId = panel.getPosition().anchorPanelId();
            PanelBounds anchor = bounds.get(anchorId);
            if (anchor == null) continue; // anchor not visible — skip

            int[] size = sizes.get(panel.getId());
            if (size == null) continue;

            PanelBounds b = switch (panel.getPosition().mode()) {
                case RIGHT_OF -> new PanelBounds(
                        anchor.x() + anchor.width() + relativeGap,
                        anchor.y(), size[0], size[1]);
                case LEFT_OF -> new PanelBounds(
                        anchor.x() - size[0] - relativeGap,
                        anchor.y(), size[0], size[1]);
                case ABOVE -> new PanelBounds(
                        anchor.x(),
                        anchor.y() - size[1] - relativeGap,
                        size[0], size[1]);
                case BELOW -> new PanelBounds(
                        anchor.x(),
                        anchor.y() + anchor.height() + relativeGap,
                        size[0], size[1]);
                default -> null; // BODY handled above
            };
            if (b != null) {
                bounds.put(panel.getId(), b);
            }
        }

        return bounds;
    }
}
