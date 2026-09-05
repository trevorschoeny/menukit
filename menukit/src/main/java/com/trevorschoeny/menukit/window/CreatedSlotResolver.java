package com.trevorschoeny.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * Port (MK defines, MKC implements): resolve a {@code CREATED_SLOT}
 * {@link Address} to its live in-menu slot and frame-relative position.
 *
 * <h2>Why a port (the §0042 reason)</h2>
 *
 * A created slot is an {@code MKCSlot} — an MKC type MK must never reference —
 * and its on-screen position lives on that type ({@code renderX/renderY}, §0047),
 * which is parked off the vanilla {@code Slot.x/y}. So MK cannot resolve a
 * created slot or read its position directly. MKC registers an implementation
 * (replacing the per-frame O(n) identity scan in {@code SlotElement.resolve()}
 * with a session-cached binding); when MKC is absent the resolver is {@code null}
 * and created-slot resolution simply yields empty (created slots need MKC) —
 * vanilla, panel, and element resolution stay fully MK-alone.
 */
public interface CreatedSlotResolver {

    /**
     * The live in-menu slot for a created-slot address, with its frame-relative
     * draw position, or {@code null} if the address names no created slot present
     * on this menu.
     *
     * @param menu    the live menu to resolve against
     * @param address a {@code CREATED_SLOT} address
     */
    @Nullable CreatedResolution resolve(AbstractContainerMenu menu, Address address);

    /**
     * @param slot   the slot AS IT SITS IN {@code menu.slots} — the raw
     *               {@code MKCSlot} on survival, or the creative wrapper around it
     *               (so a click routes correctly). Never the bare identity.
     * @param frameX draw X relative to the screen frame (from {@code renderX}).
     * @param frameY draw Y relative to the screen frame (from {@code renderY}).
     */
    record CreatedResolution(Slot slot, int frameX, int frameY) {}
}
