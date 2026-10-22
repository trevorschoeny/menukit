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
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
 * the screen's start edge to the panel's, center to center, or end edge to end edge, in GUI
 * pixels. So a panel dropped by the right edge stays that far from it at any window width or
 * GUI scale. Nothing is clamped: a position can be off a smaller screen, and a reset is the way
 * back.
 *
 * <p>A position only says where a panel sits. Whether it shows is still its declaration's call
 * ({@link PanelHost#layout()}). A missing file starts empty; a bad entry is skipped, a file
 * that isn't JSON set aside, one that can't be read left alone ({@link #load}); none of it
 * crashes.
 */
@ApiStatus.Internal
public final class PanelPositions {

    private PanelPositions() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    /** Which third of the screen an axis is measured from. */
    public enum Edge { START, CENTER, END }

    /**
     * A moved panel's place: on each axis the edge it is measured from and its offset there
     * (the panel's start edge from the screen's start, its center from the screen's center,
     * its end edge from the screen's end; negative toward the start).
     */
    public record Spot(Edge edgeX, int dx, Edge edgeY, int dy) {

        /**
         * The spot of a {@code w x h} panel whose top-left is {@code (left, top)} on an
         * {@code sw x sh} screen: each axis from the third the panel's center is in.
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

    /** The third of {@code screen} the center of a span from {@code start}, {@code size} long, is in. */
    private static Edge nearest(int start, int size, int screen) {
        // Twice the center against two and four thirds of the screen: integers only.
        int center2 = 2 * start + size;
        if (3 * center2 < 2 * screen) return Edge.START;
        if (3 * center2 > 4 * screen) return Edge.END;
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
    /** Per panel id, the scopes a newer MenuKit wrote that this one doesn't know: kept as read, written back. */
    private static final Map<String, JsonObject> FOREIGN = new HashMap<>();
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
     * A missing file is an empty store. An entry that is off (a panel that isn't an object, a
     * position missing a field or with an edge we don't know) is skipped with a warning, and
     * the rest is read. A scope this MenuKit doesn't know (a newer one's) is no error: it is
     * kept as it was and written back with the rest. A file that isn't a JSON object at all is
     * moved aside to {@code panel-positions.json.corrupt}, so the next write doesn't put an
     * empty store over it unseen, and the store starts empty. A file that can't be read at all
     * (locked, no permission) is no sign it is bad: it is left where it is, the store starts
     * empty, and nothing is written this session.
     */
    public static void load(Path path) {
        file = path;
        STORED.clear();
        FOREIGN.clear();
        if (!Files.exists(path)) {
            LOGGER.info("[MenuKit] No moved panels yet ({} doesn't exist)", path);
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            // There but unreadable now (locked, no permission): nothing says the file is bad, so
            // it stays where it is, and nothing is written over it this session.
            file = null;
            LOGGER.warn("[MenuKit] Couldn't read moved panels from {} ({}); the file is left as it is, every panel "
                    + "starts where its mod puts it, and moves aren't saved this session", path, e.toString());
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            // Not JSON, or not an object: nothing in it can be told apart. Keep it for the player.
            setAside(path, e);
            return;
        }
        List<String> skipped = new ArrayList<>();
        for (var panel : root.entrySet()) {
            if (!panel.getValue().isJsonObject()) {
                skipped.add(panel.getKey());
                continue;
            }
            Map<ModeScope, Spot> scopes = new EnumMap<>(ModeScope.class);
            for (var scope : panel.getValue().getAsJsonObject().entrySet()) {
                ModeScope known = scopeOf(scope.getKey());
                if (known == null) {
                    // A newer MenuKit's scope: not ours to read, but ours to keep.
                    FOREIGN.computeIfAbsent(panel.getKey(), k -> new JsonObject()).add(scope.getKey(), scope.getValue());
                    continue;
                }
                try {
                    JsonObject o = scope.getValue().getAsJsonObject();
                    scopes.put(known, new Spot(edge(o, "x"), o.get("dx").getAsInt(), edge(o, "y"), o.get("dy").getAsInt()));
                } catch (RuntimeException e) {
                    skipped.add(panel.getKey() + "/" + scope.getKey());
                }
            }
            if (!scopes.isEmpty()) STORED.put(panel.getKey(), scopes);
        }
        if (!skipped.isEmpty()) {
            LOGGER.warn("[MenuKit] Skipped {} unreadable moved-panel entries in {}: {}; those panels start where their mods put them",
                    skipped.size(), path, skipped);
        }
    }

    /** {@code both}, {@code survival} or {@code creative}, or {@code null} for a key this MenuKit doesn't know. */
    private static @Nullable ModeScope scopeOf(String key) {
        for (ModeScope s : ModeScope.values()) {
            if (s.name().equalsIgnoreCase(key)) return s;
        }
        return null;
    }

    /**
     * Moves an unreadable store to {@code <name>.corrupt}, replacing an older one. If even that
     * fails, nothing is written this session: a write would replace the player's file unseen.
     */
    private static void setAside(Path path, Exception why) {
        Path aside = path.resolveSibling(path.getFileName() + ".corrupt");
        try {
            Files.move(path, aside, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("[MenuKit] Couldn't read moved panels from {} ({}); moved it to {}, and every panel starts where its mod puts it",
                    path, why.toString(), aside);
        } catch (IOException e) {
            file = null;
            LOGGER.warn("[MenuKit] Couldn't read moved panels from {} ({}), nor move it aside ({}); "
                    + "every panel starts where its mod puts it, and moves aren't saved this session", path, why.toString(), e.toString());
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

    /** Forgets {@code id} in every mode (a newer MenuKit's too), back to its declaration, and writes the file. */
    public static void reset(String id) {
        boolean had = STORED.remove(id) != null;
        if (FOREIGN.remove(id) != null || had) save();
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
        Set<String> ids = new TreeSet<>(STORED.keySet());
        ids.addAll(FOREIGN.keySet());
        for (String id : ids) {
            JsonObject scopes = new JsonObject();
            STORED.getOrDefault(id, Map.of()).forEach((scope, spot) -> {
                JsonObject o = new JsonObject();
                o.addProperty("x", spot.edgeX().name().toLowerCase(Locale.ROOT));
                o.addProperty("dx", spot.dx());
                o.addProperty("y", spot.edgeY().name().toLowerCase(Locale.ROOT));
                o.addProperty("dy", spot.dy());
                scopes.add(scope.name().toLowerCase(Locale.ROOT), o);
            });
            JsonObject foreign = FOREIGN.get(id);
            if (foreign != null) foreign.entrySet().forEach(e -> scopes.add(e.getKey(), e.getValue()));
            root.add(id, scopes);
        }
        // Written whole beside it, then moved over it, so a crash mid-write never leaves half a file.
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // A file system that can't swap it in one step: replace it the plain way.
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("[MenuKit] Couldn't write moved panels to {} ({})", file, e.toString());
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // a stray .tmp beside the store is read by nothing
            }
        }
    }
}
