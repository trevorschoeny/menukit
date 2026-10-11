package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.api.panel.Focus;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses creative tab hover under a MenuKit panel. The cosmetic companion to
 * the slot-hover suppression in {@link MKSlotInputMixin}.
 *
 * <p>That hook suppresses <em>slot</em> hover by returning {@code null} from
 * {@code getHoveredSlot}, but a creative <b>tab</b> would still glow through a
 * modal, because {@code CreativeModeInventoryScreen.checkTabHovering} tests
 * {@code mouseX}/{@code mouseY} directly instead of going through
 * {@code getHoveredSlot}.
 *
 * <p>A creative-only hook is right here because tabs exist only on the creative
 * inventory screen; there is no survival equivalent to share it with. It uses the
 * same inertness check as the other suppressors (see
 * {@link ScreenPanelRegistry#hasAnyVisibleModalTracking()} for the modal part): a
 * visible modal claims the whole screen, so the tabs must not light up behind it.
 * {@link MKTooltipSuppressMixin} already suppresses the tab <em>tooltip</em> under
 * a modal; this covers the tab <em>highlight</em>.
 */
@ApiStatus.Internal
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MKCreativeTabHoverMixin {

    /**
     * When the tab position is inert under a MenuKit panel (a modal anywhere, or
     * an opaque panel, element or overlay covering this point), report the tab
     * as not hovered so vanilla draws no highlight.
     *
     * <p>Checking only for a modal let a tab glow through a non-modal opaque
     * panel sitting over it (such as the pockets controls).
     * {@link Focus#isInertUnderPanel} is the same check slot hover, widget hover,
     * tooltips and click-eating use, so tabs behave like every other surface.
     */
    @Inject(
            method = "checkTabHovering",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mk$suppressTabHoverWhenModal(GuiGraphicsExtractor guiGraphics, CreativeModeTab tab,
                                                   int mouseX, int mouseY,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (Focus.isInertUnderPanel(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }
}
