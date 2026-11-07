package com.trevlar.menukit.inject;

import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.MKRecipeBookAccessor;
import com.trevlar.menukit.mixin.MKRecipeBookComponentAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

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
 * providers read live screen state; static ones ignore the screen and return a
 * constant. The recipe book and the tab rows are every screen's, read live in
 * {@link #of}. When chrome changes, such as a recipe
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
     * The chrome extents for the screen: its provider's, matched on its exact class
     * ({@link ChromeExtents#NONE} without one), plus, on any screen (8.0.0):
     * <ul>
     *   <li>an open recipe book beside the frame ({@link #bookLeft}), the screen's own or
     *       one a mod lends it, so a panel left of the frame sits left of the book;</li>
     *   <li>the tab rows standing on its own frame ({@link ContainerTabRows#bareRows}), so
     *       a panel above or below the frame sits past the row instead of under it. While
     *       the region is up its bounds already hold its rows.</li>
     * </ul>
     * Called every frame when computing origins, so it must be cheap.
     */
    public static ChromeExtents of(AbstractContainerScreen<?> screen) {
        if (screen == null) return ChromeExtents.NONE;
        ChromeProvider provider = PROVIDERS.get(screen.getClass());
        ChromeExtents own = provider == null ? ChromeExtents.NONE : provider.compute(screen);
        int[] rows = ContainerTabRows.bareRows(screen);
        return new ChromeExtents(own.top() + rows[0], own.left() + bookLeft(screen), own.right(), own.bottom() + rows[1]);
    }

    /**
     * How far an open recipe book reaches left of the frame: from its filter tabs' left edge
     * (30 px left of the body) to {@code leftPos}, or 0 with no book open beside the frame
     * (none, closed, or over the frame on a narrow window). The book is the screen's own
     * ({@code AbstractRecipeBookScreen}) or one a mod added among its widgets.
     */
    private static int bookLeft(AbstractContainerScreen<?> screen) {
        RecipeBookComponent<?> book = screen instanceof AbstractRecipeBookScreen<?> own
                ? ((MKRecipeBookAccessor) own).mk$getRecipeBookComponent() : lentBook(screen);
        if (book == null || !book.isVisible()) return 0;
        MKRecipeBookComponentAccessor at = (MKRecipeBookComponentAccessor) book;
        if (at.mk$widthTooNarrow()) return 0;
        int left = ((AbstractContainerScreenAccessor) screen).mk$getLeftPos() - (at.mk$xOrigin() - BOOK_TABS);
        return Math.max(left, 0);
    }

    /** The recipe book's filter tabs stand this far left of its body. */
    private static final int BOOK_TABS = 30;

    /** A recipe book a mod added among the screen's widgets, or {@code null}. */
    private static @Nullable RecipeBookComponent<?> lentBook(AbstractContainerScreen<?> screen) {
        for (var child : screen.children()) {
            if (child instanceof RecipeBookComponent<?> book) return book;
        }
        return null;
    }
}
