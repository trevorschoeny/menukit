package com.trevlar.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

/**
 * Which screens a registered-slot presence (or any screen-complete feature) applies
 * to — the consumer-facing expression of inventory-screen parity's
 * <b>default-on, opt-out-per-screen</b> rule.
 *
 * <p>The default ({@link #all()}) is the full inventory-bearing screen family:
 * every {@link AbstractContainerScreen}. Under parity that is the right default —
 * a consumer registers intent once and it manifests everywhere it could. Naming
 * specific screens is therefore an explicit <em>narrowing</em>, the inverse of
 * the old footgun where {@code .on(InventoryScreen)} silently missed creative
 * (siblings, not parent/child):
 * <ul>
 *   <li>{@link #all()} — every container screen (the default).</li>
 *   <li>{@link #allExcept(Class...)} — every container screen but the named ones
 *       (a deliberate exclusion, e.g. "not in creative").</li>
 *   <li>{@link #only(Class...)} — only the named screens (full narrowing).</li>
 * </ul>
 *
 * <p>Matching is by class ancestry — the same {@code isAssignableFrom} test
 * {@link ScreenPanelAdapter#matches} uses — so naming a base class matches its
 * subclasses. This is the one matching primitive parity reuses; it is not a
 * second screen-matcher.
 *
 * <p>Immutable value type. Reuse one instance across presences freely.
 */
public final class ScreenMatcher {

    private enum Mode { ALL, ONLY, EXCEPT }

    private final Mode mode;
    private final List<Class<?>> classes;

    private ScreenMatcher(Mode mode, List<Class<?>> classes) {
        this.mode = mode;
        this.classes = classes;
    }

    /** Every inventory-bearing screen — the parity default. */
    public static ScreenMatcher all() {
        return new ScreenMatcher(Mode.ALL, List.of());
    }

    /**
     * Every inventory-bearing screen <em>except</em> the named ones (and their
     * subclasses). The clean way to opt a slot out of a specific screen, e.g.
     * {@code allExcept(CreativeModeInventoryScreen.class)}.
     */
    public static ScreenMatcher allExcept(Class<?>... screens) {
        if (screens.length == 0) return all();
        return new ScreenMatcher(Mode.EXCEPT, List.of(screens));
    }

    /**
     * Only the named screens (and their subclasses). Full narrowing — use when a
     * slot genuinely belongs on a closed set of screens rather than everywhere.
     */
    public static ScreenMatcher only(Class<?>... screens) {
        if (screens.length == 0) {
            throw new IllegalArgumentException(
                    "ScreenMatcher.only(...) needs at least one screen class; "
                    + "use ScreenMatcher.all() for the everywhere default.");
        }
        return new ScreenMatcher(Mode.ONLY, List.of(screens));
    }

    /**
     * Whether this matcher applies to the given opened screen's class. The mixins
     * already gate on {@code AbstractContainerScreen}, so {@link Mode#ALL} is an
     * unconditional yes.
     */
    public boolean matches(Class<?> screenClass) {
        return switch (mode) {
            case ALL -> true;
            case ONLY -> anyAssignableFrom(screenClass);
            case EXCEPT -> !anyAssignableFrom(screenClass);
        };
    }

    /** True iff any of the listed classes is assignable from {@code screenClass}. */
    private boolean anyAssignableFrom(Class<?> screenClass) {
        for (Class<?> c : classes) {
            if (c.isAssignableFrom(screenClass)) return true;
        }
        return false;
    }
}
