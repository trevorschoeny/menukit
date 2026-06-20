package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §0051 Fix 3 — creative tab-hover suppression under a modal. The cosmetic
 * companion to {@link MenuKitModalHoverMixin}.
 *
 * <p>That mixin suppresses <em>slot</em> hover by returning {@code null} from
 * {@code getHoveredSlot}; but a creative <b>tab</b> still glows through a modal,
 * because {@code CreativeModeInventoryScreen.checkTabHovering} tests
 * {@code mouseX}/{@code mouseY} directly rather than going through
 * {@code getHoveredSlot}. This is exactly the creative-only follow-on that the
 * hover mixin's own javadoc anticipates ("fold a creative-specific mixin into
 * {@code checkTabHovering} returning false when modal up").
 *
 * <h3>Why creative-only is correct here (§0051)</h3>
 *
 * Tabs exist only on the creative inventory screen — there is no survival analog
 * to unify, so this is legitimate mode-specific UI getting a mode-specific hook,
 * not a parity violation. Gated on the same
 * {@link ScreenPanelRegistry#hasAnyVisibleModalTracking()} signal the other
 * modal mixins use: a visible modal claims the whole screen, so its screen-level
 * chrome (the tabs) must not light up behind it. Matches the creative-aware
 * stance of {@link MenuKitTooltipSuppressMixin}, which already suppresses the
 * tab <em>tooltip</em> under a modal — this closes the matching tab
 * <em>highlight</em>.
 */
@ApiStatus.Internal
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MenuKitCreativeTabHoverMixin {

    /**
     * HEAD of {@code checkTabHovering}. When a modal panel is visible, report the
     * tab as not-hovered ({@code false}) so vanilla draws no tab highlight.
     */
    @Inject(
            method = "checkTabHovering",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$suppressTabHoverWhenModal(GuiGraphics guiGraphics, CreativeModeTab tab,
                                                   int mouseX, int mouseY,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (ScreenPanelRegistry.hasAnyVisibleModalTracking()) {
            cir.setReturnValue(false);
        }
    }
}
