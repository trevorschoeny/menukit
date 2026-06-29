package com.trevorschoeny.menukit.core;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Ordered registry of handler recognizers. Given an {@link AbstractContainerMenu},
 * produces a list of {@link VirtualSlotGroup}s describing the handler's slot
 * structure for consumer code that wants to observe vanilla menus uniformly.
 *
 * <p>Recognition is stateless — no caching. The recognizer walks the slot list
 * (O(n) in slot count, typically under 100 slots) on each call. This eliminates
 * stale-cache and memory-leak concerns. If profiling later reveals recognizer
 * calls are hot, caching can be added with proper weak references.
 *
 * <p><b>Extension point:</b> consumers can register custom recognizers for their
 * own modded handlers via {@link #register(Recognizer)}. Custom recognizers run
 * before the default fallback — first match wins.
 *
 * <p><b>Entry point for consumers:</b>
 * {@link #findGroup(AbstractContainerMenu, Slot)} returns the observed group a
 * vanilla slot belongs to.
 *
 * <p><b>§0043 scope:</b> this registry observes vanilla and consumer-recognized
 * handlers. It does not know about MenuKit-owned handlers (those are MKC's
 * concern; MKC ships its own lookup facade that fast-paths owned slots and
 * falls through here for vanilla observation).
 *
 * @see SlotGroupLike       The uniform interface consumers program against
 * @see VirtualSlotGroup    What recognizers produce
 */
public class HandlerRecognizerRegistry {

    // ── Recognizer Interface ───────────────────────────────────────────

    /**
     * A recognizer inspects a handler and returns its slot groups, or null
     * if it doesn't apply to this handler type.
     *
     * <p>When a recognizer returns a non-null, non-empty list, that list is
     * the authoritative grouping for the handler — no further recognizers run.
     * The list should cover ALL slots in the handler (including player inventory).
     */
    @FunctionalInterface
    public interface Recognizer {
        /**
         * Attempts to recognize the given handler.
         *
         * @param handler the handler to inspect
         * @return the recognized groups covering all slots, or null if this
         *         recognizer doesn't apply
         */
        @Nullable List<VirtualSlotGroup> recognize(AbstractContainerMenu handler);
    }

    // ── Registry ───────────────────────────────────────────────────────

    // Dedicated recognizers — checked before the default fallback.
    // Built-in ones are registered in the static initializer.
    // Consumer-registered ones are appended via register().
    private static final List<Recognizer> recognizers = new ArrayList<>();

    static {
        // Built-in recognizers for vanilla edge cases where
        // container-identity grouping produces wrong results.
        recognizers.add(HandlerRecognizerRegistry::recognizeInventoryMenu);
        recognizers.add(HandlerRecognizerRegistry::recognizeFurnace);
        recognizers.add(HandlerRecognizerRegistry::recognizeBrewingStand);
    }

    /**
     * Registers a custom recognizer. Called at mod init by consumers who
     * need to describe their own modded handlers.
     *
     * <p>Custom recognizers are appended after built-in ones but before
     * the default fallback. First match wins.
     */
    public static void register(Recognizer recognizer) {
        recognizers.add(recognizer);
    }

    // ── Main Entry Points ──────────────────────────────────────────────

    /**
     * Recognizes the slot structure of a handler. Returns the list of
     * {@link VirtualSlotGroup}s describing the handler's groups.
     *
     * <p>Per §0043, this is observation-only — for MenuKit-owned handlers, the
     * owning code path is authoritative and MKC ships a facade that bypasses
     * this method. Callers that may receive either owned or observed handlers
     * should go through MKC's facade rather than calling here directly.
     *
     * @param handler the handler to analyze
     * @return recognized groups (may be empty for empty handlers)
     *
     * @implNote <b>Internal plumbing.</b> Walks a live menu's raw {@link Slot}
     *           list; used by the library's grouping engine + contract
     *           verification. Consumers register their own grouping via
     *           {@link #register(Recognizer)}; they don't drive recognition over
     *           raw slots themselves.
     */
    @ApiStatus.Internal
    public static List<VirtualSlotGroup> recognize(AbstractContainerMenu handler) {
        if (handler.slots.isEmpty()) return List.of();

        // Try dedicated recognizers first (first match wins)
        for (Recognizer recognizer : recognizers) {
            List<VirtualSlotGroup> result = recognizer.recognize(handler);
            if (result != null && !result.isEmpty()) {
                return Collections.unmodifiableList(result);
            }
        }

        // Default: group all slots by container identity
        return Collections.unmodifiableList(
                groupSlotsByContainerIdentity(handler, 0, handler.slots.size()));
    }

    /**
     * Finds the observed {@link SlotGroupLike} a vanilla slot belongs to, by
     * running the recognizer chain and searching the recognized groups.
     *
     * <p>Per §0043, this is observation-only — MenuKit-owned slots are MKC's
     * concern. MKC ships a facade that fast-paths owned slots and falls through
     * here for vanilla observation. Callers that may receive either owned or
     * observed slots should go through MKC's facade rather than calling here
     * directly.
     *
     * @param handler the handler the slot belongs to
     * @param slot    the slot to look up
     * @return the observed group, or empty if no recognized group contains the slot
     *
     * @implNote <b>Internal plumbing.</b> Takes a raw vanilla {@link Slot}; the
     *           only caller is MKC's {@code MKCScreenHandler.findGroupForSlot}
     *           facade (itself internal). No consumer caller.
     */
    @ApiStatus.Internal
    public static Optional<SlotGroupLike> findGroup(AbstractContainerMenu handler,
                                                     Slot slot) {
        List<VirtualSlotGroup> groups = recognize(handler);
        for (VirtualSlotGroup group : groups) {
            if (group.containsSlot(slot)) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    // ── Shared Utility: Identity Grouping ──────────────────────────────
    // Both the default recognizer and dedicated recognizers use this to
    // group slots by Container reference identity. Extracted as a public
    // utility so custom recognizers can reuse it too.

    /**
     * Groups a range of slots by {@link Slot#container} reference identity.
     * Contiguous slots sharing the same Container become one
     * {@link VirtualSlotGroup}. Player inventory is detected via
     * {@code instanceof Inventory} and named {@code "player_inventory"}.
     *
     * <p>This is the default grouping strategy and also a building block
     * for dedicated recognizers that need to identity-group the non-special
     * portion of their handlers (typically the player inventory at the end).
     *
     * @param handler   the handler to read slots from
     * @param fromIndex start of the range (inclusive)
     * @param toIndex   end of the range (exclusive)
     * @return groups covering the specified range
     *
     * @implNote <b>Internal plumbing.</b> Reads a live menu's raw {@link Slot}
     *           list to build identity groups — used by the built-in recognizers.
     *           No consumer caller today; consumer recognizers describe their own
     *           handlers and don't need to drive raw-slot identity grouping.
     */
    @ApiStatus.Internal
    public static List<VirtualSlotGroup> groupSlotsByContainerIdentity(
            AbstractContainerMenu handler, int fromIndex, int toIndex) {

        List<VirtualSlotGroup> groups = new ArrayList<>();
        if (fromIndex >= toIndex) return groups;

        // Walk the slot list, starting a new group each time the
        // Container reference changes.
        //
        // Per §0043: this method observes vanilla handlers. MenuKit-owned
        // handlers are MKC's concern and don't reach this code path under
        // normal use (MKC's facade fast-paths owned-handler lookups).
        Object currentContainer = null;  // reference-identity comparison
        List<Slot> currentSlots = new ArrayList<>();
        int containerGroupIndex = 0;

        for (int i = fromIndex; i < toIndex; i++) {
            Slot slot = handler.slots.get(i);

            if (slot.container != currentContainer) {
                if (!currentSlots.isEmpty()) {
                    groups.add(buildIdentityGroup(
                            currentSlots, currentContainer, containerGroupIndex));
                    containerGroupIndex++;
                    currentSlots = new ArrayList<>();
                }
                currentContainer = slot.container;
            }
            currentSlots.add(slot);
        }

        // Final group
        if (!currentSlots.isEmpty()) {
            groups.add(buildIdentityGroup(
                    currentSlots, currentContainer, containerGroupIndex));
        }

        return groups;
    }

    /**
     * Builds a VirtualSlotGroup from a contiguous run of same-container slots.
     * Naming convention:
     * <ul>
     *   <li>Player inventory: {@code "player_inventory"} (detected via
     *       {@code container instanceof Inventory})</li>
     *   <li>Single non-player container: {@code "container"}</li>
     *   <li>Multiple non-player containers: {@code "container_0"},
     *       {@code "container_1"}, etc.</li>
     * </ul>
     */
    private static VirtualSlotGroup buildIdentityGroup(
            List<Slot> slots, Object container, int containerIndex) {

        // Detect player inventory by container type
        if (container instanceof Inventory) {
            return new VirtualSlotGroup("player_inventory", slots, 0);
        }

        // Non-player container
        String id = containerIndex == 0 ? "container" : "container_" + containerIndex;
        return new VirtualSlotGroup(id, slots, 100);
    }

    // ── Built-in Recognizer: Furnace ───────────────────────────────────
    // Covers FurnaceMenu, BlastFurnaceMenu, SmokerMenu (all extend
    // AbstractFurnaceMenu). Without this, all 3 slots share one Container
    // (the block entity) and the default grouper lumps them together.

    private static @Nullable List<VirtualSlotGroup> recognizeFurnace(
            AbstractContainerMenu handler) {
        if (!(handler instanceof AbstractFurnaceMenu)) return null;
        if (handler.slots.size() < 3) return null;

        List<VirtualSlotGroup> groups = new ArrayList<>();

        // Slot 0: input — accepts items that can be smelted
        groups.add(new VirtualSlotGroup("input",
                List.of(handler.slots.get(0)), 100));

        // Slot 1: fuel — accepts fuel items
        groups.add(new VirtualSlotGroup("fuel",
                List.of(handler.slots.get(1)), 90));

        // Slot 2: output — items can only be taken out
        groups.add(new VirtualSlotGroup("output",
                List.of(handler.slots.get(2)), 100));

        // Remaining slots: player inventory (identity-grouped)
        groups.addAll(groupSlotsByContainerIdentity(handler, 3, handler.slots.size()));

        return groups;
    }

    // ── Built-in Recognizer: Brewing Stand ─────────────────────────────
    // BrewingStandMenu has 5 container slots sharing one Container.
    // Slots 0-2: potion bottles, Slot 3: ingredient, Slot 4: blaze powder fuel.

    private static @Nullable List<VirtualSlotGroup> recognizeBrewingStand(
            AbstractContainerMenu handler) {
        if (!(handler instanceof BrewingStandMenu)) return null;
        if (handler.slots.size() < 5) return null;

        List<VirtualSlotGroup> groups = new ArrayList<>();

        // Slots 0-2: potion bottles
        groups.add(new VirtualSlotGroup("potions",
                List.of(handler.slots.get(0),
                        handler.slots.get(1),
                        handler.slots.get(2)), 100));

        // Slot 3: ingredient (e.g., nether wart, glowstone dust)
        groups.add(new VirtualSlotGroup("ingredient",
                List.of(handler.slots.get(3)), 100));

        // Slot 4: fuel (blaze powder)
        groups.add(new VirtualSlotGroup("fuel",
                List.of(handler.slots.get(4)), 90));

        // Remaining slots: player inventory (identity-grouped)
        groups.addAll(groupSlotsByContainerIdentity(handler, 5, handler.slots.size()));

        return groups;
    }

    // ── Built-in Recognizer: Player Inventory Menu ─────────────────────
    // InventoryMenu's armor / main / hotbar / offhand slots all share the player
    // Inventory container, so the default identity grouper lumps the whole player
    // inventory into one "player_inventory" blob. This splits them into the
    // structural groups consumers expect, by InventoryMenu's published slot-index
    // ranges. Slots past the offhand are mod slots appended to InventoryMenu —
    // identity-grouped so they're observed too, not swallowed into vanilla groups.

    private static @Nullable List<VirtualSlotGroup> recognizeInventoryMenu(
            AbstractContainerMenu handler) {
        if (!(handler instanceof InventoryMenu)) return null;
        // 1 result + 4 craft + 4 armor + 27 inv + 9 hotbar + 1 offhand = 46.
        if (handler.slots.size() < InventoryMenu.SHIELD_SLOT + 1) return null;

        List<VirtualSlotGroup> groups = new ArrayList<>();
        groups.add(new VirtualSlotGroup("crafting_output",
                List.of(handler.slots.get(InventoryMenu.RESULT_SLOT)), 100));
        groups.add(new VirtualSlotGroup("crafting_input",
                slotRange(handler, InventoryMenu.CRAFT_SLOT_START, InventoryMenu.CRAFT_SLOT_END), 100));
        groups.add(new VirtualSlotGroup("armor",
                slotRange(handler, InventoryMenu.ARMOR_SLOT_START, InventoryMenu.ARMOR_SLOT_END), 100));
        groups.add(new VirtualSlotGroup("main_inventory",
                slotRange(handler, InventoryMenu.INV_SLOT_START, InventoryMenu.INV_SLOT_END), 0));
        groups.add(new VirtualSlotGroup("hotbar",
                slotRange(handler, InventoryMenu.USE_ROW_SLOT_START, InventoryMenu.USE_ROW_SLOT_END), 0));
        groups.add(new VirtualSlotGroup("offhand",
                List.of(handler.slots.get(InventoryMenu.SHIELD_SLOT)), 100));

        if (handler.slots.size() > InventoryMenu.SHIELD_SLOT + 1) {
            groups.addAll(groupSlotsByContainerIdentity(
                    handler, InventoryMenu.SHIELD_SLOT + 1, handler.slots.size()));
        }
        return groups;
    }

    /** Immutable view of slots {@code [from, to)} for range-based recognizers. */
    private static List<Slot> slotRange(AbstractContainerMenu handler, int from, int to) {
        return List.copyOf(handler.slots.subList(from, to));
    }

}
