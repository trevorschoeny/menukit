package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders MenuKit HUD panels after vanilla's HUD finishes.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
@Mixin(Gui.class)
public class MKGuiMixin {

    @Shadow
    protected Minecraft minecraft;

    /**
     * Renders all registered MenuKit HUD panels.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void menuKit$renderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.options.hideGui) return;
        MenuKit.renderHud(graphics, deltaTracker);
    }
}
