package com.trevlar.menukit.inject;

/**
 * Screen-space top-left coordinates of a panel: what a {@code pixel(...)}
 * placement supplies each frame, and what a panel host resolves every other
 * placement to ({@code RegionMath.resolveMenu} / {@code resolveInside}).
 *
 * @param x screen-space X
 * @param y screen-space Y
 */
public record ScreenOrigin(int x, int y) {}
