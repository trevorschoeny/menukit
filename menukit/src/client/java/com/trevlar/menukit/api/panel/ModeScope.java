package com.trevlar.menukit.api.panel;

import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/**
 * Which game mode a declaration is for (7.4.0): {@link #SURVIVAL}, {@link #CREATIVE} or
 * {@link #BOTH}. Every declaration that says where a panel goes picks one; there is no
 * default. A panel can be somewhere else entirely in each mode
 * ({@link Panel.Builder#in(ModeScope, Placement)}): a screen region in survival, beside a
 * slot in creative.
 *
 * <h3>The axis is vanilla's own switch</h3>
 * {@code CREATIVE} is a player with infinite materials ({@code Player.hasInfiniteMaterials()},
 * the switch vanilla itself reads); {@code SURVIVAL} is every other player, adventure and
 * spectator included, which have the same screens. A container a creative player opens is
 * {@code CREATIVE}.
 *
 * <h3>Read where it is used</h3>
 * The mode is read on every call, every frame, never kept from when a screen opened, so
 * {@code /gamemode} with a screen open takes effect on the next frame, with no reopen. With
 * no player (the title screen, a world loading) there is no mode, and only {@code BOTH}
 * declarations apply.
 */
public enum ModeScope {
    /** Survival, adventure and spectator: a player without infinite materials. */
    SURVIVAL,
    /** Creative: a player with infinite materials. */
    CREATIVE,
    /** Either mode, and no player at all. */
    BOTH;

    /**
     * The mode {@code player} is in now: {@link #CREATIVE} while it has infinite materials,
     * {@link #SURVIVAL} otherwise, and {@code null} with no player. Never {@link #BOTH}.
     */
    public static @Nullable ModeScope of(@Nullable Player player) {
        if (player == null) return null;
        return player.hasInfiniteMaterials() ? CREATIVE : SURVIVAL;
    }

    /** Whether this scope covers {@code player} now: {@link #BOTH} always, otherwise its mode is this one. */
    public boolean includes(@Nullable Player player) {
        return this == BOTH || of(player) == this;
    }
}
