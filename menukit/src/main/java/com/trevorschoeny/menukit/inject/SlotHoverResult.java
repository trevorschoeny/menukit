package com.trevorschoeny.menukit.inject;

import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * Neutral result of resolving a screen point against the revealed registered slots
 * on a screen — the value a {@link SlotScreenHook#resolveHover} call returns to
 * MenuKit's {@code getHoveredSlot} mixin.
 *
 * <p>Defined MenuKit-side (not MenuKit-Containers-side) so the library mixin that
 * consumes it compiles without MenuKit-Containers (§0042). It carries only the
 * vanilla {@link Slot} type, never a registered-slot type.
 *
 * <p>Three outcomes mirror {@code MKCSlotInput.Resolution}:
 * <ul>
 *   <li>{@link #PASS} — no revealed slot claims the point; vanilla slot
 *       resolution proceeds untouched.</li>
 *   <li>{@link #of(Slot)} — a revealed slot is under the point; the caller
 *       overrides vanilla's resolution with this slot. <b>This is the slot that
 *       lives in the screen's own {@code menu.slots}</b> — the raw
 *       {@code MKCSlot} on a survival inventory, or the creative
 *       {@code SlotWrapper} that wraps it on the creative screen (creative's
 *       click path hard-casts the hovered slot to {@code SlotWrapper}, so the
 *       wrapper, not the bare slot, must be surfaced).</li>
 *   <li>{@link #BLOCK} — the point is inside a revealed slot panel but between
 *       slots; the covered vanilla slot is inert (§0037 bounding-box opacity).
 *       The caller returns {@code null} hover so a gap click can't fall through.</li>
 * </ul>
 */
public record SlotHoverResult(boolean handled, @Nullable Slot slot) {

    /** No slot claims the point — vanilla resolution proceeds. */
    public static final SlotHoverResult PASS = new SlotHoverResult(false, null);

    /** Inside a revealed slot panel but on a gap — block (inert covered slot). */
    public static final SlotHoverResult BLOCK = new SlotHoverResult(true, null);

    /** A revealed slot slot wins the point. Pass the slot that is in {@code menu.slots}. */
    public static SlotHoverResult of(Slot slot) {
        return new SlotHoverResult(true, slot);
    }
}
