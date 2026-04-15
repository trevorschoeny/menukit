package com.trevorschoeny.menukit.inject;

/**
 * Screen-space top-left coordinates of an injected panel. Produced by a
 * {@link ScreenOriginFn} from a {@link ScreenBounds}. The adapter uses this
 * as the origin for its {@link com.trevorschoeny.menukit.core.RenderContext}
 * and for translating mouse coordinates when dispatching input.
 *
 * @param x screen-space X
 * @param y screen-space Y
 */
public record ScreenOrigin(int x, int y) {
}
