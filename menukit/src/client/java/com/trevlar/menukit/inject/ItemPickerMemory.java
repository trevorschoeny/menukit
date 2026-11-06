package com.trevlar.menukit.inject;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * What the item picker's dialog remembers for the player (8.0.0), in
 * {@code config/menukit/item-picker.json}: whether "Show advanced settings" is ticked,
 * one value for every picker in every mod.
 *
 * <pre>
 * { "show_advanced": true }
 * </pre>
 *
 * Read once at client start, written on each change. A missing or unreadable file is
 * the default (not ticked); none of it crashes, and before {@link #load} (MenuKit's
 * headless checks) the value lives in memory only.
 */
@ApiStatus.Internal
public final class ItemPickerMemory {

    private ItemPickerMemory() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");
    private static final String SHOW_ADVANCED = "show_advanced";

    private static boolean showAdvanced = false;
    private static @Nullable Path file;

    /** The real file: {@code config/menukit/item-picker.json} in the game's config folder. */
    public static Path defaultFile(Path configDir) {
        return configDir.resolve("menukit").resolve("item-picker.json");
    }

    /** Reads {@code path}, and writes there from now on. A file that can't be read leaves the default. */
    public static void load(Path path) {
        file = path;
        showAdvanced = false;
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has(SHOW_ADVANCED)) showAdvanced = root.get(SHOW_ADVANCED).getAsBoolean();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[MenuKit] Couldn't read {} ({}); the item picker starts with advanced settings hidden", path, e.toString());
        }
    }

    /** Whether the player last left "Show advanced settings" ticked. */
    public static boolean showAdvanced() {
        return showAdvanced;
    }

    /** Remembers {@code show}, and writes the file when it changed. */
    public static void setShowAdvanced(boolean show) {
        if (show == showAdvanced) return;
        showAdvanced = show;
        if (file == null) return;
        JsonObject root = new JsonObject();
        root.addProperty(SHOW_ADVANCED, show);
        // Written beside it, then moved over it, so a crash mid-write never leaves half a file.
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, root.toString());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("[MenuKit] Couldn't write {} ({})", file, e.toString());
        }
    }
}
