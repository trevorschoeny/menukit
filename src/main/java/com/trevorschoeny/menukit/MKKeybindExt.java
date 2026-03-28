package com.trevorschoeny.menukit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Duck interface mixed into vanilla's {@link KeyMapping} via mixin to add
 * multi-key combo support. ANY KeyMapping (vanilla or mod) can have a
 * multi-key combo (up to 3 keys) through this interface.
 *
 * <p>Instead of subclassing KeyMapping (the old {@code MKKeyMapping} approach),
 * this interface is injected into KeyMapping itself, making multi-key combos
 * universally available across the entire keybind system.
 *
 * <p>Cast any KeyMapping to this interface to access the combo:
 * <pre>
 *   MKKeybind combo = ((MKKeybindExt) someKeyMapping).menuKit$getCombo();
 * </pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public interface MKKeybindExt {

    // ── Combo Access ─────────────────────────────────────────────────────────

    /**
     * Returns the multi-key combo for this KeyMapping, or null if no combo
     * has been set (single-key binding using vanilla's default behavior).
     */
    MKKeybind menuKit$getCombo();

    /**
     * Sets the multi-key combo for this KeyMapping. Pass null to clear the
     * combo and revert to vanilla single-key behavior.
     */
    void menuKit$setCombo(MKKeybind combo);

    // ── Convenience Methods ──────────────────────────────────────────────────
    //
    // These are static helpers that operate on any KeyMapping through the
    // duck interface. They replace instance methods from the old MKKeyMapping.

    /**
     * Returns the current binding as an {@link MKKeybind} for the given mapping.
     * If the mapping has a multi-key combo, returns that. Otherwise, wraps
     * the mapping's single key as a single-key MKKeybind.
     *
     * @param mapping the KeyMapping to query
     * @return the effective MKKeybind, or {@link MKKeybind#UNBOUND} if unbound
     */
    static MKKeybind getCombo(KeyMapping mapping) {
        MKKeybind combo = ((MKKeybindExt) mapping).menuKit$getCombo();
        if (combo != null && !combo.isUnbound()) {
            return combo;
        }
        // No multi-key combo set -- wrap the vanilla single key
        if (mapping.isUnbound()) {
            return MKKeybind.UNBOUND;
        }
        int keyCode = getKeyCode(mapping);
        if (keyCode == InputConstants.UNKNOWN.getValue()) {
            return MKKeybind.UNBOUND;
        }
        return MKKeybind.ofKey(keyCode);
    }

    /**
     * Updates a KeyMapping's combo from an {@link MKKeybind}. Sets both the
     * combo (via duck interface) and the vanilla base key (for vanilla's
     * internal key-to-mapping lookup). Calls {@link KeyMapping#resetMapping()}
     * to rebuild vanilla's lookup table.
     *
     * @param mapping the KeyMapping to update
     * @param keybind the new keybind value
     */
    static void updateFromKeybind(KeyMapping mapping, MKKeybind keybind) {
        if (keybind == null || keybind.isUnbound()) {
            mapping.setKey(InputConstants.UNKNOWN);
            ((MKKeybindExt) mapping).menuKit$setCombo(MKKeybind.UNBOUND);
        } else {
            // Set vanilla's base key to the primary non-modifier key
            int baseKey = keybind.keyCode();
            if (baseKey == -1) {
                mapping.setKey(InputConstants.UNKNOWN);
            } else {
                mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(baseKey));
            }
            // Store the full multi-key combo
            ((MKKeybindExt) mapping).menuKit$setCombo(keybind);
        }
        KeyMapping.resetMapping();
    }

    /**
     * Creates a vanilla {@link KeyMapping} with a multi-key combo applied.
     * Replaces the old {@code MKKeyMapping.fromKeybind()} factory.
     *
     * @param keybind  the keybind from config
     * @param name     the translation key for this binding
     * @param category the keybind category
     * @return a new KeyMapping with the combo set via duck interface
     */
    static KeyMapping fromKeybind(MKKeybind keybind, String name, KeyMapping.Category category) {
        int baseKey;
        if (keybind == null || keybind.isUnbound()) {
            baseKey = InputConstants.UNKNOWN.getValue();
        } else {
            baseKey = keybind.keyCode();
            if (baseKey == -1) baseKey = InputConstants.UNKNOWN.getValue();
        }

        KeyMapping mapping = new KeyMapping(name, baseKey, category);

        // Apply the full multi-key combo via duck interface
        if (keybind != null && !keybind.isUnbound()) {
            ((MKKeybindExt) mapping).menuKit$setCombo(keybind);
        }

        return mapping;
    }

    /**
     * Checks whether a KEY_PRESS event matches a keybind on the given mapping.
     *
     * <p>When a container screen is open, vanilla does NOT call
     * {@code KeyMapping.set(key, true)} from the GLFW key callback --
     * key events go through {@code Screen.keyPressed()} instead. This means
     * {@code isDown()} is unreliable for in-screen keybinds. Use this method
     * for handlers that respond to discrete key events.
     *
     * <p>Uses GLFW polling via {@link MKKeybind#matchesKeyEvent} to verify
     * that ALL keys in the combo are held, not just the event key.
     *
     * @param mapping        the KeyMapping to check against
     * @param eventKeyCode   the GLFW key code from the event
     * @param eventModifiers the GLFW modifier bitmask (unused -- GLFW polling replaces it)
     * @return true if this mapping's combo matches the event
     */
    static boolean matchesEvent(KeyMapping mapping, int eventKeyCode, int eventModifiers) {
        MKKeybind combo = getCombo(mapping);
        if (combo.isUnbound()) return false;

        InputConstants.Key eventKey = InputConstants.Type.KEYSYM.getOrCreate(eventKeyCode);
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        return combo.matchesKeyEvent(eventKey, windowHandle);
    }

    /**
     * Returns the GLFW key code of the given KeyMapping's current key binding.
     * Works around the fact that {@code KeyMapping.key} is a {@code protected}
     * field with no public getter in vanilla MC 1.21.11.
     *
     * @param mapping the KeyMapping to query
     * @return the GLFW key code, or {@link InputConstants#UNKNOWN} value if unbound
     */
    static int getKeyCode(KeyMapping mapping) {
        try {
            return InputConstants.getKey(mapping.saveString()).getValue();
        } catch (Exception e) {
            return InputConstants.UNKNOWN.getValue();
        }
    }
}
