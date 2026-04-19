package com.trevorschoeny.menukit.examples.injection;

import com.trevorschoeny.menukit.examples.shared.ExampleKeybindTriggeredPanel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 10 injection example: Pattern 1 — supplementary render mixin that
 * compensates for {@link AbstractRecipeBookScreen}'s override of
 * {@code render}.
 *
 * <p>The primary {@link ExampleKeybindTriggeredPanelMixin} hooks render TAIL
 * on {@code AbstractContainerScreen}. In survival inventory that never fires
 * — {@code InventoryScreen} inherits render through {@code AbstractRecipeBookScreen},
 * whose override does not reliably super-call (the render chain depends on
 * recipe-book state). Without this supplementary, the keybind-toggled text
 * label is silently inert in survival inventory even though {@code visible}
 * flips correctly.
 *
 * <p>This mixin pins render TAIL on {@code AbstractRecipeBookScreen} directly,
 * gated by {@code instanceof InventoryScreen} so it doesn't paint the text
 * on crafting-table, smoker, or other recipe-book-bearing screens. Paints
 * AFTER the recipe-book widget draws, so the text lands on top of it rather
 * than behind it (failure mode #2 — z-order occlusion — fixed).
 *
 * <h3>Why {@code extends AbstractContainerScreen}?</h3>
 *
 * Nothing in this mixin body actually reads {@code leftPos} / {@code topPos} /
 * {@code imageWidth} / {@code imageHeight} — the shared
 * {@code ExampleKeybindTriggeredPanel.ADAPTER} is positioned via
 * {@code fromScreenTopLeft} relative to the same bounds helper the primary
 * uses. But the helper's {@code bounds(...)} call still needs those four
 * ints, and they live on {@code AbstractContainerScreen}. Rather than add
 * {@code @Shadow} fields that would hit failure mode #4 (inherited-field
 * remap), extending the parent class exposes them through Java's inheritance.
 * Same shape as
 * {@link ExampleInventoryCornerButtonRecipeBookRenderMixin}.
 *
 * <p>The mixin class is never instantiated; the constructor below exists
 * solely to satisfy the compiler.
 *
 * <p><b>Dev-only.</b> See {@link DevOnlyExampleMixinsPlugin}.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class ExampleKeybindTriggeredPanelRecipeBookRenderMixin
        extends AbstractContainerScreen<AbstractContainerMenu> {

    /** Never called — mixin classes are not instantiated. */
    protected ExampleKeybindTriggeredPanelRecipeBookRenderMixin(
            AbstractContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void examples$render(GuiGraphics g, int mx, int my, float delta,
                                  CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof InventoryScreen)) return;
        ExampleKeybindTriggeredPanel.ADAPTER.render(
                g,
                ExampleKeybindTriggeredPanel.bounds(
                        this.leftPos, this.topPos, this.imageWidth, this.imageHeight),
                mx, my,
                (AbstractContainerScreen<?>) (Object) this);
    }
}
