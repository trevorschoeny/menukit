package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKSlot;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MenuKit internal mixin — adds MKSlots to ANY container menu that calls
 * {@code addStandardInventorySlots()}.
 *
 * <p>This covers virtually every vanilla container with player inventory:
 * chests, furnaces, crafting tables, anvils, etc. MKSlots are added AFTER
 * vanilla finishes adding its own slots, ensuring slot indices are consistent
 * between client and server.
 *
 * <p>InventoryMenu is SKIPPED here — it's handled separately by
 * {@link MKMenuMixin} because it has unique constructor parameters and
 * creative mode handling.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(AbstractContainerMenu.class)
public class MKGenericMenuMixin {

    @Inject(method = "addStandardInventorySlots", at = @At("TAIL"))
    private void menuKit$addSlotsToAnyMenu(Container container, int x, int y, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Skip InventoryMenu — handled by MKMenuMixin with creative mode support
        if (menu instanceof InventoryMenu) return;

        // Need a Player reference for per-player container management
        if (!(container instanceof Inventory inventory)) return;

        // Find a default context for this menu class
        MKContext context = MKContext.defaultForMenuClass(menu.getClass());
        if (context == null) return;

        // Check if any panels are registered for this context
        if (!MenuKit.hasPanelsForContext(context)) return;

        // Create and add MKSlots
        java.util.List<MKSlot> mkSlots = MenuKit.createSlotsForMenu(menu, context, inventory.player);
        for (MKSlot slot : mkSlots) {
            ((AbstractContainerMenuInvoker) menu).trevorMod$addSlot(slot);
        }
    }
}
