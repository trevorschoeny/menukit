package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContainerState;
import com.trevorschoeny.menukit.MKContainerStateRegistry;
import com.trevorschoeny.menukit.MKEventHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MenuKit read-only enforcement mixin — blocks item placement into containers
 * tagged as read-only (OUTPUT persistence mode).
 *
 * <p>Before processing any click on a slot, checks if the slot's backing
 * container is marked read-only via {@link MKContainerStateRegistry}. If so,
 * only actions that TAKE items out are allowed (left-click pickup with empty
 * hand, shift-click out). All other interactions are cancelled.
 *
 * <p>Also tracks the active player for the duration of each {@code clicked()}
 * call via {@link MKEventHelper#setCurrentClickPlayer} /
 * {@link MKEventHelper#clearCurrentClickPlayer}. This allows transfer events
 * (fired by {@link MKTransferMixin} on Slot methods like safeInsert) to
 * resolve the player even for non-player containers (chests, furnaces, etc.)
 * where the Container has no player reference.
 *
 * <p>This is completely independent of slot injection and container tracking,
 * so it lives in its own mixin to minimize conflict surface with other mods.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(AbstractContainerMenu.class)
public class MKReadOnlyMixin {

    // ── Read-Only Enforcement via clicked ─────────────────────────────────────

    /**
     * Before processing any click, check if the target slot's container is
     * tagged as read-only (OUTPUT persistence). If so, block any action that
     * would PLACE items into it. Taking items OUT is still allowed.
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void menuKit$enforceReadOnly(int slotIndex, int button, ClickType clickType,
                                          Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;

        Slot slot = menu.slots.get(slotIndex);
        Container c = slot.container;
        if (c == null) return;

        MKContainerState state = MKContainerStateRegistry.get(c);
        if (state == null || !state.isReadOnly()) return;

        // Read-only containers: only allow TAKING items out (left-click pickup,
        // shift-click out). Block placing, swapping, or any action that would
        // add items to this container.
        // PICKUP with empty carried item = taking out (allowed)
        // QUICK_MOVE from this slot = shift-click out (allowed)
        // Everything else that targets this slot = blocked
        ItemStack carried = menu.getCarried();
        if (clickType == ClickType.PICKUP && carried.isEmpty()) return; // taking out
        if (clickType == ClickType.QUICK_MOVE) return; // shift-click out

        // Block all other interactions on read-only containers
        ci.cancel();
    }

    // ── Active Player Tracking for Transfer Events ────────────────────────────
    //
    // Transfer events fire inside Slot methods (safeInsert, setByPlayer) which
    // don't always receive a Player parameter. For player inventory slots,
    // MKEventHelper.resolvePlayerFromSlot can pull the player from the Inventory
    // container. But for non-player containers (chests, furnaces, etc.), there's
    // no player on the Container object.
    //
    // We solve this by saving the Player from clicked() into a static field on
    // MKEventHelper at the start of clicked(), and clearing it at the end.
    // Any transfer event that fires during clicked() can then use this fallback.
    //
    // This is safe because:
    //   - clicked() runs on the server tick thread, one player at a time
    //   - All Slot methods called by clicked() (safeInsert, setByPlayer, etc.)
    //     execute synchronously within the same call stack
    //   - The field is cleared at RETURN so it never leaks across calls

    /**
     * At the start of every clicked() call, save the player reference so
     * transfer events can resolve it for non-player containers.
     */
    @Inject(method = "clicked", at = @At("HEAD"))
    private void menuKit$trackClickPlayer(int slotIndex, int button, ClickType clickType,
                                           Player player, CallbackInfo ci) {
        MKEventHelper.setCurrentClickPlayer(player);
    }

    /**
     * At the end of every clicked() call (all return paths), clear the
     * player reference to prevent stale references leaking.
     */
    @Inject(method = "clicked", at = @At("RETURN"))
    private void menuKit$clearClickPlayer(int slotIndex, int button, ClickType clickType,
                                           Player player, CallbackInfo ci) {
        MKEventHelper.clearCurrentClickPlayer();
    }
}
