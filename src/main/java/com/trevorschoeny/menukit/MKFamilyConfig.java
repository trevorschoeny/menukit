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
 * Individual mods read/write through {@link MKFamily#getGeneral} and
 * {@link MKFamily#setGeneral}, which delegate here. Type coercion
 * (including GSON number normalization) is handled by {@link GeneralOption#coerce}.
 * The file is loaded lazily on first access and saved when the user clicks
 * Save in the YACL config screen.
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
            } catch (Exception e) {
                // Catch GSON parse errors (JsonSyntaxException, JsonParseException, etc.)
                // so a corrupted config file doesn't crash the game. Defaults will apply.
                MenuKit.LOGGER.warn("[MenuKit] Failed to parse family config '{}': {}. " +
                        "Using defaults.", configPath.getFileName(), e.getMessage());
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

    // ── Typed Access ────────────────────────────────────────────────────────

    /**
     * Reads a value using a typed {@link GeneralOption} descriptor.
     * GSON number normalization (Double -> Integer/Float) is handled
     * by {@link GeneralOption#coerce}, so callers always get the right type.
     */
    <T> T get(GeneralOption<T> option) {
        ensureLoaded();
        Object raw = values.get(option.key());
        return option.coerce(raw);
    }

    /**
     * Sets a value in memory. Call {@link #save()} to persist.
     * Type enforcement happens at the call site via {@link GeneralOption}.
     */
    <T> void set(GeneralOption<T> option, T value) {
        ensureLoaded();
        values.put(option.key(), value);
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void ensureLoaded() {
        if (!loaded) load();
    }
}
