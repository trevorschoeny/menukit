package com.trevorschoeny.menukit.examples.injection;

import com.trevorschoeny.menukit.examples.shared.ExampleKeybindTriggeredPanel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 10 injection example: Pattern 1 — input-intercept mixin + pre-declared
 * panel with supplier-driven visibility.
 *
 * <p>Primary mixin: hooks {@code render} (TAIL) and {@code keyPressed} (HEAD)
 * on {@link AbstractContainerScreen}. Fires for every container screen subclass
 * whose dispatch chain reaches the parent's method — which includes chests,
 * creative inventory, and most custom container screens.
 *
 * <h3>The {@code InventoryScreen} supplement</h3>
 *
 * {@code InventoryScreen}'s dispatch chain does <em>not</em> reach
 * {@code AbstractContainerScreen.keyPressed} — {@code AbstractRecipeBookScreen}
 * (the parent class that adds the recipe-book toggle widget) overrides
 * {@code keyPressed} without calling {@code super}, so the chain stops there.
 * This is the <b>silent-inert failure mode</b> the design doc warns about:
 * the mixin installs successfully, but nothing fires when the player presses
 * a key inside {@code InventoryScreen}.
 *
 * <p>The fix is a supplementary mixin pinned to
 * {@link ExampleKeybindTriggeredPanelRecipeBookMixin
 * AbstractRecipeBookScreen.keyPressed}, gated with an {@code instanceof
 * InventoryScreen} runtime check so it doesn't hijack keypresses on
 * crafting-table, smoker, blast-furnace, and other recipe-book-bearing
 * screens. Cancelling vanilla's {@code keyPressed} at HEAD prevents
 * double-toggle if {@code AbstractRecipeBookScreen.keyPressed} ever starts
 * super-calling in a future update.
 *
 * <h3>Shared state</h3>
 *
 * Visibility state lives on {@link ExampleKeybindTriggeredPanel} as a plain
 * static field. Both mixins read and write it. Keeping it off the mixin class
 * avoids mixin-static-field quirks where each target class gets its own copy.
 *
 * <p><b>Dev-only.</b> See {@link DevOnlyExampleMixinsPlugin}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ExampleKeybindTriggeredPanelMixin {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Inject(method = "render", at = @At("TAIL"))
    private void examples$render(GuiGraphics g, int mx, int my, float delta,
                                  CallbackInfo ci) {
        ExampleKeybindTriggeredPanel.ADAPTER.render(
                g,
                ExampleKeybindTriggeredPanel.bounds(leftPos, topPos, imageWidth, imageHeight),
                mx, my,
                (AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void examples$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == GLFW.GLFW_KEY_P) {
            ExampleKeybindTriggeredPanel.visible = !ExampleKeybindTriggeredPanel.visible;
            cir.setReturnValue(true);
        }
    }
}
