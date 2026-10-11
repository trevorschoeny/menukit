package com.trevlar.menukit.mixin;

import com.trevlar.menukit.core.VanillaLook;
import com.trevlar.menukit.core.PressedTracker;

import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Part of MenuKit's one styling exception (see {@link MKVanillaButtonPressedMixin}).
 *
 * <p>Draws the pressed overlay on YACL controller widgets (toggles, sliders,
 * dropdowns, colour pickers) while they are held down. Press state comes from
 * {@link PressedTracker}, set by {@link MKYaclWidgetPressedMixin} on the
 * AbstractWidget superclass. Both YACL mixins are {@code @Pseudo}, so they are
 * skipped when YACL isn't loaded.
 *
 * <p>Scoped to {@link ControllerWidget} rather than AbstractWidget because
 * controllers are the "click to toggle or adjust" elements. YACL's other widgets
 * (search field, option-list entries and so on) don't get the overlay.
 */
@ApiStatus.Internal
@Pseudo
@Mixin(ControllerWidget.class)
public abstract class MKYaclControllerOverlayMixin {

    // In 26.2 / YACL 3.9.5 the render entry point followed vanilla's
    // extract/draw split, from Renderable.render to extractRenderState
    // (checked against the 3.9.5+26.2 jar).
    // require = 0: a YACL release that renames this method should lose the
    // pressed overlay on its controllers, not crash the game at load.
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"), require = 0)
    private void mk$drawPressedOverlay(GuiGraphicsExtractor graphics, int mouseX,
                                             int mouseY, float partialTick,
                                             CallbackInfo ci) {
        if (!PressedTracker.isPressedAndCheckRelease(this)) return;

        // isHovered() is on ControllerWidget, not YACL's AbstractWidget base;
        // getDimension() is inherited from AbstractWidget. The raw type is fine
        // because the type parameter doesn't matter for hover and bounds.
        @SuppressWarnings("rawtypes")
        ControllerWidget self = (ControllerWidget) (Object) this;
        if (!self.isHovered()) return;

        Dimension<Integer> dim = self.getDimension();
        VanillaLook.pressedOverlay(graphics,
                dim.x(), dim.y(), dim.width(), dim.height());
    }
}
