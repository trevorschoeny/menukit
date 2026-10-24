package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.menukit.api.element.SlotScroll;
import com.trevlar.menukit.inject.RegionReveal;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Shows where a shift-click landed (7.4.0, the container region). Every click the
 * player makes in a menu, whoever sends it (vanilla's screen, another mod), goes through
 * {@code handleContainerInput}, which plays it on the client's menu before telling the
 * server. Around that local play, and only for a quick move while a region is up, the
 * stacks are noted before and compared after; {@link RegionReveal#quickMoved} scrolls
 * the region to the slot that gained items when it is the region's and out of view. The same
 * holds while a scrolling slot grid ({@link SlotScroll}) is on screen, region or not: the grid
 * holding the slot scrolls to it.
 * The click itself runs exactly as before.
 */
@ApiStatus.Internal
@Mixin(MultiPlayerGameMode.class)
public abstract class MKQuickMoveRevealMixin {

    @WrapOperation(method = "handleContainerInput", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"))
    private void mk$revealQuickMove(AbstractContainerMenu menu, int slot, int button, ContainerInput input,
                                    Player player, Operation<Void> original) {
        if (input != ContainerInput.QUICK_MOVE || !(RegionReveal.active() || SlotScroll.anyShown())) {
            original.call(menu, slot, button, input, player);
            return;
        }
        List<ItemStack> before = RegionReveal.snapshot(menu);
        original.call(menu, slot, button, input, player);
        RegionReveal.quickMoved(menu, before);
    }
}
