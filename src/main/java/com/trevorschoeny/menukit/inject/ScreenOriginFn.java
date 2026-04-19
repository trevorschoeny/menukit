package com.trevorschoeny.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * A function from vanilla-screen bounds + screen instance to the screen-space
 * origin of an injected panel. The adapter calls this each frame (render and
 * input) so the origin follows the vanilla screen's layout across resizes
 * and state changes (recipe-book toggle, etc.).
 *
 * <p>The screen parameter enables chrome-aware resolution via the M5 region
 * path — {@link RegionRegistry#menuOriginFn} consults
 * {@link MenuChrome#of(AbstractContainerScreen)} to extend the bounds
 * by the screen's chrome extents before computing the origin. Origin
 * functions that don't need chrome (custom
 * {@link ScreenOriginFns#fromScreenTopLeft} etc.) simply ignore the parameter.
 *
 * <p>Consumers typically compose their {@code ScreenOriginFn} from
 * {@link ScreenOriginFns} constructors. For positioning rules not covered
 * by the constructors, supply a lambda directly.
 *
 * <p>Example — below a slot grid, left-aligned with it, 2 px gap:
 * <pre>{@code
 * ScreenOriginFn below = (bounds, screen) ->
 *     new ScreenOrigin(bounds.leftPos() + 8, bounds.topPos() + 72);
 * }</pre>
 */
@FunctionalInterface
public interface ScreenOriginFn {

    /**
     * Computes the panel's screen-space top-left origin given the vanilla
     * screen's bounds and the live screen instance.
     *
     * @param bounds the vanilla screen's frame bounds this frame
     * @param screen the live screen instance — consulted by chrome-aware
     *               region origin functions, ignored by consumer lambdas
     *               that don't need chrome. May be null when the adapter
     *               is invoked without a screen reference (e.g., tests).
     */
    ScreenOrigin compute(ScreenBounds bounds, AbstractContainerScreen<?> screen);
}
