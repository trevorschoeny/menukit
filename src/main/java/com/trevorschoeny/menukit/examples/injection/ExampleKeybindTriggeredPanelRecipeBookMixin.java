package com.trevorschoeny.menukit.examples.injection;

import com.trevorschoeny.menukit.examples.shared.ExampleKeybindTriggeredPanel;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 10 injection example: Pattern 1 — supplementary mixin that compensates
 * for {@link AbstractRecipeBookScreen}'s non-super-calling override of
 * {@code keyPressed}.
 *
 * <p>{@link InventoryScreen} inherits from {@code AbstractRecipeBookScreen},
 * which overrides {@code keyPressed} to handle the recipe-book toggle. That
 * override does not call {@code super.keyPressed(...)}, so mixins on
 * {@code AbstractContainerScreen.keyPressed} never fire when the player is in
 * the inventory screen. This mixin pins a second hook to
 * {@code AbstractRecipeBookScreen.keyPressed} itself — the declaration point
 * that the dispatch chain actually reaches.
 *
 * <p>The runtime {@code instanceof InventoryScreen} gate narrows dispatch to
 * just the inventory variant. {@code AbstractRecipeBookScreen} is also the
 * parent of crafting-table, smoker, and blast-furnace screens; without the
 * gate, this keybind would hijack those too.
 *
 * <p>Cancelling at HEAD with {@code cir.setReturnValue(true)} also prevents
 * double-toggle if a future vanilla update adds a {@code super.keyPressed}
 * call — the return-value cancellation stops the method before any super chain
 * could reach the primary mixin on {@code AbstractContainerScreen}.
 *
 * <p><b>Dev-only.</b> See {@link DevOnlyExampleMixinsPlugin}.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class ExampleKeybindTriggeredPanelRecipeBookMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void examples$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof InventoryScreen)) return;
        if (event.key() == GLFW.GLFW_KEY_P) {
            ExampleKeybindTriggeredPanel.visible = !ExampleKeybindTriggeredPanel.visible;
            cir.setReturnValue(true);
        }
    }
}
