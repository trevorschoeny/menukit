package com.trevorschoeny.menukit.inject;

import net.minecraft.client.gui.screens.Screen;

/**
 * A function from screen dimensions + live {@link Screen} instance to the
 * screen-space origin of an MK panel injected onto a vanilla non-container
 * screen. Parallel to {@link ScreenOriginFn} but typed against
 * {@link Screen} rather than {@code AbstractContainerScreen} — vanilla
 * non-container screens have no inventory chrome (no {@code leftPos},
 * {@code topPos}, {@code imageWidth}, {@code imageHeight}); positions are
 * computed relative to the screen's full {@code width × height} instead.
 *
 * <p>Called each frame (render + input) so the origin follows window
 * resizes. The screen instance is supplied for cases where the consumer
 * wants to position relative to vanilla widget positions on a specific
 * screen subclass (cast on need).
 *
 * <p>Example — top-right corner, 4px inset:
 * <pre>{@code
 * VanillaScreenOriginFn topRight = (screenW, screenH, screen) ->
 *     new ScreenOrigin(screenW - panelWidth - 4, 4);
 * }</pre>
 *
 * <p>Region-anchored adapters compute their {@link VanillaScreenOriginFn}
 * internally from the consumer's
 * {@link com.trevorschoeny.menukit.core.VanillaScreenRegion} declaration;
 * consumers using region anchoring never construct a {@code VanillaScreenOriginFn}
 * directly.
 */
@FunctionalInterface
public interface VanillaScreenOriginFn {

    /**
     * Computes the panel's screen-space top-left origin given the vanilla
     * screen's dimensions and the live instance.
     *
     * @param screenW GUI-scaled screen width  (= {@code screen.width})
     * @param screenH GUI-scaled screen height (= {@code screen.height})
     * @param screen  the live {@link Screen} instance — supplied for
     *                consumer lambdas that want to position relative to
     *                vanilla widget bounds. May be ignored.
     */
    ScreenOrigin compute(int screenW, int screenH, Screen screen);
}
