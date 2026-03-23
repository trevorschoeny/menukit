package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MenuKit internal mixin — fixes click detection for MKSlots in screens that
 * extend {@link AbstractRecipeBookScreen} (like {@link net.minecraft.client.gui.screens.inventory.InventoryScreen}).
 *
 * <p>AbstractRecipeBookScreen overrides {@code hasClickedOutside()} WITHOUT
 * calling super, so the override on AbstractContainerScreen never fires.
 * This mixin adds the panel bounds check directly on AbstractRecipeBookScreen.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(AbstractRecipeBookScreen.class)
public class MKRecipeBookScreenMixin {

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void menuKit$expandClickBounds(double mouseX, double mouseY,
                                            int leftPos, int topPos,
                                            CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;

        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        if (MenuKit.isClickInsideAnyPanel(mouseX, mouseY, leftPos, topPos, context)) {
            cir.setReturnValue(false);
        }
    }
}
