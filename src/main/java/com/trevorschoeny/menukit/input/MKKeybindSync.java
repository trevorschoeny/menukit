package com.trevorschoeny.menukit.input;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bridges vanilla's Controls screen and mod config persistence. When a user
 * changes a keybind via the vanilla Controls screen (which operates on
 * {@link KeyMapping} instances directly), the new combo needs to be written
 * back to the mod's config file. Without this, keybind changes made in Controls
 * are lost on restart.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Each mod registers sync entries at startup via {@link #register}. An
 *       entry pairs a {@link KeyMapping} with a {@link Consumer} that writes
 *       the combo back to the config and saves the file.</li>
 *   <li>When the vanilla Controls screen closes ({@code KeyBindsScreen.removed()}),
 *       the {@code MKKeyBindsScreenMixin} calls {@link #syncToConfig()}.</li>
 *   <li>{@code syncToConfig()} iterates all registered entries and invokes each
 *       writer with the mapping's current combo (read via the {@link MKKeybindExt}
 *       duck interface).</li>
 * </ol>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public final class MKKeybindSync {

    private MKKeybindSync() {
        // Utility class -- no instances
    }

    // ── Registry ────────────────────────────────────────────────────────────

    /** A single sync entry: a KeyMapping paired with a config writer. */
    private record SyncEntry(KeyMapping mapping, Consumer<MKKeybind> writer) {
    }

    /** All registered sync entries. Populated at mod init, read on screen close. */
    private static final List<SyncEntry> entries = new ArrayList<>();

    /**
     * Registers a KeyMapping for Controls -> config sync.
     *
     * <p>Call this at mod initialization (e.g., in {@code onInitializeClient})
     * after creating the KeyMapping. The writer lambda should update the
     * config field and persist to disk.
     *
     * @param mapping the KeyMapping instance (registered with Fabric)
     * @param writer  a callback that receives the mapping's current combo and
     *                writes it to the config. Responsible for calling save().
     */
    public static void register(KeyMapping mapping, Consumer<MKKeybind> writer) {
        entries.add(new SyncEntry(mapping, writer));
    }

    // ── Sync ────────────────────────────────────────────────────────────────

    /**
     * Writes all registered KeyMapping combos back to their respective configs.
     * Called by {@code MKKeyBindsScreenMixin} when the vanilla Controls screen
     * closes ({@code removed()}).
     *
     * <p>Reads the current combo via the {@link MKKeybindExt} duck interface.
     * Each writer lambda is responsible for both updating the config field
     * and persisting to disk (e.g., calling {@code InventoryPlusConfig.save()}).
     * Writers that share the same save call will write to disk multiple times,
     * but this is harmless -- the Controls screen closes infrequently.
     */
    public static void syncToConfig() {
        for (SyncEntry entry : entries) {
            MKKeybind currentCombo = MKKeybindExt.getCombo(entry.mapping);
            entry.writer.accept(currentCombo);
        }
    }
}
