package com.trevlar.menukit.api.panel;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

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
 *   <li>{@link #where(Predicate)}, the screens a test accepts, asked of the open
 *       screen itself (7.3.0): for a scope only known per menu at runtime, such as
 *       "the menus a slot group's slots were added to".</li>
 * </ul>
 *
 * <p>Matching is by class ancestry, the same {@code isAssignableFrom} test
 * {@link ScreenPanelAdapter#matches} uses, so naming a base class matches its
 * subclasses.
 *
 * <p>Immutable; one instance can be shared freely.
 */
public final class ScreenMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private enum Mode { ALL, ONLY, EXCEPT, WHERE }

    private final Mode mode;
    private final List<Class<?>> classes;
    /** The test of {@link #where}; accepts everything otherwise. */
    private final Predicate<? super AbstractContainerScreen<?>> test;

    private ScreenMatcher(Mode mode, List<Class<?>> classes) {
        this(mode, classes, s -> true);
    }

    private ScreenMatcher(Mode mode, List<Class<?>> classes, Predicate<? super AbstractContainerScreen<?>> test) {
        this.mode = mode;
        this.classes = classes;
        this.test = test;
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
     * The screens {@code test} accepts, asked of each container screen as it opens
     * (its init, when its menu already holds every slot). For a scope that is not a
     * set of screen classes: a slot library's group, say, whose slots the server adds
     * to some menus and not others, so a panel showing them belongs on exactly those
     * screens ({@code adapter.onMatching(group.screens())}). It is decided when the
     * screen opens and not asked again while it stays open: a creative tab switch, which
     * swaps the menu's slots on the same screen, doesn't ask it again. What changes while
     * the screen is open is the panel's mode scope ({@link Panel.Builder#in}) or its
     * {@code visibleWhen}, not this. The test runs on the render thread; it must not throw.
     */
    public static ScreenMatcher where(Predicate<? super AbstractContainerScreen<?>> test) {
        return new ScreenMatcher(Mode.WHERE, List.of(), Objects.requireNonNull(test, "test"));
    }

    /**
     * Whether this matcher applies to an open screen: its class for the class forms,
     * the screen itself for {@link #where}. What an adapter asks.
     */
    public boolean matches(AbstractContainerScreen<?> screen) {
        if (mode != Mode.WHERE) return matches(screen.getClass());
        try {
            return test.test(screen);
        } catch (RuntimeException e) {
            // Game state never throws (conventions): a test that fails is a "no", logged.
            LOGGER.warn("MenuKit: a ScreenMatcher.where test threw on {}; the panel is left off it",
                    screen.getClass().getName(), e);
            return false;
        }
    }

    /**
     * Whether this matcher applies to an open screen's class. The mixins already
     * check for {@code AbstractContainerScreen}, so {@code all()} always matches. A
     * {@link #where} matcher has no answer from the class alone and says yes; ask
     * {@link #matches(AbstractContainerScreen)}.
     */
    public boolean matches(Class<?> screenClass) {
        return switch (mode) {
            case ALL, WHERE -> true;
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
