package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.verification.RegionProbes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Supplementary dev-scaffolding mixin for {@link InventoryScreen}, which
 * reaches {@link AbstractRecipeBookScreen#render} but not
 * {@link AbstractContainerScreen#render} (the intermediate override calls
 * {@code super.renderContents} instead of {@code super.render}). Without
 * this supplementary, {@link ProbeRenderMixin} never fires for the survival
 * inventory screen.
 *
 * <p>Gated by {@code instanceof InventoryScreen} so other potential
 * AbstractRecipeBookScreen subclasses don't get double-dispatch.
 *
 * <p>Mirrors IP's three-mixin inventory-screen injection pattern
 * (AbstractContainerScreen + AbstractRecipeBookScreen + CreativeModeInventoryScreen).
 * CreativeModeInventoryScreen doesn't need its own supplementary here —
 * vanilla's creative screen super-calls cleanly and the base
 * {@link ProbeRenderMixin} fires for it.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class ProbeRenderRecipeBookMixin
        extends AbstractContainerScreen<AbstractContainerMenu> {

    // Dummy constructor — mixin class is never instantiated; required
    // because the extended target has no default constructor.
    private ProbeRenderRecipeBookMixin() {
        super(null, null, null);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void menuKit$renderRegionProbes(GuiGraphics graphics,
                                              int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen)) return;
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        RegionProbes.renderInventoryProbes(self, graphics, mouseX, mouseY);
    }
}
