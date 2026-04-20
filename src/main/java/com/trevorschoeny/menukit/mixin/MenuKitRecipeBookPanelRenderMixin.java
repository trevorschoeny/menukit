package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Library-private mixin that dispatches MenuContext + SlotGroupContext
 * panel rendering into {@link AbstractRecipeBookScreen#render}'s base
 * stratum — the recipe-book-aware counterpart to
 * {@link MenuKitPanelRenderMixin}.
 *
 * <h3>Why this mixin exists</h3>
 *
 * {@link AbstractRecipeBookScreen} overrides {@code render} and does NOT
 * call {@code super.render(...)}. Its override paints {@code renderContents}
 * (or {@code renderBackground} in the narrow-window-recipe-book path),
 * pushes a stratum for the recipe-book overlay, pushes another stratum for
 * the cursor, and finally calls {@code renderCarriedItem}. Because
 * {@code AbstractContainerScreen.render} is never invoked for recipe-book-
 * hosted screens, {@link MenuKitPanelRenderMixin}'s inject point is
 * silent-inert for every {@link AbstractRecipeBookScreen} subclass —
 * {@link net.minecraft.client.gui.screens.inventory.InventoryScreen},
 * {@link net.minecraft.client.gui.screens.inventory.CraftingScreen},
 * {@link net.minecraft.client.gui.screens.inventory.FurnaceScreen},
 * {@link net.minecraft.client.gui.screens.inventory.SmokerScreen},
 * {@link net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen}.
 *
 * <p>Those are the highest-traffic screens in the game. Without this
 * mixin, every region-based {@link com.trevorschoeny.menukit.inject.ScreenPanelAdapter}
 * targeting one of them via {@code .on(Class...)} silently fails to
 * render. The bug shipped with Phase 12.5 V2's tooltip-layering fix (which
 * introduced {@link MenuKitPanelRenderMixin}) and sat invisible until V5.6
 * exercised a crafting-table backdrop.
 *
 * <h3>Injection point rationale</h3>
 *
 * {@code @At INVOKE renderContents shift AFTER} — fires immediately after
 * slots + labels render, before the first {@code GuiGraphics.nextStratum()}
 * call. Panels render on the base stratum alongside slots — above them,
 * below the recipe-book overlay if visible, below cursor and tooltips.
 * Matches {@link MenuKitPanelRenderMixin}'s layering contract (panels-above-
 * slots, cursor-above-panels, tooltips-on-top).
 *
 * <p>Narrow-window-with-recipe-book-open path ({@code widthTooNarrow &&
 * recipeBookComponent.isVisible} → branch takes {@code renderBackground}
 * instead of {@code renderContents}): this mixin does not fire in that
 * case. Correct behavior — slots aren't rendered either, so rendering
 * panels there would be out of place.
 *
 * <p><b>Click dispatch stays on Fabric's hook</b>, same as
 * {@link MenuKitPanelRenderMixin}. {@code ScreenMouseEvents.allowMouseClick}
 * fires per-click regardless of render pipeline, so no click mixin is
 * needed here.
 *
 * <p>See {@code menukit/Design Docs/Phase 12.5/V5_6_FINDING_RECIPEBOOK_RENDER_OVERRIDE.md}
 * for the finding that surfaced this and the design rationale.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class MenuKitRecipeBookPanelRenderMixin {

    /**
     * Fires after {@code renderContents} returns (slots + labels drawn),
     * before the first {@code nextStratum()} push. {@code require = 1} so
     * the mixin fails loudly if a vanilla refactor removes or renames the
     * target method.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderContents(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void menuKit$renderPanels(GuiGraphics graphics, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        ScreenPanelRegistry.renderMatchingPanels(self, graphics, mouseX, mouseY);
    }
}
