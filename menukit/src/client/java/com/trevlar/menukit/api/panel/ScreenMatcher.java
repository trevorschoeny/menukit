package com.trevlar.menukit.api.panel;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

/**
 * Which screens a registered slot (or any feature meant for every inventory screen)
 * applies to. Features are <b>on everywhere by default, and opted out per
 * screen</b>.
 *
 * <p>The default, {@link #all()}, is every {@link AbstractContainerScreen}: you
 * register once and the feature appears on every screen it could. Naming screens
 * <em>narrows</em> that on purpose. It avoids the old trap where
 * {@code .on(InventoryScreen)} quietly missed creative, which is a sibling class,
 * not a subclass:
 * <ul>
 *   <li>{@link #all()}, every container screen (the default).</li>
 *   <li>{@link #allExcept(Class...)}, every container screen but the named ones
 *       (a deliberate exclusion, e.g. "not in creative").</li>
 *   <li>{@link #only(Class...)}, only the named screens (full narrowing).</li>
 * </ul>
 *
 * <p>Matching is by class ancestry, the same {@code isAssignableFrom} test
 * {@link ScreenPanelAdapter#matches} uses, so naming a base class matches its
 * subclasses.
 *
 * <p>Immutable; one instance can be shared freely.
 */
public final class ScreenMatcher {

    private enum Mode { ALL, ONLY, EXCEPT }

    private final Mode mode;
    private final List<Class<?>> classes;

    private ScreenMatcher(Mode mode, List<Class<?>> classes) {
        this.mode = mode;
        this.classes = classes;
    }

    /** Every inventory screen: the default. */
    public static ScreenMatcher all() {
        return new ScreenMatcher(Mode.ALL, List.of());
    }

    /**
     * Every inventory screen <em>except</em> the named ones and their subclasses.
     * Opts a slot out of specific screens, as in
     * {@code allExcept(CreativeModeInventoryScreen.class)}.
     */
    public static ScreenMatcher allExcept(Class<?>... screens) {
        if (screens.length == 0) return all();
        return new ScreenMatcher(Mode.EXCEPT, List.of(screens));
    }

    /**
     * Only the named screens and their subclasses, for a slot that belongs on a
     * fixed set of screens rather than everywhere.
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
     * Whether this matcher applies to an open screen's class. The mixins already
     * check for {@code AbstractContainerScreen}, so {@code all()} always matches.
     */
    public boolean matches(Class<?> screenClass) {
        return switch (mode) {
            case ALL -> true;
            case ONLY -> anyAssignableFrom(screenClass);
            case EXCEPT -> !anyAssignableFrom(screenClass);
        };
    }

    private boolean anyAssignableFrom(Class<?> screenClass) {
        for (Class<?> c : classes) {
            if (c.isAssignableFrom(screenClass)) return true;
        }
        return false;
    }
}
