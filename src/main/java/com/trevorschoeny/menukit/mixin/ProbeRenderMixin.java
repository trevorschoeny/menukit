package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.verification.RegionProbes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side dev-scaffolding mixin: after every
 * {@link AbstractContainerScreen#render} call, iterate the registered
 * {@link RegionProbes} inventory adapters. Gated at the {@code RegionProbes}
 * layer — when probes are off the render path is a single boolean check.
 *
 * <p>Lives in the standard {@code mixin/} package so the mixins.json
 * package-relative resolution finds it; the associated state and render
 * logic live in {@code verification/RegionProbes} to keep the dev-
 * verification surface grouped.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ProbeRenderMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void menuKit$renderRegionProbes(GuiGraphics graphics,
                                              int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        RegionProbes.renderInventoryProbes(self, graphics, mouseX, mouseY);
    }
}
