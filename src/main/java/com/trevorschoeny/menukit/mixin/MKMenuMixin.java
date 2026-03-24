package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKSlot;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MenuKit internal mixin — injects MKSlots into menus during construction.
 *
 * <p>Targets {@link InventoryMenu} specifically because its constructor provides
 * the {@link Player} reference needed for per-player container management.
 * Other menu types can be added with additional injection points.
 *
 * <p>Runs on BOTH client and server — slot counts must match for network sync.
 * Uses SURVIVAL_INVENTORY context since InventoryMenu is always constructed
 * with survival dimensions. Creative mode re-positions slots separately via
 * MKCreativeMixin.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(InventoryMenu.class)
public class MKMenuMixin {


    @Inject(method = "<init>", at = @At("TAIL"))
    private void menuKit$createSlots(Inventory inventory, boolean active,
                                      Player owner, CallbackInfo ci) {
        // Ask MenuKit for all slots registered to InventoryMenu via SURVIVAL_INVENTORY context.
        // Not creative context here: InventoryMenu constructor runs for both modes,
        // and creative mode fixes positions later via MKCreativeMixin.
        java.util.List<MKSlot> mkSlots = MenuKit.createSlotsForMenu(
                (net.minecraft.world.inventory.AbstractContainerMenu)(Object) this,
                MKContext.SURVIVAL_INVENTORY, owner);

        for (MKSlot slot : mkSlots) {
            ((AbstractContainerMenuInvoker) this).trevorMod$addSlot(slot);
        }

        // DEBUG: log slot count + side
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        String side = (owner instanceof net.minecraft.server.level.ServerPlayer) ? "SERVER" : "CLIENT";
        com.trevorschoeny.menukit.MenuKit.LOGGER.info(
            "[MenuKit] MKMenuMixin {} added {} MKSlots, total menu slots={}",
            side, mkSlots.size(), menu.slots.size());
    }

    // Shift-click (quickMoveStack) is now handled by MKGenericMenuMixin
    // on AbstractContainerMenu — covers ALL menu types including InventoryMenu.
}
