package com.trevorschoeny.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Library-owned registry of vanilla-inventory-chrome extents — the pixel
 * amounts each {@link AbstractContainerScreen} subclass draws <i>outside</i>
 * its declared {@code imageWidth × imageHeight} frame. The M5 region system
 * consults this registry when resolving inventory-region origins so panels
 * anchor to the effective visible edge of the screen, not the declared
 * frame edge.
 *
 * <p>See {@code menukit/Design Docs/Phase 12.5/M7_CHROME_AWARE_REGIONS.md}
 * for the design rationale and Principle 9 tie-in.
 *
 * <h3>Resolution — exact-class only</h3>
 *
 * {@link #of(AbstractContainerScreen)} looks up by {@code screen.getClass()}
 * — no inheritance walk. Rationale: vanilla subclass relationships don't
 * map to chrome relationships (creative's tabs live on
 * {@code CreativeModeInventoryScreen}; its parents don't draw tabs). Exact-
 * class-only is predictable and avoids the "register on a base class,
 * accidentally cover every screen in existence" foot-gun. Modded consumers
 * register for their own concrete classes.
 *
 * <h3>Transition semantics</h3>
 *
 * Chrome extents are recomputed per frame via {@link ChromeProvider}.
 * Dynamic providers read live screen state (recipe book open/closed);
 * static providers ignore the screen parameter and return a constant. When
 * a screen's chrome changes mid-session (recipe book toggle), there may
 * be a one-frame visual transition as the provider picks up the new state.
 * In practice imperceptible; acceptable for current cases.
 */
public final class InventoryChrome {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private InventoryChrome() {}

    /**
     * Per-side chrome extents outside the declared inventory frame.
     * {@code top}/{@code left}/{@code right}/{@code bottom} are non-negative
     * pixel amounts added to the frame's bounds to produce the effective
     * visible boundary.
     */
    public record ChromeExtents(int top, int left, int right, int bottom) {
        /** All-zero extents — the default for screens without a registered provider. */
        public static final ChromeExtents NONE = new ChromeExtents(0, 0, 0, 0);
    }

    /**
     * Computes chrome extents for a live screen instance. Providers take
     * the screen even when their answer is static (they ignore it) so the
     * interface is uniform across dynamic and static cases.
     */
    @FunctionalInterface
    public interface ChromeProvider {
        ChromeExtents compute(AbstractContainerScreen<?> screen);
    }

    // Exact-class-only map — no inheritance walk.
    private static final Map<Class<? extends AbstractContainerScreen<?>>, ChromeProvider> PROVIDERS
            = new HashMap<>();

    /**
     * Registers a chrome provider for a screen class. First registration of
     * a class wins; subsequent calls with the same class are no-ops with a
     * warning log (same pattern as {@code MKSlotState.register}).
     *
     * <p>Called at mod init — library-shipped providers register during
     * {@code MenuKitClient.onInitializeClient}; modded consumers register
     * from their own {@code ClientModInitializer}.
     *
     * @param screenClass the concrete screen class
     * @param provider    the provider
     * @param <T>         the screen type
     */
    public static <T extends AbstractContainerScreen<?>> void register(
            Class<T> screenClass, ChromeProvider provider) {
        ChromeProvider existing = PROVIDERS.get(screenClass);
        if (existing != null) {
            LOGGER.warn("[InventoryChrome] provider for {} already registered — ignoring second registration",
                    screenClass.getName());
            return;
        }
        PROVIDERS.put(screenClass, provider);
        LOGGER.info("[InventoryChrome] registered provider for {}", screenClass.getSimpleName());
    }

    /**
     * Returns the chrome extents for the given screen instance. Exact-class
     * match on {@code screen.getClass()}; returns {@link ChromeExtents#NONE}
     * for screens without a registered provider.
     *
     * <p>Called per-frame from {@code RegionRegistry.inventoryOriginFn} as
     * part of origin computation. Must be cheap.
     */
    public static ChromeExtents of(AbstractContainerScreen<?> screen) {
        if (screen == null) return ChromeExtents.NONE;
        ChromeProvider provider = PROVIDERS.get(screen.getClass());
        if (provider == null) return ChromeExtents.NONE;
        return provider.compute(screen);
    }
}
