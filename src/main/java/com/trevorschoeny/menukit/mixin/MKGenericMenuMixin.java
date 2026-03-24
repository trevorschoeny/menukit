package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKContainerState;
import com.trevorschoeny.menukit.MKContainerStateRegistry;
import com.trevorschoeny.menukit.MKSlot;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * MenuKit internal mixin — adds MKSlots to ANY container menu and handles
 * directional shift-click routing for all menu types.
 *
 * <p><b>Slot injection:</b> Targets {@code addStandardInventorySlots()} which
 * covers virtually every vanilla container with player inventory: chests,
 * furnaces, crafting tables, anvils, etc. MKSlots are added AFTER vanilla
 * finishes adding its own slots, ensuring slot indices are consistent.
 *
 * <p>InventoryMenu is SKIPPED for slot injection — it's handled separately by
 * {@link MKMenuMixin} because it has unique constructor parameters and
 * creative mode handling.
 *
 * <p><b>Shift-click:</b> Intercepts {@code quickMoveStack()} on ALL menu types
 * (including InventoryMenu) to enforce directional shift-click flags. MKSlots
 * only participate in shift-click routing when their panel has the appropriate
 * {@code shiftClickIn} or {@code shiftClickOut} flag set and the panel is visible.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(AbstractContainerMenu.class)
public class MKGenericMenuMixin {

    // ── Container Change Tracking ────────────────────────────────────────────
    // Cached snapshots of each container's contents, keyed by Container identity.
    // Used by broadcastChanges to detect modifications and fire change listeners.
    @Unique
    private Map<Container, ItemStack[]> menuKit$containerSnapshots;

    // ── Slot Injection ───────────────────────────────────────────────────────

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
        if (context == null) return;

        // Auto-create vanilla container wrappers for the unified API
        MenuKit.createVanillaContainerWrappers(menu, context, inventory.player);

        // Check if any panels are registered for this context
        if (!MenuKit.hasPanelsForContext(context)) return;

        // Create and add custom MKSlots (equipment, pockets, peek, etc.)
        java.util.List<MKSlot> mkSlots = MenuKit.createSlotsForMenu(menu, context, inventory.player);
        for (MKSlot slot : mkSlots) {
            ((AbstractContainerMenuInvoker) menu).trevorMod$addSlot(slot);
        }
    }

    // (Menu cleanup is handled below in menuKit$cleanupOnRemoved)

    // ── Container State Attachment ──────────────────────────────────────────

    /**
     * After slots are injected, scan all unique Containers in this menu
     * and ensure each has an MKContainerState. Also snapshot their contents
     * for change detection.
     */
    @Inject(method = "addStandardInventorySlots", at = @At("TAIL"))
    private void menuKit$attachContainerState(Container container, int x, int y, CallbackInfo ci2) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        menuKit$snapshotContainers(menu);
    }

    /**
     * Builds container content snapshots for change detection.
     * Called after container state is attached (by MenuKit.discoverAndAttachContainerState).
     */
    @Unique
    private void menuKit$snapshotContainers(AbstractContainerMenu menu) {
        if (menuKit$containerSnapshots == null) {
            menuKit$containerSnapshots = new IdentityHashMap<>();
        }

        // Find every unique Container backing the slots in this menu
        for (Slot slot : menu.slots) {
            Container c = slot.container;
            if (c == null || menuKit$containerSnapshots.containsKey(c)) continue;

            // Snapshot current contents for change detection
            ItemStack[] snapshot = new ItemStack[c.getContainerSize()];
            for (int i = 0; i < snapshot.length; i++) {
                snapshot[i] = c.getItem(i).copy();
            }
            menuKit$containerSnapshots.put(c, snapshot);
        }
    }

    // ── Change Detection via broadcastChanges ────────────────────────────────

    /**
     * After vanilla's broadcastChanges(), compare current container contents
     * against our snapshots. If anything changed, fire change listeners on
     * the affected containers. This gives 100% coverage — every Container
     * in every menu, regardless of implementation class.
     */
    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void menuKit$detectContainerChanges(CallbackInfo ci) {
        if (menuKit$containerSnapshots == null) return;

        for (Map.Entry<Container, ItemStack[]> entry : menuKit$containerSnapshots.entrySet()) {
            Container c = entry.getKey();
            ItemStack[] snapshot = entry.getValue();
            MKContainerState state = MKContainerStateRegistry.get(c);
            if (state == null || !state.hasChangeListeners()) continue;

            // Compare each slot against snapshot
            boolean changed = false;
            int size = Math.min(snapshot.length, c.getContainerSize());
            for (int i = 0; i < size; i++) {
                ItemStack current = c.getItem(i);
                if (!ItemStack.matches(current, snapshot[i])) {
                    changed = true;
                    snapshot[i] = current.copy();
                }
            }

            if (changed) {
                state.fireChangeListeners(c);
            }
        }
    }

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

    // ── Cleanup ──────────────────────────────────────────────────────────────

    /**
     * Cleans up MenuKit state when a menu is closed.
     * Removes slot→panel mappings, vanilla container wrappers,
     * and container state snapshots.
     */
    @Inject(method = "removed", at = @At("TAIL"))
    private void menuKit$cleanupOnRemoved(Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        MenuKit.cleanupSlotPanelMapping(menu);
        MenuKit.cleanupVanillaContainers(menu);
        MKSlotStateRegistry.cleanupMenu(menu);

        // Clean up container state for non-persistent containers
        // (Player inventory persists across menus, so don't remove its state)
        if (menuKit$containerSnapshots != null) {
            for (Container c : menuKit$containerSnapshots.keySet()) {
                if (!(c instanceof Inventory)) {
                    MKContainerStateRegistry.remove(c);
                }
            }
            menuKit$containerSnapshots.clear();
            menuKit$containerSnapshots = null;
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
