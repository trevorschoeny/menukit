package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.MKPressedTracker;


import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>ACCEPTED aesthetic-only exception to §0019</b> (see
 * {@link MKVanillaButtonPressedMixin} class javadoc for the
 * full carve-out rationale).
 *
 * <p>YACL counterpart to {@link MKVanillaButtonPressedMixin} —
 * tracks press state on YACL's controller-element widgets (subclasses
 * of YACL's own {@code AbstractWidget} base, sibling-not-subclass of
 * vanilla's AbstractWidget; since YACL 3.9.5 each element declares its
 * own mouseClicked, so the targets are per-element — see the @Mixin
 * target list). Pairs with {@code MKYaclControllerOverlayMixin} which
 * reads the tracked state at render time.
 *
 * <h3>{@code @Pseudo} for soft loading</h3>
 *
 * MK doesn't take YACL as a runtime dependency. When YACL is absent
 * (which is most environments — only consumer mods that JIJ YACL per
 * §0031 bring it in), Mixin sees the target classes can't be resolved
 * and silently skips this mixin entirely thanks to {@code @Pseudo}
 * (plus {@code require = 0} per injection, so partial-target presence
 * is tolerated too). No runtime error, no behavior change.
 *
 * <h3>Why two YACL mixins</h3>
 *
 * Mixin can't inject into inherited methods on a subclass target — it
 * needs the method body in the target's own bytecode. Press tracking
 * lives where {@code mouseClicked} bodies live (the per-element classes
 * since YACL 3.9.5); the pressed-overlay render lives where the render
 * body lives ({@code ControllerWidget.extractRenderState} — the sibling
 * overlay mixin). Both share state via {@link MKPressedTracker}.
 */
@ApiStatus.Internal
@Pseudo
@Mixin(targets = {
        // 26.2 / YACL 3.9.5 retarget: YACL's AbstractWidget no longer
        // declares mouseClicked — each controller element declares its own
        // (verified against the 3.9.5+26.2 jar, 2026-07-02). The old
        // single-injection-on-the-base-body seam is gone; the equivalent
        // coverage is every ControllerWidget element that declares
        // mouseClicked. (In 3.8.1, subclasses that overrode mouseClicked
        // without a super-call never hit the base injection either — so
        // per-element injection IS the faithful translation, not a
        // broadening.) String targets keep this @Pseudo-soft: any class
        // YACL drops or renames later just skips.
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
        // Only mark pressed when YACL's dispatch confirmed the click
        // was claimed by this widget (return value true = "I'm
        // handling this click"). Filters out cursor-over-widget
        // clicks that didn't actually fire on us.
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            MKPressedTracker.markPressed(this);
        }
    }

    // The explicit clear-on-release path from 3.8.1 is gone with the base
    // mouseReleased body (only slider/color-picker/list-entry declare it in
    // 3.9.5, and the release seam differs per class). The tracker's
    // auto-clear — isPressedAndCheckRelease polls GLFW's real button state
    // at render time — already covered every widget whose release never
    // routed through the base body in 3.8.1, and now covers all of them.
}
