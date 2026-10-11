package com.trevlar.menukit.mixin;

import com.trevlar.menukit.core.PressedTracker;


import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Part of MenuKit's one styling exception (see {@link MKVanillaButtonPressedMixin}).
 *
 * <p>The YACL counterpart of {@link MKVanillaButtonPressedMixin}: tracks press
 * state on YACL's controller element widgets. These extend YACL's own
 * {@code AbstractWidget}, not vanilla's, and since YACL 3.9.5 each element declares
 * its own mouseClicked, so the targets are listed per element.
 * {@code MKYaclControllerOverlayMixin} reads the tracked state at render time.
 *
 * <p>MenuKit doesn't depend on YACL at runtime. When YACL is absent (most setups;
 * only consumer mods that bundle it bring it in), {@code @Pseudo} makes Mixin skip
 * this mixin quietly, and {@code require = 0} on each injection tolerates some
 * targets being missing.
 *
 * <p>There are two YACL mixins because Mixin can only inject into a method whose
 * body is in the target's own bytecode, not an inherited one. Press tracking goes
 * where the {@code mouseClicked} bodies are (the per-element classes since 3.9.5);
 * the overlay goes where the render body is
 * ({@code ControllerWidget.extractRenderState}). They share state through
 * {@link PressedTracker}.
 */
@ApiStatus.Internal
@Pseudo
@Mixin(targets = {
        // Since 26.2 / YACL 3.9.5, YACL's AbstractWidget no longer declares
        // mouseClicked; each controller element declares its own (checked
        // against the 3.9.5+26.2 jar). So instead of one injection on the base,
        // every ControllerWidget element that declares mouseClicked is a target.
        // In 3.8.1, subclasses that overrode mouseClicked without calling super
        // never reached the base injection either, so this covers the same
        // widgets as before. String targets keep it soft: a class YACL later
        // drops or renames is just skipped.
        "dev.isxander.yacl3.gui.controllers.ActionController$ActionControllerElement",
        "dev.isxander.yacl3.gui.controllers.BooleanController$BooleanControllerElement",
        "dev.isxander.yacl3.gui.controllers.ColorController$ColorControllerElement",
        "dev.isxander.yacl3.gui.controllers.LabelController$LabelControllerElement",
        "dev.isxander.yacl3.gui.controllers.TickBoxController$TickBoxControllerElement",
        "dev.isxander.yacl3.gui.controllers.cycling.CyclingControllerElement",
        "dev.isxander.yacl3.gui.controllers.dropdown.AbstractDropdownControllerElement",
        "dev.isxander.yacl3.gui.controllers.slider.SliderControllerElement",
        "dev.isxander.yacl3.gui.controllers.string.StringControllerElement"
})
public abstract class MKYaclWidgetPressedMixin {

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("TAIL"), require = 0)
    private void mk$trackPress(MouseButtonEvent event, boolean alreadyHandled,
                                     CallbackInfoReturnable<Boolean> cir) {
        // Only mark pressed when this widget claimed the click (returned true),
        // not merely because the cursor was over it.
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            PressedTracker.markPressed(this);
        }
    }

    // There is no release hook. 3.9.5 removed the base mouseReleased body (only
    // the slider, colour picker and list entry declare one, each differently).
    // The tracker clears itself instead: isPressedAndCheckRelease checks the
    // real button state at render time, which already handled every widget
    // whose release skipped the base body in 3.8.1.
}
