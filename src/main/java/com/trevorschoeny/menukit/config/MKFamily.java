package com.trevorschoeny.menukit.config;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.panel.MKPanel;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
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
 * <p><b>General options</b> are family-wide settings that any mod can register.
 * Deduplicated by key — first registration wins. Each option is described by
 * a {@link GeneralOption} record that carries the key, default value, and
 * expected Java type. Values are stored in a MenuKit-owned file
 * ({@code config/menukit-family-{id}.json}), loaded lazily on first access.
 *
 * <p><b>Shared panels</b> are UI panels that any mod can register. Deduplicated
 * by panel name — if the panel already exists, the registrar is skipped.
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

    // General config — family-wide settings, deduplicated by key
    private final Map<String, Option<?>> generalOptions = new LinkedHashMap<>();
    private MKFamilyConfig generalConfig;

    // Lazily created keybind category — shared by all family members
    private KeyMapping.Category keybindCategory;

    public MKFamily(String id) {
        this.id = id;
    }

    // ── Builder-style setters (return this for chaining) ─────────────────

    /**
     * Sets the display name for this family. Shown in ModMenu and used
     * as the keybind category name in Controls.
     */
    public MKFamily displayName(String name) {
        // Warn if being overwritten with a different value — helps catch
        // accidental mismatches between mods in the same family
        if (this.displayName != null && !this.displayName.equals(name)) {
            MenuKit.LOGGER.warn("[MenuKit] Family '{}': displayName being overwritten from '{}' to '{}'",
                    id, this.displayName, name);
        }
        this.displayName = name;
        return this;
    }

    /**
     * Sets the shared description for this family. Shown in ModMenu's
     * mod list when the family is selected.
     */
    public MKFamily description(String desc) {
        // Warn if being overwritten with a different value — helps catch
        // accidental mismatches between mods in the same family
        if (this.description != null && !this.description.equals(desc)) {
            MenuKit.LOGGER.warn("[MenuKit] Family '{}': description being overwritten from '{}' to '{}'",
                    id, this.description, desc);
        }
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

    // ── General Options (family-wide, deduplicated) ─────────────────────

    /**
     * Registers a general (family-wide) config option. Deduplicated by key —
     * if an option with this key is already registered, this call is silently
     * ignored. Values are stored in MenuKit's family config file, not in
     * any individual mod's config.
     *
     * <p>Multiple mods in the family should register the same general options
     * so that the options appear regardless of which mods are installed.
     *
     * <p>Example:
     * <pre>{@code
     * static final GeneralOption<Boolean> SHOW_BUTTON =
     *     new GeneralOption<>("show_button", true, Boolean.class);
     *
     * family.generalOption(SHOW_BUTTON,
     *     Option.<Boolean>createBuilder()
     *         .name(Component.literal("Show Settings Button"))
     *         .binding(true,
     *             () -> family.getGeneral(SHOW_BUTTON),
     *             val -> family.setGeneral(SHOW_BUTTON, val))
     *         .controller(TickBoxControllerBuilder::create)
     *         .build());
     * }</pre>
     *
     * @param option typed descriptor containing key, default, and type info
     * @param uiOption the YACL Option to display in the General tab
     */
    public <T> MKFamily generalOption(GeneralOption<T> option, Option<T> uiOption) {
        // First-writer-wins: if already registered, skip
        if (!generalOptions.containsKey(option.key())) {
            generalOptions.put(option.key(), uiOption);
            MenuKit.LOGGER.debug("[MenuKit] Family '{}' registered general option '{}'", id, option.key());
        }
        return this;
    }

    /**
     * Reads a typed general option value. The {@link GeneralOption} descriptor
     * carries the key, default value, and expected type — GSON number
     * normalization is handled automatically.
     *
     * @param option typed descriptor for the option to read
     * @return the stored value, or the option's default if not present
     */
    public <T> T getGeneral(GeneralOption<T> option) {
        return getConfig().get(option);
    }

    /**
     * Sets a typed general option value in memory. Persisted when the user
     * clicks Save in the config screen (or via {@link #saveGeneralConfig()}).
     *
     * @param option typed descriptor for the option to write
     * @param value  the new value (must match the option's type)
     */
    public <T> void setGeneral(GeneralOption<T> option, T value) {
        getConfig().set(option, value);
    }

    /** Saves the general config to disk. Called by the composite save handler. */
    public void saveGeneralConfig() {
        if (generalConfig != null) {
            generalConfig.save();
        }
    }

    /** Loads the general config from disk. Safe to call multiple times. */
    public void loadGeneralConfig() {
        getConfig().load();
    }

    // ── Shared Panels (deduplicated by panel name) ──────────────────────

    /**
     * Registers a shared panel that multiple mods in the family can provide.
     * If a panel with this name already exists (registered by another mod),
     * the registrar is never called — deduplication by panel name.
     *
     * <p>This allows multiple mods to each register the same UI element
     * (e.g., a settings button) without worrying about duplicates.
     *
     * <p>Example:
     * <pre>{@code
     * family.sharedPanel("trevmods_settings", () -> {
     *     MKPanel.builder("trevmods_settings")
     *         .showIn(MKContext.PERSONAL)
     *         .posAboveRight()
     *         .autoSize()
     *         .column()
     *             .button().label("⚙").onClick(btn -> ...).done()
     *         .build();
     * });
     * }</pre>
     *
     * @param panelName  the panel name (must match the name in MKPanel.builder())
     * @param registrar  a Runnable that registers the panel via MKPanel.builder()
     */
    public MKFamily sharedPanel(String panelName, Runnable registrar) {
        if (!MenuKit.hasPanel(panelName)) {
            registrar.run();
            MenuKit.LOGGER.debug("[MenuKit] Family '{}' registered shared panel '{}'", id, panelName);
        } else {
            MenuKit.LOGGER.debug("[MenuKit] Family '{}' skipped shared panel '{}' (already registered)",
                    id, panelName);
        }
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

    /** Whether this family has any general options registered. */
    public boolean hasGeneralOptions() {
        return !generalOptions.isEmpty();
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
     * <p>If general options are registered, a "General" tab is inserted
     * as the first category.
     *
     * @param parent      the screen to return to when the config screen closes
     * @param focusModId  if non-null, the screen will auto-select this mod's
     *                    config category tab after opening
     * @return the assembled config screen, or null if no categories are registered
     */
    public Screen buildConfigScreen(Screen parent, String focusModId) {
        boolean hasGeneral = hasGeneralOptions();
        if (configCategories.isEmpty() && !hasGeneral) return null;

        var builder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal(displayName()));

        // Track total tab count for focus index calculation
        int tabOffset = 0;

        // Insert "General" tab first if there are general options
        if (hasGeneral) {
            var generalCategory = ConfigCategory.createBuilder()
                    .name(Component.literal(displayName()))
                    .tooltip(Component.literal("Settings shared across all " + displayName() + " mods"));

            // Add options directly to the category (no group wrapper)
            for (Option<?> option : generalOptions.values()) {
                generalCategory.option(option);
            }

            builder.category(generalCategory.build());
            tabOffset = 1;
        }

        // Add each mod's config category, track which index to focus
        int focusIndex = -1;
        for (int i = 0; i < configCategories.size(); i++) {
            ConfigEntry entry = configCategories.get(i);
            builder.category(entry.builder().get());
            if (focusModId != null && focusModId.equals(entry.modId())) {
                focusIndex = tabOffset + i;
            }
        }

        // Composite save: save general config + call every mod's save callback
        builder.save(() -> {
            // Save family-wide general config
            saveGeneralConfig();

            // Save each mod's config
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

    /** Lazily creates and loads the general config. */
    private MKFamilyConfig getConfig() {
        if (generalConfig == null) {
            generalConfig = new MKFamilyConfig(id);
            generalConfig.load();
        }
        return generalConfig;
    }

    /**
     * A config category contribution from one mod in the family.
     *
     * @param name    display name for the tab
     * @param builder creates the YACL ConfigCategory (called per screen open)
     * @param onSave  persists config + applies runtime changes on save
     */
    record ConfigEntry(String name, String modId, Supplier<ConfigCategory> builder, Runnable onSave) {}
}
