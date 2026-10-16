package com.trevlar.menukit.api.host;

import com.trevlar.menukit.inject.SlotScreenDispatcher;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * The plug a slot library fills so its panel-hosted slots (created slots, drawn inside
 * MenuKit panels) take hover and clicks through MenuKit's own container-screen
 * dispatch, without MenuKit naming a slot type. MenuKit keeps the mixins on
 * {@code AbstractContainerScreen} (hover, click, scroll, release and the frame's draw
 * order), so two mods never patch the same vanilla methods; the library answers the
 * questions below.
 *
 * <p>Drawing is not part of it: a created slot is a real {@code Slot} that vanilla
 * draws in its own slot pass once the library has written the panel position into
 * {@code Slot.x/y}. MenuKit only needs {@link #isCreated} to know where created slots
 * start in that pass, so they draw above FLOW panels and below OVERLAY ones. The
 * element that places a slot also reports it with {@link SlotClip#shown}, so a slot
 * across a clip's edge (a {@code ScrollContainer}'s viewport) draws and hovers only
 * where it is shown.
 *
 * <p>One library installs one hook, once, from its client initializer
 * ({@link #install}). Without one, MenuKit has no created slots and every dispatch is a
 * no-op.
 */
public interface SlotScreenHook {

    /**
     * Installs the hook. Call once from a client initializer, before MenuKit's
     * declarations freeze.
     *
     * @throws IllegalStateException if a hook is already installed (two slot libraries
     *                               would each answer for the other's slots), or after
     *                               the freeze
     */
    static void install(SlotScreenHook hook) {
        SlotScreenDispatcher.install(hook);
    }

    /**
     * Whether {@code slot} is a created slot. MenuKit finds the layer boundary in
     * vanilla's slot pass, the first created slot, by asking this. False by default.
     */
    default boolean isCreated(Slot slot) { return false; }

    /**
     * Once per frame, after every panel on the screen has rendered. The library parks
     * every created slot no panel presented this frame (writes an off-screen
     * {@code Slot.x/y}), so a slot whose panel is hidden is neither drawn nor
     * hit-tested at a stale position next frame. Does nothing by default.
     */
    default void endFrame(AbstractContainerScreen<?> screen) {}

    /**
     * At {@code getHoveredSlot}, after MenuKit's claim (a panel over a slot makes it
     * inert) let the point through: whether a shown panel-hosted slot takes the point,
     * and which in-menu slot wins it (see {@link SlotHoverResult}).
     */
    SlotHoverResult resolveHover(AbstractContainerScreen<?> screen, double mouseX, double mouseY);

    /**
     * At {@code mouseClicked}: eats a click in a shown slot panel's empty space, so a
     * carried item cannot drop through to the inert vanilla slot behind it.
     *
     * @return whether the click was consumed (vanilla does not see it)
     */
    boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button);

    /** At {@code mouseScrolled}: whether a slot region consumed the scroll. False by default. */
    default boolean mouseScrolled(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
                                  double scrollX, double scrollY) {
        return false;
    }

    /** At {@code mouseReleased}: whether a drag over a slot region consumed it. False by default. */
    default boolean mouseReleased(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        return false;
    }
}
