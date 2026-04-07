package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.widget.MKSlot;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MenuKit internal mixin — adds MKSlots to the creative screen's ItemPickerMenu.
 *
 * <p>On creative ITEM tabs (Building Blocks, Search, etc.), the active menu is
 * ItemPickerMenu, NOT InventoryMenu. Without MKSlots in ItemPickerMenu, panels
 * render visually but have no clickable slots.
 *
 * <p>These MKSlots share the SAME containers as the InventoryMenu MKSlots
 * (via {@code computeIfAbsent}), so items persist across tab switches.
 *
 * <p>On the INVENTORY tab, vanilla wraps InventoryMenu slots in SlotWrappers
 * instead — these MKSlots are saved as {@code originalSlots} and restored
 * when switching back to item tabs.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$ItemPickerMenu")
public class MKItemPickerMenuMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void menuKit$addSlotsToItemPickerMenu(Player player, CallbackInfo ci) {
        var menu = (net.minecraft.world.inventory.AbstractContainerMenu)(Object) this;

        // Create MKSlots for all panels targeting InventoryMenu
        // (creative tabs are part of the inventory context family)
        var slots = MenuKit.createSlotsForMenu(menu, MKContext.CREATIVE_TABS, player);
        for (MKSlot slot : slots) {
            ((AbstractContainerMenuInvoker) menu).trevorMod$addSlot(slot);
        }
    }
}
