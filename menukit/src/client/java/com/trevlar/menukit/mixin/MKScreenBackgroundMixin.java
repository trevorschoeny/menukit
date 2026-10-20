package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.menukit.inject.ScreenBackground;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The background layer's two hooks (7.4.0; {@link ScreenBackground}). The screen's
 * {@code extractBackground} call is wrapped, not replaced: vanilla's background and every
 * mod's hook in it run as before. At the base method's return, the dim done and the
 * screen's own image next, the background panels draw. Fabric's {@code afterBackground}
 * fires after the wrapped call, so its listeners draw over them.
 */
@ApiStatus.Internal
@Mixin(Screen.class)
public abstract class MKScreenBackgroundMixin {

    @WrapOperation(method = "extractRenderStateWithTooltipAndSubtitles", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void mk$backgroundPass(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
                                   Operation<Void> original) {
        ScreenBackground.around(screen, graphics, mouseX, mouseY, () -> original.call(screen, graphics, mouseX, mouseY, a));
    }

    @Inject(method = "extractBackground", at = @At("RETURN"), require = 1)
    private void mk$afterDim(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        ScreenBackground.afterDim((Screen) (Object) this, graphics, mouseX, mouseY);
    }
}
