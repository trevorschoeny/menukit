package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.*;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * MenuKit container tracking mixin — snapshots container contents for change
 * detection and fires change listeners when modifications are detected.
 *
 * <p>After vanilla's {@code addStandardInventorySlots()} runs, this mixin
 * snapshots every unique Container's contents. On each
 * {@code broadcastChanges()} tick, it compares current contents against the
 * snapshot and fires {@link MKContainerState} change listeners when differences
 * are found. This gives 100% coverage of every Container in every menu.
 *
 * <p>Also fires state-change events ({@link MKEvent.Type#SLOT_CHANGED},
 * {@link MKEvent.Type#SLOT_EMPTIED}, {@link MKEvent.Type#SLOT_FILLED})
 * through the {@link MKEventBus} when slot contents change, and fires
 * {@link MKEvent.Type#MENU_CLOSE} when the menu is removed.
 *
 * <p>Also handles cleanup of all MenuKit per-menu state (slot mappings, vanilla
 * container wrappers, slot state, container snapshots) when the menu is closed.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(AbstractContainerMenu.class)
public class MKContainerTrackingMixin {

    // ── Container Change Tracking ────────────────────────────────────────────
    // Cached snapshots of each container's contents, keyed by Container identity.
    // Used by broadcastChanges to detect modifications and fire change listeners.
    @Unique
    private Map<Container, ItemStack[]> menuKit$containerSnapshots;

    // ── Player Reference ─────────────────────────────────────────────────────
    // Captured during addStandardInventorySlots (the Container parameter is
    // always an Inventory for menus that use this method). Needed for firing
    // state-change and lifecycle events through the event bus, since
    // broadcastChanges() has no Player parameter.
    @Unique
    private @Nullable Player menuKit$player;

    // ── Container State Attachment ──────────────────────────────────────────

    /**
     * After slots are injected, scan all unique Containers in this menu
     * and ensure each has an MKContainerState. Also snapshot their contents
     * for change detection. Captures the player reference for event firing.
     */
    // NOTE: This injection runs AFTER MKSlotInjectionMixin's injection on the
    // same method because MKSlotInjectionMixin is listed first in menukit.mixins.json.
    // This ordering matters — we need to snapshot container contents AFTER MKSlots
    // have been added to the menu, so change detection covers them too.
    @Inject(method = "addStandardInventorySlots", at = @At("TAIL"))
    private void menuKit$attachContainerState(Container container, int x, int y, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Capture the player reference from the Inventory container.
        // addStandardInventorySlots always receives the player's Inventory.
        if (container instanceof Inventory inventory) {
            menuKit$player = inventory.player;
        }

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
     * the affected containers AND dispatch slot state events through the
     * {@link MKEventBus}. This gives 100% coverage — every Container
     * in every menu, regardless of implementation class.
     *
     * <p>For each changed slot, determines the appropriate event type:
     * <ul>
     *   <li>{@link MKEvent.Type#SLOT_FILLED} — old was empty, new has item</li>
     *   <li>{@link MKEvent.Type#SLOT_EMPTIED} — old had item, new is empty</li>
     *   <li>{@link MKEvent.Type#SLOT_CHANGED} — both non-empty but different</li>
     * </ul>
     */
    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void menuKit$detectContainerChanges(CallbackInfo ci) {
        if (menuKit$containerSnapshots == null) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Resolve the context for this menu class (may be null for unknown types).
        // Used to enrich events with context info.
        MKContext context = MKContext.defaultForMenuClass(menu.getClass());

        for (Map.Entry<Container, ItemStack[]> entry : menuKit$containerSnapshots.entrySet()) {
            Container c = entry.getKey();
            ItemStack[] snapshot = entry.getValue();
            MKContainerState containerState = MKContainerStateRegistry.get(c);

            // Track whether ANY slot in this container changed (for legacy listeners)
            boolean anyChanged = false;
            int size = Math.min(snapshot.length, c.getContainerSize());

            for (int i = 0; i < size; i++) {
                ItemStack current = c.getItem(i);
                ItemStack old = snapshot[i];

                if (!ItemStack.matches(current, old)) {
                    anyChanged = true;

                    // ── Fire slot state event through the event bus ──────────
                    // Determine which event type: FILLED, EMPTIED, or CHANGED
                    if (menuKit$player != null) {
                        MKEvent.Type eventType;
                        boolean oldEmpty = old.isEmpty();
                        boolean newEmpty = current.isEmpty();

                        if (oldEmpty && !newEmpty) {
                            // Was empty, now has item → SLOT_FILLED
                            eventType = MKEvent.Type.SLOT_FILLED;
                        } else if (!oldEmpty && newEmpty) {
                            // Had item, now empty → SLOT_EMPTIED
                            eventType = MKEvent.Type.SLOT_EMPTIED;
                        } else {
                            // Both non-empty but different → SLOT_CHANGED
                            eventType = MKEvent.Type.SLOT_CHANGED;
                        }

                        // Find the Slot object in the menu that corresponds to
                        // this Container + container index. The tracking mixin
                        // iterates Containers, not Slots, so we need to search
                        // the menu's slot list for a matching slot.
                        Slot matchedSlot = menuKit$findSlotForContainerIndex(menu, c, i);

                        if (matchedSlot != null) {
                            // Look up MenuKit state and region for this slot
                            MKSlotState slotState = MKSlotStateRegistry.get(matchedSlot);
                            MKRegion region = MKRegionRegistry.getRegionForSlot(
                                    menu, matchedSlot.index);
                            String panelName = (slotState != null) ? slotState.getPanelName() : null;

                            MKSlotEvent event = new MKSlotEvent(
                                    eventType, -1,
                                    matchedSlot, slotState,
                                    context, region,
                                    panelName, matchedSlot.index,
                                    current.copy(), ItemStack.EMPTY,
                                    menuKit$player, -1
                            );
                            MKEventBus.fire(event);

                            // ── Advancement trigger for personal MK regions ──────
                            // Vanilla only fires INVENTORY_CHANGED when the player's
                            // own Inventory object changes (checked via identity:
                            // slot.container == player.getInventory()). Items in
                            // custom MKContainer regions (equipment panel, etc.)
                            // never trigger advancements because they're a different
                            // Container object.
                            //
                            // Fix: when an MKContainer changes, re-fire the trigger
                            // so vanilla re-evaluates all inventory-based criteria.
                            // This handles both directions: item moved INTO an MK
                            // region (inventory lost an item, recheck) and item
                            // moved OUT of an MK region (inventory gained an item).
                            //
                            // Guards:
                            // - Server only (advancements are server-side)
                            // - Skip vanilla Inventory containers (vanilla handles those)
                            // - Only fire for MKContainer (custom personal regions)
                            //   NOT for external containers like chests/furnaces
                            if (c instanceof MKContainer
                                    && menuKit$player instanceof ServerPlayer serverPlayer) {
                                CriteriaTriggers.INVENTORY_CHANGED.trigger(
                                        serverPlayer,
                                        serverPlayer.getInventory(),
                                        current
                                );
                            }
                        }
                    }

                    // Update snapshot to current value
                    snapshot[i] = current.copy();
                }
            }

            // Fire legacy container-level change listeners (existing behavior)
            if (anyChanged && containerState != null && containerState.hasChangeListeners()) {
                containerState.fireChangeListeners(c);
            }
        }
    }

    /**
     * Finds the Slot in the menu whose backing Container and container-local
     * index match the given parameters. Returns null if no match is found.
     *
     * <p>This is O(n) over the menu's slots, but broadcastChanges runs at
     * most 20 times per second and typical menus have fewer than 100 slots,
     * so the cost is negligible.
     */
    @Unique
    private static @Nullable Slot menuKit$findSlotForContainerIndex(
            AbstractContainerMenu menu, Container container, int containerIndex) {
        for (Slot slot : menu.slots) {
            // Identity comparison for the Container (same object, not equals)
            if (slot.container == container && slot.getContainerSlot() == containerIndex) {
                return slot;
            }
        }
        return null;
    }

    // ── Lifecycle: MENU_CLOSE ────────────────────────────────────────────────

    /**
     * Fires {@link MKEvent.Type#MENU_CLOSE} when the menu is removed,
     * BEFORE any cleanup happens. This lets event consumers inspect menu state
     * (slots, regions, container contents) before it's torn down.
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void menuKit$fireMenuClose(Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Resolve context for the event — may be null for unknown menu types.
        // Fire the event anyway so consumers can still react to menu close.
        MKContext context = MKContext.defaultForMenuClass(menu.getClass());

        MKSlotEvent event = MKSlotEvent.lifecycle(
                MKEvent.Type.MENU_CLOSE, context, player);
        MKEventBus.fire(event);
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    /**
     * Cleans up MenuKit state when a menu is closed.
     * Removes slot->panel mappings, vanilla container wrappers,
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

        // Clear the cached player reference
        menuKit$player = null;
    }
}
