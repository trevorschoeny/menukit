package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.SlotScreenDispatcher;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Library-owned registered-slot render dispatch — half of inventory-screen parity.
 *
 * <p>Targets {@code AbstractContainerScreen.renderContents}, which is the single
 * point every container screen renders its slots through:
 * <ul>
 *   <li>the survival inventory routes here via {@code AbstractRecipeBookScreen.render};</li>
 *   <li>the creative screen routes here via its {@code render → super.render};</li>
 *   <li>every chest/furnace/anvil/etc. routes here via {@code AbstractContainerScreen.render}.</li>
 * </ul>
 * So one mixin gives slots the screen-completeness panels already have — no
 * per-screen consumer mixin, no silently-missed screen (creative included).
 *
 * <h3>Two injection points, two operations</h3>
 * <ul>
 *   <li><b>HEAD</b> — {@code prepare}: update hover-reveal + reposition registered
 *       slots for this screen, <em>before</em> {@code renderContents} computes the
 *       hovered slot (offset within the method) and draws the grid. So the frame's
 *       reveal + layout are current for both hover resolution and the draw.</li>
 *   <li><b>after {@code Matrix3x2fStack.popMatrix}</b> — {@code render}: draw the
 *       decoration + registered slots. {@code renderContents} pushes a matrix,
 *       translates to {@code leftPos/topPos}, draws the vanilla slots, then pops —
 *       so injecting right after the pop lands in absolute screen space (where the
 *       slot helpers expect to draw, {@code leftPos + renderX}) and after the
 *       vanilla grid, but still before {@code render}'s carried-item + tooltip.
 *       This is the exact z-window the hand-written consumer mixin used.</li>
 * </ul>
 *
 * <p>Thin by design: both injects forward to {@link SlotScreenDispatcher}, which
 * no-ops when MenuKit-Containers is absent (§0042 — this mixin names no
 * registered-slot type).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKSlotRenderMixin {

    @Inject(method = "renderContents", at = @At("HEAD"))
    private void mk$slotPrepare(GuiGraphics graphics, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        SlotScreenDispatcher.firePrepare(
                (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
    }

    @Inject(
            method = "renderContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix3x2fStack;popMatrix()Lorg/joml/Matrix3x2fStack;",
                    shift = At.Shift.AFTER
            )
    )
    private void mk$slotRender(GuiGraphics graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        SlotScreenDispatcher.fireRender(
                (AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY, partialTick);
    }
}
