package com.trevlar.menukit.inject;

import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * The result of resolving a screen point against the revealed registered slots on a
 * screen: what {@link SlotScreenHook#resolveHover} returns to MenuKit's
 * {@code getHoveredSlot} mixin.
 *
 * <p>It lives in MenuKit, not MenuKit-Containers, so the mixin that reads it compiles
 * without MenuKit-Containers. It carries only vanilla's {@link Slot} type.
 *
 * <p>The three outcomes match {@code CreatedSlotInput.Resolution}:
 * <ul>
 *   <li>{@link #PASS}: no revealed slot claims the point, and vanilla's slot
 *       resolution runs untouched.</li>
 *   <li>{@link #of(Slot)}: a revealed slot is under the point, and the caller
 *       replaces vanilla's answer with it. This must be the slot in the screen's
 *       own {@code menu.slots}: the raw {@code CreatedSlot} on a survival
 *       inventory, or the {@code SlotWrapper} around it on the creative screen,
 *       because creative's click path casts the hovered slot to
 *       {@code SlotWrapper}.</li>
 *   <li>{@link #BLOCK}: the point is inside a revealed slot panel but between
 *       slots. The covered vanilla slot is inert (the panel's bounding box is
 *       opaque), so the caller returns {@code null} and a click in the gap can't
 *       fall through.</li>
 * </ul>
 */
public record SlotHoverResult(boolean handled, @Nullable Slot slot) {

    /** No slot claims the point, so vanilla's resolution runs. */
    public static final SlotHoverResult PASS = new SlotHoverResult(false, null);

    /** Inside a revealed slot panel but on a gap: blocked, the covered slot is inert. */
    public static final SlotHoverResult BLOCK = new SlotHoverResult(true, null);

    /** A revealed slot wins the point. Pass the slot that is in {@code menu.slots}. */
    public static SlotHoverResult of(Slot slot) {
        return new SlotHoverResult(true, slot);
    }
}
