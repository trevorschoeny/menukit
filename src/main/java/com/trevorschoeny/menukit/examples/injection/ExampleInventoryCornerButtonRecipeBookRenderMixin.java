package com.trevorschoeny.menukit.examples.injection;

import com.trevorschoeny.menukit.examples.shared.ExampleInventoryCornerButton;

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
 * Phase 10 injection example: Pattern 3 — supplementary render mixin that
 * compensates for {@link AbstractRecipeBookScreen}'s non-super-calling
 * override of {@code render}.
 *
 * <p>{@link InventoryScreen} inherits from {@code AbstractRecipeBookScreen},
 * which overrides {@code render} to draw the recipe-book widget. That
 * override does not call {@code super.render(...)}, so mixins on
 * {@code AbstractContainerScreen.render} never fire when the player is in
 * survival inventory. (The same class <em>does</em> super-call for
 * {@code mouseClicked} — the asymmetry is vanilla-side, not ours.)
 *
 * <p>This mixin pins a second render hook to
 * {@code AbstractRecipeBookScreen.render} itself, gated to the inventory
 * variant with a runtime {@code instanceof} check. Without the gate, the
 * corner button would also draw on crafting-table, smoker, and blast-furnace
 * screens (all {@code AbstractRecipeBookScreen} subclasses).
 *
 * <h3>Why {@code extends AbstractContainerScreen}?</h3>
 *
 * {@code leftPos}, {@code topPos}, {@code imageWidth}, and {@code imageHeight}
 * are declared on {@code AbstractContainerScreen}, not on
 * {@code AbstractRecipeBookScreen}. Mixin's {@code @Shadow} cannot attach to
 * fields that aren't declared on the immediate target class — even if they
 * are inherited at runtime. The canonical workaround: declare the mixin
 * class as {@code extends AbstractContainerScreen<...>}, and the fields
 * become visible through Java's inheritance. The mixin class itself is
 * never instantiated; the constructor below exists solely to satisfy the
 * compiler.
 *
 * <p>Click dispatch does not need a supplementary mixin — {@code
 * AbstractRecipeBookScreen.mouseClicked} does super-call, so the primary
 * {@link ExampleInventoryCornerButtonMixin} handles it.
 *
 * <p><b>Dev-only.</b> See {@link DevOnlyExampleMixinsPlugin}.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class ExampleInventoryCornerButtonRecipeBookRenderMixin
        extends AbstractContainerScreen<AbstractContainerMenu> {

    /**
     * Never called — mixin classes are not instantiated. Exists only to
     * satisfy the compiler's requirement that a subclass of an abstract
     * class have a matching constructor.
     */
    protected ExampleInventoryCornerButtonRecipeBookRenderMixin(
            AbstractContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void examples$render(GuiGraphics g, int mx, int my, float delta,
                                  CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof InventoryScreen)) return;
        ExampleInventoryCornerButton.ADAPTER.render(
                g,
                ExampleInventoryCornerButton.bounds(
                        this.leftPos, this.topPos, this.imageWidth, this.imageHeight),
                mx, my);
    }
}
