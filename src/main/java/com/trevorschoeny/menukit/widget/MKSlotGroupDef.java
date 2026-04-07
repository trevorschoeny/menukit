package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerDef;
import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.region.MKRegion;
import com.trevorschoeny.menukit.region.RegionGroupBuilder;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Immutable definition of a slot group — the primary declaration unit in
 * MenuKit v2. A slot group combines what was previously spread across
 * {@link MKContainerDef}, {@link RegionGroupBuilder}, and individual
 * {@link MKSlotDef} slot rules into a single declaration.
 *
 * <p>Slot groups are the "star" of the backend API. They define:
 * <ul>
 *   <li>How many slots exist in the group</li>
 *   <li>What items each slot accepts (filter, max stack, output-only)</li>
 *   <li>How the group persists (player NBT, block entity, item NBT, ephemeral)</li>
 *   <li>What happens on block break (drop vs retain)</li>
 *   <li>Shift-click transfer policy (in/out)</li>
 * </ul>
 *
 * <p>Created via {@link MKSlotGroupBuilder} and registered with
 * {@code MenuKit.slotGroup("name")...register()}.
 *
 * <p>Internally, MenuKit builds {@link MKContainerDef} and {@link MKRegion}
 * objects from this definition — consumers never see those internals.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public record MKSlotGroupDef(
        String name,                    // unique identifier
        int size,                       // number of slots
        MKContainerDef.BindingType binding,   // PLAYER, INSTANCE, EPHEMERAL
        MKContainerDef.Persistence persistence, // PERSISTENT, TRANSIENT, OUTPUT
        MKContainerType containerType,  // functional classification
        boolean shiftClickIn,           // can items be shift-clicked into this group?
        boolean shiftClickOut,          // can items be shift-clicked out?
        @Nullable Predicate<ItemStack> shiftInFilter, // conditional shift-in acceptance
        @Nullable Predicate<ItemStack> slotFilter,    // item filter applied to all slots
        int maxStack,                   // per-slot max stack override (0 = vanilla default)
        BreakBehavior breakBehavior     // what happens when the block is broken
) {

    /** What happens to items when the containing block is broken.
     *  Only meaningful for INSTANCE-bound (block entity) groups. */
    public enum BreakBehavior {
        /** Items spill into the world, block drops empty. Like a chest. */
        DROPS_ON_BREAK,
        /** Contents transfer to the dropped item's NBT. Like a shulker box. */
        RETAINED_ON_BREAK
    }
}
