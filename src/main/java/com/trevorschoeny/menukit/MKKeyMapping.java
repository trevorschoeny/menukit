package com.trevorschoeny.menukit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;

/**
 * A multi-key extension of vanilla's {@link KeyMapping} that also checks
 * modifier state (Ctrl, Shift, Alt, Cmd) before reporting as "down".
 *
 * <p><b>Why this exists:</b> Vanilla's {@code KeyMapping} tracks a single
 * {@link InputConstants.Key} and ignores modifiers entirely. When vanilla
 * calls {@code KeyMapping.set(key, true)}, it marks the mapping as down
 * purely based on the key code match. This means a binding to "R" fires
 * even if the user is pressing "Ctrl+R" (intending a different action).
 *
 * <p>{@code MKKeyMapping} fixes this by overriding {@link #setDown(boolean)}
 * to also check whether the required modifiers are currently held. If the
 * key is pressed but the modifiers don't match, the mapping stays "not down".
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // Register a Ctrl+R keybind (requires Ctrl held)
 * MKKeyMapping sortKey = new MKKeyMapping(
 *     "key.mymod.sort",
 *     GLFW.GLFW_KEY_R,
 *     InputConstants.MOD_CONTROL,
 *     family.getKeybindCategory()
 * );
 * KeyBindingHelper.registerKeyBinding(sortKey);
 *
 * // Or create from an MKKeybind (e.g., from YACL config)
 * MKKeyMapping fromConfig = MKKeyMapping.fromKeybind(
 *     config.sortKeybind, "key.mymod.sort", family.getKeybindCategory()
 * );
 * }</pre>
 *
 * <p><b>Integration with MKKeybind:</b> The YACL config screen uses
 * {@link MKKeybindController} to let users configure {@link MKKeybind} values.
 * When the config changes, call {@link #updateFromKeybind(MKKeybind)} to
 * sync the runtime key mapping without re-registering.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKKeyMapping extends KeyMapping {

    // The GLFW modifier bitmask that must be held for this mapping to fire.
    // 0 means no modifiers required (behaves like vanilla KeyMapping).
    private int requiredModifiers;

    // ── Constructors ─────────────────────────────────────────────────────────

    /**
     * Creates a multi-key mapping with the given key and required modifiers.
     *
     * @param name      the translation key for this binding (e.g., "key.mymod.sort")
     * @param keyCode   the GLFW key code (e.g., GLFW_KEY_R)
     * @param modifiers the required GLFW modifier bitmask (e.g., MOD_CONTROL | MOD_SHIFT)
     * @param category  the keybind category (shown in Controls screen)
     */
    public MKKeyMapping(String name, int keyCode, int modifiers, KeyMapping.Category category) {
        super(name, keyCode, category);
        this.requiredModifiers = modifiers & MODIFIER_MASK;
    }

    /**
     * Creates a multi-key mapping with no required modifiers (behaves like vanilla).
     * Useful as a starting point when the actual binding comes from config later.
     */
    public MKKeyMapping(String name, int keyCode, KeyMapping.Category category) {
        this(name, keyCode, 0, category);
    }

    // ── Modifier Checking ────────────────────────────────────────────────────

    // Same mask as MKKeybind — only check Shift/Ctrl/Alt/Super bits
    private static final int MODIFIER_MASK = InputConstants.MOD_SHIFT
            | InputConstants.MOD_CONTROL
            | InputConstants.MOD_ALT
            | InputConstants.MOD_SUPER;

    /**
     * Overrides vanilla's setDown to also check modifier state.
     *
     * <p>Vanilla calls {@code KeyMapping.set(key, true/false)} from the GLFW
     * key callback. When the key matches, vanilla calls {@code setDown(true)}.
     * We intercept this: if the key is being pressed (isDown=true) but the
     * required modifiers aren't held, we force isDown to false.
     *
     * <p>When isDown=false (key released), we always pass through — releasing
     * a key should always clear the mapping regardless of modifier state.
     */
    @Override
    public void setDown(boolean isDown) {
        if (isDown && requiredModifiers != 0) {
            // Check if the required modifiers are currently held by querying
            // GLFW directly. We can't rely on the event's modifier parameter
            // because setDown() is called without modifier context.
            if (!areModifiersHeld()) {
                // Key is pressed but modifiers don't match — suppress
                super.setDown(false);
                return;
            }
        }
        super.setDown(isDown);
    }

    /**
     * Checks whether the required modifier keys are currently held down
     * by querying GLFW key state directly. This is the only reliable way
     * to check modifiers from within setDown(), which doesn't receive
     * the modifier bitmask as a parameter.
     *
     * <p>Each modifier bit is checked against the corresponding physical
     * key(s). For example, MOD_CONTROL checks both left and right Ctrl.
     */
    private boolean areModifiersHeld() {
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        int required = requiredModifiers & MODIFIER_MASK;

        // Check each required modifier against GLFW key state.
        // A modifier is "held" if either the left or right variant is pressed.
        if ((required & InputConstants.MOD_SHIFT) != 0) {
            if (!isKeyDown(windowHandle, InputConstants.KEY_LSHIFT)
                    && !isKeyDown(windowHandle, InputConstants.KEY_RSHIFT)) {
                return false;
            }
        }
        if ((required & InputConstants.MOD_CONTROL) != 0) {
            if (!isKeyDown(windowHandle, InputConstants.KEY_LCONTROL)
                    && !isKeyDown(windowHandle, InputConstants.KEY_RCONTROL)) {
                return false;
            }
        }
        if ((required & InputConstants.MOD_ALT) != 0) {
            if (!isKeyDown(windowHandle, InputConstants.KEY_LALT)
                    && !isKeyDown(windowHandle, InputConstants.KEY_RALT)) {
                return false;
            }
        }
        if ((required & InputConstants.MOD_SUPER) != 0) {
            if (!isKeyDown(windowHandle, InputConstants.KEY_LSUPER)
                    && !isKeyDown(windowHandle, InputConstants.KEY_RSUPER)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Queries GLFW for whether a specific key is currently pressed.
     * Wraps {@link org.lwjgl.glfw.GLFW#glfwGetKey} for readability.
     */
    private static boolean isKeyDown(long windowHandle, int glfwKeyCode) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, glfwKeyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    // ── MKKeybind Integration ────────────────────────────────────────────────

    /**
     * Creates an MKKeyMapping from an {@link MKKeybind} value (typically from
     * YACL config). This bridges the config layer (MKKeybind records) and the
     * runtime layer (KeyMapping instances registered with Fabric).
     *
     * <p>If the keybind is unbound, the mapping is created with
     * {@link InputConstants#UNKNOWN} as the key.
     *
     * @param keybind  the keybind from config
     * @param name     the translation key for this binding
     * @param category the keybind category
     * @return a new MKKeyMapping ready for registration
     */
    public static MKKeyMapping fromKeybind(MKKeybind keybind, String name, KeyMapping.Category category) {
        if (keybind == null || keybind.isUnbound()) {
            // Unbound: register with UNKNOWN key and no modifiers.
            // The mapping will exist in Controls but show as unbound.
            return new MKKeyMapping(name, InputConstants.UNKNOWN.getValue(), 0, category);
        }
        return new MKKeyMapping(name, keybind.keyCode(), keybind.modifiers(), category);
    }

    /**
     * Updates this mapping's key and modifiers from an {@link MKKeybind}.
     * Call this when the user changes the keybind in the YACL config screen
     * to sync the runtime mapping without re-registering.
     *
     * <p>Also calls {@link KeyMapping#resetMapping()} to update vanilla's
     * internal key-to-mapping lookup table so the new key is recognized.
     */
    public void updateFromKeybind(MKKeybind keybind) {
        if (keybind == null || keybind.isUnbound()) {
            setKey(InputConstants.UNKNOWN);
            this.requiredModifiers = 0;
        } else {
            // Update the base key through vanilla's API
            setKey(InputConstants.Type.KEYSYM.getOrCreate(keybind.keyCode()));
            this.requiredModifiers = keybind.modifiers() & MODIFIER_MASK;
        }

        // Rebuild vanilla's key -> mapping lookup so the new key is recognized.
        // This is the same thing vanilla does when the user changes a keybind
        // in the Controls screen.
        KeyMapping.resetMapping();
    }

    /**
     * Returns the current binding as an {@link MKKeybind} record.
     * Useful for syncing back to config after the user changes the binding
     * through vanilla's Controls screen.
     */
    public MKKeybind toKeybind() {
        if (isUnbound()) {
            return MKKeybind.UNBOUND;
        }
        return new MKKeybind(this.key.getValue(), 0, requiredModifiers);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns the required modifier bitmask for this mapping.
     */
    public int getRequiredModifiers() {
        return requiredModifiers;
    }
}
