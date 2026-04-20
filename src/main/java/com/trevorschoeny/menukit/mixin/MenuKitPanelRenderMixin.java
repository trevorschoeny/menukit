package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Library-private mixin that dispatches MenuContext + SlotGroupContext
 * panel rendering into the correct render stratum of
 * {@link AbstractContainerScreen#render}.
 *
 * <h3>Why a mixin (not {@code ScreenEvents.afterRender})</h3>
 *
 * Fabric's {@code ScreenEvents.afterRender} fires inside
 * {@code GameRendererMixin.onRenderScreen} <i>after</i>
 * {@code Screen.renderWithTooltipAndSubtitles} returns, which means it
 * fires after {@code GuiGraphics.renderDeferredElements()} has flushed
 * the tooltip queue populated by {@code setTooltipForNextFrame}. Panels
 * rendered in that hook overdraw tooltips — a Principle 9 violation
 * (tooltip layering is a gameplay-rooted ordering: tooltips must visually
 * dominate the UI so players can read them; panels covering tooltips
 * inverts the ordering without a gameplay reason).
 *
 * <p>Injecting at {@code INVOKE renderCarriedItem} from
 * {@code AbstractContainerScreen.render} puts panels at the right
 * painter's-algorithm stratum:
 *
 * <ol>
 *   <li>{@code renderContents}: slots + labels (base stratum).</li>
 *   <li><b>This injection point — panels render here</b>, still on the
 *       base stratum, so they overdraw slots but live under subsequent
 *       strata.</li>
 *   <li>{@code renderCarriedItem}: cursor-carried item on a new stratum
 *       via {@code guiGraphics.nextStratum()}.</li>
 *   <li>{@code renderSnapbackItem}: snapback animation on another
 *       stratum.</li>
 *   <li>{@code renderDeferredElements()} (after {@code render} returns):
 *       tooltips, drawn on top of everything.</li>
 * </ol>
 *
 * <p>Result: panels above slots, tooltips + cursor above panels.
 * Principle 9 layering restored.
 *
 * <p><b>Click dispatch stays on Fabric's hook.</b>
 * {@code ScreenMouseEvents.allowMouseClick} fires per-click regardless
 * of render ordering, so there's no reason to mixin-dispatch clicks.
 * {@link ScreenPanelRegistry#onScreenInit} still registers the click
 * handler via Fabric.
 *
 * <p>See {@code menukit/Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md}
 * §8.2 for the design note explaining this deviation from the initial
 * spec.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MenuKitPanelRenderMixin {

    /**
     * Fires before {@code renderCarriedItem} is invoked, which means
     * after slot rendering (done in {@code renderContents}) and before
     * cursor + snapback strata are pushed. {@code require = 1} so the
     * mixin fails loudly if a vanilla refactor removes or renames the
     * target method.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderCarriedItem(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            ),
            require = 1
    )
    private void menuKit$renderPanels(GuiGraphics graphics, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        ScreenPanelRegistry.renderMatchingPanels(self, graphics, mouseX, mouseY);
    }
}
