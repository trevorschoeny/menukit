package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.inject.PanelHost;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Panels several mods share (7.4.0). Mods that work together put their elements in one
 * panel by joining it under an agreed id, a plain string that belongs to no single mod
 * ({@code "trevlar:gear"}). No mod creates the panel and no mod depends on another.
 *
 * <pre>{@code
 * SharedPanels.join("trevlar:gear", Contribution.builder(10, ModeScope.BOTH)
 *         .add(elytraSlot)
 *         .in(ModeScope.BOTH, Placement.screen(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP)))
 *         .build());
 * }</pre>
 *
 * <ul>
 *   <li><b>Built once, at client start</b>, from whatever contributions joined, after every
 *       client entrypoint ran and before declarations freeze. Join from your client
 *       initializer; a join after that throws.</li>
 *   <li><b>One flow.</b> The contributions are laid out in one {@link Flow}, by order, ties by
 *       mod id, so the result doesn't depend on load order. A contribution out of its mode
 *       scope or whose {@code visibleWhen} is false takes no room; with all of them hidden the
 *       panel isn't placed, so it draws and claims nothing.</li>
 *   <li><b>The declaration</b> (placements, style, axis, gap, {@code fixed()}) travels with
 *       every contribution. Where they differ, the lowest order's wins and MenuKit logs a
 *       warning naming both mods. Two {@code pixel(...)} origins are lambdas, which can't be
 *       compared, so they never count as a difference.</li>
 *   <li><b>Registered like an ordinary panel</b>: with a screen placement, through a
 *       {@link ScreenPanelAdapter} on <em>every</em> container screen (chests, furnaces and the
 *       rest, not only the inventory; it also puts its anchored placements in their anchors'
 *       hosts there), since no contribution says which screens; with only anchored placements,
 *       through an {@link AnchorPanelAdapter}, wherever its anchors are. Its id is the shared id.</li>
 * </ul>
 * With one mod installed it is an ordinary panel; with none, there is no panel.
 */
public final class SharedPanels {

    private SharedPanels() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private record Joined(String modId, Contribution contribution) {}

    /** Contributions by shared id, in join order (the stable sort keeps it within one mod and order). */
    private static final Map<String, List<Joined>> JOINED = new LinkedHashMap<>();
    private static final Map<String, Panel> BUILT = new HashMap<>();
    private static boolean built = false;
    private static int warnings = 0;

    /**
     * Adds {@code contribution} to the shared panel {@code id}, for the calling mod. Call from
     * your client initializer.
     *
     * @throws IllegalStateException after the shared panels were built at client start
     */
    public static void join(String id, Contribution contribution) {
        join(id, contribution, PanelHost.captureCallerModId());
    }

    /** {@link #join(String, Contribution)} for a named mod: MenuKit's checks, and the join above. */
    @ApiStatus.Internal
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
    @ApiStatus.Internal
    public static void buildAll() {
        if (built) return;
        built = true;
        JOINED.forEach(SharedPanels::buildAndRegister);
    }

    /** The shared panel built for {@code id}, or {@code null} before the build or with no contribution. */
    @ApiStatus.Internal
    public static @Nullable Panel panel(String id) {
        return BUILT.get(id);
    }

    /** How many disagreement warnings were logged, for MenuKit's checks. */
    @VisibleForTesting
    public static int warningsForChecks() {
        return warnings;
    }

    private static void buildAndRegister(String id, List<Joined> joined) {
        List<Joined> sorted = new ArrayList<>(joined);
        sorted.sort(Comparator.<Joined>comparingInt(j -> j.contribution().order).thenComparing(Joined::modId));
        Joined winner = sorted.get(0);
        Contribution decl = winner.contribution();

        // A disagreement is a version mismatch between mods, not the player's fault: warn, don't crash.
        for (Joined j : sorted.subList(1, sorted.size())) {
            List<String> diff = j.contribution().differences(decl);
            if (diff.isEmpty()) continue;
            warnings++;
            LOGGER.warn("[SharedPanels] '{}': {}'s contribution (order {}) differs from {}'s (order {}) in {}; "
                            + "{}'s declaration is used", id, j.modId(), j.contribution().order,
                    winner.modId(), decl.order, String.join(", ", diff), winner.modId());
        }

        // One flow of contributions, each its own flow on the same axis, so hiding one hides
        // its elements together and the next moves up into its place.
        Flow.Builder flow = Flow.builder().gap(decl.gap);
        if (decl.vertical) flow.vertical();
        for (Joined j : sorted) {
            Contribution c = j.contribution();
            Flow.Builder part = Flow.builder().gap(decl.gap).visibleWhen(c::shown);
            if (decl.vertical) part.vertical();
            part.addAll(c.elements);
            flow.add(part.build());
        }
        // All hidden: the panel isn't placed, so it draws and claims nothing. Read every frame.
        List<Contribution> parts = sorted.stream().map(Joined::contribution).toList();
        Panel.Builder panel = Panel.builder(id).style(decl.style).add(flow.build())
                .visibleWhen(() -> {
                    for (Contribution c : parts) if (c.shown()) return true;
                    return false;
                });
        decl.placements.forEach(panel::in);
        if (decl.fixed) panel.fixed();
        Panel built = panel.build();
        BUILT.put(id, built);

        // Registered as an ordinary panel with these placements would be: with a screen placement,
        // the screen adapter on every container screen (it also hosts the anchored placements
        // there); with only anchored ones, the anchor adapter, on any screen. Credited to the
        // mod whose declaration won, the tie-break after priority in a shared region.
        boolean onScreen = decl.placements.values().stream().anyMatch(p -> p.anchor() == null);
        if (onScreen) new ScreenPanelAdapter(built, built.interiorPadding(), winner.modId());
        else new AnchorPanelAdapter(built, built.interiorPadding(), winner.modId());
    }
}
