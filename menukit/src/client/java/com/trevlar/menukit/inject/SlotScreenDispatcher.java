package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.host.SlotHoverResult;
import com.trevlar.menukit.api.host.SlotScreenHook;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Forwards MenuKit's container-screen dispatch (frame start, hover, click, scroll,
 * release) to the one {@link SlotScreenHook} a slot library installs, so MenuKit never
 * names a slot type. With no hook there are no created slots, and every call does
 * nothing.
 *
 * <p>The only state is the hook, {@code volatile} so the client-init write is visible
 * to the render thread.
 */
@ApiStatus.Internal
public final class SlotScreenDispatcher {

    private SlotScreenDispatcher() {}

    // Null with no slot library. Written once at client init, read on the render thread.
    private static volatile @Nullable SlotScreenHook hook = null;

    /** {@link SlotScreenHook#install}: once, before the freeze. */
    public static synchronized void install(SlotScreenHook hook) {
        Declarations.requireOpen("SlotScreenHook.install");
        if (SlotScreenDispatcher.hook != null) {
            throw new IllegalStateException("A SlotScreenHook is already installed ("
                    + SlotScreenDispatcher.hook.getClass().getName() + "); only one slot library may install one.");
        }
        SlotScreenDispatcher.hook = java.util.Objects.requireNonNull(hook, "hook");
    }

    /** Whether a hook is installed. */
    public static boolean hasHook() {
        return hook != null;
    }

    // Called by the AbstractContainerScreen mixins. Each does nothing when no hook is set.

    /** At {@code extractSlot} HEAD: whether this is a created slot (the layer boundary). False with no hook. */
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
