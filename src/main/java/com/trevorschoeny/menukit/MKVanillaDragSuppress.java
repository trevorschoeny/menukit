package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Suppresses vanilla's quick-craft drag-distribution system so that custom
 * {@link MKDragMode} handlers can operate on RMB (or LMB) without fighting
 * vanilla's built-in hold-and-drag behavior.
 *
 * <p>Vanilla quick-craft: LMB hold+drag splits items evenly across visited
 * slots; RMB hold+drag places one item per slot. When a custom drag mode
 * activates, this class cancels vanilla's in-progress quick-craft and tells
 * vanilla to ignore the upcoming mouse release.
 *
 * <p>Used internally by {@link MKDragMode} — consumer modules should not
 * need to call this directly.
 *
 * <p>Part of the <b>MenuKit</b> gesture-to-action framework.
 */
public final class MKVanillaDragSuppress {

    private static boolean suppressed = false;

    private MKVanillaDragSuppress() {}

    /**
     * Suppresses vanilla's quick-craft state for the given screen.
     *
     * <p>Sets {@code isQuickCrafting = false} to cancel any in-progress
     * vanilla drag distribution, clears the visited slot set, and sets
     * {@code skipNextRelease = true} so vanilla ignores the mouse release
     * that ends the custom drag.
     *
     * @param screen the container screen whose quick-craft state to suppress
     */
    public static void suppress(AbstractContainerScreen<?> screen) {
        var acc = (AbstractContainerScreenAccessor) screen;

        // Cancel any in-progress vanilla quick-craft
        if (acc.menuKit$isQuickCrafting()) {
            acc.menuKit$setIsQuickCrafting(false);
            acc.menuKit$getQuickCraftSlots().clear();
        }

        // Tell vanilla to ignore the next mouse release — prevents it from
        // processing a phantom click when our custom drag ends
        acc.menuKit$setSkipNextRelease(true);

        suppressed = true;
    }

    /**
     * Restores vanilla quick-craft state after a custom drag ends.
     * Called automatically by the drag mixin on drag end.
     *
     * @param screen the container screen to restore
     */
    public static void restore(AbstractContainerScreen<?> screen) {
        // skipNextRelease is self-clearing in vanilla (reset on next release),
        // but we clear our tracking flag
        suppressed = false;
    }

    /** Whether vanilla quick-craft is currently suppressed by a custom drag. */
    public static boolean isSuppressed() {
        return suppressed;
    }
}
