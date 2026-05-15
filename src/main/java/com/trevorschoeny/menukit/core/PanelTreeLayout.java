package com.trevorschoeny.menukit.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Phase 16j R1 — shared layout primitive across the standalone-screen
 * (MK) and container-screen (MKC) rendering contexts. Bundles three
 * operations that both contexts previously implemented in parallel:
 *
 * <ol>
 *   <li>Per-panel size computation via a context-supplied
 *       {@code panelSizeFn} (MK derives size from elements + padding;
 *       MKC also factors in slot groups + pinned dims).</li>
 *   <li>Position resolution via {@link PanelLayout#resolve} (already
 *       context-neutral; this primitive just orchestrates the call).</li>
 *   <li>Layout-extent + origin derivation across visible panels, used
 *       by the consumer to drive screen-space centering ({@code leftPos}
 *       / {@code topPos} in MK; {@code recenter()} correction in MKC).</li>
 * </ol>
 *
 * <p>Hidden panels are excluded from extent calculation — toggling a
 * panel hidden mid-frame collapses the layout naturally. When every
 * panel is hidden, the result falls back to {@code minImageWidth} /
 * {@code minImageHeight} centered at the origin so the centering math
 * doesn't underflow.
 *
 * <h3>Why a record instead of out-parameters</h3>
 *
 * Java has no native multi-return; the alternatives were (a) passing
 * mutable arrays/objects to fill, or (b) returning a small record.
 * The record keeps the call site readable and the contract immutable.
 *
 * <h3>What stays per-context</h3>
 *
 * The centering math itself (translating layout-local origin into
 * screen-space leftPos/topPos) lives in each context — MK applies it
 * directly in {@code computeLayout}; MKC applies it post-{@code super.init}
 * in {@code recenter} to override vanilla's centering. Both formulas are
 * the same shape: {@code leftPos = (screenW - totalWidth)/2 - originX}.
 *
 * @param bounds        layout-local bounds per panel ID (from PanelLayout.resolve)
 * @param layoutOriginX leftmost x across visible panels (0 if none visible)
 * @param layoutOriginY topmost y across visible panels (0 if none visible)
 * @param totalWidth    horizontal extent across visible panels, clamped to {@code minImageWidth}
 * @param totalHeight   vertical extent across visible panels, clamped to {@code minImageHeight}
 */
public record PanelTreeLayout(
        Map<String, PanelBounds> bounds,
        int layoutOriginX, int layoutOriginY,
        int totalWidth, int totalHeight) {

    /**
     * Computes the layout for {@code panels} using {@code panelSizeFn} to
     * derive each panel's pixel size. Sizes are computed for ALL panels
     * (even hidden ones — they may become visible mid-frame and need
     * stable layout-local coords); extent is calculated only across
     * VISIBLE panels so toggled-hidden panels don't inflate the screen.
     *
     * @param panels         panel tree to lay out (order matters — BODY
     *                       panels stack in declaration order)
     * @param panelSizeFn    per-context function mapping Panel → {width, height}
     * @param bodyGap        vertical gap between BODY-stack siblings
     * @param relativeGap    gap between an anchored panel and its anchor
     * @param titleHeight    vertical space reserved above the first BODY panel
     * @param minImageWidth  minimum image width (used in the degenerate
     *                       all-hidden case AND as a clamp on the
     *                       returned {@code totalWidth})
     * @param minImageHeight same, vertical axis
     */
    public static PanelTreeLayout resolve(
            List<Panel> panels,
            Function<Panel, int[]> panelSizeFn,
            int bodyGap, int relativeGap, int titleHeight,
            int minImageWidth, int minImageHeight) {

        Map<String, int[]> sizes = new LinkedHashMap<>();
        for (Panel panel : panels) {
            sizes.put(panel.getId(), panelSizeFn.apply(panel));
        }

        Map<String, PanelBounds> bounds = PanelLayout.resolve(
                panels, sizes, bodyGap, relativeGap, titleHeight);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        boolean anyVisible = false;
        for (Panel panel : panels) {
            if (!panel.isVisible()) continue;
            PanelBounds b = bounds.get(panel.getId());
            if (b == null) continue;
            anyVisible = true;
            minX = Math.min(minX, b.x());
            minY = Math.min(minY, b.y());
            maxX = Math.max(maxX, b.x() + b.width());
            maxY = Math.max(maxY, b.y() + b.height());
        }
        if (!anyVisible) {
            // Degenerate case — pick safe defaults so the consumer's
            // centering math doesn't underflow.
            minX = 0; minY = 0;
            maxX = minImageWidth; maxY = minImageHeight;
        }

        int totalWidth  = Math.max(maxX - minX, minImageWidth);
        int totalHeight = Math.max(maxY - minY, minImageHeight);
        return new PanelTreeLayout(bounds, minX, minY, totalWidth, totalHeight);
    }
}
