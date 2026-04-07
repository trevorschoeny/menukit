package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.region.MKRegion;
import com.trevorschoeny.menukit.region.MKRegionRegistry;
import com.trevorschoeny.menukit.widget.MKSlotState;
import com.trevorschoeny.menukit.widget.MKSlotStateRegistry;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

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

        // ── Locked slot gate ──────────────────────────────────────────────
        // If the source slot is fully locked, block shift-click out.
        // Locked = Ctrl+click full lock (blocks ALL interactions).
        // NOTE: sort-locked is NOT checked here — sort-lock only blocks
        // shift-click-IN (items entering the slot) and sorting. The player
        // should still be able to shift-click items OUT of a sort-locked slot.
        // Shift-click-IN protection is in MenuKit.tryRouteToOtherPanels() and
        // MenuKit.tryRouteToCustomSlots() where target slots are checked.
        MKSlotState sourceState = MKSlotStateRegistry.get(sourceSlot);
        if (sourceState != null && sourceState.isLocked()) {
            LOGGER.info("[MKDoClick] BLOCKED shift-click out: slot {} is LOCKED (panel={})",
                    slotId, sourceState.getPanelName());
            ci.cancel();
            return;
        }

        // ── Region-level gate ────────────────────────────────────────────
        // If the source slot belongs to a region with shiftClickOut=false, block.
        MKRegion region = MKRegionRegistry.getRegionForSlot(menu, slotId);
        if (region != null && !region.shiftClickOut()) {
            LOGGER.info("[MKDoClick] BLOCKED shift-click out: slot {} region '{}' has shiftClickOut=false",
                    slotId, region.name());
            ci.cancel();
            return;
        }

        // ── Panel-level gate ─────────────────────────────────────────────
        // If the source slot belongs to a panel with shiftClickOut=false, block.
        // This covers MKSlots (custom equipment, pockets, etc.) that have
        // their own shift-click flags separate from region flags.
        String panel = MenuKit.getEffectivePanelName(menu, sourceSlot);
        if (panel != null && !MenuKit.isShiftClickOut(panel)) {
            LOGGER.info("[MKDoClick] BLOCKED shift-click out: slot {} panel '{}' isShiftClickOut=false "
                    + "(isPanelHidden={}, isPanelDisabled={})",
                    slotId, panel,
                    MenuKit.isPanelHidden(panel),
                    MenuKit.isPanelDisabled(panel));
            ci.cancel();
        } else {
            LOGGER.debug("[MKDoClick] ALLOWED shift-click out: slot {} panel='{}' isShiftClickOut={}",
                    slotId, panel, panel != null ? MenuKit.isShiftClickOut(panel) : "null(no panel)");
        }
    }
}
