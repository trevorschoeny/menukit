package com.trevlar.menukit.inject;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trevlar.menukit.api.panel.ModeScope;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where the player has moved panels (7.4.0): {@code config/menukit/panel-positions.json}, read
 * once at client start and written on each drop and each reset.
 *
 * <p>Keyed by panel id (a shared panel's is its shared id), then by mode scope, {@code both},
 * {@code survival} or {@code creative}:
 *
 * <pre>
 * {
 *   "mymod:elytra": {
 *     "both":     { "x": "end",   "dx": -12, "y": "start",  "dy": 40 },
 *     "creative": { "x": "start", "dx": 8,   "y": "center", "dy": -20 }
 *   }
 * }
 * </pre>
 *
 * Each axis is measured from the third of the screen the panel was nearest when dropped: from
 * the screen's start edge to the panel's, centre to centre, or end edge to end edge, in GUI
 * pixels. So a panel dropped by the right edge stays that far from it at any window width or
 * GUI scale. Nothing is clamped: a position can be off a smaller screen, and a reset is the way
 * back.
 *
 * <p>A position only says where a panel sits. Whether it shows is still its declaration's call
 * ({@link PanelHost#layout()}). A corrupt or missing file starts empty and never crashes.
 */
@ApiStatus.Internal
public final class PanelPositions {

    private PanelPositions() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    /** Which third of the screen an axis is measured from. */
    public enum Edge { START, CENTER, END }

    /**
     * A moved panel's place: on each axis the edge it is measured from and its offset there
     * (the panel's start edge from the screen's start, its centre from the screen's centre,
     * its end edge from the screen's end; negative toward the start).
     */
    public record Spot(Edge edgeX, int dx, Edge edgeY, int dy) {

        /**
         * The spot of a {@code w x h} panel whose top-left is {@code (left, top)} on an
         * {@code sw x sh} screen: each axis from the third the panel's centre is in.
         */
        public static Spot of(int left, int top, int w, int h, int sw, int sh) {
            Edge ex = nearest(left, w, sw), ey = nearest(top, h, sh);
            return new Spot(ex, offset(ex, left, w, sw), ey, offset(ey, top, h, sh));
        }

        /** The left edge of a {@code w}-wide panel at this spot on an {@code sw}-wide screen. */
        public int left(int w, int sw) {
            return start(edgeX, dx, w, sw);
        }

        /** The top edge of an {@code h}-tall panel at this spot on an {@code sh}-tall screen. */
        public int top(int h, int sh) {
            return start(edgeY, dy, h, sh);
        }
    }

    /** The third of {@code screen} the centre of a span from {@code start}, {@code size} long, is in. */
    private static Edge nearest(int start, int size, int screen) {
        // Twice the centre against two and four thirds of the screen: integers only.
        int centre2 = 2 * start + size;
        if (3 * centre2 < 2 * screen) return Edge.START;
        if (3 * centre2 > 4 * screen) return Edge.END;
        return Edge.CENTER;
    }

    private static int offset(Edge edge, int start, int size, int screen) {
        return switch (edge) {
            case START -> start;
            case CENTER -> start + size / 2 - screen / 2;
            case END -> start + size - screen;
        };
    }

    private static int start(Edge edge, int offset, int size, int screen) {
        return switch (edge) {
            case START -> offset;
            case CENTER -> screen / 2 + offset - size / 2;
            case END -> screen + offset - size;
        };
    }

    // The store. Render thread only: read by every layout, written by edit mode.

    private static final Map<String, Map<ModeScope, Spot>> STORED = new HashMap<>();
    /** Where the store is written, or {@code null} before {@link #load} (MenuKit's headless checks). */
    private static @Nullable Path file;

    /** The panel being dragged right now and where it is, ahead of anything stored: edit mode shows the drag live. */
    private static @Nullable String dragId;
    private static @Nullable Spot dragSpot;

    /** The real file: {@code config/menukit/panel-positions.json} in the game's config folder. */
    public static Path defaultFile(Path configDir) {
        return configDir.resolve("menukit").resolve("panel-positions.json");
    }

    /**
     * Reads {@code path} into the store, replacing what it held, and writes there from now on.
     * A missing file is an empty store; a corrupt one is too, with a warning, and is replaced
     * on the next drop or reset.
     */
    public static void load(Path path) {
        file = path;
        STORED.clear();
        if (!Files.exists(path)) {
            LOGGER.info("[MenuKit] No moved panels yet ({} doesn't exist)", path);
            return;
        }
        try (Reader in = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
            for (var panel : root.entrySet()) {
                Map<ModeScope, Spot> scopes = new EnumMap<>(ModeScope.class);
                for (var scope : panel.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = scope.getValue().getAsJsonObject();
                    scopes.put(ModeScope.valueOf(scope.getKey().toUpperCase(Locale.ROOT)),
                            new Spot(edge(o, "x"), o.get("dx").getAsInt(), edge(o, "y"), o.get("dy").getAsInt()));
                }
                if (!scopes.isEmpty()) STORED.put(panel.getKey(), scopes);
            }
        } catch (Exception e) {
            // Anything off in the file (not JSON, the wrong shape, a scope or edge we don't know):
            // nothing moved rather than half of it, and never a crash.
            STORED.clear();
            LOGGER.warn("[MenuKit] Couldn't read moved panels from {} ({}); every panel starts where its mod puts it", path, e.toString());
        }
    }

    private static Edge edge(JsonObject o, String key) {
        return Edge.valueOf(o.get(key).getAsString().toUpperCase(Locale.ROOT));
    }

    /**
     * Where {@code id} sits now: the drag in progress, else its position for the current mode,
     * else its {@code both} one, else {@code null} (its declaration).
     */
    public static @Nullable Spot lookup(String id) {
        if (id.equals(dragId)) return dragSpot;
        Map<ModeScope, Spot> scopes = STORED.get(id);
        if (scopes == null) return null;
        ModeScope now = PanelHost.currentMode();
        Spot own = now == null ? null : scopes.get(now);
        return own != null ? own : scopes.get(ModeScope.BOTH);
    }

    /**
     * Stores {@code spot} for {@code id} in {@code scope}, and writes the file. A {@code both}
     * drop also clears the current mode's own position, which would otherwise win over it and
     * put the panel back where it was: the player sees where it was dropped. The other mode's
     * own position is kept.
     */
    public static void store(String id, ModeScope scope, Spot spot) {
        Map<ModeScope, Spot> scopes = STORED.computeIfAbsent(id, k -> new EnumMap<>(ModeScope.class));
        scopes.put(scope, spot);
        ModeScope now = PanelHost.currentMode();
        if (scope == ModeScope.BOTH && now != null) scopes.remove(now);
        save();
    }

    /** Forgets {@code id} in every mode, back to its declaration, and writes the file. */
    public static void reset(String id) {
        if (STORED.remove(id) != null) save();
    }

    /** Shows {@code id} at {@code spot} while it is dragged; {@code null} ends the drag. Never written. */
    public static void dragging(@Nullable String id, @Nullable Spot spot) {
        dragId = id;
        dragSpot = spot;
    }

    /** Writes the store, sorted by id so the file diffs cleanly. A failure is logged; the store keeps it until the next write. */
    private static void save() {
        if (file == null) return;
        JsonObject root = new JsonObject();
        for (var panel : new TreeMap<>(STORED).entrySet()) {
            JsonObject scopes = new JsonObject();
            panel.getValue().forEach((scope, spot) -> {
                JsonObject o = new JsonObject();
                o.addProperty("x", spot.edgeX().name().toLowerCase(Locale.ROOT));
                o.addProperty("dx", spot.dx());
                o.addProperty("y", spot.edgeY().name().toLowerCase(Locale.ROOT));
                o.addProperty("dy", spot.dy());
                scopes.add(scope.name().toLowerCase(Locale.ROOT), o);
            });
            root.add(panel.getKey(), scopes);
        }
        try {
            Files.createDirectories(file.getParent());
            // Written whole beside it, then moved over it, so a crash mid-write never leaves half a file.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("[MenuKit] Couldn't write moved panels to {} ({})", file, e.toString());
        }
    }
}
