package com.trevlar.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * How far each {@link AbstractContainerScreen} subclass draws <i>outside</i> its
 * declared {@code imageWidth x imageHeight} frame. Region resolution reads this
 * when computing MenuContext region origins, so panels anchor to the visible edge
 * of the screen rather than the declared frame edge.
 *
 * <h3>Exact class only</h3>
 *
 * {@link #of(AbstractContainerScreen)} looks up {@code screen.getClass()} with no
 * inheritance walk. Vanilla's subclassing doesn't follow its chrome (creative's
 * tabs are on {@code CreativeModeInventoryScreen}; its parents draw none), and a
 * provider registered on a base class would otherwise cover every screen. Mods
 * register for their own concrete classes.
 *
 * <h3>Changes mid-session</h3>
 *
 * Extents are recomputed every frame through {@link ChromeProvider}. Dynamic
 * providers read live screen state (recipe book open or closed); static ones
 * ignore the screen and return a constant. When chrome changes, such as a recipe
 * book toggle, there may be one frame before the provider picks it up, which in
 * practice can't be seen.
 */
@ApiStatus.Internal
public final class MenuChrome {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private MenuChrome() {}

    /**
     * Chrome outside the declared menu frame, per side: non-negative pixel amounts
     * added to the frame's bounds to give the visible boundary.
     */
    public record ChromeExtents(int top, int left, int right, int bottom) {
        /** No chrome: the default for screens without a provider. */
        public static final ChromeExtents NONE = new ChromeExtents(0, 0, 0, 0);
    }

    /** Computes chrome extents for a live screen. Static providers ignore the screen. */
    @FunctionalInterface
    public interface ChromeProvider {
        ChromeExtents compute(AbstractContainerScreen<?> screen);
    }

    // Keyed by exact class; no inheritance walk.
    private static final Map<Class<? extends AbstractContainerScreen<?>>, ChromeProvider> PROVIDERS
            = new HashMap<>();

    /**
     * Registers a chrome provider for a concrete screen class, at mod init. MenuKit's
     * own providers register in {@code MenuKitClient.onInitializeClient}; mods
     * register from their own {@code ClientModInitializer}.
     *
     * @throws IllegalStateException if the class already has a provider, since load
     *         order would otherwise decide which one applies
     */
    public static <T extends AbstractContainerScreen<?>> void register(
            Class<T> screenClass, ChromeProvider provider) {
        com.trevlar.menukit.api.Declarations.requireOpen("MenuChrome.register");
        ChromeProvider existing = PROVIDERS.get(screenClass);
        if (existing != null) {
            throw new IllegalStateException("MenuChrome: a provider for " + screenClass.getName()
                    + " is already registered; two would be settled by load order");
        }
        PROVIDERS.put(screenClass, provider);
        LOGGER.info("[MenuChrome] registered provider for {}", screenClass.getSimpleName());
    }

    /**
     * The chrome extents for the screen, matched on its exact class, or
     * {@link ChromeExtents#NONE} without a provider. Called every frame when
     * computing origins, so it must be cheap.
     */
    public static ChromeExtents of(AbstractContainerScreen<?> screen) {
        if (screen == null) return ChromeExtents.NONE;
        ChromeProvider provider = PROVIDERS.get(screen.getClass());
        if (provider == null) return ChromeExtents.NONE;
        return provider.compute(screen);
    }
}
