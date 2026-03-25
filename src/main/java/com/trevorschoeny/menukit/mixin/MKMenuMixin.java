package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MenuKit internal mixin — injects MKSlots into menus during construction.
 *
 * <p>Targets {@link InventoryMenu} specifically because its constructor provides
 * the {@link Player} reference needed for per-player container management.
 * Other menu types can be added with additional injection points.
 *
 * <p>Runs on BOTH client and server — slot counts must match for network sync.
 * Uses SURVIVAL_INVENTORY context since InventoryMenu is always constructed
 * with survival dimensions. Creative mode re-positions slots separately via
 * MKCreativeMixin.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(InventoryMenu.class)
public class MKMenuMixin {


    @Inject(method = "<init>", at = @At("TAIL"))
    private void menuKit$createSlots(Inventory inventory, boolean active,
                                      Player owner, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // ── Step 1: Register vanilla slot → panel mappings ──────────────────
        // InventoryMenu slot layout:
        //   0     = crafting result
        //   1-4   = 2x2 crafting grid
        //   5-8   = armor (helmet=5, chest=6, legs=7, boots=8)
        //   9-35  = main inventory (27 slots)
        //   36-44 = hotbar (9 slots)
        //   45    = offhand
        // Vanilla slots stay vanilla — MenuKit tracks panel association via a map.
        MenuKit.registerSlotPanelMapping(menu, 0,  1,  MenuKit.PANEL_CRAFT_RESULT);
        MenuKit.registerSlotPanelMapping(menu, 1,  5,  MenuKit.PANEL_CRAFT_2X2);
        MenuKit.registerSlotPanelMapping(menu, 5,  9,  MenuKit.PANEL_ARMOR);
        MenuKit.registerSlotPanelMapping(menu, 9,  36, MenuKit.PANEL_MAIN_INVENTORY);
        MenuKit.registerSlotPanelMapping(menu, 36, 45, MenuKit.PANEL_HOTBAR);
        MenuKit.registerSlotPanelMapping(menu, 45, 46, MenuKit.PANEL_OFFHAND);

        // ── Step 2: Create vanilla container wrappers for the unified API ─────
        MenuKit.createVanillaContainerWrappers(menu, MKContext.SURVIVAL_INVENTORY, owner);

        // ── Step 3: Add custom MKSlots (equipment, pockets, peek, etc.) ─────
        java.util.List<MKSlot> mkSlots = MenuKit.createSlotsForMenu(
                menu, MKContext.SURVIVAL_INVENTORY, owner);

        for (MKSlot slot : mkSlots) {
            ((AbstractContainerMenuInvoker) this).trevorMod$addSlot(slot);
        }

        // ── Step 4: Attach container state to ALL unique containers ───────
        // Ensures change listeners, read-only enforcement, and tagging work
        // for every container in this menu (crafting, armor, offhand, etc.)
        MenuKit.discoverAndAttachContainerState(menu);

        // ── Step 5: Universal State ──────────────────────────────────────
        // Ensure every slot in the menu has MKSlotState — vanilla crafting,
        // armor, hotbar, offhand, AND custom MKSlots. This enables the
        // event system (right-click, filters, locks, etc.) on ALL slots.
        // getOrCreate is idempotent: MKSlots already have state from their
        // constructor; vanilla slots get a fresh default state here.
        for (Slot slot : menu.slots) {
            MKSlotState state = MKSlotStateRegistry.getOrCreate(slot);

            // Backfill panel name from the map-based tracking (Step 1) if
            // not already set. MKSlots set their own panel name during
            // construction, so this only affects the 46 vanilla slots.
            if (state.getPanelName() == null) {
                String panel = MenuKit.getSlotPanelName(menu, slot.index);
                if (panel != null) {
                    state.setPanelName(panel);
                }
            }
        }

        String side = (owner instanceof net.minecraft.server.level.ServerPlayer) ? "SERVER" : "CLIENT";
        MenuKit.LOGGER.info(
            "[MenuKit] MKMenuMixin {} mapped 46 vanilla slots + added {} MKSlots, total={}",
            side, mkSlots.size(), menu.slots.size());

        // ── Fire MENU_OPEN ───────────────────────────────────────────────
        // Fires after all slots are injected, container state is attached,
        // and universal state is created. InventoryMenu always uses
        // SURVIVAL_INVENTORY context. Consumers can check for creative
        // mode via the player or screen if needed.
        MKSlotEvent openEvent = MKSlotEvent.lifecycle(
                MKEvent.Type.MENU_OPEN, MKContext.SURVIVAL_INVENTORY, owner);
        MKEventBus.fire(openEvent);
    }

    /**
     * Intercepts shift-click on InventoryMenu to enforce directional flags
     * and route items to custom MKSlots (equipment, peek) first.
     *
     * <p>Map-based model: vanilla slots stay vanilla, but MenuKit tracks their
     * panel association via a map. Custom MKSlots (equipment, peek) are real
     * MKSlot instances added after vanilla slots.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void menuKit$directionalShiftClick(net.minecraft.world.entity.player.Player player,
                                                int slotIndex,
                                                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;
        Slot sourceSlot = menu.slots.get(slotIndex);
        if (!sourceSlot.hasItem()) return;

        net.minecraft.world.item.ItemStack sourceStack = sourceSlot.getItem();

        // ── Unified shift-click handling via MKSlotState ──────────────────────
        // Get the panel name for the source slot (from state registry or map)
        String sourcePanel = MenuKit.getEffectivePanelName(menu, sourceSlot);
        com.trevorschoeny.menukit.MKSlotState sourceState = com.trevorschoeny.menukit.MKSlotStateRegistry.get(sourceSlot);

        // Case 1: Source is a MenuKit-managed slot → route to other panels
        if (sourceState != null && sourceState.isMenuKitSlot()) {
            net.minecraft.world.item.ItemStack original = sourceStack.copy();
            boolean moved = MenuKit.tryRouteToOtherPanels(menu, sourceSlot, sourceStack, sourcePanel);
            if (moved) {
                sourceSlot.setChanged();
                cir.setReturnValue(original);
            } else {
                cir.setReturnValue(net.minecraft.world.item.ItemStack.EMPTY);
            }
            return;
        }

        // Case 2: Source is a vanilla slot → try priority routes first, then generic
        net.minecraft.world.item.ItemStack original = sourceStack.copy();

        // Priority routing: certain items have a "natural home" slot (e.g., elytra
        // → equipment elytra slot, totem → equipment totem slot). Check these
        // before generic routing so the item goes to its intended destination
        // even if the target panel has shiftClickIn=false.
        boolean moved = MenuKit.tryPriorityRoute(menu, sourceStack);
        if (moved && sourceStack.isEmpty()) {
            sourceSlot.setChanged();
            cir.setReturnValue(original);
            return;
        }

        // Generic routing: try remaining MenuKit-managed slots with shiftClickIn=true
        moved |= MenuKit.tryRouteToCustomSlots(menu, sourceSlot, sourceStack);
        if (moved && sourceStack.isEmpty()) {
            sourceSlot.setChanged();
            cir.setReturnValue(original);
            return;
        }
        if (moved) {
            sourceSlot.setChanged();
        }

        // Fall through to vanilla's InventoryMenu.quickMoveStack for standard routing
    }
}
