package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code key} field on {@link KeyBindsList.KeyEntry} so that
 * we can find which list entry corresponds to a given {@link KeyMapping} when
 * auto-scrolling to a keybind from the YACL settings gear icon.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryAccessor {

    @Accessor("key")
    KeyMapping menuKit$getKey();
}
