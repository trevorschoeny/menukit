package com.trevorschoeny.menukit.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mutable;

import java.util.List;
import java.util.Map;

/**
 * Accessor mixin for {@link KeyMapping}'s private fields. Provides read/write
 * access to fields needed by the multi-key combo system:
 *
 * <ul>
 *   <li>{@code clickCount} -- cleared by the click guard when a combo isn't active</li>
 *   <li>{@code key} -- read/written when syncing the base key with the combo</li>
 *   <li>{@code MAP} -- the static Key->KeyMapping lookup, modified at TAIL of resetMapping</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> keybind system.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    /**
     * Reads the current click count. Vanilla increments this on each key press
     * (via {@code KeyMapping.click()}) and decrements it via {@code consumeClick()}.
     */
    @Accessor("clickCount")
    int menuKit$getClickCount();

    /**
     * Sets the click count directly. Used by the combo click guard to clear
     * false clicks when the full combo isn't held.
     */
    @Accessor("clickCount")
    void menuKit$setClickCount(int count);

    /**
     * Reads the current key binding. Vanilla's {@code key} field is protected,
     * but the accessor bypasses access modifiers.
     */
    @Accessor("key")
    InputConstants.Key menuKit$getKey();

    /**
     * Sets the key binding directly. Combined with {@code @Mutable} to allow
     * writing to the final-ish field.
     */
    @Mutable
    @Accessor("key")
    void menuKit$setKey(InputConstants.Key key);

    /**
     * Reads the static MAP field: Key -> List of KeyMappings. Used at TAIL of
     * resetMapping to register multi-key combos under ALL constituent keys.
     */
    @Accessor("MAP")
    static Map<InputConstants.Key, List<KeyMapping>> menuKit$getMap() {
        throw new AssertionError("mixin");
    }
}
