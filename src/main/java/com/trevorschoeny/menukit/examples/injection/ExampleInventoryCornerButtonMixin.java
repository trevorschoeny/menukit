package com.trevorschoeny.menukit.examples.injection;

import com.trevorschoeny.menukit.examples.shared.ExampleInventoryCornerButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 10 injection example: Pattern 3 — panel at vanilla-screen-bounds corner.
 *
 * <p>Single 11×11 button in the above-right corner of {@link InventoryScreen}
 * and {@link CreativeModeInventoryScreen}. Posts an overlay message on click.
 * Demonstrates {@code ScreenOriginFns.fromScreenTopRight} and the
 * broad-target-with-runtime-gate mixin pattern.
 *
 * <h3>Mixin targeting strategy — broad target, narrow gate</h3>
 *
 * Targets {@link AbstractContainerScreen} (the abstract parent where both
 * {@code render} and {@code mouseClicked} are declared), then gates dispatch
 * with a runtime {@code instanceof} check narrowing to the two inventory
 * variants. This is the pattern the Phase 10 design doc recommends for
 * decorating multiple related screen classes: the mixin installs on a single
 * declaration point, and a tiny {@code instanceof} check handles scope.
 *
 * <h3>Why not multi-target {@code @Mixin}?</h3>
 *
 * An earlier version of this example used
 * {@code @Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class})}.
 * Fabric's refmap cannot remap {@code @Shadow} on inherited fields across
 * multiple target classes — it emits {@code Found a remappable @Shadow
 * annotation} errors and the mixin's field accesses resolve to garbage (or
 * not at all). Single-target mixins don't hit this limitation. Broad target
 * plus runtime gate sidesteps it entirely.
 *
 * <h3>Silent-inert failure mode (consumer-facing caveat)</h3>
 *
 * If an intermediate parent class overrides {@code render} or
 * {@code mouseClicked} without calling {@code super}, the mixin on
 * {@code AbstractContainerScreen} is installed but never fires for that
 * subclass. No warning, no error — the decoration just doesn't appear.
 * Consumers hitting this add a supplementary single-target mixin pinned to
 * the declaration point of the override. See the design doc's "Targeting
 * multiple screen classes" section.
 *
 * <p><b>Dev-only.</b> See {@link DevOnlyExampleMixinsPlugin}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ExampleInventoryCornerButtonMixin {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Unique
    private boolean examples$appliesToThisScreen() {
        Object self = this;
        return self instanceof InventoryScreen
                || self instanceof CreativeModeInventoryScreen;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void examples$render(GuiGraphics g, int mx, int my, float delta,
                                  CallbackInfo ci) {
        if (!examples$appliesToThisScreen()) return;
        ExampleInventoryCornerButton.ADAPTER.render(
                g,
                ExampleInventoryCornerButton.bounds(leftPos, topPos, imageWidth, imageHeight),
                mx, my,
                (AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void examples$click(MouseButtonEvent event, boolean doubleClick,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (!examples$appliesToThisScreen()) return;
        if (ExampleInventoryCornerButton.ADAPTER.mouseClicked(
                ExampleInventoryCornerButton.bounds(leftPos, topPos, imageWidth, imageHeight),
                event.x(), event.y(), event.button(),
                (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
