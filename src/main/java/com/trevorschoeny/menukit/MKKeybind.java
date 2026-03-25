package com.trevorschoeny.menukit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A multi-key keybind descriptor that captures both a key and its modifier state
 * (Ctrl, Shift, Alt, Cmd). Unlike vanilla's {@link net.minecraft.client.KeyMapping},
 * which only tracks a single key, MKKeybind stores the full modifier bitmask so
 * bindings like "Ctrl+K" or "Shift+F5" work correctly.
 *
 * <p>Modifier bits follow the GLFW convention (same as {@link InputConstants}):
 * <ul>
 *   <li>{@code MOD_SHIFT = 1}</li>
 *   <li>{@code MOD_CONTROL = 2}</li>
 *   <li>{@code MOD_ALT = 4}</li>
 *   <li>{@code MOD_SUPER = 8} (Cmd on macOS)</li>
 * </ul>
 *
 * <p>The record is immutable and safe to store in config. Serialize with
 * {@link #serialize()} and restore with {@link #deserialize(String)}.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 *
 * @param keyCode   the GLFW key code (e.g., GLFW_KEY_K = 75), or -1 for unbound
 * @param scanCode  the platform-specific scan code, or 0 if not applicable
 * @param modifiers the GLFW modifier bitmask (Shift|Ctrl|Alt|Super)
 */
public record MKKeybind(int keyCode, int scanCode, int modifiers) {

    // ── Constants ────────────────────────────────────────────────────────────

    /** An unbound keybind — matches nothing. Used as the default for optional bindings. */
    public static final MKKeybind UNBOUND = new MKKeybind(-1, 0, 0);

    // Only check the four modifier bits we care about — ignore CapsLock (16)
    // and NumLock (32) since those are toggle states, not intentional modifiers.
    private static final int MODIFIER_MASK = InputConstants.MOD_SHIFT
            | InputConstants.MOD_CONTROL
            | InputConstants.MOD_ALT
            | InputConstants.MOD_SUPER;

    // ── Display ──────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable display name like "Ctrl+K", "Shift+F5", or "Unbound".
     * On macOS, shows platform-appropriate modifier labels (Cmd instead of Super,
     * Ctrl maps to the physical Control key).
     */
    public Component getDisplayName() {
        if (isUnbound()) {
            return Component.translatable("key.menukit.unbound");
        }

        StringBuilder sb = new StringBuilder();
        int mods = modifiers & MODIFIER_MASK;

        // Build modifier prefix in conventional order: Ctrl/Cmd > Shift > Alt
        // On macOS: Super (Cmd) is the primary modifier, Control is secondary.
        // On other platforms: Control is primary, Super is uncommon.
        if (Util.getPlatform() == Util.OS.OSX) {
            // macOS: Cmd key sends MOD_SUPER, physical Ctrl sends MOD_CONTROL
            if ((mods & InputConstants.MOD_SUPER) != 0) sb.append("Cmd+");
            if ((mods & InputConstants.MOD_CONTROL) != 0) sb.append("Ctrl+");
        } else {
            if ((mods & InputConstants.MOD_CONTROL) != 0) sb.append("Ctrl+");
            if ((mods & InputConstants.MOD_SUPER) != 0) sb.append("Super+");
        }
        if ((mods & InputConstants.MOD_SHIFT) != 0) sb.append("Shift+");
        if ((mods & InputConstants.MOD_ALT) != 0) sb.append("Alt+");

        // Append the base key name from vanilla's translation system.
        // InputConstants handles GLFW -> human-readable (e.g., 75 -> "K").
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        sb.append(key.getDisplayName().getString());

        return Component.literal(sb.toString());
    }

    // ── Matching ─────────────────────────────────────────────────────────────

    /**
     * Returns true if this keybind matches the given key event parameters.
     * Checks both the key code and the modifier bitmask (masked to the four
     * relevant bits). An unbound keybind never matches.
     *
     * @param eventKeyCode   the GLFW key code from the key event
     * @param eventScanCode  the scan code from the key event (unused for KEYSYM matching)
     * @param eventModifiers the GLFW modifier bitmask from the key event
     */
    public boolean matches(int eventKeyCode, int eventScanCode, int eventModifiers) {
        if (isUnbound()) return false;

        // Match the base key — use keyCode (KEYSYM) as primary
        boolean keyMatches = this.keyCode == eventKeyCode;

        // Match modifiers — mask off CapsLock/NumLock bits
        boolean modsMatch = (this.modifiers & MODIFIER_MASK) == (eventModifiers & MODIFIER_MASK);

        return keyMatches && modsMatch;
    }

    /**
     * Returns true if this keybind has no key assigned.
     */
    public boolean isUnbound() {
        return keyCode == -1 || keyCode == InputConstants.UNKNOWN.getValue();
    }

    // ── Serialization ────────────────────────────────────────────────────────
    //
    // Format: "key.keyboard.k:6" where the part before the colon is the
    // InputConstants.Key name and the part after is the modifier bitmask.
    // UNBOUND serializes as "key.keyboard.unknown:0".

    /**
     * Serializes this keybind to a string suitable for JSON config storage.
     * Format: {@code "<key_name>:<modifiers>"} (e.g., "key.keyboard.k:6").
     */
    public String serialize() {
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        return key.getName() + ":" + (modifiers & MODIFIER_MASK);
    }

    /**
     * Deserializes a keybind from the format produced by {@link #serialize()}.
     * Returns {@link #UNBOUND} if the string is null, empty, or malformed.
     */
    public static MKKeybind deserialize(String raw) {
        if (raw == null || raw.isEmpty()) return UNBOUND;

        try {
            int colonIdx = raw.lastIndexOf(':');
            if (colonIdx < 0) {
                // Legacy format or just a key name with no modifiers
                InputConstants.Key key = InputConstants.getKey(raw);
                return new MKKeybind(key.getValue(), 0, 0);
            }

            String keyName = raw.substring(0, colonIdx);
            int mods = Integer.parseInt(raw.substring(colonIdx + 1));
            InputConstants.Key key = InputConstants.getKey(keyName);
            return new MKKeybind(key.getValue(), 0, mods & MODIFIER_MASK);
        } catch (Exception e) {
            // Corrupted config value — return unbound rather than crashing
            MenuKit.LOGGER.warn("[MenuKit] Failed to deserialize keybind '{}': {}", raw, e.getMessage());
            return UNBOUND;
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    /**
     * Strips modifier-only keys (Shift, Ctrl, Alt, Super) from a key code.
     * When the user presses "Ctrl+K", GLFW fires key events for both the
     * Ctrl key and the K key. We only want to capture K as the base key —
     * Ctrl is already captured in the modifier bitmask.
     *
     * @return true if the keyCode is a modifier key (should be ignored as a base key)
     */
    public static boolean isModifierKey(int keyCode) {
        return keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT
                || keyCode == InputConstants.KEY_LCONTROL || keyCode == InputConstants.KEY_RCONTROL
                || keyCode == InputConstants.KEY_LALT || keyCode == InputConstants.KEY_RALT
                || keyCode == InputConstants.KEY_LSUPER || keyCode == InputConstants.KEY_RSUPER;
    }
}
