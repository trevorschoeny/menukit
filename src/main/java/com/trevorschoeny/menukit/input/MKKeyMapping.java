package com.trevorschoeny.menukit.input;

import com.trevorschoeny.menukit.MenuKit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A multi-key extension of vanilla's {@link KeyMapping} that checks a full
 * {@link MKKeybind} combo (arbitrary key set) before reporting as "down".
 *
 * <p><b>Why this exists:</b> Vanilla's {@code KeyMapping} tracks a single
 * {@link InputConstants.Key} and ignores multi-key combos entirely. When vanilla
 * calls {@code KeyMapping.set(key, true)}, it marks the mapping as down
 * purely based on the key code match. This means a binding to "R" fires
 * even if the user is pressing "Ctrl+R" (intending a different action).
 *
 * <p>{@code MKKeyMapping} fixes this by overriding {@link #setDown(boolean)}
 * to check whether all keys in the {@link MKKeybind} combo are currently held
 * via GLFW polling. If any combo key is missing, the mapping stays "not down".
 *
 * <p><b>Priority dispatch:</b> When this mapping is activated (all combo keys
 * held), it checks for OTHER MKKeyMappings whose combos are strict subsets
 * of this one. If found, those smaller combos are suppressed (set to not-down)
 * to prevent "Shift+K" from also triggering "K".
 *
 * <p><b>V1 compatibility:</b> The constructor still accepts (name, keyCode,
 * modifiers, category). Internally, these are converted to an {@link MKKeybind}.
 * The {@link #matchesEvent(int, int)} API bridges the old bitmask-based approach
 * to the V2 GLFW-polling path.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKKeyMapping extends KeyMapping {

    // The full multi-key combo. Replaces the old `requiredModifiers` bitmask.
    // For backwards compat, V1 constructors build an MKKeybind from keyCode+mods.
    private MKKeybind combo;

    // ── Constructors ─────────────────────────────────────────────────────────

    /**
     * Creates a multi-key mapping with the given key and required modifiers.
     * V1-compatible: converts keyCode + modifier bitmask into an MKKeybind.
     *
     * @param name      the translation key for this binding (e.g., "key.mymod.sort")
     * @param keyCode   the GLFW key code (e.g., GLFW_KEY_R)
     * @param modifiers the required GLFW modifier bitmask (e.g., MOD_CONTROL | MOD_SHIFT)
     * @param category  the keybind category (shown in Controls screen)
     */
    public MKKeyMapping(String name, int keyCode, int modifiers, KeyMapping.Category category) {
        super(name, keyCode, category);
        this.combo = MKKeybind.ofKeyAndModifiers(keyCode, 0, modifiers);
    }

    /**
     * Creates a multi-key mapping with no required modifiers (behaves like vanilla).
     */
    public MKKeyMapping(String name, int keyCode, KeyMapping.Category category) {
        this(name, keyCode, 0, category);
    }

    // ── Combo Checking ──────────────────────────────────────────────────────

    /**
     * Overrides vanilla's setDown to check the full combo via GLFW polling.
     *
     * <p>When isDown=true (key pressed), we verify that ALL keys in the combo
     * are currently held. If not, we suppress (force false). When isDown=false
     * (key released), we always pass through -- releasing a key should always
     * clear the mapping regardless of combo state.
     *
     * <p>Additionally implements priority dispatch: when this multi-key binding
     * is activated, any other MKKeyMapping with a strict subset combo is
     * suppressed to prevent "Shift+K" from also triggering "K".
     */
    @Override
    public void setDown(boolean isDown) {
        if (isDown && !combo.isUnbound()) {
            long windowHandle = Minecraft.getInstance().getWindow().handle();

            // Check if the full combo is active (all keys held)
            if (!combo.isActive(windowHandle)) {
                // Combo keys not all held -- suppress
                super.setDown(false);
                return;
            }

            // Priority dispatch: when this larger combo fires, suppress
            // any smaller combos that are subsets of ours
            if (combo.size() > 1) {
                suppressSubsetMappings();
            }
        }
        super.setDown(isDown);
    }

    /**
     * When this mapping's full combo is active, check all OTHER MKKeyMappings.
     * If another mapping's combo is a strict subset of ours, suppress it.
     * This prevents "K" from firing when "Shift+K" is what the user intended.
     */
    private void suppressSubsetMappings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return;

        for (KeyMapping other : mc.options.keyMappings) {
            if (other == this) continue;
            if (!(other instanceof MKKeyMapping mkOther)) continue;
            if (mkOther.combo.isUnbound()) continue;

            // Check if the other's combo is a strict subset of ours
            if (mkOther.combo.size() >= this.combo.size()) continue;
            if (this.combo.getKeys().containsAll(mkOther.combo.getKeys())) {
                // The other's combo is a strict subset -- suppress it
                mkOther.setDown(false);
            }
        }
    }

    // ── Vanilla Overrides ─────────────────────────────────────────────────────

    /**
     * Overrides vanilla's {@link KeyMapping#setKey(InputConstants.Key)} to keep
     * the combo field in sync. Vanilla calls {@code setKey(getDefaultKey())} when
     * the user clicks "Reset" in the Controls screen. Without this override, the
     * vanilla base key resets but the combo retains the old multi-key binding,
     * causing the keybind to silently malfunction.
     */
    @Override
    public void setKey(InputConstants.Key key) {
        super.setKey(key);
        // Sync the combo field to match the new base key.
        // If the key is UNKNOWN (unbound), clear the combo entirely.
        // Otherwise, create a single-key combo from the new key.
        if (key.equals(InputConstants.UNKNOWN)) {
            this.combo = MKKeybind.UNBOUND;
        } else {
            this.combo = new MKKeybind(java.util.Set.of(key));
        }
    }

    /**
     * Overrides vanilla's {@link KeyMapping#getTranslatedKeyMessage()} to show
     * the full multi-key combo in the Controls screen. Vanilla only shows the
     * single base key (e.g., "K"), which is misleading for a "Shift+K" binding.
     *
     * <p>For single-key combos, defers to vanilla's implementation. For multi-key
     * combos, returns the full display name from {@link MKKeybind#getDisplayName()}.
     */
    @Override
    public Component getTranslatedKeyMessage() {
        if (combo != null && !combo.isUnbound() && combo.size() > 1) {
            return combo.getDisplayName();
        }
        return super.getTranslatedKeyMessage();
    }

    /**
     * Overrides vanilla's {@link KeyMapping#matches(KeyEvent)} to account for
     * multi-key combos. Vanilla's implementation only checks the single base key,
     * which means a "Shift+K" binding would match a bare "K" press.
     *
     * <p>For single-key combos, defers to vanilla. For multi-key combos, checks
     * whether the event's key is part of the combo AND whether all other combo
     * keys are currently held via GLFW polling.
     */
    @Override
    public boolean matches(KeyEvent keyEvent) {
        if (combo == null || combo.isUnbound()) return false;
        if (combo.size() == 1) return super.matches(keyEvent);

        // For multi-key combos, verify the event key is part of the combo
        // and all other keys are currently held. This uses the same GLFW
        // polling approach as matchesEvent() / MKKeybind.matchesKeyEvent().
        InputConstants.Key eventKey;
        if (keyEvent.key() != InputConstants.UNKNOWN.getValue()) {
            eventKey = InputConstants.Type.KEYSYM.getOrCreate(keyEvent.key());
        } else {
            eventKey = InputConstants.Type.SCANCODE.getOrCreate(keyEvent.scancode());
        }
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        return combo.matchesKeyEvent(eventKey, windowHandle);
    }

    // ── MKKeybind Integration ────────────────────────────────────────────────

    /**
     * Creates an MKKeyMapping from an {@link MKKeybind} value (typically from
     * YACL config). Bridges the config layer (MKKeybind) and the runtime
     * layer (KeyMapping instances registered with Fabric).
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
            // Unbound: register with UNKNOWN key, no modifiers
            MKKeyMapping m = new MKKeyMapping(name, InputConstants.UNKNOWN.getValue(), 0, category);
            m.combo = MKKeybind.UNBOUND;
            return m;
        }

        // Use the primary non-modifier key for vanilla's base key registration
        int baseKey = keybind.keyCode();
        if (baseKey == -1) baseKey = InputConstants.UNKNOWN.getValue();

        MKKeyMapping m = new MKKeyMapping(name, baseKey, keybind.modifiers(), category);
        m.combo = keybind; // Use the full MKKeybind, not the reconstructed one
        return m;
    }

    /**
     * Updates this mapping's combo from an {@link MKKeybind}. Call this when
     * the user changes the keybind in the YACL config screen to sync the
     * runtime mapping without re-registering.
     *
     * <p>Also calls {@link KeyMapping#resetMapping()} to update vanilla's
     * internal key-to-mapping lookup table so the new key is recognized.
     */
    public void updateFromKeybind(MKKeybind keybind) {
        if (keybind == null || keybind.isUnbound()) {
            // setKey(UNKNOWN) also resets combo to UNBOUND via our override
            setKey(InputConstants.UNKNOWN);
        } else {
            // Update vanilla's base key to the primary non-modifier key.
            // setKey() will create a single-key combo, but we override it
            // immediately with the full multi-key combo below.
            int baseKey = keybind.keyCode();
            if (baseKey == -1) {
                setKey(InputConstants.UNKNOWN);
            } else {
                setKey(InputConstants.Type.KEYSYM.getOrCreate(baseKey));
            }
            // Override the single-key combo that setKey() created with the
            // full multi-key combo from the keybind
            this.combo = keybind;
        }

        // Rebuild vanilla's key -> mapping lookup
        KeyMapping.resetMapping();
    }

    /**
     * Returns the current binding as an {@link MKKeybind}.
     */
    public MKKeybind toKeybind() {
        if (isUnbound() && combo.isUnbound()) {
            return MKKeybind.UNBOUND;
        }
        return combo;
    }

    // ── Event Matching ─────────────────────────────────────────────────────

    /**
     * Checks whether a KEY_PRESS event matches this keybind.
     *
     * <p>When a container screen is open, vanilla does NOT call
     * {@code KeyMapping.set(key, true)} from the GLFW key callback --
     * key events go through {@code Screen.keyPressed()} instead. This means
     * {@link #isDown()} is unreliable for in-screen keybinds. Handlers that
     * respond to key-press events through a screen's event system should
     * use this method.
     *
     * <p>Uses GLFW polling via {@link MKKeybind#matchesKeyEvent} to verify
     * that ALL keys in the combo are held, not just the event key. This
     * ensures multi-key combos (e.g., W+R) only trigger when the completing
     * key is pressed while all other keys are already held.
     *
     * @param eventKeyCode   the GLFW key code from the event
     * @param eventModifiers the GLFW modifier bitmask from the event (unused
     *                       -- kept for API compat; GLFW polling replaces it)
     * @return true if this mapping's combo matches the event
     */
    public boolean matchesEvent(int eventKeyCode, int eventModifiers) {
        if (combo.isUnbound()) return false;

        // Build the InputConstants.Key for the event key so we can use the
        // GLFW-polling path that correctly checks ALL keys in the combo.
        InputConstants.Key eventKey = InputConstants.Type.KEYSYM.getOrCreate(eventKeyCode);
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        return combo.matchesKeyEvent(eventKey, windowHandle);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns the full MKKeybind combo for this mapping.
     */
    public MKKeybind getCombo() {
        return combo;
    }

    /**
     * Returns the GLFW key code of the given KeyMapping's current key binding.
     * Works around the fact that {@code KeyMapping.key} is a {@code protected}
     * field with no public getter in vanilla MC 1.21.11.
     *
     * @param mapping the KeyMapping to query
     * @return the GLFW key code, or {@link InputConstants#UNKNOWN} value if unbound
     */
    public static int getKeyCode(KeyMapping mapping) {
        try {
            return InputConstants.getKey(mapping.saveString()).getValue();
        } catch (Exception e) {
            return InputConstants.UNKNOWN.getValue();
        }
    }

    // ── Conflict Detection (Vanilla Integration) ─────────────────────────────

    /**
     * Overrides vanilla's {@code same()} to account for multi-key combos.
     *
     * <p>Two MKKeyMappings: exact combo match = conflict. Different combos
     * (even sharing a base key) = NOT a conflict.
     *
     * <p>MKKeyMapping vs vanilla KeyMapping: multi-key MKKeyMapping (size > 1)
     * does NOT conflict with single-key vanilla because setDown() suppresses
     * when extra combo keys are missing. Single-key MKKeybinds still conflict
     * with vanilla on the same base key.
     */
    @Override
    public boolean same(KeyMapping other) {
        if (other instanceof MKKeyMapping mkOther) {
            // Two multi-key mappings: exact combo match = conflict
            return this.combo.equals(mkOther.combo);
        }

        // Other is plain vanilla: multi-key combo (size > 1) never conflicts
        // because setDown() suppresses when extra keys aren't held
        if (this.combo.size() > 1) return false;

        // Single-key combo: fall back to vanilla base-key comparison
        return super.same(other);
    }
}
