package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.trevlar.menukit.window.ActingPlayer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Captures the acting player for the duration of a click transaction, so a deep
 * seam with no player in scope (vanilla's {@code moveItemStackTo}) can read it
 * through {@link ActingPlayer}. Set on entry, cleared in {@code finally}, so it
 * never leaks past the click.
 */
@Mixin(AbstractContainerMenu.class)
public class MKActingPlayerMixin {

    @WrapMethod(method = "clicked")
    private void mk$captureActingPlayer(int slotId, int button, ContainerInput clickType, Player player,
                                        Operation<Void> original) {
        ActingPlayer.set(player);
        try {
            original.call(slotId, button, clickType, player);
        } finally {
            ActingPlayer.clear();
        }
    }
}
