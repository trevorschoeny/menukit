package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.MK;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lifts vanilla's held-item name and action-bar message above the HUD's status column
 * while anything shows in it ({@code HudPanel.Builder.statusColumn}, 7.3.0). Both are
 * centred over the hotbar and would land on the column otherwise.
 *
 * <p>Each draw is wrapped in a pose translated up by {@link MK#statusColumnLift()}: the
 * push at HEAD, the pop at every return, so every path through the method stays
 * balanced. Moving both by the same amount keeps vanilla's spacing between them, and
 * between the name and what's below it (the level number, or the hotbar in creative).
 */
@ApiStatus.Internal
@Mixin(Hud.class)
public abstract class MKHudStatusLiftMixin {

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"))
    private void mk$liftItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        mk$push(graphics);
    }

    @Inject(method = "extractSelectedItemName", at = @At("RETURN"))
    private void mk$dropItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"))
    private void mk$liftOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        mk$push(graphics);
    }

    @Inject(method = "extractOverlayMessage", at = @At("RETURN"))
    private void mk$dropOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    private static void mk$push(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        int lift = MK.statusColumnLift();
        if (lift > 0) graphics.pose().translate(0f, -lift);
    }
}
