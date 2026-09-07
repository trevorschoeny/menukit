package com.trevlar.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * Port (MK defines, MKC implements): resolve a {@code CREATED_SLOT}
 * {@link Address} to its live in-menu slot.
 *
 * <h2>Why a port (the §0042 reason)</h2>
 *
 * A created slot is an {@code MKCSlot} — an MKC type MK must never reference —
 * and only MKC can recognise one in {@code menu.slots}. MKC registers an
 * implementation (a session-cached identity binding); when MKC is absent the
 * resolver is {@code null} and created-slot resolution simply yields empty
 * (created slots need MKC) — vanilla, panel, and element resolution stay fully
 * MK-alone.
 *
 * <h2>Position is not part of the port</h2>
 *
 * A created slot's on-screen position is vanilla's own {@code Slot.x/y} on the
 * in-menu slot, written each frame by the panel that presents it — exactly where
 * a vanilla slot's position lives. So MK reads position off the returned
 * {@link Slot} the same way for both kinds, and the port carries nothing but the
 * slot. (Position used to be parked on the MKC type as {@code renderX/renderY},
 * which is why an earlier version of this port returned it; that made created
 * slots invisible to vanilla's slot pass and to every other mod's slot hooks.)
 */
public interface CreatedSlotResolver {

    /**
     * The live in-menu slot for a created-slot address — the slot AS IT SITS IN
     * {@code menu.slots}: the raw {@code MKCSlot} on survival, or the creative
     * wrapper around it (so a click routes correctly and its {@code x/y} are the
     * ones vanilla draws at). {@code null} if the address names no created slot
     * present on this menu.
     *
     * @param menu    the live menu to resolve against
     * @param address a {@code CREATED_SLOT} address
     */
    @Nullable Slot resolve(AbstractContainerMenu menu, Address address);
}
