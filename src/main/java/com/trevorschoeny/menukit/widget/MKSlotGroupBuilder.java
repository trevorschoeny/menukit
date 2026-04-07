package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerDef;
import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.region.MKRegion;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Fluent builder for {@link MKSlotGroupDef} — the primary declaration unit
 * in MenuKit v2.
 *
 * <p>Usage:
 * <pre>{@code
 * // Simple storage (like a chest)
 * MenuKit.slotGroup("my_backpack")
 *     .slots(27)
 *     .playerBound()
 *     .register();
 *
 * // Furnace fuel slot with conditional shift-in
 * MenuKit.slotGroup("fuel")
 *     .slots(1)
 *     .filter(stack -> stack.getBurnTime() > 0)
 *     .shiftIn()
 *     .instanceBound()
 *     .register();
 *
 * // Output-only slot (take items out, can't put in)
 * MenuKit.slotGroup("output")
 *     .slots(1)
 *     .outputOnly()
 *     .instanceBound()
 *     .register();
 * }</pre>
 *
 * <p>Internally, this creates an {@link MKContainerDef} and configures
 * the appropriate {@link MKRegion} shift-click flags. The consumer never
 * sees those internal objects.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public class MKSlotGroupBuilder {

    private final String name;
    private int size = 0;
    private MKContainerDef.BindingType binding = MKContainerDef.BindingType.PLAYER;
    private MKContainerDef.Persistence persistence = MKContainerDef.Persistence.PERSISTENT;
    private MKContainerType containerType = MKContainerType.SIMPLE;
    private boolean shiftClickIn = false;   // default: off (groups don't accept shift-in unless opted in)
    private boolean shiftClickOut = true;   // default: on (items can always leave via shift-click)
    private @Nullable Predicate<ItemStack> shiftInFilter;
    private @Nullable Predicate<ItemStack> slotFilter;
    private int maxStack = 0;  // 0 = vanilla default
    private MKSlotGroupDef.BreakBehavior breakBehavior = MKSlotGroupDef.BreakBehavior.DROPS_ON_BREAK;

    public MKSlotGroupBuilder(String name) {
        this.name = name;
    }

    // ── Size ─────────────────────────────────────────────────────────────

    /** Sets the number of slots in the group. */
    public MKSlotGroupBuilder slots(int size) {
        this.size = size;
        return this;
    }

    // ── Binding (where data is stored) ──────────────────────────────────

    /** Container follows the player. Stored in player NBT. Default. */
    public MKSlotGroupBuilder playerBound() {
        this.binding = MKContainerDef.BindingType.PLAYER;
        return this;
    }

    /** Container tied to a block position. Stored in world SavedData. */
    public MKSlotGroupBuilder instanceBound() {
        this.binding = MKContainerDef.BindingType.INSTANCE;
        return this;
    }

    /** Temporary container — not persisted. For binding to external sources. */
    public MKSlotGroupBuilder ephemeral() {
        this.binding = MKContainerDef.BindingType.EPHEMERAL;
        return this;
    }

    // ── Persistence (how items behave) ──────────────────────────────────

    /** Items persist across screen close. Default. */
    public MKSlotGroupBuilder persistent() {
        this.persistence = MKContainerDef.Persistence.PERSISTENT;
        return this;
    }

    /** Items eject to player inventory on screen close (like crafting grids). */
    public MKSlotGroupBuilder transientItems() {
        this.persistence = MKContainerDef.Persistence.TRANSIENT;
        return this;
    }

    /** Read-only — items can be taken out but not placed in. */
    public MKSlotGroupBuilder outputOnly() {
        this.persistence = MKContainerDef.Persistence.OUTPUT;
        return this;
    }

    // ── Container Type ──────────────────────────────────────────────────

    /** Sets the functional classification. Defaults to SIMPLE. */
    public MKSlotGroupBuilder type(MKContainerType type) {
        this.containerType = type;
        return this;
    }

    // ── Shift-Click Transfer Policy ─────────────────────────────────────

    /** Allow items to be shift-clicked INTO this group (unconditionally). */
    public MKSlotGroupBuilder shiftIn() {
        this.shiftClickIn = true;
        return this;
    }

    /** Allow items to be shift-clicked INTO this group only when the filter matches. */
    public MKSlotGroupBuilder shiftIn(Predicate<ItemStack> filter) {
        this.shiftClickIn = true;
        this.shiftInFilter = filter;
        return this;
    }

    /** Disable shift-click OUT of this group. */
    public MKSlotGroupBuilder noShiftOut() {
        this.shiftClickOut = false;
        return this;
    }

    /** Only shift-click OUT, never IN. Convenience for output-style groups. */
    public MKSlotGroupBuilder shiftOutOnly() {
        this.shiftClickIn = false;
        this.shiftClickOut = true;
        return this;
    }

    // ── Slot Rules ──────────────────────────────────────────────────────

    /** Applies an item filter to ALL slots in this group. */
    public MKSlotGroupBuilder filter(Predicate<ItemStack> filter) {
        this.slotFilter = filter;
        return this;
    }

    /** Sets a per-slot max stack size override for all slots. 0 = vanilla default. */
    public MKSlotGroupBuilder maxStack(int max) {
        this.maxStack = max;
        return this;
    }

    // ── Break Behavior (block entity only) ──────────────────────────────

    /** Items spill into the world on block break. Default. */
    public MKSlotGroupBuilder dropsOnBreak() {
        this.breakBehavior = MKSlotGroupDef.BreakBehavior.DROPS_ON_BREAK;
        return this;
    }

    /** Contents transfer to the dropped item's NBT on block break (shulker-style). */
    public MKSlotGroupBuilder retainedOnBreak() {
        this.breakBehavior = MKSlotGroupDef.BreakBehavior.RETAINED_ON_BREAK;
        return this;
    }

    // ── Terminal ─────────────────────────────────────────────────────────

    /**
     * Builds and registers the slot group with MenuKit.
     *
     * <p>Internally, this creates an {@link MKContainerDef} and stores the
     * group definition for region configuration at menu construction time.
     */
    public void register() {
        if (size <= 0) {
            throw new IllegalStateException(
                    "[MenuKit] Slot group '" + name + "' must have size > 0");
        }

        MKSlotGroupDef def = new MKSlotGroupDef(
                name, size, binding, persistence, containerType,
                shiftClickIn, shiftClickOut, shiftInFilter,
                slotFilter, maxStack, breakBehavior
        );

        // Delegate to MenuKit for registration — this creates the
        // internal MKContainerDef and stores the group definition
        MenuKit.registerSlotGroup(def);
    }
}
