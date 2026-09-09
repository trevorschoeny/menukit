package com.trevlar.menukit.mixin;

import com.trevlar.menukit.core.SlotAddresses;
import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.WindowEngine;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces the two {@linkplain com.trevlar.menukit.window.SlotOperations operations}
 * MenuKit declares, at vanilla's own seams for them, for every slot kind:
 * <ul>
 *   <li>{@link BehaviorKeys#COLLECT} at {@code canTakeItemForPickAll} — the
 *       per-slot test inside {@code PICKUP_ALL} (double-click collect), which
 *       sweeps every slot holding the carried type;</li>
 *   <li>{@link BehaviorKeys#DRAG_FILL} at {@code canDragTo} — the per-slot test
 *       inside {@code QUICK_CRAFT} (dragging a carried stack across slots to
 *       spread it).</li>
 * </ul>
 *
 * Both seams are menu-level and run on both sides inside {@code doClick}, so a
 * declaration made once at init holds identically on the client's prediction
 * and the server's authoritative run. A slot resolves to its default
 * ({@code TRUE}, vanilla) unless a consumer set the key by address, so untouched
 * menus are exactly vanilla. Kind-blind: a created pocket declared
 * {@code collect(false)} and a vanilla slot a locking mod set {@code COLLECT=FALSE}
 * on are refused by the same line. A third-party operation is enforced by the mod
 * that declared it, in its own code, the same way.
 *
 * <p>Known limit: a menu subclass that overrides one of these methods without
 * calling {@code super} (creative's item picker overrides
 * {@code canTakeItemForPickAll}) bypasses the injection on that menu.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MKCOperationMixin {

    @Inject(method = "canTakeItemForPickAll", at = @At("HEAD"), cancellable = true)
    private void mkc$collectOperation(ItemStack carried, Slot slot, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!WindowEngine.resolve(SlotAddresses.of(self, slot), BehaviorKeys.COLLECT).asBoolean()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
    private void mkc$dragFillOperation(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!WindowEngine.resolve(SlotAddresses.of(self, slot), BehaviorKeys.DRAG_FILL).asBoolean()) {
            cir.setReturnValue(false);
        }
    }
}
