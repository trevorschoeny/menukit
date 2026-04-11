package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.event.MKEvent;
import com.trevorschoeny.menukit.event.MKEventBus;
import com.trevorschoeny.menukit.event.MKEventHelper;
import com.trevorschoeny.menukit.event.MKSlotEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires {@link MKEvent.Type#SCROLL} when the mouse wheel scrolls while
 * the cursor hovers a slot.
 *
 * <p>Intercepts {@code AbstractContainerScreen.mouseScrolled()} at HEAD.
 * If a slot is currently hovered and a handler returns CONSUMED, vanilla's
 * scroll handling is cancelled (the scroll is swallowed).
 *
 * <p><b>Creative mode coverage:</b> {@code CreativeModeInventoryScreen}
 * overrides {@code mouseScrolled()} but calls {@code super.mouseScrolled()}
 * first. Since our injection is on the super class at HEAD, this mixin
 * fires for creative screens too. If consumed, super returns true and the
 * creative override short-circuits without scrolling the item grid.
 *
 * <p>SlotWrapper unwrapping is handled centrally by {@link MKEventHelper} —
 * this mixin passes the raw hovered slot through and the helper unwraps at
 * event-construction time to enforce the slot-identity invariant.
 *
 * <p><b>Use cases:</b>
 * <ul>
 *   <li>Cycling through item variants in a selection slot</li>
 *   <li>Adjusting quantities (scroll up = more, scroll down = fewer)</li>
 *   <li>Browsing through enchantment options or recipe alternatives</li>
 *   <li>Custom scroll behaviors on specific regions/panels</li>
 * </ul>
 *
 * <p><b>Event data:</b> The {@code scrollDelta} field on the event carries
 * the vertical scroll amount. Positive = scroll up, negative = scroll down.
 * Typical values are +1.0 or -1.0 per notch, but touchpad/smooth scroll
 * may produce fractional values.
 *
 * <p>Runs on CLIENT only. Part of the <b>MenuKit</b> event system.
 */
@Mixin(AbstractContainerScreen.class)
public class MKScrollMixin {

    // ── Vanilla Field Access ─────────────────────────────────────────────────
    //
    // hoveredSlot is the slot currently under the cursor. May be null if the
    // cursor is between slots or outside the container area.

    @Shadow private Slot hoveredSlot;

    // ── Scroll Interception ──────────────────────────────────────────────────
    //
    // mouseScrolled returns boolean: true if the scroll was handled, false to
    // let vanilla continue processing. We use CallbackInfoReturnable so we can
    // cancel vanilla handling by setting the return value to true.
    //
    // 1.21+ signature: mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    // scrollY is the vertical axis (the one that matters for mouse wheels).
    // scrollX is horizontal (some mice/touchpads support it, but we only care about vertical).

    /**
     * Fires SCROLL through the event bus when the mouse wheel scrolls while
     * hovering a slot. If any handler returns CONSUMED, cancels vanilla's
     * scroll handling by returning true (scroll was handled).
     *
     * @param mouseX  cursor X position
     * @param mouseY  cursor Y position
     * @param scrollX horizontal scroll amount (ignored — vertical only)
     * @param scrollY vertical scroll amount (positive = up, negative = down)
     * @param cir     callback — set return value to cancel vanilla handling
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void menuKit$onScroll(double mouseX, double mouseY,
                                   double scrollX, double scrollY,
                                   CallbackInfoReturnable<Boolean> cir) {

        // ── Only fire when hovering a slot ───────────────────────────────
        // If the cursor isn't over a slot, there's nothing to target.
        // Let vanilla handle the scroll normally.
        if (this.hoveredSlot == null) return;

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;

        // ── Context gate ─────────────────────────────────────────────────
        // Skip screens MenuKit doesn't recognize.
        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        // ── Player resolution ────────────────────────────────────────────
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // ── Build and fire the event ─────────────────────────────────────
        // scrollY is the vertical wheel delta. Positive = up, negative = down.
        // Typical mice produce +1.0 or -1.0 per notch; touchpads may give
        // fractional values for smooth scrolling. MKEventHelper unwraps the
        // creative SlotWrapper at event construction.
        MKSlotEvent event = MKEventHelper.buildScrollEvent(
                this.hoveredSlot, scrollY, self, player);
        if (event == null) return;

        // ── Dispatch through the bus ─────────────────────────────────────
        // If any handler returns CONSUMED, we cancel vanilla's scroll handling.
        // For AbstractContainerScreen this prevents vanilla's ItemSlotMouseAction
        // from processing the scroll. For CreativeModeInventoryScreen (which calls
        // super.mouseScrolled first), returning true here causes super to return
        // true, which short-circuits the creative tab's own scroll logic.
        boolean consumed = MKEventBus.fire(event);
        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
