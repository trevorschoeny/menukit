package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKEvent;
import com.trevorschoeny.menukit.MKEventBus;
import com.trevorschoeny.menukit.MKEventHelper;
import com.trevorschoeny.menukit.MKSlotEvent;
import com.trevorschoeny.menukit.MKSlotState;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires ALL click event types through the {@link com.trevorschoeny.menukit.MKEventBus}
 * for every {@link AbstractContainerScreen}.
 *
 * <p>This is the primary click-to-bus wiring mixin. It has two injection points:
 *
 * <ol>
 *   <li>{@code slotClicked} — catches all click types that vanilla routes through
 *       the standard slot-click pipeline (LEFT_CLICK, SHIFT_CLICK, SWAP, MIDDLE_CLICK
 *       in creative, THROW, DOUBLE_CLICK).</li>
 *   <li>{@code mouseClicked} — catches middle-click (button=2) in survival mode,
 *       where vanilla never calls slotClicked because ClickType.CLONE is creative-only.
 *       Without this, middle-click would be a dead button for non-creative players.</li>
 * </ol>
 *
 * <p><b>RIGHT_CLICK is skipped here.</b> {@link MKSlotRightClickMixin} already
 * intercepts right-clicks to fire per-slot handlers AND the bus (after this
 * patch). Handling it here too would cause double-firing. All other click
 * types (LEFT_CLICK, SHIFT_CLICK, SWAP, MIDDLE_CLICK, THROW, DOUBLE_CLICK)
 * are handled exclusively by this mixin.
 *
 * <p><b>Ordering:</b> This mixin fires BEFORE vanilla behavior. If any bus
 * handler returns {@link com.trevorschoeny.menukit.MKEventResult#CONSUMED},
 * vanilla is cancelled via {@code ci.cancel()}.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKSlotClickBusMixin {

    // ── Shadow: hoveredSlot is needed for the mouseClicked fallback ──────
    // In survival mode, button=2 never reaches slotClicked, so we resolve
    // the target slot from hoveredSlot at the mouseClicked level.
    @Shadow protected Slot hoveredSlot;

    // ── slotClicked: primary click dispatch ──────────────────────────────
    //
    // Handles all vanilla ClickTypes that route through slotClicked.
    // This covers creative-mode middle-click (ClickType.CLONE) as well as
    // left-click, shift-click, swap, throw, and double-click in all modes.

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void menuKit$fireClickBusEvent(Slot slot, int slotId, int button,
                                            ClickType clickType, CallbackInfo ci) {
        // ── Skip RIGHT_CLICK — handled by MKSlotRightClickMixin ─────────
        // That mixin fires per-slot handlers first, then the bus. We don't
        // want to double-fire RIGHT_CLICK events.
        if (clickType == ClickType.PICKUP && button == 1) return;

        // ── Ctrl+Click: toggle slot lock ─────────────────────────────────
        // When Ctrl is held during a left-click, we toggle the slot's lock
        // state instead of performing the normal click action. This lets
        // players pin items in place so they are excluded from sorting and
        // protected from accidental pickup/placement.
        //
        // NOTE: Lock state lives in MKSlotState, which is stored in the
        // IdentityHashMap-based MKSlotStateRegistry. It resets when the
        // menu closes (Slot objects are recreated each time). Persistent
        // locking would require saving to player NBT — deferred for now.
        if (clickType == ClickType.PICKUP && button == 0
                && isControlDown() && slot != null) {
            MKSlotState lockState = MKSlotStateRegistry.getOrCreate(slot);
            lockState.toggleLocked();

            // Play a subtle UI click sound so the player has audio feedback
            // that the lock toggled (especially helpful since the visual
            // indicator is subtle).
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

            // Cancel vanilla — don't pick up or place items on a lock toggle
            ci.cancel();
            return;
        }

        // ── Locked slot protection: block interactions ───────────────────
        // If the target slot is locked, block clicks that would move items
        // in or out. This covers LEFT_CLICK (pickup/place), SHIFT_CLICK
        // (quick move), SWAP (number key), and DOUBLE_CLICK (collect).
        // CLONE (creative middle-click) copies without removing — allowed through.
        // THROW (Q key) drops items and IS blocked — locked means "stay put".
        if (slot != null && clickType != ClickType.CLONE) {
            MKSlotState slotState = MKSlotStateRegistry.get(slot);
            if (slotState != null && slotState.isLocked()) {
                ci.cancel();
                return;
            }
        }

        // SWAP (number key) is bidirectional: it swaps the hovered slot with
        // a hotbar slot. The check above guards the hovered slot, but we also
        // need to guard the TARGET hotbar slot. Without this, a player could
        // bypass a locked hotbar slot by hovering an unlocked slot and pressing
        // the locked slot's number key.
        if (slot != null && clickType == ClickType.SWAP && button >= 0 && button < 9) {
            AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
            for (var menuSlot : self.getMenu().slots) {
                if (menuSlot.container instanceof net.minecraft.world.entity.player.Inventory
                        && menuSlot.getContainerSlot() == button) {
                    MKSlotState hotbarState = MKSlotStateRegistry.get(menuSlot);
                    if (hotbarState != null && hotbarState.isLocked()) {
                        ci.cancel();
                        return;
                    }
                    break;
                }
            }
        }

        // ── Resolve the player ──────────────────────────────────────────
        // slotClicked is client-side only (AbstractContainerScreen is a client class),
        // so Minecraft.getInstance().player is always the local player.
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // ── Cast self to AbstractContainerScreen ────────────────────────
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        // ── Map, build, fire ────────────────────────────────────────────
        // MKEventHelper handles the ClickType-to-Type mapping, context resolution,
        // and bus dispatch in one call. Returns true if any handler consumed.
        if (MKEventHelper.fireClickEvent(clickType, slot, button, screen, player)) {
            // A bus handler consumed this click — cancel vanilla behavior
            ci.cancel();
        }
    }

    // ── mouseClicked: survival-mode middle-click fallback ────────────────
    //
    // Vanilla's AbstractContainerScreen.mouseClicked() only calls slotClicked
    // with ClickType.CLONE when the player has creative-mode infinite items
    // (hasInfiniteItems()). For survival/adventure players, button=2 is
    // silently ignored — slotClicked is never called.
    //
    // This injection catches button=2 at the mouseClicked level BEFORE
    // vanilla's creative check, ensuring MIDDLE_CLICK fires through the
    // event bus for ALL game modes. Consumers can use middle-click for
    // bookmarking, info lookup, tagging, etc. regardless of creative status.
    //
    // Guard: if the player IS in creative mode, slotClicked will fire
    // ClickType.CLONE which the injection above already handles. We skip
    // here to avoid double-firing.

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$fireMiddleClickBusEvent(MouseButtonEvent event, boolean bl,
                                                   CallbackInfoReturnable<Boolean> cir) {
        // ── Only handle middle-click (button=2) ─────────────────────────
        if (event.button() != 2) return;

        // ── Need a hovered slot to fire a slot event ────────────────────
        if (this.hoveredSlot == null) return;

        // ── Skip creative mode — slotClicked already handles CLONE ──────
        // In creative, vanilla calls slotClicked(slot, slotId, 2, CLONE),
        // which the slotClicked injection above catches and fires as
        // MIDDLE_CLICK. Firing here too would double-fire.
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getAbilities().instabuild) return;

        // ── Build and fire MIDDLE_CLICK event ───────────────────────────
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MKSlotEvent mkEvent = MKEventHelper.buildSlotEvent(
                MKEvent.Type.MIDDLE_CLICK, this.hoveredSlot, 2, screen, player);

        if (MKEventBus.fire(mkEvent)) {
            // A bus handler consumed — tell vanilla we handled the click
            cir.setReturnValue(true);
        }
    }

    // Use GLFW directly to check Ctrl — Screen.hasControlDown() doesn't exist in MC 1.21.11.
    // On macOS, Ctrl is GLFW_KEY_LEFT_SUPER / RIGHT_SUPER (Cmd key), but for Minecraft
    // the convention is Left/Right Control keys.
    @Unique
    private static boolean isControlDown() {
        long wnd = Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}
