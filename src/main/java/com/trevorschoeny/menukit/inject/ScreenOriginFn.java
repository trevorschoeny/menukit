package com.trevorschoeny.menukit.inject;

/**
 * A pure function from vanilla-screen bounds to the screen-space origin of
 * an injected panel. The adapter calls this each frame (render and input)
 * so the origin follows the vanilla screen's layout across resizes.
 *
 * <p>Consumers typically compose their {@code ScreenOriginFn} from
 * {@link ScreenOriginFns} constructors (common cases: above or below a slot
 * grid, corners of the screen frame). For positioning rules not covered by
 * the constructors, supply a lambda directly.
 *
 * <p>Example — below a slot grid, left-aligned with it, 2px gap:
 * <pre>{@code
 * ScreenOriginFn below = bounds ->
 *     new ScreenOrigin(bounds.leftPos() + 8, bounds.topPos() + 72);
 * }</pre>
 */
@FunctionalInterface
public interface ScreenOriginFn {

    /** Computes the panel's screen-space top-left origin given the vanilla screen's bounds. */
    ScreenOrigin compute(ScreenBounds bounds);
}
