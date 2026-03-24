package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKSlotState;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Creative inventory right-click handler — catches clicks that bypass
 * {@code slotClicked} (especially hotbar slots in creative mode).
 *
 * <p>Delegates to {@link MKSlotState}'s right-click handlers, ensuring
 * the unified callback system works in creative mode too.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MKCreativeRightClickMixin
        extends AbstractContainerScreen<net.minecraft.world.inventory.AbstractContainerMenu> {

    protected MKCreativeRightClickMixin() { super(null, null, null); }

    // ── slotClicked: catches most creative inventory clicks ──────────────
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void menuKit$onCreativeSlotRightClick(Slot slot, int slotId, int button,
                                                    ClickType clickType, CallbackInfo ci) {
        if (button != 1 || clickType != ClickType.PICKUP) return;
        if (slot == null) return;

        MKSlotState state = MKSlotStateRegistry.get(slot);
        if (state == null || !state.hasRightClickHandlers()) return;

        if (state.fireRightClick(slot, slot.getItem())) {
            ci.cancel();
        }
    }

    // ── mouseClicked: catches hotbar clicks that bypass slotClicked ──────
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$onCreativeMouseRightClick(MouseButtonEvent event, boolean bl,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 1) return;
        if (this.hoveredSlot == null) return;

        Slot hovered = this.hoveredSlot;
        MKSlotState state = MKSlotStateRegistry.get(hovered);
        if (state == null || !state.hasRightClickHandlers()) return;

        if (state.fireRightClick(hovered, hovered.getItem())) {
            cir.setReturnValue(true);
        }
    }
}
