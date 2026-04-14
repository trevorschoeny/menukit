package com.trevorschoeny.menukit.core;

/**
 * Computed screen-relative bounds for a {@link Panel} after layout
 * resolution. Context-neutral — the coordinate origin depends on the
 * caller (inventory-menu handlers use container-relative origin;
 * standalone screens use screen-absolute origin).
 *
 * <p>Produced by {@link PanelLayout#resolve} from declared
 * {@link PanelPosition} constraints and per-panel sizes.
 *
 * @param x      origin X in the layout's coordinate space
 * @param y      origin Y in the layout's coordinate space
 * @param width  panel width in pixels (including padding)
 * @param height panel height in pixels (including padding)
 */
public record PanelBounds(int x, int y, int width, int height) {}
