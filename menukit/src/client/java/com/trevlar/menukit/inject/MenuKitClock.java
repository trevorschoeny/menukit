package com.trevlar.menukit.inject;

import net.minecraft.util.Util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.function.LongSupplier;

/**
 * The one clock MenuKit's own container screen motion runs on (7.4.0): the tab groups'
 * slides, the paging arrows' room and a swapped screen's moving frame. Vanilla's
 * {@code Util.getMillis()}, the clock every MenuKit animation reads, unless a check stops it
 * to step through a motion a frame at a time. Render thread only.
 */
@ApiStatus.Internal
public final class MenuKitClock {

    private MenuKitClock() {}

    private static LongSupplier source = Util::getMillis;

    /** Now, in ms, on {@code Util.getMillis()}'s scale. */
    public static long now() {
        return source.getAsLong();
    }

    /** For the checks only: the clock MenuKit reads from now on, or {@code null} for vanilla's again. */
    @VisibleForTesting
    public static void setForChecks(@Nullable LongSupplier clock) {
        source = clock == null ? Util::getMillis : clock;
    }
}
