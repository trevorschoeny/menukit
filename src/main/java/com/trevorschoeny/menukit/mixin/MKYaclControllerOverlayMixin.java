package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.ControlStyle;
import com.trevorschoeny.menukit.core.MKPressedTracker;

import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;

import net.minecraft.client.gui.GuiGraphics;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>ACCEPTED aesthetic-only exception to §0019</b> (see
 * {@link MKVanillaButtonPressedMixin} class javadoc for the
 * full carve-out rationale).
 *
 * <p>Render-time overlay for YACL controller widgets (toggles,
 * sliders, dropdowns, color-pickers) when they're being pressed.
 * Reads press state from {@link MKPressedTracker} (set by
 * {@link MKYaclWidgetPressedMixin} on the AbstractWidget
 * superclass). Both YACL mixins use {@code @Pseudo} so they
 * silently skip when YACL isn't loaded.
 *
 * <p>Scoped to {@link ControllerWidget} (not the broader
 * AbstractWidget) because controllers are the user-facing "click to
 * toggle/adjust" elements — applying the overlay to YACL's other
 * widget kinds (search field, option-list entries, etc.) would be
 * out of scope.
 */
@ApiStatus.Internal
@Pseudo
@Mixin(ControllerWidget.class)
public abstract class MKYaclControllerOverlayMixin {

    // Mojang-mapped: method_25394 → render. See sibling mixin's
    // comment about MK using officialMojangMappings.
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("TAIL"))
    private void mk$drawPressedOverlay(GuiGraphics graphics, int mouseX,
                                             int mouseY, float partialTick,
                                             CallbackInfo ci) {
        if (!MKPressedTracker.isPressedAndCheckRelease(this)) return;

        // isHovered() is on ControllerWidget (not the YACL AbstractWidget
        // base). getDimension() is inherited from AbstractWidget. Cast
        // to ControllerWidget (raw type — YACL's parameterization is
        // irrelevant for this hover/coord access).
        @SuppressWarnings("rawtypes")
        ControllerWidget self = (ControllerWidget) (Object) this;
        if (!self.isHovered()) return;

        Dimension<Integer> dim = self.getDimension();
        ControlStyle.renderVanillaPressedOverlay(graphics,
                dim.x(), dim.y(), dim.width(), dim.height());
    }
}
