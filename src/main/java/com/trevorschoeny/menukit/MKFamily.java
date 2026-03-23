package com.trevorschoeny.menukit;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Supplier;

/**
 * A Family groups multiple mods under a shared identity — one config screen,
 * one keybind category, one ModMenu entry. Mods join a family by calling
 * {@link MenuKit#family(String)} with the same ID.
 *
 * <p>Each call to {@code MenuKit.family("id")} returns the same MKFamily
 * instance. Display name and description are set by whichever mod calls
 * them — last-writer-wins is fine since it's the developer's responsibility
 * to keep their family consistent.
 *
 * <p>Config categories accumulate additively: each mod contributes its own
 * category via {@link #configCategory}. When ModMenu opens the config
 * screen, {@link #buildConfigScreen} assembles them all into one YACL screen.
 *
 * <p>The keybind category is derived from the display name. All mods in
 * the family share the same section in Controls.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKFamily {

    private final String id;
    private String displayName;
    private String description;
    private final List<ConfigEntry> configCategories = new ArrayList<>();
    private final Set<String> modIds = new LinkedHashSet<>();

    // Lazily created keybind category — shared by all family members
    private KeyMapping.Category keybindCategory;

    MKFamily(String id) {
        this.id = id;
    }

    // ── Builder-style setters (return this for chaining) ─────────────────

    /**
     * Sets the display name for this family. Shown in ModMenu and used
     * as the keybind category name in Controls.
     */
    public MKFamily displayName(String name) {
        this.displayName = name;
        return this;
    }

    /**
     * Sets the shared description for this family. Shown in ModMenu's
     * mod list when the family is selected.
     */
    public MKFamily description(String desc) {
        this.description = desc;
        return this;
    }

    /**
     * Registers a config category for this family. Each mod contributes
     * its own category — they're assembled into one screen by
     * {@link #buildConfigScreen}.
     *
     * @param name    the category tab name (e.g., "Inventory Plus")
     * @param builder supplier that creates the YACL ConfigCategory (called
     *                each time the config screen opens, so it reads fresh values)
     * @param onSave  called when the user saves the config screen — persist
     *                your config and apply any runtime changes here
     */
    public MKFamily configCategory(String name,
                                    Supplier<ConfigCategory> builder,
                                    Runnable onSave) {
        configCategories.add(new ConfigEntry(name, null, builder, onSave));
        return this;
    }

    /**
     * Registers a config category for this family, associated with a mod ID.
     * When the user opens config from this mod's ModMenu entry, the screen
     * will auto-select this category's tab.
     *
     * @param modId   the mod's fabric.mod.json ID (e.g., "inventory-plus")
     * @param name    the category tab name
     * @param builder supplier that creates the YACL ConfigCategory
     * @param onSave  called when the user saves
     */
    public MKFamily configCategory(String modId, String name,
                                    Supplier<ConfigCategory> builder,
                                    Runnable onSave) {
        configCategories.add(new ConfigEntry(name, modId, builder, onSave));
        return this;
    }

    /**
     * Registers a mod ID as a member of this family. ModMenu will show
     * a config button for this mod that opens the family's shared screen.
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
     * Returns the shared keybind category for this family. Created lazily
     * from the display name. All mods in the family should use this
     * instead of creating their own category.
     */
    public KeyMapping.Category getKeybindCategory() {
        if (keybindCategory == null) {
            // Register under the family ID namespace with the display name as the label
            keybindCategory = KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(id, id));
        }
        return keybindCategory;
    }

    // ── Config screen ────────────────────────────────────────────────────

    /**
     * Builds the unified YACL config screen from all registered categories.
     * Called by MenuKit's ModMenu integration when the user clicks the
     * config button for any mod in this family.
     *
     * @param parent the screen to return to when the config screen closes
     * @return the assembled config screen, or null if no categories are registered
     */
    public Screen buildConfigScreen(Screen parent) {
        return buildConfigScreen(parent, null);
    }

    /**
     * Builds the unified YACL config screen, optionally focused on the
     * category associated with the given mod ID.
     *
     * @param parent      the screen to return to when the config screen closes
     * @param focusModId  if non-null, the screen will auto-select this mod's
     *                    config category tab after opening
     * @return the assembled config screen, or null if no categories are registered
     */
    public Screen buildConfigScreen(Screen parent, String focusModId) {
        if (configCategories.isEmpty()) return null;

        var builder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal(displayName()));

        // Add each mod's config category, track which index to focus
        int focusIndex = -1;
        for (int i = 0; i < configCategories.size(); i++) {
            ConfigEntry entry = configCategories.get(i);
            builder.category(entry.builder().get());
            if (focusModId != null && focusModId.equals(entry.modId())) {
                focusIndex = i;
            }
        }

        // Composite save: call every mod's save callback
        builder.save(() -> {
            for (ConfigEntry entry : configCategories) {
                if (entry.onSave() != null) {
                    entry.onSave().run();
                }
            }
        });

        Screen screen = builder.build().generateScreen(parent);

        // Auto-select the right tab after the screen initializes.
        // init() creates the tabs, so we schedule selection for next frame.
        if (focusIndex >= 0 && screen instanceof YACLScreen yaclScreen) {
            final int idx = focusIndex;
            Minecraft.getInstance().execute(() -> {
                var navBar = yaclScreen.tabNavigationBar;
                if (navBar != null) {
                    var tabs = navBar.getTabs();
                    if (idx < tabs.size()) {
                        yaclScreen.tabManager.setCurrentTab(tabs.get(idx), true);
                    }
                }
            });
        }

        return screen;
    }

    // ── Internal ─────────────────────────────────────────────────────────

    /**
     * A config category contribution from one mod in the family.
     *
     * @param name    display name for the tab
     * @param builder creates the YACL ConfigCategory (called per screen open)
     * @param onSave  persists config + applies runtime changes on save
     */
    record ConfigEntry(String name, String modId, Supplier<ConfigCategory> builder, Runnable onSave) {}
}
