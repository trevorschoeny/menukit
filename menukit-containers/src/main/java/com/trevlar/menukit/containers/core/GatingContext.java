package com.trevlar.menukit.containers.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/**
 * The context a {@link SlotGate} decision runs in — who/what is acting, plus the
 * library-owned non-modded capability query. Absorbs Inventory Max's
 * {@code enforceForActingPlayer()} pattern into the library so every gate gets
 * the same primitive.
 *
 * <h2>Acting player</h2>
 *
 * A deep seam (a hopper transfer, vanilla's {@code moveItemStackTo}) has no player
 * in scope. MenuKit captures the acting player at the click boundary
 * ({@link com.trevlar.menukit.window.ActingPlayer}, cleared in a {@code finally})
 * so a nested decision can see it. It is set on both sides: the client's
 * prediction of a click runs with the local player. Automation leaves it {@code null}.
 */
public final class GatingContext {

    /** The context for the current thread (the captured acting player, if any). */
    public static GatingContext current() {
        return new GatingContext(com.trevlar.menukit.window.ActingPlayer.current());
    }

    private final @Nullable Player actingPlayer;

    private GatingContext(@Nullable Player actingPlayer) {
        this.actingPlayer = actingPlayer;
    }

    /** Who is acting (a {@link ServerPlayer} for a real interaction; null for automation). */
    public @Nullable Player actingPlayer() {
        return actingPlayer;
    }

    /**
     * Whether the acting player can see slot-state (i.e. is modded). A gate whose
     * effect is invisible to a non-modded client (a shared lock) should bypass
     * when this is false — they can't see it, so it shouldn't wall them (§0050).
     * Returns {@code true} for automation / client-side / a capable server player
     * (the safe-enforce direction); only a non-capable {@link ServerPlayer} is
     * {@code false}.
     */
    public boolean actingPlayerCapable() {
        return !(actingPlayer instanceof ServerPlayer sp) || MKSlotState.isSlotStateCapable(sp);
    }
}
