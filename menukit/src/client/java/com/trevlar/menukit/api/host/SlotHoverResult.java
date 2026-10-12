package com.trevlar.menukit.api.host;

import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * What {@link SlotScreenHook#resolveHover} answers for a screen point. It carries only
 * vanilla's {@link Slot}.
 *
 * <ul>
 *   <li>{@link #PASS}: no shown slot takes the point; vanilla's slot resolution runs
 *       untouched.</li>
 *   <li>{@link #of(Slot)}: a shown slot is under the point and replaces vanilla's
 *       answer. It must be the slot in the screen's own {@code menu.slots} (on the
 *       creative screen, the wrapper around it, since creative's click path casts
 *       the hovered slot to its wrapper).</li>
 *   <li>{@link #BLOCK}: the point is inside a shown slot panel but between slots;
 *       the covered vanilla slot is inert, so a click in the gap cannot fall through.</li>
 * </ul>
 *
 * @param handled whether the hook took the point
 * @param slot    the winning slot, or {@code null} for {@link #PASS} and {@link #BLOCK}
 */
public record SlotHoverResult(boolean handled, @Nullable Slot slot) {

    /** No slot takes the point, so vanilla's resolution runs. */
    public static final SlotHoverResult PASS = new SlotHoverResult(false, null);

    /** Inside a shown slot panel but on a gap: blocked, the covered slot is inert. */
    public static final SlotHoverResult BLOCK = new SlotHoverResult(true, null);

    /** A shown slot wins the point. Pass the slot that is in {@code menu.slots}. */
    public static SlotHoverResult of(Slot slot) {
        return new SlotHoverResult(true, slot);
    }
}
