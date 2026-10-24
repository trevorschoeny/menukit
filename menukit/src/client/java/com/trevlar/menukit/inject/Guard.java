package com.trevlar.menukit.inject;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A mod's callback run from MenuKit's render and input loops, kept from taking the screen down
 * with it (7.4.0), as {@code ScreenMatcher.matches} and {@code SlotItemLooks} keep theirs:
 * one that throws counts as off this time (hidden, not selected, no keys, nothing live), and
 * is logged once, the first time, by what it is and whose. Render thread only.
 */
@ApiStatus.Internal
public final class Guard {

    private Guard() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");
    /** The callbacks that have thrown, so each is logged once. Weak: one nothing holds any more goes. */
    private static final Set<Object> LOGGED = Collections.newSetFromMap(new WeakHashMap<>());

    /** {@code test}'s answer, or {@code false} when it throws. */
    public static boolean test(BooleanSupplier test, String what, String whose) {
        try {
            return test.getAsBoolean();
        } catch (RuntimeException e) {
            failed(test, what, whose, e);
            return false;
        }
    }

    /** {@code value}'s answer, or {@code null} when it throws. */
    public static <T> @Nullable T get(Supplier<? extends @Nullable T> value, String what, String whose) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            failed(value, what, whose, e);
            return null;
        }
    }

    /** {@code value}'s answer, or {@code fallback} when it throws. */
    public static int getInt(IntSupplier value, int fallback, String what, String whose) {
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            failed(value, what, whose, e);
            return fallback;
        }
    }

    /** Runs {@code action}; whether it finished without throwing. */
    public static boolean run(Runnable action, String what, String whose) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            failed(action, what, whose, e);
            return false;
        }
    }

    /** {@code callback}, {@code what} of {@code whose}, threw {@code e}: logged the first time only. */
    public static void failed(Object callback, String what, String whose, RuntimeException e) {
        if (LOGGED.add(callback)) {
            LOGGER.error("[MenuKit] The {} of '{}' threw; it counts as off where it throws (logged once)", what, whose, e);
        }
    }
}
