package com.trevorschoeny.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The neutral plug MenuKit exposes so panel-hosted registered slots can resolve
 * hover/click through a <b>library-owned</b> screen dispatch — without MenuKit ever
 * referencing a registered-slot type (§0042).
 *
 * <p>Draw and reveal are no longer this hook's concern: a registered slot is a
 * {@code SlotElement} on the panel pipeline, which renders it inline and tracks its
 * reveal/inertness as panel properties. This hook is the residual <em>input limb</em> —
 * it answers, for a screen point, which in-menu slot a panel-hosted slot covers, so
 * vanilla's {@code getHoveredSlot} routes hover/click to the registered slot rather
 * than the vanilla slot beneath it (and eats clicks that fall in a panel's empty space).
 *
 * <h3>The split</h3>
 *
 * MenuKit owns the <em>dispatch</em>: a set of mixins on {@code AbstractContainerScreen}
 * (hover / click / scroll / release) that fire on <b>every</b> container screen —
 * survival inventory, creative (via {@code super.render}), and every chest/furnace/anvil.
 * MenuKit-Containers owns the registered-slot <em>input resolution</em>: fed by the live
 * {@code SlotElementRegistry}, it answers which {@code MKCSlot} (if any) a panel-hosted
 * slot covers at a screen point. That resolution plugs in here. Drawing the slot is the
 * panel pipeline's job (a {@code SlotElement} renders inline), not this hook's.
 *
 * <h3>Registration &amp; absence</h3>
 *
 * MenuKit-Containers registers the single implementation once at client init via
 * {@link SlotScreenDispatcher#setHook}. When MenuKit-Containers is <b>not</b>
 * loaded (an MK-only consumer), the hook stays null and every dispatch call is a
 * cheap no-op — there are no slots without MenuKit-Containers, so there is
 * nothing to dispatch.
 *
 * <h3>Why a library-owned dispatch at all (the §0019 evolution)</h3>
 *
 * {@code MKCSlots} historically shipped <em>no</em> dispatch — the consumer
 * hand-wrote a per-screen render/input mixin and called the static helpers. That
 * left slots silently invisible on any screen the consumer forgot (creative is a
 * sibling class of the survival inventory, not a subclass, so targeting "the
 * inventory screen" missed it). Inventory-screen parity reframes that as a broken
 * library promise: a consumer registers a slot once and the library guarantees
 * it manifests + behaves the same on every inventory-bearing screen it could show
 * on. Panels already work this way (their {@code ScreenPanelRegistry} /
 * {@code SlotGroupPanelRegistry} are library-owned, consumers register adapters);
 * this gives slots the same.
 */
public interface SlotScreenHook {

    /**
     * Fired at {@code getHoveredSlot} HEAD. Returns whether a revealed panel-hosted
     * slot claims the point and which in-menu slot wins it (see {@link SlotHoverResult}).
     */
    SlotHoverResult resolveHover(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY);

    /**
     * Fired at {@code mouseClicked} HEAD. Eats clicks that land in a revealed panel's
     * empty space (so a carried item can't drop through to the inert vanilla slot
     * behind it). Returns true when the click was consumed (the caller cancels
     * vanilla handling).
     */
    boolean mouseClicked(AbstractContainerScreen<?> screen,
                         double mouseX, double mouseY, int button);

    /**
     * Fired at {@code mouseScrolled} HEAD. Default no-op — panel-hosted slots don't
     * consume scroll; an implementation opts in by overriding this.
     */
    default boolean mouseScrolled(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY,
                                  double scrollX, double scrollY) {
        return false;
    }

    /**
     * Fired at {@code mouseReleased} HEAD. Default no-op.
     */
    default boolean mouseReleased(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY, int button) {
        return false;
    }
}
