package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.MK;
import net.minecraft.client.gui.components.ChatComponent;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lifts the chat clear of the HUD's status column ({@code HudPanel.Builder.statusColumn},
 * 7.3.0) while the column reaches above the chat's bottom edge, by exactly that much.
 *
 * <p>Every chat pass (drawing it on the HUD, drawing it open, and finding the clickable
 * text under the mouse) goes through the one private {@code extractRenderState} that
 * takes the screen height and puts the chat's bottom 40 px above it. Lowering that
 * height moves all three together, so a lifted chat's links still click where they show.
 */
@ApiStatus.Internal
@Mixin(ChatComponent.class)
public abstract class MKChatStatusLiftMixin {

    @Shadow private int getWidth() { throw new AssertionError(); }

    @ModifyVariable(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int mk$liftChat(int screenHeight) {
        // The chat spans from the window's left edge to its width plus the 4 px margins
        // either side of its lines; a column wholly right of that leaves it in place.
        return screenHeight - MK.statusColumnClearance(screenHeight - 40, 0, getWidth() + 8);
    }
}
