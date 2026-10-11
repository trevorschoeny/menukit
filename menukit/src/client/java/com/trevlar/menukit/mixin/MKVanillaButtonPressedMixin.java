package com.trevlar.menukit.mixin;

import com.trevlar.menukit.core.VanillaLook;
import com.trevlar.menukit.core.PressedTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>MenuKit's one exception to its rule of not restyling vanilla UI.</b>
 *
 * <p>Applies MenuKit's vanilla-style pressed visual (inverted bevel and a dark
 * overlay) to every vanilla {@link AbstractButton} in the game (title screen,
 * Options, Pause, world select, Controls), so a vanilla button looks pushed in
 * while pressed, the same as MenuKit's own VANILLA-styled controls. With the two
 * YACL mixins it is library-wide and always on, and it changes how buttons MenuKit
 * did not create look. It stays because players see it as part of MenuKit's feel,
 * and it is meant to stay the only one: a second styling change to vanilla would
 * need its own decision.
 *
 * <p>It is visual only. It reads existing widget state ({@code isHovered},
 * {@code active}, a press-tracker flag) and draws over vanilla's own draw;
 * pressing a button does exactly what it did before. A change here that
 * intercepted input or changed behaviour would fall outside the exception.
 *
 * <h3>Known costs</h3>
 *
 * <ol>
 *   <li>Consumers can't opt out; there is no toggle. Every mod using MenuKit
 *       brings the visual to every screen.</li>
 *   <li>If another mod also draws over vanilla AbstractButton, both overlays
 *       may paint. Both are visual only, so the result looks odd but works.</li>
 *   <li>A Mojang refactor of the injected render method breaks the mixin, but
 *       loudly at load, so a vanilla update makes it obvious.</li>
 * </ol>
 *
 * <h3>How it works</h3>
 *
 * Press state is tracked per button: set in {@code onClick}, which vanilla only
 * calls when the press starts on this button, and cleared on the first frame after
 * the mouse is released. The overlay is only drawn while hovered, so dragging off
 * the button while holding removes it and dragging back shows it again, as vanilla
 * buttons behave.
 *
 * <p>Polling GLFW alone gave a false positive: click elsewhere, drag onto a button
 * while holding, and it looked pressed. Tracking through onClick fixes that.
 */
@ApiStatus.Internal
@Mixin(AbstractButton.class)
public abstract class MKVanillaButtonPressedMixin {

    @Inject(method = "onClick", at = @At("TAIL"))
    private void mk$markPressed(MouseButtonEvent event, boolean alreadyHandled,
                                      CallbackInfo ci) {
        // Vanilla's dispatch only calls onClick when isMouseOver is true,
        // so reaching here means the press originated on this button.
        PressedTracker.markPressed(this);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void mk$drawVanillaPressedOverlay(GuiGraphicsExtractor graphics, int mouseX,
                                                    int mouseY, float partialTick,
                                                    CallbackInfo ci) {
        // The YACL mixins use the same PressedTracker. isPressedAndCheckRelease
        // clears every entry once GLFW reports the mouse released, so stale
        // entries go on the next frame.
        if (!PressedTracker.isPressedAndCheckRelease(this)) return;

        // No overlay while the player has dragged off the button with the mouse
        // still held, matching vanilla's hover visual.
        AbstractButton self = (AbstractButton) (Object) this;
        if (!self.active) return;
        if (!self.isHovered()) return;

        VanillaLook.pressedOverlay(graphics,
                self.getX(), self.getY(),
                self.getWidth(), self.getHeight());
    }
}
