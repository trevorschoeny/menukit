package com.trevlar.menukit.inject;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * The §0042 firewall between MenuKit's registered-slot screen <em>dispatch</em> and
 * MenuKit-Containers' registered-slot <em>work</em>.
 *
 * <p>MenuKit's mixins on {@code AbstractContainerScreen} (render / hover / click /
 * scroll / release) call the static {@code fire*} methods here. This class holds
 * the one {@link SlotScreenHook} MenuKit-Containers registers at client init and
 * forwards to it — or no-ops when no hook is set (MK-only consumer: no slots
 * exist, so nothing to dispatch). MenuKit thus drives the per-screen dispatch
 * while never naming a registered-slot type.
 *
 * <p>Stateless beyond the single hook reference; the hook is {@code volatile} so
 * the client-init write is visible to the render thread. There is no
 * {@code init()} — the dispatch is the mixins themselves, which exist as soon as
 * the class loads; registration is a single {@link #setHook} call.
 */
@ApiStatus.Internal
public final class SlotScreenDispatcher {

    private SlotScreenDispatcher() {}

    /**
     * The single registered-slot hook, or null when MenuKit-Containers is absent.
     * Written once from {@code MKCClient.onInitializeClient}, read
     * from the render/input threads — hence volatile.
     */
    private static volatile @Nullable SlotScreenHook hook = null;

    /**
     * Registers the registered-slot hook. Called once by MenuKit-Containers at client
     * init. Idempotent in practice (one MKC instance), last write wins.
     */
    public static void setHook(SlotScreenHook hook) {
        SlotScreenDispatcher.hook = hook;
    }

    /** Whether a registered-slot hook is present (MenuKit-Containers loaded). */
    public static boolean hasHook() {
        return hook != null;
    }

    // ── Fire methods — called by the AbstractContainerScreen mixins ─────────
    // Each is a no-op when no hook is set. Kept tiny so the mixins stay thin.

    /**
     * {@code getHoveredSlot} HEAD — resolve slot hover. Returns {@link SlotHoverResult#PASS}
     * (vanilla resolution proceeds) when no hook is set.
     */
    public static SlotHoverResult fireResolveHover(AbstractContainerScreen<?> screen,
                                                    double mouseX, double mouseY) {
        SlotScreenHook h = hook;
        return h != null ? h.resolveHover(screen, mouseX, mouseY) : SlotHoverResult.PASS;
    }

    /** {@code mouseClicked} HEAD — consumer buttons + gap-block. */
    public static boolean fireMouseClicked(AbstractContainerScreen<?> screen,
                                           double mouseX, double mouseY, int button) {
        SlotScreenHook h = hook;
        return h != null && h.mouseClicked(screen, mouseX, mouseY, button);
    }

    /** {@code mouseScrolled} HEAD — consumer scroll over a slot region. */
    public static boolean fireMouseScrolled(AbstractContainerScreen<?> screen, double mouseX,
                                            double mouseY, double scrollX, double scrollY) {
        SlotScreenHook h = hook;
        return h != null && h.mouseScrolled(screen, mouseX, mouseY, scrollX, scrollY);
    }

    /** {@code mouseReleased} HEAD — finish a drag over a slot region. */
    public static boolean fireMouseReleased(AbstractContainerScreen<?> screen,
                                            double mouseX, double mouseY, int button) {
        SlotScreenHook h = hook;
        return h != null && h.mouseReleased(screen, mouseX, mouseY, button);
    }
}
