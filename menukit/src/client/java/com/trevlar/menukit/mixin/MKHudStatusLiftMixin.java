package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.MK;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lifts vanilla's held-item name and action-bar message clear of the HUD's status
 * column ({@code HudPanel.Builder.statusColumn}, 7.3.0) while the column reaches them.
 * Both are centred over the hotbar and would land on it otherwise.
 *
 * <p>Each draw is wrapped in a pose translated up by how far the column rises above the
 * name's bottom edge ({@link MK#statusColumnClearance}): the push at HEAD, the pop at
 * every return, so every path through the method stays balanced. The action-bar message
 * sits above the name and moves by the same amount, keeping vanilla's spacing between
 * the two.
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
        // The name's bottom edge as Hud.extractSelectedItemName draws it: at
        // guiHeight - 59 (14 lower where the player can't be hurt), 9 px of text and
        // textWithBackdrop's 2 px backdrop below.
        Minecraft mc = Minecraft.getInstance();
        boolean hurtable = mc.gameMode != null && mc.gameMode.canHurtPlayer();
        int nameBottom = graphics.guiHeight() - 59 + (hurtable ? 0 : 14) + 9 + 2;
        int lift = MK.statusColumnClearance(nameBottom, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (lift > 0) graphics.pose().translate(0f, -lift);
    }
}
