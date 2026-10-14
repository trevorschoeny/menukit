package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.element.MKTooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Marks item tooltips for {@link MKTooltip.Kind#ITEM} at the step vanilla's item paths
 * share: {@code Screen.getTooltipFromItem}, which turns a stack into its tooltip lines.
 * In 26.3 it is how {@code GuiGraphicsExtractor.setTooltipForNextFrame(font, stack, x, y)}
 * (an item in chat, a villager's trades, the stonecutter, the item widget in the stats
 * screen, and any mod calling that overload), a container's slot tooltip, and the
 * recipe book's buttons and ghost slots get their lines, each queuing them straight
 * after. The suppress hook takes the mark with that tooltip.
 *
 * <p>A screen frame's start drops any mark left over, so a stack read for its lines
 * outside a screen (a HUD that shows an item's lines) cannot mark a screen's tooltip.
 */
@ApiStatus.Internal
@Mixin(Screen.class)
public abstract class MKItemTooltipMarkMixin {

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"))
    private static void mk$markItemTooltip(Minecraft minecraft, ItemStack itemStack,
                                           CallbackInfoReturnable<List<Component>> cir) {
        MKTooltip.itemTooltipNext();
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void mk$screenFrameStarts(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        MKTooltip.screenFrameStarts();
    }
}
