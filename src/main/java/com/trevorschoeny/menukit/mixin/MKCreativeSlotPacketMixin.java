package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKSlot;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MenuKit internal mixin — extends creative mode's slot packet handler to
 * accept MKSlot indices beyond vanilla's hardcoded 1-45 range.
 *
 * <p>Vanilla's {@code handleSetCreativeModeSlot} only processes slots 1-45.
 * MKSlots added to InventoryMenu have higher indices (46+) and are silently
 * rejected. This mixin catches those indices and processes them.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MKCreativeSlotPacketMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At("TAIL"))
    private void menuKit$handleMKSlotCreativePacket(
            ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {

        // Only handle slots beyond vanilla's range (1-45)
        int slotNum = packet.slotNum();
        if (slotNum <= 45) return; // vanilla already handled these

        if (!player.hasInfiniteMaterials()) return;

        // Check if the slot index is valid in the player's inventory menu
        if (slotNum >= player.inventoryMenu.slots.size()) return;

        Slot slot = player.inventoryMenu.getSlot(slotNum);
        com.trevorschoeny.menukit.MKSlotState state = com.trevorschoeny.menukit.MKSlotStateRegistry.get(slot);
        if (state == null || !state.isMenuKitSlot()) return; // only process our slots

        ItemStack stack = packet.itemStack();

        // Validate item
        if (!stack.isEmpty() && !stack.isItemEnabled(player.level().enabledFeatures())) return;
        if (!stack.isEmpty() && stack.getCount() > stack.getMaxStackSize()) return;

        // Set the item on the server's MKSlot
        slot.setByPlayer(stack);
        player.inventoryMenu.setRemoteSlot(slotNum, stack);
        player.inventoryMenu.broadcastChanges();
    }
}
