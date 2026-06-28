package com.trevorschoeny.menukit.window;

import net.minecraft.world.Container;

import java.util.Optional;

/**
 * Port (MK defines, MKC implements via §0050): the menu-INDEPENDENT identity of a
 * vanilla slot — its container's stable identity plus the container-relative
 * index. This is what lets a vanilla slot's {@link Address} be the same whether
 * the slot is touched through its menu, by a hopper, or after a reopen (the same
 * reason created slots are menu-independent).
 *
 * <h2>Why a port</h2>
 *
 * The stable container identity comes from §0050's container resolution
 * ({@code PersistentContainerKey}: {@code pos+dim} for block entities — side-
 * agnostic — or a UUID for player/entity containers), which lives in MKC; §0042
 * forbids MK referencing it. So MK defines this port and MKC fills it. With MKC
 * absent the {@link NoServerTier} null-object returns {@link Optional#empty()},
 * and the address minter falls back to a menu-based identity (fine — vanilla-slot
 * gating only exists when MKC is present, so client-only decoration has nothing
 * to stay consistent with).
 */
public interface VanillaSlotIdentity {

    /**
     * The stable, menu-independent identity of the vanilla slot at
     * {@code containerSlotIndex} in {@code container}, or empty when the
     * container has no stable identity (MK alone, or an unresolvable container
     * such as an ender chest / horse-family bag).
     */
    Optional<Resolved> identify(Container container, int containerSlotIndex);

    /**
     * @param scopeId    a stable string id of the owning container (namespaces the
     *                   address scope; composite/double-chest already resolved to
     *                   the owning half by §0050).
     * @param localIndex the slot's index within that resolved container.
     */
    record Resolved(String scopeId, int localIndex) {}
}
