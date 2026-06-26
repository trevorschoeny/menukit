package com.trevorschoeny.menukit.inject;

import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * Neutral result of resolving a screen point against the revealed grafted slots
 * on a screen — the value a {@link GraftScreenHook#resolveHover} call returns to
 * MenuKit's {@code getHoveredSlot} mixin.
 *
 * <p>Defined MenuKit-side (not MenuKit-Containers-side) so the library mixin that
 * consumes it compiles without MenuKit-Containers (§0042). It carries only the
 * vanilla {@link Slot} type, never a grafted-slot type.
 *
 * <p>Three outcomes mirror {@code MenuKitGraftInput.Resolution}:
 * <ul>
 *   <li>{@link #PASS} — no revealed graft claims the point; vanilla slot
 *       resolution proceeds untouched.</li>
 *   <li>{@link #of(Slot)} — a revealed graft is under the point; the caller
 *       overrides vanilla's resolution with this slot. <b>This is the slot that
 *       lives in the screen's own {@code menu.slots}</b> — the raw
 *       {@code MenuKitSlot} on a survival inventory, or the creative
 *       {@code SlotWrapper} that wraps it on the creative screen (creative's
 *       click path hard-casts the hovered slot to {@code SlotWrapper}, so the
 *       wrapper, not the bare graft, must be surfaced).</li>
 *   <li>{@link #BLOCK} — the point is inside a revealed graft panel but between
 *       slots; the covered vanilla slot is inert (§0037 bounding-box opacity).
 *       The caller returns {@code null} hover so a gap click can't fall through.</li>
 * </ul>
 */
public record GraftHoverResult(boolean handled, @Nullable Slot slot) {

    /** No graft claims the point — vanilla resolution proceeds. */
    public static final GraftHoverResult PASS = new GraftHoverResult(false, null);

    /** Inside a revealed graft panel but on a gap — block (inert covered slot). */
    public static final GraftHoverResult BLOCK = new GraftHoverResult(true, null);

    /** A revealed graft slot wins the point. Pass the slot that is in {@code menu.slots}. */
    public static GraftHoverResult of(Slot slot) {
        return new GraftHoverResult(true, slot);
    }
}
