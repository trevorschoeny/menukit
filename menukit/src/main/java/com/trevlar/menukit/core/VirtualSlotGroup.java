package com.trevlar.menukit.core;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * The observed-screen equivalent of {@code SlotGroup}. Wraps vanilla
 * {@link Slot}s that MenuKit doesn't own, providing the same query
 * surface via {@link SlotGroupLike}.
 *
 * <p>Created by {@link HandlerRecognizerRegistry} when analyzing
 * non-MenuKit handlers (chests, furnaces, brewing stands, etc.).
 *
 * <p>Key differences from {@code SlotGroup}:
 * <ul>
 *   <li><b>Storage is read-only.</b> {@link ReadOnlyStorage} reads live
 *       from the wrapped slots but ignores writes. MenuKit doesn't mutate
 *       handlers it doesn't own.</li>
 *   <li><b>No layout metadata.</b> Columns, row gaps, panel references
 *       don't apply — MenuKit doesn't lay out observed screens.</li>
 * </ul>
 *
 * @see SlotGroupLike      The uniform abstraction consumers program against
 * See {@code SlotGroup}           MenuKit-native implementation
 * @see HandlerRecognizerRegistry  Where these are created
 */
public class VirtualSlotGroup implements SlotGroupLike {

    private final String id;
    private final List<Slot> slots;
    private final ReadOnlyStorage storage;
    private final int shiftClickPriority;

    /**
     * @param id                  group identifier (e.g., "container", "input", "player_inventory")
     * @param slots               the vanilla slots this group wraps (in handler order)
     * @param shiftClickPriority  numeric priority (higher = tried first)
     */
    public VirtualSlotGroup(String id, List<Slot> slots, int shiftClickPriority) {
        this.id = id;
        this.slots = List.copyOf(slots);
        // ReadOnlyStorage needs the backing Container — use the first slot's container.
        // All slots in a group share the same Container (that's how grouping works).
        this.storage = new ReadOnlyStorage(this.slots,
                this.slots.isEmpty() ? null : this.slots.get(0).container);
        this.shiftClickPriority = shiftClickPriority;
    }

    /** Convenience: default priority (100). */
    public VirtualSlotGroup(String id, List<Slot> slots) {
        this(id, slots, 100);
    }

    // ── SlotGroupLike Implementation ───────────────────────────────────

    @Override
    public String getId() { return id; }

    @Override
    public Storage getStorage() { return storage; }

    @Override
    public int getShiftClickPriority() { return shiftClickPriority; }

    /**
     * Returns this group's vanilla slots. The handler parameter is
     * ignored — virtual groups already hold their slot references.
     *
     * <p><b>Internal plumbing</b> (see {@link SlotGroupLike#getSlots}) — raw
     * vanilla {@link Slot}s for the grouping engine, no consumer caller.
     */
    @ApiStatus.Internal
    @Override
    public List<? extends Slot> getSlots(AbstractContainerMenu handler) {
        return slots;
    }

    // ── Reverse Lookup ─────────────────────────────────────────────────

    /**
     * Returns true if the given slot belongs to this group.
     * Checks by reference identity — the same Slot object must be
     * in this group's slot list.
     *
     * <p><b>Internal plumbing.</b> Takes a raw vanilla {@link Slot}; its only
     * caller is {@link HandlerRecognizerRegistry#findGroup}. No consumer caller.
     */
    @ApiStatus.Internal
    public boolean containsSlot(Slot slot) {
        for (Slot s : slots) {
            if (s == slot) return true;
        }
        return false;
    }

    /** Returns the number of slots in this group. */
    public int size() {
        return slots.size();
    }
}
