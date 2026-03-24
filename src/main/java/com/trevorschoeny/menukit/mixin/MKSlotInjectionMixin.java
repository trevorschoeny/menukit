package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MenuKit slot injection mixin — adds MKSlots to any container menu.
 *
 * <p>Targets {@code addStandardInventorySlots()} which covers virtually every
 * vanilla container with player inventory: chests, furnaces, crafting tables,
 * anvils, etc. MKSlots are added AFTER vanilla finishes adding its own slots,
 * ensuring slot indices are consistent.
 *
 * <p>InventoryMenu is SKIPPED — it's handled separately by {@link MKMenuMixin}
 * because it has unique constructor parameters and creative mode handling.
 *
 * <p>After slot injection and universal state creation, fires
 * {@link MKSlotEvent.Type#MENU_OPEN} through the {@link MKEventBus}.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(AbstractContainerMenu.class)
public class MKSlotInjectionMixin {

    // ── MENU_OPEN Guard ──────────────────────────────────────────────────────
    // addStandardInventorySlots can theoretically be called more than once on
    // a menu (unlikely but possible). MENU_OPEN should fire exactly ONCE per
    // menu lifecycle, so we guard with a boolean flag.
    @Unique
    private boolean menuKit$menuOpenFired = false;

    // ── Slot Injection ───────────────────────────────────────────────────────

    // NOTE: MKContainerTrackingMixin also injects at TAIL on this method.
    // This mixin is listed first in menukit.mixins.json, so slot injection
    // runs before container snapshotting. Keep this ordering in the JSON.
    @Inject(method = "addStandardInventorySlots", at = @At("TAIL"))
    private void menuKit$addSlotsToAnyMenu(Container container, int x, int y, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Skip InventoryMenu — handled by MKMenuMixin with creative mode support
        if (menu instanceof InventoryMenu) return;

        // Need a Player reference for per-player container management
        if (!(container instanceof Inventory inventory)) return;

        // ── Map the 36 player inventory slots just added by vanilla ────
        // addStandardInventorySlots() adds 27 main inventory + 9 hotbar = 36 slots.
        // They're the last 36 slots in the menu's slot list (before any MKSlots).
        int totalSlots = menu.slots.size();
        int playerSlotsStart = totalSlots - 36;

        // Main inventory (27 slots)
        MenuKit.registerSlotPanelMapping(menu, playerSlotsStart, playerSlotsStart + 27,
                MenuKit.PANEL_MAIN_INVENTORY);
        // Hotbar (9 slots)
        MenuKit.registerSlotPanelMapping(menu, playerSlotsStart + 27, playerSlotsStart + 36,
                MenuKit.PANEL_HOTBAR);

        // Find a default context for this menu class
        MKContext context = MKContext.defaultForMenuClass(menu.getClass());
        if (context != null) {
            // Auto-create vanilla container wrappers for the unified API
            MenuKit.createVanillaContainerWrappers(menu, context, inventory.player);

            // Add custom MKSlots if panels are registered for this context
            if (MenuKit.hasPanelsForContext(context)) {
                java.util.List<MKSlot> mkSlots = MenuKit.createSlotsForMenu(menu, context, inventory.player);
                for (MKSlot slot : mkSlots) {
                    ((AbstractContainerMenuInvoker) menu).trevorMod$addSlot(slot);
                }
            }
        }

        // ── Universal State ──────────────────────────────────────────────
        // Ensure every slot in the menu has MKSlotState. This enables the
        // event system and all MKSlotState features (filters, locks,
        // right-click handlers, etc.) to work on ALL slots — vanilla
        // hotbar, chest slots, furnace slots, custom mod slots, everything.
        // Runs unconditionally: even menus with no MKContext still get
        // universal state so the event system works on their vanilla slots.
        // getOrCreate is idempotent: MKSlots that already have state keep
        // their existing state; vanilla slots get a fresh default state.
        for (Slot slot : menu.slots) {
            MKSlotState state = MKSlotStateRegistry.getOrCreate(slot);

            // Backfill panel name from the map-based tracking if not already
            // set. MKSlots set their own panel name during construction, so
            // this only affects vanilla slots that were mapped above.
            if (state.getPanelName() == null) {
                String panel = MenuKit.getSlotPanelName(menu, slot.index);
                if (panel != null) {
                    state.setPanelName(panel);
                }
            }
        }

        // ── Fire MENU_OPEN ───────────────────────────────────────────────
        // Fires after all slots are injected and universal state is created.
        // Guard ensures this only fires once per menu lifecycle, even if
        // addStandardInventorySlots is called multiple times.
        // Context may be null for unknown menu types — fire anyway.
        if (!menuKit$menuOpenFired) {
            menuKit$menuOpenFired = true;
            MKSlotEvent event = MKSlotEvent.lifecycle(
                    MKSlotEvent.Type.MENU_OPEN, context, inventory.player);
            MKEventBus.fire(event);
        }
    }

    // ── Shift-click routing is handled per-menu-class ───────────────────────
    // quickMoveStack() is abstract on AbstractContainerMenu, so we can't inject
    // here. Instead:
    //   - InventoryMenu: handled by MKMenuMixin
    //   - Other concrete menus: vanilla's own quickMoveStack uses moveItemStackTo,
    //     which calls mayPlace() on each target slot. MKSlots block items from
    //     going into hidden/disabled slots naturally.
    //
    // Custom MKSlots (equipment, peek) are injected AFTER vanilla slots in the
    // menu. Vanilla's quickMoveStack doesn't know about them — it only routes
    // within its own slot ranges. Our MKMenuMixin handles the InventoryMenu case
    // where custom MKSlots need priority routing.
}
