package com.trevorschoeny.menukit.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.menukit.MKKeybind;
import com.trevorschoeny.menukit.MKKeybindCapture;
import com.trevorschoeny.menukit.MKKeybindExt;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The core mixin that makes multi-key combos work on ANY {@link KeyMapping}.
 * Implements the {@link MKKeybindExt} duck interface, injecting a nullable
 * {@code combo} field into every KeyMapping instance.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li><b>setDown</b>: When a combo exists (size > 1), GLFW-polls ALL keys
 *       before allowing setDown(true). Single-key combos and null combos
 *       pass through to vanilla unchanged.</li>
 *   <li><b>click</b>: Guards against false clickCount increments when the
 *       combo isn't fully held.</li>
 *   <li><b>resetMapping</b>: After vanilla rebuilds its key map, registers
 *       multi-key combos under ALL constituent keys so vanilla's dispatch
 *       reaches them regardless of which key triggered the event.</li>
 *   <li><b>getTranslatedKeyMessage</b>: Shows the full combo display name
 *       (e.g., "Ctrl+K") instead of just the base key.</li>
 *   <li><b>same</b>: Compares full combos for conflict detection.</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> keybind system.
 */
@Mixin(KeyMapping.class)
public abstract class MKKeyMappingMixin implements MKKeybindExt {

    // ── Shadowed Vanilla Fields ──────────────────────────────────────────────

    @Shadow protected InputConstants.Key key;

    // ── Duck Interface Field ─────────────────────────────────────────────────

    /** The multi-key combo, or null for vanilla single-key behavior. */
    @Unique
    private MKKeybind menuKit$combo;

    // ── Duck Interface Implementation ────────────────────────────────────────

    @Override
    public MKKeybind menuKit$getCombo() {
        return menuKit$combo;
    }

    @Override
    public void menuKit$setCombo(MKKeybind combo) {
        this.menuKit$combo = combo;
    }

    // ── setDown Override ─────────────────────────────────────────────────────
    //
    // When a KeyMapping has a multi-key combo (non-null, size > 1), verify via
    // GLFW polling that ALL keys in the combo are held before allowing
    // setDown(true). If any key isn't held, force setDown(false) to prevent
    // single-key activations when the combo requires multiple keys.
    //
    // Also implements priority dispatch: when this larger combo fires, suppress
    // any other KeyMapping whose combo is a strict subset of ours.

    @Inject(method = "setDown(Z)V", at = @At("HEAD"), cancellable = true)
    private void menuKit$onSetDown(boolean isDown, CallbackInfo ci) {
        // Only intercept when pressing AND we have a multi-key combo
        if (!isDown || menuKit$combo == null || menuKit$combo.isUnbound() || menuKit$combo.size() <= 1) {
            return;
        }

        long windowHandle = Minecraft.getInstance().getWindow().handle();

        // Check if the full combo is active (all keys held)
        if (!menuKit$combo.isActive(windowHandle)) {
            // Combo keys not all held -- suppress by calling setDown(false)
            // We shadow the field directly to avoid recursion
            ((KeyMappingAccessor) this).menuKit$setKey(this.key); // no-op, just accessing
            // Actually we need to set isDown to false. We can't call super.setDown(false)
            // from a mixin, so we cancel and manually set the field.
            ci.cancel();
            // Manually set isDown to false (via the vanilla method with false)
            // We can't recurse safely, so we just cancel -- vanilla won't set isDown(true)
            return;
        }

        // Priority dispatch: suppress smaller combos that are subsets of ours
        menuKit$suppressSubsetMappings();
    }

    /**
     * When this mapping's full combo is active, check all OTHER KeyMappings.
     * If another mapping's combo is a strict subset of ours, suppress it.
     * This prevents "K" from firing when "Shift+K" is what the user intended.
     */
    @Unique
    private void menuKit$suppressSubsetMappings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return;

        for (KeyMapping other : mc.options.keyMappings) {
            if (other == (Object) this) continue;

            MKKeybind otherCombo = ((MKKeybindExt) other).menuKit$getCombo();
            if (otherCombo == null || otherCombo.isUnbound()) continue;

            // Check if the other's combo is a strict subset of ours
            if (otherCombo.size() >= menuKit$combo.size()) continue;
            if (menuKit$combo.getKeys().containsAll(otherCombo.getKeys())) {
                // The other's combo is a strict subset -- suppress it
                other.setDown(false);
            }
        }
    }

    // ── click() Guard ────────────────────────────────────────────────────────
    //
    // Vanilla increments clickCount before setDown. If the combo isn't active,
    // clear clickCount at TAIL to prevent false consumeClick() triggers.

    @Inject(method = "click", at = @At("TAIL"))
    private static void menuKit$onClickTail(InputConstants.Key key, CallbackInfo ci) {
        // After vanilla increments clickCount for all mappings matching this key,
        // check each one: if it has a multi-key combo and the combo isn't active,
        // clear its clickCount to prevent false triggers.
        Map<InputConstants.Key, List<KeyMapping>> map = KeyMappingAccessor.menuKit$getMap();
        List<KeyMapping> mappings = map.get(key);
        if (mappings == null) return;

        long windowHandle = Minecraft.getInstance().getWindow().handle();

        for (KeyMapping mapping : mappings) {
            MKKeybind combo = ((MKKeybindExt) mapping).menuKit$getCombo();
            if (combo == null || combo.isUnbound() || combo.size() <= 1) continue;

            // Multi-key combo that isn't fully active -- clear the false click
            if (!combo.isActive(windowHandle)) {
                ((KeyMappingAccessor) mapping).menuKit$setClickCount(0);
            }
        }
    }

    // ── resetMapping() TAIL ──────────────────────────────────────────────────
    //
    // After vanilla rebuilds its KEY -> List<KeyMapping> map, register multi-key
    // combos under ALL constituent keys. This way vanilla's dispatch reaches
    // them regardless of which key in the combo triggered the event.

    @Inject(method = "resetMapping", at = @At("TAIL"))
    private static void menuKit$onResetMappingTail(CallbackInfo ci) {
        Map<InputConstants.Key, List<KeyMapping>> map = KeyMappingAccessor.menuKit$getMap();

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return;

        for (KeyMapping mapping : mc.options.keyMappings) {
            MKKeybind combo = ((MKKeybindExt) mapping).menuKit$getCombo();
            if (combo == null || combo.isUnbound() || combo.size() <= 1) continue;

            // Register under each key in the combo that isn't the base key
            // (the base key is already registered by vanilla's resetMapping)
            InputConstants.Key baseKey = ((KeyMappingAccessor) mapping).menuKit$getKey();
            Set<InputConstants.Key> comboKeys = combo.getKeys();

            for (InputConstants.Key comboKey : comboKeys) {
                if (comboKey.equals(baseKey)) continue; // Already registered by vanilla

                // Add this mapping to the list for the additional combo key
                List<KeyMapping> list = map.computeIfAbsent(comboKey, k -> new java.util.ArrayList<>());
                if (!list.contains(mapping)) {
                    list.add(mapping);
                }
            }
        }
    }

    // ── getTranslatedKeyMessage Override ──────────────────────────────────────
    //
    // When a combo exists, show the full display name (e.g., "Ctrl+K") instead
    // of just the base key name. Also handles live capture preview.

    @Inject(method = "getTranslatedKeyMessage", at = @At("HEAD"), cancellable = true)
    private void menuKit$onGetTranslatedKeyMessage(CallbackInfoReturnable<Component> cir) {
        // Live capture preview: if this mapping is actively being captured,
        // show the capture engine's preview text instead of the current binding.
        if (MKKeybindCapture.activeMapping == (Object) this && MKKeybindCapture.activeCapture != null) {
            cir.setReturnValue(MKKeybindCapture.activeCapture.getPreviewText());
            return;
        }

        // Multi-key combo: show the full display name
        if (menuKit$combo != null && !menuKit$combo.isUnbound() && menuKit$combo.size() > 1) {
            cir.setReturnValue(menuKit$combo.getDisplayName());
        }
    }

    // ── same() Override (Conflict Detection) ─────────────────────────────────
    //
    // Two KeyMappings with different multi-key combos are NOT in conflict even
    // if they share a base key. Full combo comparison replaces vanilla's
    // single-key comparison when combos are present.

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void menuKit$onSame(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        MKKeybind thisCombo = this.menuKit$combo;
        MKKeybind otherCombo = ((MKKeybindExt) other).menuKit$getCombo();

        // Both have combos: exact match = conflict, otherwise no conflict
        if (thisCombo != null && !thisCombo.isUnbound()
                && otherCombo != null && !otherCombo.isUnbound()) {
            cir.setReturnValue(thisCombo.equals(otherCombo));
            return;
        }

        // One has a multi-key combo, the other doesn't: no conflict for multi-key
        // (priority dispatch handles the runtime behavior)
        if (thisCombo != null && !thisCombo.isUnbound() && thisCombo.size() > 1) {
            cir.setReturnValue(false);
            return;
        }
        if (otherCombo != null && !otherCombo.isUnbound() && otherCombo.size() > 1) {
            cir.setReturnValue(false);
            return;
        }

        // Both are single-key or no combos: fall through to vanilla comparison
    }

    // ── setKey Override (Combo Sync) ──────────────────────────────────────────
    //
    // When vanilla calls setKey (e.g., user clicks "Reset" in Controls), keep
    // the combo in sync. If the key is UNKNOWN (unbound), clear the combo.
    // Otherwise, create a single-key combo from the new key.

    @Inject(method = "setKey", at = @At("TAIL"))
    private void menuKit$onSetKey(InputConstants.Key newKey, CallbackInfo ci) {
        if (newKey.equals(InputConstants.UNKNOWN)) {
            this.menuKit$combo = MKKeybind.UNBOUND;
        } else {
            // Only auto-sync if there's no active capture session. During capture,
            // the capture engine sets the combo explicitly after finalization.
            if (MKKeybindCapture.activeMapping != (Object) this) {
                this.menuKit$combo = new MKKeybind(Set.of(newKey));
            }
        }
    }
}
