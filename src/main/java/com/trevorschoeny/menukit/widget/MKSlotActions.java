package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.mixin.AbstractContainerScreenInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * Programmatic click simulation through vanilla's own {@code slotClicked}.
 *
 * <p>Every method delegates to {@link AbstractContainerScreen#slotClicked},
 * which handles client prediction, packet construction, server validation,
 * and container state updates. This means all simulated clicks go through
 * the same path as real player clicks — vanilla handles the hard parts.
 *
 * <p>Simulated clicks set a thread-local flag that the bus mixins check.
 * When the flag is active, {@code MKSlotClickBusMixin} and
 * {@code MKSlotRightClickMixin} skip event dispatch, preventing infinite
 * loops when a bus handler calls back into MKSlotActions.
 *
 * <p>Part of the <b>MenuKit</b> gesture-to-action framework.
 *
 * <h3>Example usage (inside a drag mode handler):</h3>
 * <pre>{@code
 * .onSlotEntered(event -> MKSlotActions.shiftClick(event.slot()))
 * }</pre>
 */
public final class MKSlotActions {

    // Thread-local flag: true while a simulated click is in progress.
    // Checked by MKSlotClickBusMixin and MKSlotRightClickMixin to skip
    // bus event dispatch for programmatic clicks.
    private static final ThreadLocal<Boolean> SIMULATED = ThreadLocal.withInitial(() -> false);

    private MKSlotActions() {}

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Simulates a left-click (pickup) on the given slot.
     * Vanilla behavior: picks up or places the cursor stack.
     */
    public static void leftClick(Slot slot) {
        invoke(currentScreen(), slot, slot.index, 0, ClickType.PICKUP);
    }

    /** @see #leftClick(Slot) */
    public static void leftClick(AbstractContainerScreen<?> screen, Slot slot) {
        invoke(screen, slot, slot.index, 0, ClickType.PICKUP);
    }

    /**
     * Simulates a right-click (pickup half / place one) on the given slot.
     * Vanilla behavior: picks up half the stack, or places one item.
     */
    public static void rightClick(Slot slot) {
        invoke(currentScreen(), slot, slot.index, 1, ClickType.PICKUP);
    }

    /** @see #rightClick(Slot) */
    public static void rightClick(AbstractContainerScreen<?> screen, Slot slot) {
        invoke(screen, slot, slot.index, 1, ClickType.PICKUP);
    }

    /**
     * Simulates a shift-click (quick-move) on the given slot.
     * Vanilla behavior: transfers the stack to the "other" inventory.
     */
    public static void shiftClick(Slot slot) {
        invoke(currentScreen(), slot, slot.index, 0, ClickType.QUICK_MOVE);
    }

    /** @see #shiftClick(Slot) */
    public static void shiftClick(AbstractContainerScreen<?> screen, Slot slot) {
        invoke(screen, slot, slot.index, 0, ClickType.QUICK_MOVE);
    }

    /**
     * Simulates a hotbar swap (number key) on the given slot.
     * Vanilla behavior: swaps the slot contents with the hotbar slot.
     *
     * @param hotbarIndex 0-8 for slots 1-9, or 40 for offhand
     */
    public static void swap(Slot slot, int hotbarIndex) {
        invoke(currentScreen(), slot, slot.index, hotbarIndex, ClickType.SWAP);
    }

    /** @see #swap(Slot, int) */
    public static void swap(AbstractContainerScreen<?> screen, Slot slot, int hotbarIndex) {
        invoke(screen, slot, slot.index, hotbarIndex, ClickType.SWAP);
    }

    // ── Simulated-click flag ─────────────────────────────────────────────

    /**
     * Whether a simulated click is currently in progress on this thread.
     * Checked by bus mixins to skip event dispatch for programmatic clicks.
     *
     * <p>Consumer modules can also check this in {@code .where()} predicates
     * if they need to distinguish simulated from real clicks.
     */
    public static boolean isSimulated() {
        return SIMULATED.get();
    }

    // ── Screen resolution ────────────────────────────────────────────────

    /**
     * Returns the current {@link AbstractContainerScreen}, or throws if
     * no container screen is open.
     *
     * <p>Convenience for call sites (drag handlers, scroll handlers) that
     * don't have a screen reference readily available.
     */
    public static AbstractContainerScreen<?> currentScreen() {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof AbstractContainerScreen<?> acs) {
            return acs;
        }
        throw new IllegalStateException(
                "[MenuKit] MKSlotActions called but no container screen is open");
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private static void invoke(AbstractContainerScreen<?> screen,
                               @Nullable Slot slot, int slotId, int button,
                               ClickType clickType) {
        SIMULATED.set(true);
        try {
            ((AbstractContainerScreenInvoker) screen)
                    .menuKit$invokeSlotClicked(slot, slotId, button, clickType);
        } finally {
            SIMULATED.set(false);
        }
    }
}
