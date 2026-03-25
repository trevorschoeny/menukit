package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKRegion;
import com.trevorschoeny.menukit.MKRegionRegistry;
import com.trevorschoeny.menukit.MKSlotState;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import com.trevorschoeny.menukit.MenuKit;
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
 * Universal shiftClickOut gate — intercepts {@code QUICK_MOVE} (shift-click)
 * on slots whose region or panel has {@code shiftClickOut = false}.
 *
 * <p>Unlike the old approach of overriding the abstract {@code quickMoveStack()}
 * in each specific menu subclass, this mixin targets the non-abstract
 * {@link AbstractContainerMenu#doClick(int, int, ClickType, Player)} which
 * is called for ALL menu types before {@code quickMoveStack()} is invoked.
 * This makes shift-click gating universal — it works for InventoryMenu,
 * ChestMenu, and any modded menu without needing per-class overrides.
 *
 * <p>Two checks are performed in order:
 * <ol>
 *   <li><b>Region gate:</b> If the source slot's MKRegion has
 *       {@code shiftClickOut = false}, the move is blocked.</li>
 *   <li><b>Panel gate:</b> If the source slot's panel has
 *       {@code shiftClickOut = false}, the move is blocked.</li>
 * </ol>
 *
 * <p>If blocked, returns {@link ItemStack#EMPTY} (vanilla's "nothing moved"
 * sentinel), which prevents {@code quickMoveStack()} from running.
 *
 * <p>Runs on both client and server (registered in the common mixin section).
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(AbstractContainerMenu.class)
public class MKDoClickMixin {

    @Inject(
        method = "doClick(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void menuKit$enforceShiftClickOut(int slotId, int button, ClickType clickType,
                                               Player player,
                                               CallbackInfo ci) {
        // Only gate QUICK_MOVE (shift-click) — let all other click types through
        if (clickType != ClickType.QUICK_MOVE) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Guard: slot must be valid
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        Slot sourceSlot = menu.slots.get(slotId);

        // ── Locked slot gate ───────────────────────────────────────────────
        // If the source slot is locked, block shift-click out entirely.
        // Locked means the player has explicitly pinned this slot's contents —
        // shift-clicking should NOT move items out of a locked slot. This runs
        // before region/panel gates because it's a per-slot override.
        MKSlotState sourceState = MKSlotStateRegistry.get(sourceSlot);
        if (sourceState != null && sourceState.isLocked()) {
            ci.cancel();
            return;
        }

        // ── Region-level gate ────────────────────────────────────────────
        // If the source slot belongs to a region with shiftClickOut=false, block.
        MKRegion region = MKRegionRegistry.getRegionForSlot(menu, slotId);
        if (region != null && !region.shiftClickOut()) {
            ci.cancel();
            return;
        }

        // ── Panel-level gate ─────────────────────────────────────────────
        // If the source slot belongs to a panel with shiftClickOut=false, block.
        // This covers MKSlots (custom equipment, pockets, etc.) that have
        // their own shift-click flags separate from region flags.
        String panel = MenuKit.getEffectivePanelName(menu, sourceSlot);
        if (panel != null && !MenuKit.isShiftClickOut(panel)) {
            ci.cancel();
        }
    }
}
