package com.trevorschoeny.menukit.config;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A Family groups multiple mods under a shared identity — one shared keybind
 * category in vanilla's Controls screen, one canonical handle. Mods join a
 * family by calling {@link MenuKit#family(String)} with the same ID; each
 * call returns the same {@code MKFamily} instance.
 *
 * <p><b>Scope — Layer A only.</b> Per Phase 12.5 DESIGN.md §11 (M3 scope-down),
 * MenuKit owns identity grouping + keybind-category sharing. Config-screen
 * aggregation, YACL integration, ModMenu mediation, and cross-mod persistent
 * storage (Layer B) are <i>not</i> MenuKit's responsibility — consumers own
 * their own config UI and persistence.
 *
 * <p>What a family provides:
 * <ul>
 *   <li><b>Canonical instance.</b> {@code MenuKit.family("id")} returns the
 *       same {@code MKFamily} across every caller in the current JVM.</li>
 *   <li><b>Shared metadata.</b> Mods contribute a display name and description
 *       (last-writer-wins with a warn-log on mismatches — helps catch
 *       accidental divergence between family members).</li>
 *   <li><b>Mod-id roster.</b> Each family tracks which mod IDs have joined.</li>
 *   <li><b>Shared keybind category.</b> Lazily created from the family ID;
 *       all mods in the family register their keybinds under the same section
 *       in vanilla's Controls screen.</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKFamily {

    private final String id;
    private String displayName;
    private String description;
    private final Set<String> modIds = new LinkedHashSet<>();

    // Lazily created keybind category — shared by all family members.
    private KeyMapping.Category keybindCategory;

    public MKFamily(String id) {
        this.id = id;
    }

    // ── Builder-style setters (return this for chaining) ─────────────────

    /**
     * Sets the display name for this family. Used as the keybind category
     * label in vanilla's Controls screen. Also available to consumers that
     * want to title their own config screens consistently.
     */
    public MKFamily displayName(String name) {
        // Warn if being overwritten with a different value — helps catch
        // accidental mismatches between mods in the same family.
        if (this.displayName != null && !this.displayName.equals(name)) {
            MenuKit.LOGGER.warn("[MenuKit] Family '{}': displayName being overwritten from '{}' to '{}'",
                    id, this.displayName, name);
        }
        this.displayName = name;
        return this;
    }

    /**
     * Sets the shared description for this family. Available to consumers
     * that want to show a consistent description across their own config
     * UIs; not used by MenuKit itself post-M3 scope-down.
     */
    public MKFamily description(String desc) {
        // Warn if being overwritten with a different value — helps catch
        // accidental mismatches between mods in the same family.
        if (this.description != null && !this.description.equals(desc)) {
            MenuKit.LOGGER.warn("[MenuKit] Family '{}': description being overwritten from '{}' to '{}'",
                    id, this.description, desc);
        }
        this.description = desc;
        return this;
    }

    /**
     * Registers a mod ID as a member of this family. The roster is available
     * to consumers (e.g., for optional cross-mod feature detection) via
     * {@link #getRegisteredModIds()}.
     */
    public MKFamily modId(String modId) {
        this.modIds.add(modId);
        return this;
    }

    // ── Queries ──────────────────────────────────────────────────────────

    public String id() { return id; }

    /** Returns all mod IDs registered in this family. */
    public Set<String> getRegisteredModIds() {
        return Collections.unmodifiableSet(modIds);
    }

    public String displayName() {
        return displayName != null ? displayName : id;
    }

    public String description() {
        return description != null ? description : "";
    }

    // ── Keybind category ─────────────────────────────────────────────────

    /**
     * Returns the shared keybind category for this family. Created lazily on
     * first access. All mods in the family should call this instead of
     * registering their own category — the shared category gives users one
     * section per family in the Controls screen rather than one per mod.
     */
    public KeyMapping.Category getKeybindCategory() {
        if (keybindCategory == null) {
            // Register under the family ID namespace with the display name as the label.
            keybindCategory = KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(id, id));
        }
        return keybindCategory;
    }
}
