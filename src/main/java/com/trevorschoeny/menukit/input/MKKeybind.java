package com.trevorschoeny.menukit.input;

import com.trevorschoeny.menukit.MenuKit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A key-agnostic multi-key keybind descriptor. ANY combination of simultaneously
 * held keys and/or mouse buttons forms a binding (up to {@link #MAX_COMBO} keys).
 *
 * <p>Unlike the V1 implementation (which stored a base key + modifier bitmask),
 * V2 stores an arbitrary {@link SortedSet} of {@link InputConstants.Key} values.
 * This means bindings like "G+H", "Mouse4+Shift", or even "A+B+C" are fully
 * supported -- there is no distinction between "modifier" and "base" keys at the
 * data level.
 *
 * <p><b>L/R normalization:</b> Left and right variants of Shift, Ctrl, Alt, and
 * Super are normalized to the LEFT variant at construction time. This means
 * LShift+I and RShift+I are the same binding.
 *
 * <p><b>Serialization:</b> The new format is {@code "key.keyboard.left.shift+key.keyboard.i"}
 * (keys joined by "+"). V1 format ({@code "key.keyboard.k:6"}) is still accepted
 * by {@link #deserialize(String)} for backwards compatibility.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public final class MKKeybind {

    // ── Constants ────────────────────────────────────────────────────────────

    /** An unbound keybind -- matches nothing. Used as the default for optional bindings. */
    public static final MKKeybind UNBOUND = new MKKeybind(Set.of());

    /** Maximum number of keys in a combo. Enforced at construction time. */
    public static final int MAX_COMBO = 3;

    // Only check these four modifier bits when doing V1 backwards compat
    private static final int MODIFIER_MASK = InputConstants.MOD_SHIFT
            | InputConstants.MOD_CONTROL
            | InputConstants.MOD_ALT
            | InputConstants.MOD_SUPER;

    // ── Fields ───────────────────────────────────────────────────────────────

    // Sorted by key name for deterministic ordering in serialization/display.
    // Using TreeSet with comparator on key name string for stable ordering.
    private final SortedSet<InputConstants.Key> keys;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a multi-key keybind from a set of keys. Normalizes L/R modifier
     * variants to the LEFT side and caps the combo at {@link #MAX_COMBO} keys.
     *
     * @param rawKeys the keys in this combo (may include L/R variants -- they
     *                will be normalized)
     */
    public MKKeybind(Set<InputConstants.Key> rawKeys) {
        // Normalize L/R modifiers and deduplicate
        TreeSet<InputConstants.Key> normalized = new TreeSet<>(
                Comparator.comparing(InputConstants.Key::getName));
        for (InputConstants.Key key : rawKeys) {
            // Skip UNKNOWN keys
            if (key.equals(InputConstants.UNKNOWN)) continue;
            normalized.add(normalizeKey(key));
        }

        // Cap at MAX_COMBO
        if (normalized.size() > MAX_COMBO) {
            TreeSet<InputConstants.Key> capped = new TreeSet<>(
                    Comparator.comparing(InputConstants.Key::getName));
            int count = 0;
            for (InputConstants.Key k : normalized) {
                if (count >= MAX_COMBO) break;
                capped.add(k);
                count++;
            }
            normalized = capped;
        }

        this.keys = Collections.unmodifiableSortedSet(normalized);
    }

    /**
     * V1-compatible constructor: creates an MKKeybind from a GLFW key code,
     * scan code, and modifier bitmask. This allows existing callers using
     * {@code new MKKeybind(keyCode, scanCode, modifiers)} to keep working.
     *
     * @param keyCode   the GLFW key code (e.g., GLFW_KEY_K = 75), or -1 for unbound
     * @param scanCode  the scan code (ignored in V2)
     * @param modifiers the GLFW modifier bitmask (Shift|Ctrl|Alt|Super)
     */
    public MKKeybind(int keyCode, int scanCode, int modifiers) {
        this(buildKeySetFromV1(keyCode, modifiers));
    }

    /**
     * Converts V1-style keyCode + modifier bitmask into a key set.
     */
    private static Set<InputConstants.Key> buildKeySetFromV1(int keyCode, int modifiers) {
        if (keyCode == -1 || keyCode == InputConstants.UNKNOWN.getValue()) {
            return Set.of();
        }
        Set<InputConstants.Key> keySet = new HashSet<>();
        keySet.add(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        int mods = modifiers & (InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL
                | InputConstants.MOD_ALT | InputConstants.MOD_SUPER);
        if ((mods & InputConstants.MOD_SHIFT) != 0)
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT));
        if ((mods & InputConstants.MOD_CONTROL) != 0)
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LCONTROL));
        if ((mods & InputConstants.MOD_ALT) != 0)
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LALT));
        if ((mods & InputConstants.MOD_SUPER) != 0)
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSUPER));
        return keySet;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Returns true if this keybind has no keys assigned. */
    public boolean isUnbound() {
        return keys.isEmpty();
    }

    /** Number of keys in this combo. */
    public int size() {
        return keys.size();
    }

    /** Returns an unmodifiable view of the keys in this combo. */
    public SortedSet<InputConstants.Key> getKeys() {
        return keys;
    }

    /**
     * Returns true if this is a single keyboard key (not mouse). Used by
     * {@link MKKeybindExt} to determine which key to register with vanilla.
     */
    public boolean isSingleKeyboard() {
        return keys.size() == 1
                && keys.first().getType() == InputConstants.Type.KEYSYM;
    }

    /**
     * Returns the single key if this is a single-key combo, or
     * {@link InputConstants#UNKNOWN} if multi-key or unbound.
     * Used by {@link MKKeybindExt} to set vanilla's base key.
     */
    public InputConstants.Key getSingleKey() {
        if (keys.size() == 1) return keys.first();
        return InputConstants.UNKNOWN;
    }

    // ── L/R Normalization ────────────────────────────────────────────────────
    //
    // Converts right-side modifier keys to their left-side equivalents so
    // LShift+I and RShift+I resolve to the same binding.

    /**
     * Normalizes a key by converting right-side modifier variants to left-side.
     */
    private static InputConstants.Key normalizeKey(InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.KEYSYM) {
            int code = key.getValue();
            if (code == InputConstants.KEY_RSHIFT)   return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT);
            if (code == InputConstants.KEY_RCONTROL)  return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LCONTROL);
            if (code == InputConstants.KEY_RALT)      return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LALT);
            if (code == InputConstants.KEY_RSUPER)    return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSUPER);
        }
        return key;
    }

    /**
     * Returns true if the keyCode is a traditional modifier key (Shift, Ctrl,
     * Alt, Super -- either side). Used for display ordering only: modifiers
     * are shown first in the display name.
     */
    public static boolean isTraditionalModifier(InputConstants.Key key) {
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        int code = key.getValue();
        return code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT
                || code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL
                || code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT
                || code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER;
    }

    /**
     * Returns true if the GLFW keyCode is a modifier key. Convenience overload
     * for callers that have the raw int key code (e.g., from GLFW key events).
     */
    public static boolean isModifierKey(int keyCode) {
        return keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT
                || keyCode == InputConstants.KEY_LCONTROL || keyCode == InputConstants.KEY_RCONTROL
                || keyCode == InputConstants.KEY_LALT || keyCode == InputConstants.KEY_RALT
                || keyCode == InputConstants.KEY_LSUPER || keyCode == InputConstants.KEY_RSUPER;
    }

    // ── Live State Checking ──────────────────────────────────────────────────
    //
    // Polls GLFW for the current pressed state of all keys in this combo.
    // Used by MKKeyMappingMixin.setDown() for runtime activation checks.

    /**
     * Returns true if ALL keys in this combo are currently pressed, as reported
     * by GLFW. For keyboard keys, polls glfwGetKey; for mouse buttons, polls
     * glfwGetMouseButton.
     *
     * <p>L/R normalization applies: if the combo contains LShift, either
     * physical LShift or RShift being held counts as a match.
     *
     * @param windowHandle the GLFW window handle (from Minecraft.getWindow().handle())
     */
    public boolean isActive(long windowHandle) {
        if (isUnbound()) return false;
        for (InputConstants.Key key : keys) {
            if (!isKeyActive(windowHandle, key)) return false;
        }
        return true;
    }

    /**
     * Checks if a single key is currently pressed. For normalized modifier
     * keys (stored as left), checks BOTH left and right physical keys.
     */
    private static boolean isKeyActive(long windowHandle, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, key.getValue())
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }

        // Keyboard key
        int code = key.getValue();

        // For normalized left-side modifiers, check both L and R physical keys
        if (code == InputConstants.KEY_LSHIFT) {
            return isGlfwKeyDown(windowHandle, InputConstants.KEY_LSHIFT)
                    || isGlfwKeyDown(windowHandle, InputConstants.KEY_RSHIFT);
        }
        if (code == InputConstants.KEY_LCONTROL) {
            return isGlfwKeyDown(windowHandle, InputConstants.KEY_LCONTROL)
                    || isGlfwKeyDown(windowHandle, InputConstants.KEY_RCONTROL);
        }
        if (code == InputConstants.KEY_LALT) {
            return isGlfwKeyDown(windowHandle, InputConstants.KEY_LALT)
                    || isGlfwKeyDown(windowHandle, InputConstants.KEY_RALT);
        }
        if (code == InputConstants.KEY_LSUPER) {
            return isGlfwKeyDown(windowHandle, InputConstants.KEY_LSUPER)
                    || isGlfwKeyDown(windowHandle, InputConstants.KEY_RSUPER);
        }

        return isGlfwKeyDown(windowHandle, code);
    }

    private static boolean isGlfwKeyDown(long windowHandle, int glfwKeyCode) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, glfwKeyCode)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    // ── Event Matching ──────────────────────────────────────────────────────
    //
    // For in-screen keybind checking where we receive discrete key/mouse events
    // rather than polling GLFW state. The event key "completes" the combo if
    // all other keys are already held.

    /**
     * Returns true if the given keyboard key event completes this combo.
     * The event key must be part of this combo, and all other keys in the
     * combo must currently be held (polled via GLFW).
     *
     * @param eventKey     the key that was just pressed
     * @param windowHandle the GLFW window handle
     */
    public boolean matchesKeyEvent(InputConstants.Key eventKey, long windowHandle) {
        if (isUnbound()) return false;

        // Normalize the event key (L/R -> L)
        InputConstants.Key normalized = normalizeKey(eventKey);

        // The event key must be part of this combo
        if (!keys.contains(normalized)) return false;

        // All OTHER keys in the combo must be currently held
        for (InputConstants.Key key : keys) {
            if (key.equals(normalized)) continue; // skip the event key itself
            if (!isKeyActive(windowHandle, key)) return false;
        }

        return true;
    }

    /**
     * Returns true if the given mouse button event completes this combo.
     * Same logic as {@link #matchesKeyEvent} but for mouse buttons.
     *
     * @param mouseButton  the GLFW mouse button (0=left, 1=right, 2=middle, etc.)
     * @param windowHandle the GLFW window handle
     */
    public boolean matchesMouseEvent(int mouseButton, long windowHandle) {
        if (isUnbound()) return false;

        InputConstants.Key eventKey = InputConstants.Type.MOUSE.getOrCreate(mouseButton);

        // The mouse button must be part of this combo
        if (!keys.contains(eventKey)) return false;

        // All OTHER keys must be currently held
        for (InputConstants.Key key : keys) {
            if (key.equals(eventKey)) continue;
            if (!isKeyActive(windowHandle, key)) return false;
        }

        return true;
    }

    // ── Display ──────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable display name like "Ctrl+Shift+K", "Mouse4+G",
     * or "Unbound". Traditional modifiers are shown first (Ctrl/Cmd > Shift >
     * Alt), then remaining keys alphabetically, then mouse buttons last.
     */
    public Component getDisplayName() {
        if (isUnbound()) {
            return Component.translatable("key.menukit.unbound");
        }

        // Partition keys into modifiers, regular keyboard keys, and mouse buttons
        List<InputConstants.Key> modifiers = new ArrayList<>();
        List<InputConstants.Key> regularKeys = new ArrayList<>();
        List<InputConstants.Key> mouseKeys = new ArrayList<>();

        for (InputConstants.Key key : keys) {
            if (isTraditionalModifier(key)) {
                modifiers.add(key);
            } else if (key.getType() == InputConstants.Type.MOUSE) {
                mouseKeys.add(key);
            } else {
                regularKeys.add(key);
            }
        }

        // Sort modifiers in conventional display order
        modifiers.sort(Comparator.comparingInt(MKKeybind::modifierDisplayOrder));

        // Build the display string
        StringBuilder sb = new StringBuilder();

        // Modifiers first, with platform-appropriate labels
        for (InputConstants.Key mod : modifiers) {
            if (!sb.isEmpty()) sb.append("+");
            sb.append(getModifierLabel(mod));
        }

        // Then regular keys (alphabetical by display name)
        regularKeys.sort(Comparator.comparing(k -> k.getDisplayName().getString()));
        for (InputConstants.Key key : regularKeys) {
            if (!sb.isEmpty()) sb.append("+");
            sb.append(key.getDisplayName().getString());
        }

        // Then mouse buttons last
        for (InputConstants.Key key : mouseKeys) {
            if (!sb.isEmpty()) sb.append("+");
            sb.append(key.getDisplayName().getString());
        }

        return Component.literal(sb.toString());
    }

    /**
     * Returns the platform-appropriate label for a modifier key.
     * On macOS: Super -> "Cmd", Control -> "Ctrl".
     * On other platforms: Control -> "Ctrl", Super -> "Super".
     */
    private static String getModifierLabel(InputConstants.Key key) {
        int code = key.getValue();
        boolean mac = Util.getPlatform() == Util.OS.OSX;

        if (code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT) return "Shift";
        if (code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL) return "Ctrl";
        if (code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT) return "Alt";
        if (code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER) return mac ? "Cmd" : "Super";

        return key.getDisplayName().getString();
    }

    /**
     * Returns a sort order for modifier keys in the display string.
     * Lower number = shown first.
     */
    private static int modifierDisplayOrder(InputConstants.Key key) {
        int code = key.getValue();
        boolean mac = Util.getPlatform() == Util.OS.OSX;

        if (mac) {
            // macOS: Cmd > Ctrl > Shift > Alt
            if (code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER) return 0;
            if (code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL) return 1;
        } else {
            // Other: Ctrl > Super > Shift > Alt
            if (code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL) return 0;
            if (code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER) return 1;
        }
        if (code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT) return 2;
        if (code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT) return 3;
        return 99;
    }

    // ── Serialization ────────────────────────────────────────────────────────
    //
    // V2 format: "key.keyboard.left.shift+key.keyboard.i" (keys joined by "+")
    // V1 format: "key.keyboard.k:6" (key name + colon + modifier bitmask)
    // Both formats are accepted by deserialize() for backwards compatibility.

    /**
     * Serializes this keybind to a string suitable for JSON config storage.
     * V2 format: key names joined by "+".
     */
    public String serialize() {
        if (isUnbound()) {
            return "key.keyboard.unknown";
        }
        return keys.stream()
                .map(InputConstants.Key::getName)
                .collect(Collectors.joining("+"));
    }

    /**
     * Deserializes a keybind from either V2 format ("key1+key2") or V1 format
     * ("key:modifiers"). Returns {@link #UNBOUND} if the string is null, empty,
     * or malformed.
     */
    public static MKKeybind deserialize(String raw) {
        if (raw == null || raw.isEmpty()) return UNBOUND;

        try {
            // Detect V1 format: contains ":" with a number after it, and no "+"
            // V2 keys can contain ":" in names like "key.keyboard.semicolon" but
            // those won't have a pure integer after the last ":"
            if (!raw.contains("+") && raw.contains(":")) {
                return deserializeV1(raw);
            }

            // V2 format: split by "+" and resolve each key name
            String[] parts = raw.split("\\+");
            Set<InputConstants.Key> keySet = new HashSet<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                InputConstants.Key key = InputConstants.getKey(trimmed);
                if (!key.equals(InputConstants.UNKNOWN)) {
                    keySet.add(key);
                }
            }

            if (keySet.isEmpty()) return UNBOUND;
            return new MKKeybind(keySet);

        } catch (Exception e) {
            MenuKit.LOGGER.warn("[MenuKit] Failed to deserialize keybind '{}': {}", raw, e.getMessage());
            return UNBOUND;
        }
    }

    /**
     * Deserializes V1 format: "key.keyboard.k:6" where the part before the
     * colon is the InputConstants.Key name and the part after is the modifier
     * bitmask.
     */
    private static MKKeybind deserializeV1(String raw) {
        int colonIdx = raw.lastIndexOf(':');
        if (colonIdx < 0) {
            // Just a key name with no modifiers
            InputConstants.Key key = InputConstants.getKey(raw);
            if (key.equals(InputConstants.UNKNOWN)) return UNBOUND;
            return new MKKeybind(Set.of(key));
        }

        String keyName = raw.substring(0, colonIdx);
        String modStr = raw.substring(colonIdx + 1);

        int mods;
        try {
            mods = Integer.parseInt(modStr);
        } catch (NumberFormatException e) {
            // Not a V1 format after all -- the ":" is part of the key name
            InputConstants.Key key = InputConstants.getKey(raw);
            if (key.equals(InputConstants.UNKNOWN)) return UNBOUND;
            return new MKKeybind(Set.of(key));
        }

        InputConstants.Key baseKey = InputConstants.getKey(keyName);
        if (baseKey.equals(InputConstants.UNKNOWN)) return UNBOUND;

        // Convert V1 modifier bitmask to key set
        Set<InputConstants.Key> keySet = new HashSet<>();
        keySet.add(baseKey);

        mods = mods & MODIFIER_MASK;
        if ((mods & InputConstants.MOD_SHIFT) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT));
        }
        if ((mods & InputConstants.MOD_CONTROL) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LCONTROL));
        }
        if ((mods & InputConstants.MOD_ALT) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LALT));
        }
        if ((mods & InputConstants.MOD_SUPER) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSUPER));
        }

        return new MKKeybind(keySet);
    }

    // ── Factory Helpers ─────────────────────────────────────────────────────

    /**
     * Creates an MKKeybind from a single GLFW key code with no modifiers.
     * Convenience for callers migrating from the V1 constructor.
     *
     * @param keyCode GLFW key code (e.g., GLFW_KEY_K)
     */
    public static MKKeybind ofKey(int keyCode) {
        if (keyCode == -1 || keyCode == InputConstants.UNKNOWN.getValue()) return UNBOUND;
        return new MKKeybind(Set.of(InputConstants.Type.KEYSYM.getOrCreate(keyCode)));
    }

    /**
     * Creates an MKKeybind from a GLFW key code + modifier bitmask.
     * Bridges the V1 (keyCode, scanCode, modifiers) API to V2.
     * Used by callers still using the old constructor pattern.
     *
     * @param keyCode   GLFW key code
     * @param scanCode  scan code (ignored -- kept for API compat)
     * @param modifiers GLFW modifier bitmask
     */
    public static MKKeybind ofKeyAndModifiers(int keyCode, int scanCode, int modifiers) {
        if (keyCode == -1 || keyCode == InputConstants.UNKNOWN.getValue()) return UNBOUND;

        Set<InputConstants.Key> keySet = new HashSet<>();
        keySet.add(InputConstants.Type.KEYSYM.getOrCreate(keyCode));

        int mods = modifiers & MODIFIER_MASK;
        if ((mods & InputConstants.MOD_SHIFT) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT));
        }
        if ((mods & InputConstants.MOD_CONTROL) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LCONTROL));
        }
        if ((mods & InputConstants.MOD_ALT) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LALT));
        }
        if ((mods & InputConstants.MOD_SUPER) != 0) {
            keySet.add(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSUPER));
        }

        return new MKKeybind(keySet);
    }

    // ── V1-Compatible Accessors ─────────────────────────────────────────────
    //
    // These exist for callers that still use the old record-style API
    // (e.g., InventoryPlusConfig constructors). They extract a "primary key"
    // and "modifier bitmask" from the key set.

    /**
     * Returns the GLFW key code of the "primary" non-modifier key in this combo,
     * or -1 if unbound or all keys are modifiers.
     * V1-compatible accessor.
     */
    public int keyCode() {
        for (InputConstants.Key key : keys) {
            if (key.getType() == InputConstants.Type.KEYSYM && !isModifierKey(key.getValue())) {
                return key.getValue();
            }
        }
        // If all keys are modifiers or mouse buttons, return the first key's value
        if (!keys.isEmpty()) return keys.first().getValue();
        return -1;
    }

    /**
     * Converts a modifier GLFW key code to its modifier bitmask bit.
     */
    private static int modifierKeyToBit(int keyCode) {
        if (keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT)
            return InputConstants.MOD_SHIFT;
        if (keyCode == InputConstants.KEY_LCONTROL || keyCode == InputConstants.KEY_RCONTROL)
            return InputConstants.MOD_CONTROL;
        if (keyCode == InputConstants.KEY_LALT || keyCode == InputConstants.KEY_RALT)
            return InputConstants.MOD_ALT;
        if (keyCode == InputConstants.KEY_LSUPER || keyCode == InputConstants.KEY_RSUPER)
            return InputConstants.MOD_SUPER;
        return 0;
    }

    /**
     * Returns the GLFW modifier bitmask derived from the modifier keys in this combo.
     * V1-compatible accessor.
     */
    public int modifiers() {
        int mods = 0;
        for (InputConstants.Key key : keys) {
            if (key.getType() == InputConstants.Type.KEYSYM) {
                mods |= modifierKeyToBit(key.getValue());
            }
        }
        return mods & MODIFIER_MASK;
    }

    // ── equals / hashCode ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MKKeybind other)) return false;
        return keys.equals(other.keys);
    }

    @Override
    public int hashCode() {
        return keys.hashCode();
    }

    @Override
    public String toString() {
        return "MKKeybind{" + serialize() + "}";
    }
}
