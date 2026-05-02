package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 14d-1 modal hover suppression — render-time piece of the modal
 * primitive.
 *
 * <p>Hover detection is RENDER-time (not input-time) — every frame, the
 * screen calls {@link AbstractContainerScreen#getHoveredSlot} to find the
 * slot under the cursor, sets {@code hoveredSlot} field, and renders the
 * white-rectangle highlight + queues the slot's tooltip. Suppressing hover
 * isn't covered by the input-handler mixins because they only intercept
 * actual input events.
 *
 * <p>This mixin makes {@code getHoveredSlot} return {@code null} when a
 * modal panel is visible — no slot is "hovered," no highlight, no slot
 * tooltip. The cursor still moves freely (we don't suppress mouse-move
 * tracking — modal needs accurate cursor position for its own button
 * hover state), but the underlying inventory's slot rendering treats the
 * cursor as if it weren't over any slot.
 *
 * <h3>What this doesn't cover</h3>
 *
 * <ul>
 *   <li>Tab hover in {@link net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen}.
 *       Tab hover is rendered via {@code checkTabHovering} which uses
 *       mouseX/mouseY directly, not {@code getHoveredSlot}. If smoke shows
 *       tab hover highlights are visible through modal, fold a creative-
 *       specific mixin into {@code checkTabHovering} returning false when
 *       modal up.</li>
 *   <li>Other render-time mouse-position-driven feedback. Each is a
 *       separate fold-on-evidence task. v1 covers slot hover, which is
 *       the dominant case for "things behind the modal feeling alive."</li>
 * </ul>
 *
 * <h3>Architecture note</h3>
 *
 * Per round-3 advisor verdict, hover suppression is the only remaining
 * Screen-level mixin in the modal primitive. Input dispatch (clicks, keys,
 * scroll) lives at MouseHandler/KeyboardHandler level (single hook before
 * per-screen routing). Hover is render-driven — fundamentally a per-screen
 * concept — so the mixin lives at the screen-class level. AbstractContainerScreen
 * covers all inventory-screen subclasses uniformly.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MenuKitModalHoverMixin {

    /**
     * Fires at HEAD of {@code getHoveredSlot}. When a modal panel is visible
     * on this screen, returns null — no slot under cursor as far as vanilla
     * knows. Highlights, hover tooltips, and any other slot-hover-driven
     * behavior are all suppressed.
     */
    @Inject(
            method = "getHoveredSlot",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$suppressHoverWhenOpaque(double mouseX, double mouseY,
                                                  CallbackInfoReturnable<Slot> cir) {
        // M9: pointer-driven hover suppression honors both panel scopes:
        //   - tracksAsModal panel visible → suppress GLOBALLY (modal
        //     claims the whole screen; everything behind is inert per
        //     Trevor's principle).
        //   - else if cursor inside any visible opaque panel → suppress
        //     LOCALLY (bounds-local for non-modal opaque panels like
        //     popovers / dropdowns covering slot edges).
        //   - else → don't suppress; vanilla hover proceeds normally.
        // See M9 §4.7 for the scope-asymmetry framing.
        if (ScreenPanelRegistry.hasAnyVisibleModalTracking()
                || ScreenPanelRegistry.hasAnyVisibleOpaquePanelAt(mouseX, mouseY)) {
            cir.setReturnValue(null);
        }
    }
}
