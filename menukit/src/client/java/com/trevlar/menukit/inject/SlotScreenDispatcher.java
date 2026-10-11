package com.trevlar.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * The boundary between MenuKit's registered-slot screen <em>dispatch</em> and
 * MenuKit-Containers' registered-slot <em>work</em>, so MenuKit never names a
 * registered-slot type.
 *
 * <p>MenuKit's hooks on {@code AbstractContainerScreen} (frame start, hover, click,
 * scroll, release) call the static {@code fire*} methods here, which forward to the
 * one {@link SlotScreenHook} MenuKit-Containers registers at client init. With no
 * hook set (MenuKit without MenuKit-Containers) there are no registered slots, and
 * every call does nothing.
 *
 * <p>The only state is the hook, which is {@code volatile} so the client-init write
 * is visible to the render thread. There is no {@code init()}: the mixins do the
 * dispatch, and registering is one {@link #setHook} call.
 */
@ApiStatus.Internal
public final class SlotScreenDispatcher {

    private SlotScreenDispatcher() {}

    // Null when MenuKit-Containers is absent. Written once at client init, read on
    // the render thread.
    private static volatile @Nullable SlotScreenHook hook = null;

    /**
     * Sets the registered-slot hook. MenuKit-Containers calls this once at client
     * init; a later call replaces the hook.
     */
    public static void setHook(SlotScreenHook hook) {
        SlotScreenDispatcher.hook = hook;
    }

    /** Whether a registered-slot hook is present (MenuKit-Containers loaded). */
    public static boolean hasHook() {
        return hook != null;
    }

    // Called by the AbstractContainerScreen mixins. Each does nothing when no hook is set.

    /** At {@code extractSlot} HEAD: whether this is a created slot (the layer boundary). False without MKC. */
    public static boolean fireIsCreated(net.minecraft.world.inventory.Slot slot) {
        SlotScreenHook h = hook;
        return h != null && h.isCreated(slot);
    }

    /** At the end of {@code ContainerScreenLayers}: parks created slots no panel showed this frame. */
    public static void fireEndFrame(AbstractContainerScreen<?> screen) {
        SlotScreenHook h = hook;
        if (h != null) h.endFrame(screen);
    }

    /**
     * At {@code getHoveredSlot} HEAD: resolves slot hover. Returns {@link SlotHoverResult#PASS},
     * so vanilla resolves it, when no hook is set.
     */
    public static SlotHoverResult fireResolveHover(AbstractContainerScreen<?> screen,
                                                    double mouseX, double mouseY) {
        SlotScreenHook h = hook;
        return h != null ? h.resolveHover(screen, mouseX, mouseY) : SlotHoverResult.PASS;
    }

    /** At {@code mouseClicked} HEAD: consumer buttons and blocking clicks in panel gaps. */
    public static boolean fireMouseClicked(AbstractContainerScreen<?> screen,
                                           double mouseX, double mouseY, int button) {
        SlotScreenHook h = hook;
        return h != null && h.mouseClicked(screen, mouseX, mouseY, button);
    }

    /** At {@code mouseScrolled} HEAD: consumer scrolling over a slot region. */
    public static boolean fireMouseScrolled(AbstractContainerScreen<?> screen, double mouseX,
                                            double mouseY, double scrollX, double scrollY) {
        SlotScreenHook h = hook;
        return h != null && h.mouseScrolled(screen, mouseX, mouseY, scrollX, scrollY);
    }

    /** At {@code mouseReleased} HEAD: finishes a drag over a slot region. */
    public static boolean fireMouseReleased(AbstractContainerScreen<?> screen,
                                            double mouseX, double mouseY, int button) {
        SlotScreenHook h = hook;
        return h != null && h.mouseReleased(screen, mouseX, mouseY, button);
    }
}
