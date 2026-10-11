package com.trevlar.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * The plug that lets panel-hosted registered slots resolve hover and clicks through
 * MenuKit's own screen dispatch, without MenuKit referencing a registered-slot type.
 *
 * <p>Drawing is not part of it: a registered slot is a real {@code Slot} that
 * vanilla draws in its own slot pass once its {@code SlotElement} has written the
 * panel position into {@code Slot.x/y} (layer 2 of {@code ContainerScreenLayers}).
 * This hook handles input, plus two per-frame helpers ({@link #isCreated},
 * {@link #endFrame}). For a screen point it answers which in-menu slot a
 * panel-hosted slot covers, so vanilla's {@code getHoveredSlot} routes hover and
 * clicks to the registered slot rather than a vanilla slot beneath it, and it eats
 * clicks in a panel's empty space.
 *
 * <h3>The split</h3>
 *
 * MenuKit owns the <em>dispatch</em>: mixins on {@code AbstractContainerScreen}
 * (hover, click, scroll, release) that fire on <b>every</b> container screen, from
 * the survival inventory and creative (via {@code super.render}) to every chest,
 * furnace and anvil. MenuKit-Containers owns the <em>resolution</em>: from the live
 * {@code SlotElementRegistry} it works out which {@code CreatedSlot}, if any, a
 * panel-hosted slot covers at a screen point.
 *
 * <h3>Registration and absence</h3>
 *
 * MenuKit-Containers registers its implementation once at client init through
 * {@link SlotScreenDispatcher#setHook}. Without MenuKit-Containers the hook stays
 * null and every dispatch call is a cheap no-op, since there are no registered
 * slots to dispatch to.
 *
 * <h3>Why the library dispatches</h3>
 *
 * {@code CreatedSlots} used to ship no dispatch: each consumer wrote its own
 * per-screen render and input mixin and called static helpers. Slots then went
 * silently missing on any screen the consumer forgot (creative is a sibling class
 * of the survival inventory, not a subclass, so targeting "the inventory screen"
 * missed it). Now a consumer registers a slot once and the library makes it appear
 * and behave the same on every inventory screen it could show on, as
 * {@code ScreenPanelRegistry} already does for panels.
 */
public interface SlotScreenHook {

    /**
     * Whether {@code slot} is a created (registered) slot. Lets MenuKit find the
     * layer boundary in vanilla's slot pass, the first created slot, without
     * naming an MKC type. False by default: MenuKit alone has none.
     */
    default boolean isCreated(Slot slot) { return false; }

    /**
     * Fired once per frame at the end of {@code ContainerScreenLayers}, after every
     * panel has rendered. The implementation parks every created slot no panel
     * presented this frame (writes an off-screen {@code Slot.x/y}), so a slot whose
     * panel is hidden, out of region, or hidden by the window is not drawn or
     * hit-tested by vanilla at a stale position next frame. Does nothing by default.
     */
    default void endFrame(AbstractContainerScreen<?> screen) {}

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
     * Fired at {@code mouseScrolled} HEAD. By default panel-hosted slots don't
     * consume scrolling; an implementation opts in by overriding this.
     */
    default boolean mouseScrolled(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY,
                                  double scrollX, double scrollY) {
        return false;
    }

    /** Fired at {@code mouseReleased} HEAD. Does nothing by default. */
    default boolean mouseReleased(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY, int button) {
        return false;
    }
}
