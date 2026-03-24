package com.trevorschoeny.menukit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent key-value storage for a family's general config options.
 * One file per family: {@code config/menukit-family-{id}.json}.
 *
 * <p>This is the single source of truth for family-wide settings.
 * Individual mods read/write through {@link MKFamily#getGeneralBool} etc.,
 * which delegate here. The file is loaded lazily on first access and
 * saved when the user clicks Save in the YACL config screen.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
class MKFamilyConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private Map<String, Object> values = new LinkedHashMap<>();
    private boolean loaded = false;

    MKFamilyConfig(String familyId) {
        this.configPath = FabricLoader.getInstance().getConfigDir()
                .resolve("menukit-family-" + familyId + ".json");
    }

    // ── Load / Save ─────────────────────────────────────────────────────────

    /** Loads values from disk. Safe to call multiple times — only reads once. */
    void load() {
        if (loaded) return;
        loaded = true;

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                Map<String, Object> parsed = GSON.fromJson(json,
                        new TypeToken<Map<String, Object>>() {}.getType());
                if (parsed != null) {
                    values = new LinkedHashMap<>(parsed);
                }
                MenuKit.LOGGER.info("[MenuKit] Loaded family config from {}", configPath.getFileName());
            } catch (IOException e) {
                MenuKit.LOGGER.error("[MenuKit] Failed to load family config from {}",
                        configPath.getFileName(), e);
            }
        }
    }

    /** Writes current values to disk. Creates the file if it doesn't exist. */
    void save() {
        try {
            Files.writeString(configPath, GSON.toJson(values));
        } catch (IOException e) {
            MenuKit.LOGGER.error("[MenuKit] Failed to save family config to {}",
                    configPath.getFileName(), e);
        }
    }

    // ── Typed Getters ───────────────────────────────────────────────────────

    boolean getBool(String key, boolean defaultValue) {
        ensureLoaded();
        Object val = values.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    int getInt(String key, int defaultValue) {
        ensureLoaded();
        Object val = values.get(key);
        // GSON deserializes all numbers as Double
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }

    float getFloat(String key, float defaultValue) {
        ensureLoaded();
        Object val = values.get(key);
        if (val instanceof Number n) return n.floatValue();
        return defaultValue;
    }

    String getString(String key, String defaultValue) {
        ensureLoaded();
        Object val = values.get(key);
        if (val instanceof String s) return s;
        return defaultValue;
    }

    // ── Setter ──────────────────────────────────────────────────────────────

    /** Sets a value in memory. Call {@link #save()} to persist. */
    void set(String key, Object value) {
        ensureLoaded();
        values.put(key, value);
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void ensureLoaded() {
        if (!loaded) load();
    }
}
