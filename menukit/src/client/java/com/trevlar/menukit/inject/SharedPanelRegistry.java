package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.Contribution;
import com.trevlar.menukit.api.panel.Panel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The shared panels' lifecycle ({@code SharedPanels}, 7.4.0): the contributions joined per
 * shared id, the one build at client start, and the panel built for each id.
 *
 * <p>How one shared panel is built from its contributions is the API package's
 * ({@link Builder}): it reads a contribution's declaration and declares the panel's adapter
 * for the winning mod, both package-private there. {@link Contribution} hands it over when
 * its class loads, which is before any contribution can be joined.
 */
@ApiStatus.Internal
public final class SharedPanelRegistry {

    private SharedPanelRegistry() {}

    /** One mod's contribution to a shared panel. */
    public record Joined(String modId, Contribution contribution) {}

    /** Builds and registers one shared panel from everything joined under its id, in join order. */
    @FunctionalInterface
    public interface Builder {
        Panel build(String id, List<Joined> joined);
    }

    private static @Nullable Builder builder;

    /** Contributions by shared id, in join order (the build's stable sort keeps it within one mod and order). */
    private static final Map<String, List<Joined>> JOINED = new LinkedHashMap<>();
    private static final Map<String, Panel> BUILT = new HashMap<>();
    private static boolean built = false;
    private static int warnings = 0;

    /** Set once, by {@link Contribution}'s class initializer. */
    public static void builder(Builder b) {
        builder = Objects.requireNonNull(b, "builder");
    }

    /**
     * Adds {@code contribution} to the shared panel {@code id} for {@code modId}: what
     * {@code SharedPanels.join} does for the calling mod, and MenuKit's checks for a named one.
     *
     * @throws IllegalStateException after the shared panels were built at client start
     */
    public static void join(String id, Contribution contribution, String modId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contribution, "contribution");
        if (built) {
            throw new IllegalStateException("SharedPanels.join(\"" + id + "\") from " + modId
                    + " after the shared panels were built at client start. Join from your client initializer.");
        }
        JOINED.computeIfAbsent(id, k -> new ArrayList<>()).add(new Joined(modId, contribution));
    }

    /**
     * Builds and registers every shared panel, once. MenuKit runs it at client start, in a
     * phase after the default one and before declarations freeze.
     */
    public static void buildAll() {
        if (built) return;
        built = true;
        if (JOINED.isEmpty()) return;
        Builder b = Objects.requireNonNull(builder, "no shared panel builder: Contribution never loaded");
        JOINED.forEach((id, joined) -> BUILT.put(id, b.build(id, List.copyOf(joined))));
    }

    /** The shared panel built for {@code id}, or {@code null} before the build or with no contribution. */
    public static @Nullable Panel panel(String id) {
        return BUILT.get(id);
    }

    /** Counts one disagreement warning the build logged. */
    public static void warned() {
        warnings++;
    }

    /** How many disagreement warnings were logged, for MenuKit's checks. */
    @VisibleForTesting
    public static int warningsForChecks() {
        return warnings;
    }
}
