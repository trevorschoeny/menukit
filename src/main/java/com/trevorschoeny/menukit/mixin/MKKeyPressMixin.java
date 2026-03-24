package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKEventBus;
import com.trevorschoeny.menukit.MKEventHelper;
import com.trevorschoeny.menukit.MKSlotEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires {@link MKSlotEvent.Type#KEY_PRESS} when a key is pressed while
 * the cursor hovers a slot.
 *
 * <p>Intercepts {@code AbstractContainerScreen.keyPressed()} at HEAD.
 * If a slot is currently hovered and a handler returns CONSUMED, vanilla's
 * key handling is cancelled (the key press is swallowed).
 *
 * <p><b>Use cases:</b>
 * <ul>
 *   <li>Custom hotkeys on specific slots (e.g., "L" to lock a slot)</li>
 *   <li>Slot-specific keybindings that differ from global keybindings</li>
 *   <li>Blocking vanilla key actions on certain slots (e.g., prevent
 *       number-key swap on locked slots)</li>
 * </ul>
 *
 * <p><b>Event data:</b> The {@code keyCode} field on the event carries the
 * GLFW key code (e.g., {@code GLFW.GLFW_KEY_Q} = 81). Consumers can check
 * this to respond to specific keys.
 *
 * <p>Runs on CLIENT only. Part of the <b>MenuKit</b> event system.
 */
@Mixin(AbstractContainerScreen.class)
public class MKKeyPressMixin {

    // ── Vanilla Field Access ─────────────────────────────────────────────────
    //
    // hoveredSlot is the slot currently under the cursor. May be null if the
    // cursor is between slots or outside the container area.

    @Shadow private Slot hoveredSlot;

    // ── Key Press Interception ───────────────────────────────────────────────
    //
    // keyPressed returns boolean: true if the key was handled, false to let
    // vanilla continue processing. We use CallbackInfoReturnable so we can
    // cancel vanilla handling by setting the return value to true.

    /**
     * Fires KEY_PRESS through the event bus when a key is pressed while
     * hovering a slot. If any handler returns CONSUMED, cancels vanilla's
     * key handling by returning true (key was handled).
     *
     * @param event  1.21.11 KeyEvent wrapping the GLFW key code (use event.key())
     * @param cir    callback — set return value to cancel vanilla handling
     */
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$onKeyPress(KeyEvent event,
                                     CallbackInfoReturnable<Boolean> cir) {

        // ── Only fire when hovering a slot ───────────────────────────────
        // If the cursor isn't over a slot, there's nothing to target.
        // Let vanilla handle the key normally.
        if (this.hoveredSlot == null) return;

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;

        // ── Context gate ─────────────────────────────────────────────────
        // Skip screens MenuKit doesn't recognize.
        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        // ── Player resolution ────────────────────────────────────────────
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // ── Extract key code from the 1.21.11 KeyEvent ────────────────────
        // event.key() returns the GLFW key constant. Consumers use
        // mkEvent.getKeyCode() to check which key was pressed.
        int keyCode = event.key();

        // ── Build and fire the event ─────────────────────────────────────
        MKSlotEvent mkEvent = MKEventHelper.buildKeyEvent(
                this.hoveredSlot, self, player, keyCode);
        if (mkEvent == null) return;

        // ── Dispatch through the bus ─────────────────────────────────────
        // If any handler returns CONSUMED, we cancel vanilla's key handling.
        // This prevents the default action (e.g., number-key swap, drop key)
        // from executing when a MenuKit handler has claimed the key press.
        boolean consumed = MKEventBus.fire(mkEvent);
        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
