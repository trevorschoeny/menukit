package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Global registry of per-panel element visibility state.
 *
 * <p>Each panel with element-level visibility overrides gets an
 * {@link MKPanelState} instance, lazily created on first access.
 * Cleaned up on screen init to prevent stale state from persisting
 * across screen reopens.
 *
 * <p>Part of the <b>MenuKit</b> conditional visibility system.
 */
public class MKPanelStateRegistry {

    private static final Map<String, MKPanelState> states = new HashMap<>();

    /** Gets or creates the panel state for the given panel name. */
    public static MKPanelState getOrCreate(String panelName) {
        return states.computeIfAbsent(panelName, k -> new MKPanelState());
    }

    /** Gets the panel state for the given panel name, or null if none exists. */
    public static @Nullable MKPanelState get(String panelName) {
        return states.get(panelName);
    }

    /** Clears all panel states. Called on screen init to reset stale state. */
    public static void cleanup() {
        states.clear();
    }
}
