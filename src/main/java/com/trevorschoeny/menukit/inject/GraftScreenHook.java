package com.trevorschoeny.menukit.inject;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The neutral plug MenuKit exposes so grafted-slot draw / input / reveal can ride
 * a <b>library-owned</b> screen dispatch — the same screen-completeness panels
 * already get — without MenuKit ever referencing a grafted-slot type (§0042).
 *
 * <h3>The split</h3>
 *
 * MenuKit owns the <em>dispatch</em>: a set of mixins on {@code AbstractContainerScreen}
 * (render / hover / click / scroll / release) that fire on <b>every</b> container
 * screen — survival inventory, creative (via {@code super.render}), and every
 * chest/furnace/anvil — plus the {@link ScreenMatcher} that expresses the
 * default-on / opt-out-per-screen targeting. MenuKit-Containers owns the
 * grafted-slot <em>work</em>: it walks {@code menu.slots} for its
 * {@code MenuKitSlot}s, draws them, resolves their hover/click, and fires the
 * consumer's per-screen decoration + reveal callbacks. That work plugs in here.
 *
 * <h3>Registration &amp; absence</h3>
 *
 * MenuKit-Containers registers the single implementation once at client init via
 * {@link GraftScreenDispatcher#setHook}. When MenuKit-Containers is <b>not</b>
 * loaded (an MK-only consumer), the hook stays null and every dispatch call is a
 * cheap no-op — there are no grafts without MenuKit-Containers, so there is
 * nothing to dispatch.
 *
 * <h3>Why a library-owned dispatch at all (the §0019 evolution)</h3>
 *
 * {@code MenuKitGraft} historically shipped <em>no</em> dispatch — the consumer
 * hand-wrote a per-screen render/input mixin and called the static helpers. That
 * left grafts silently invisible on any screen the consumer forgot (creative is a
 * sibling class of the survival inventory, not a subclass, so targeting "the
 * inventory screen" missed it). Inventory-screen parity reframes that as a broken
 * library promise: a consumer registers a graft once and the library guarantees
 * it manifests + behaves the same on every inventory-bearing screen it could show
 * on. Panels already work this way (their {@code ScreenPanelRegistry} /
 * {@code SlotGroupPanelRegistry} are library-owned, consumers register adapters);
 * this gives grafts the same.
 */
public interface GraftScreenHook {

    /**
     * Fired once per screen-frame at {@code renderContents} HEAD — before vanilla
     * computes the hovered slot or draws the slots. The implementation runs each
     * matching presence's prepare callback (update hover-reveal state, reposition
     * grafted slots for <em>this</em> screen) so render + hit-test that frame see
     * the current reveal + layout.
     */
    void prepare(AbstractContainerScreen<?> screen, int mouseX, int mouseY);

    /**
     * Fired once per screen-frame at {@code renderContents} TAIL — after the
     * vanilla slots draw, before the carried item. Draws, in z-order: each
     * matching presence's background decoration, then its grafted slot frames +
     * items + hover, then its foreground decoration (icons, buttons).
     */
    void render(AbstractContainerScreen<?> screen, GuiGraphics graphics,
                int mouseX, int mouseY, float partialTick);

    /**
     * Fired at {@code getHoveredSlot} HEAD. Returns whether a revealed graft
     * claims the point and which in-menu slot wins it (see {@link GraftHoverResult}).
     */
    GraftHoverResult resolveHover(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY);

    /**
     * Fired at {@code mouseClicked} HEAD. Lets a presence's interactive decoration
     * (resize buttons, etc.) consume the click, and eats clicks that land in a
     * revealed panel's empty space (so a carried item can't drop through). Returns
     * true when the click was consumed (the caller cancels vanilla handling).
     */
    boolean mouseClicked(AbstractContainerScreen<?> screen,
                         double mouseX, double mouseY, int button);

    /**
     * Fired at {@code mouseScrolled} HEAD. Lets a presence consume scroll over its
     * region (e.g. cycle pages). Returns true when consumed. Default no-op — most
     * grafts ignore scroll; a presence opts in by overriding its scroll callback.
     */
    default boolean mouseScrolled(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY,
                                  double scrollX, double scrollY) {
        return false;
    }

    /**
     * Fired at {@code mouseReleased} HEAD. Lets a presence finish a drag started
     * over its decoration. Returns true when consumed. Default no-op.
     */
    default boolean mouseReleased(AbstractContainerScreen<?> screen,
                                  double mouseX, double mouseY, int button) {
        return false;
    }
}
