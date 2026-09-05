package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MK;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders MenuKit HUD panels after vanilla's HUD finishes.
 *
 * <p>26.2: vanilla split the old {@code Gui} into {@code Gui} (screen/overlay
 * manager) and {@code Hud} (the in-world HUD); HUD rendering became
 * {@code Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)} under the
 * extract/draw render split. Same injection point, new home. The old
 * {@code options.hideGui} flag became {@code Hud.isHidden()}.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
@ApiStatus.Internal
@Mixin(Hud.class)
public abstract class MKGuiMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract boolean isHidden();

    /**
     * Renders all registered MenuKit HUD panels.
     */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void mk$renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (minecraft.player == null || isHidden()) return;
        MK.renderHud(graphics, deltaTracker);
    }
}
