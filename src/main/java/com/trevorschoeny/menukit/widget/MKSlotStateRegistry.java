package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;

/**
 * Static registry mapping {@link Slot} instances to their
 * {@link MKSlotState}. Uses identity-based lookup so each Slot object
 * has its own independent state.
 *
 * <p>State is created lazily on first access. Cleanup should be called
 * when a menu is closed to prevent memory leaks.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
public class MKSlotStateRegistry {

    private static final IdentityHashMap<Slot, MKSlotState> states = new IdentityHashMap<>();

    /** Gets or creates state for a slot. */
    public static MKSlotState getOrCreate(Slot slot) {
        return states.computeIfAbsent(slot, k -> new MKSlotState());
    }

    /** Gets state for a slot, or null if none exists. */
    public static @Nullable MKSlotState get(Slot slot) {
        return states.get(slot);
    }

    /** Removes state for a slot. */
    public static void remove(Slot slot) {
        states.remove(slot);
    }

    /** Whether any state exists for this slot. */
    public static boolean has(Slot slot) {
        return states.containsKey(slot);
    }

    /** Removes state for all slots in a menu. */
    public static void cleanupMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            states.remove(slot);
        }
    }
}
