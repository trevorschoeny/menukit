package com.trevorschoeny.menukit.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.trevorschoeny.menukit.MKFamily;
import com.trevorschoeny.menukit.MenuKit;

import java.util.HashMap;
import java.util.Map;

/**
 * ModMenu integration — provides config screens for all mods registered
 * in MenuKit families.
 *
 * <p>Uses {@code getProvidedConfigScreenFactories()} to provide a config
 * button for each mod in each family. Clicking the button opens the
 * family's unified YACL config screen.
 *
 * <p>MenuKit itself has no config screen (it's a library).
 */
public class ModMenuIntegration implements ModMenuApi {

    // No override of getModConfigScreenFactory() — MenuKit has no config
    // of its own. The default returns an empty factory, so ModMenu won't
    // show a config button for the menukit mod entry.

    /**
     * Provide config screens for all mods registered in MenuKit families.
     * Each mod's config button opens its family's unified screen.
     */
    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new HashMap<>();

        for (MKFamily family : MenuKit.getFamilies()) {
            // Each mod gets a factory that opens the family screen
            // focused on its own config tab
            for (String modId : family.getRegisteredModIds()) {
                final String id = modId;
                factories.put(id, parent -> family.buildConfigScreen(parent, id));
            }
        }

        return factories;
    }
}
