package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.MKPressedTracker;

import dev.isxander.yacl3.gui.AbstractWidget;

import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>ACCEPTED aesthetic-only exception to §0019</b> (see
 * {@link MKVanillaButtonPressedMixin} class javadoc for the
 * full carve-out rationale).
 *
 * <p>YACL counterpart to {@link MKVanillaButtonPressedMixin} —
 * tracks press state on {@link AbstractWidget} instances (YACL's own
 * base, sibling-not-subclass of vanilla's AbstractWidget). Pairs with
 * {@code MKYaclControllerOverlayMixin} which reads the tracked
 * state at render time.
 *
 * <h3>{@code @Pseudo} for soft loading</h3>
 *
 * MK doesn't take YACL as a runtime dependency. When YACL is absent
 * (which is most environments — only consumer mods that JIJ YACL per
 * §0031 bring it in), Mixin sees the {@link AbstractWidget} target
 * class can't be resolved and silently skips this mixin entirely
 * thanks to {@code @Pseudo}. No runtime error, no behavior change.
 *
 * <h3>Why two YACL mixins</h3>
 *
 * YACL's {@code ControllerWidget} (toggles, sliders, dropdowns,
 * color-pickers) inherits {@code mouseClicked} from
 * {@link AbstractWidget} but overrides {@code render} on its own
 * subclasses. Mixin can't inject into inherited methods on a subclass
 * target — it needs the method body in the target's own bytecode. So
 * the split: this mixin on AbstractWidget for press tracking; the
 * sibling overlay mixin on ControllerWidget for render. Both share
 * state via {@link MKPressedTracker}.
 */
@ApiStatus.Internal
@Pseudo
@Mixin(AbstractWidget.class)
public abstract class MKYaclWidgetPressedMixin {

    // Mojang-mapped names: method_25402 → mouseClicked, method_25406
    // → mouseReleased. MK builds with officialMojangMappings, so the
    // runtime bytecode has Mojang names; using intermediary here
    // (without a refmap) would fail the mixin apply.

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("TAIL"))
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

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            at = @At("TAIL"))
    private void mk$clearOnRelease(MouseButtonEvent event,
                                         CallbackInfoReturnable<Boolean> cir) {
        // Explicit clear on release. The tracker also auto-clears
        // when GLFW reports release at next isPressedAndCheckRelease
        // call, but the explicit path is cleaner when YACL routes
        // the release event to us directly.
        MKPressedTracker.clearPressed(this);
    }
}
