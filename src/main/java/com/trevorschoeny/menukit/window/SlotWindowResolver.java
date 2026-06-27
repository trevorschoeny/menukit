package com.trevorschoeny.menukit.window;

import com.trevorschoeny.menukit.inject.SlotScreenRect;
import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Resolves an {@link Address} back to its live backing — the resolution
 * mechanics of THE ONE WINDOW (still no behavior; pure location). Given the
 * live screen, it answers "what slot is this address, right now?" and "where is
 * it on screen?".
 *
 * <h2>The held-handle no-op (Lead condition #3a) is structural here</h2>
 *
 * Every resolution first checks that the address's root owner matches what
 * {@link WindowMint} derives from the LIVE menu. A handle used on the wrong (or
 * no) screen fails this check and resolves to {@link Optional#empty()} — one
 * guard, identical for every kind, no per-call-site screen test. (Writes drop at
 * the engine layer for the same reason; that lands with Phase 3.)
 *
 * <h2>Tiering</h2>
 * Vanilla-slot resolution is pure MK (a menu-space index lookup) and works with
 * MK alone. Created-slot resolution delegates to the {@link CreatedSlotResolver}
 * port (MKC) — {@code null} when MKC is absent, so created slots simply don't
 * resolve client-only. Panel/element resolution arrives with the handle (Phase
 * 6, via the panel registry).
 *
 * <p>This class is built but not yet CALLED — the minting/calling sites are the
 * handle (Phase 6) and the engine (Phase 3). Verified here by compile + review;
 * exercised live once the handle wires it.
 */
public final class SlotWindowResolver {

    private SlotWindowResolver() {}

    // The §0042 firewall seat for created-slot resolution: MK holds the optional
    // port; MKC installs its impl at client init. Null => MK-alone (no created
    // slots to resolve).
    private static volatile @Nullable CreatedSlotResolver createdResolver;

    /** MKC installs its created-slot resolver here at client init. */
    public static void setCreatedSlotResolver(CreatedSlotResolver resolver) {
        createdResolver = resolver;
    }

    /**
     * The live in-menu {@link Slot} an address names on this screen, or empty if
     * the address doesn't belong to this screen (held-handle no-op) or names
     * nothing present. Returns the slot AS IT SITS IN {@code menu.slots} (the
     * creative wrapper on creative), so a click routes correctly.
     */
    public static Optional<Slot> resolve(AbstractContainerScreen<?> screen, Address address) {
        AbstractContainerMenu menu = screen.getMenu();
        return switch (address.kind()) {
            // Vanilla slots are menu-intrinsic: the owner gate (family+scope == the
            // live menu) IS the held-handle no-op for them.
            case VANILLA_SLOT -> ownerMatches(menu, address.owner())
                    ? vanillaSlot(menu, address) : Optional.empty();
            // Created slots are menu-INDEPENDENT (identity = panel + decl); the
            // port's scan is itself the presence check, so no family gate applies.
            case CREATED_SLOT -> created(menu, address).map(CreatedSlotResolver.CreatedResolution::slot);
            // Panel + element resolution lands with the handle (Phase 6, panel registry).
            case PANEL, PANEL_ELEMENT -> Optional.empty();
        };
    }

    /**
     * The absolute on-screen item box for an address, or empty (same no-op rules).
     * Vanilla slots report their in-menu {@code x/y} (the creative wrapper carries
     * creative coords); created slots report their {@code renderX/renderY} via the
     * port; both are offset by the live screen frame.
     */
    public static Optional<SlotScreenRect> resolvePosition(AbstractContainerScreen<?> screen, Address address) {
        AbstractContainerMenu menu = screen.getMenu();
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int left = acc.mk$getLeftPos();
        int top = acc.mk$getTopPos();
        return switch (address.kind()) {
            // Vanilla: menu-family gate (held-handle no-op). Created: scan is the check.
            case VANILLA_SLOT -> ownerMatches(menu, address.owner())
                    ? vanillaSlot(menu, address).map(s -> new SlotScreenRect(left + s.x, top + s.y, 16, 16))
                    : Optional.empty();
            case CREATED_SLOT -> created(menu, address)
                    .map(r -> new SlotScreenRect(left + r.frameX(), top + r.frameY(), 16, 16));
            case PANEL, PANEL_ELEMENT -> Optional.empty();
        };
    }

    // ── internals ──────────────────────────────────────────────────────

    private static Optional<Slot> vanillaSlot(AbstractContainerMenu menu, Address address) {
        if (!(address.token() instanceof Token.IndexToken it)) return Optional.empty();
        int i = it.index();
        if (i < 0 || i >= menu.slots.size()) return Optional.empty();
        return Optional.of(menu.slots.get(i));
    }

    private static Optional<CreatedSlotResolver.CreatedResolution> created(AbstractContainerMenu menu, Address address) {
        CreatedSlotResolver r = createdResolver;
        if (r == null) return Optional.empty();
        return Optional.ofNullable(r.resolve(menu, address));
    }

    /**
     * Whether an address's ROOT owner matches the live menu's derived family +
     * scope. The within-panel part of a nested address (the panel a created slot
     * or element belongs to) is matched downstream by the created-slot resolver /
     * panel registry; this is purely the root gate that powers the no-op.
     */
    private static boolean ownerMatches(AbstractContainerMenu menu, OwnerRef owner) {
        OwnerRef.RootOwner root = rootOf(owner);
        return root.family().equals(WindowMint.familyOf(menu))
                && root.scope().equals(WindowMint.scopeOf(menu));
    }

    private static OwnerRef.RootOwner rootOf(OwnerRef owner) {
        OwnerRef cur = owner;
        while (cur instanceof OwnerRef.NestedOwner nested) {
            cur = nested.parent();
        }
        return (OwnerRef.RootOwner) cur;
    }
}
