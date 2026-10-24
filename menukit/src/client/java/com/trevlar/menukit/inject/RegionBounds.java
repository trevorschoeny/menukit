package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The container region's screen bounds (7.4.0; see {@link ContainerRegions}, "The screen's bounds
 * are the two panels' union"): the union written as the screen's own and vanilla's placements
 * moved back by as much, and the player's slots placed with the inventory panel every frame.
 * Render thread only.
 */
@ApiStatus.Internal
public final class RegionBounds {

    private RegionBounds() {}

    /**
     * Writes {@code bounds} as the screen's own, and moves vanilla's own placements back by
     * as much as the origin moved ({@link RegionLayout#counterShift}): the title stays where
     * vanilla put it on screen, and the player's inventory slots and "Inventory" label go with
     * the inventory panel. Only on a change of layout. The labels move here, by the change;
     * the slots are placed from the new shift ({@link #placeSlots}), here and every frame.
     */
    static void applyBounds(AbstractContainerScreen<?> screen, Reference bounds, int below) {
        Reference vanilla = ContainerRegions.state.vanilla;
        if (vanilla == null) return;
        int[] shift = RegionLayout.counterShift(vanilla, bounds, below);
        int dx = shift[0] - ContainerRegions.state.shiftX, dy = shift[1] - ContainerRegions.state.shiftY, invDy = shift[2] - ContainerRegions.state.inventoryShiftY;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        acc.mk$setLeftPos(bounds.leftPos());
        acc.mk$setTopPos(bounds.topPos());
        acc.mk$setImageWidth(bounds.imageWidth());
        acc.mk$setImageHeight(bounds.imageHeight());
        acc.mk$setInventoryLabelX(acc.mk$getInventoryLabelX() + dx);
        acc.mk$setInventoryLabelY(acc.mk$getInventoryLabelY() + invDy);
        acc.mk$setTitleLabelX(acc.mk$getTitleLabelX() + dx);
        acc.mk$setTitleLabelY(acc.mk$getTitleLabelY() + dy);
        ContainerRegions.state.shiftX = shift[0];
        ContainerRegions.state.shiftY = shift[1];
        ContainerRegions.state.inventoryShiftY = shift[2];
        ContainerRegions.state.applied = bounds;
        // So every mod's init listener after this one reads the slots where they now are.
        placeSlots(screen.getMenu().slots, ContainerRegions.state.shiftX, ContainerRegions.state.inventoryShiftY);
    }

    /**
     * Back to vanilla's own bounds and placements, if they are not already. Every slot the
     * region moved goes back by exactly what it got, whether or not it is still in the menu.
     */
    static void restoreBounds(AbstractContainerScreen<?> screen) {
        unplaceSlots();
        if (ContainerRegions.state.applied == null || ContainerRegions.state.vanilla == null) return;
        applyBounds(screen, ContainerRegions.state.vanilla, 0);
        ContainerRegions.state.applied = null;
    }

    // The player's slots, placed every frame

    /**
     * How far the region has moved each of the player's slots, {@code {dx, dy}}. Weak and
     * keyed by the slot object (a slot keeps its identity; a closed menu's slots go with it).
     * Slots come and go while a screen is open (another mod puts its own into the menu), so
     * each frame moves every slot by what it lacks, and a restore takes back exactly what was
     * given: never a slot that was not moved, never twice.
     */
    private static final Map<Slot, int[]> MOVED = new WeakHashMap<>();

    /** Moves a slot by {@code (dx, dy)}. Swapped by MenuKit's checks, which have no mixins. */
    @FunctionalInterface
    public interface SlotMover {
        void move(Slot slot, int dx, int dy);
    }

    private static final SlotMover WRITE = (slot, dx, dy) -> {
        SlotAccessor pos = (SlotAccessor) slot;
        pos.mk$setX(slot.x + dx);
        pos.mk$setY(slot.y + dy);
    };
    private static SlotMover mover = WRITE;

    /** For MenuKit's checks: swaps how a slot is moved, and back with {@code null}. */
    @VisibleForTesting
    public static void moverForChecks(@Nullable SlotMover m) {
        mover = m == null ? WRITE : m;
    }

    /**
     * Puts every slot of {@code slots} that stays with the inventory ({@link #stays}) at
     * {@code (shiftX, shiftY)} from where vanilla placed it, by moving it the difference from
     * what it already got.
     */
    @VisibleForTesting
    public static void placeSlots(List<Slot> slots, int shiftX, int shiftY) {
        for (Slot slot : slots) {
            if (!stays(slot)) continue;
            int[] got = MOVED.get(slot);
            int gx = got == null ? 0 : got[0], gy = got == null ? 0 : got[1];
            if (gx == shiftX && gy == shiftY) continue;
            mover.move(slot, shiftX - gx, shiftY - gy);
            if (shiftX == 0 && shiftY == 0) MOVED.remove(slot);
            else MOVED.put(slot, new int[]{shiftX, shiftY});
        }
    }

    /** Moves every slot the region moved back by what it got, and forgets them. */
    @VisibleForTesting
    public static void unplaceSlots() {
        for (Map.Entry<Slot, int[]> e : MOVED.entrySet()) mover.move(e.getKey(), -e.getValue()[0], -e.getValue()[1]);
        MOVED.clear();
    }

    /**
     * Whether a slot is one of vanilla's that MenuKit places with the inventory panel: the
     * player's inventory, which never scrolls. A container's own slots are the region's
     * (whoever put the region on the screen presents them in a section, and their library
     * places them); a created slot is its library's.
     */
    static boolean stays(Slot slot) {
        return slot.container instanceof Inventory && !SlotScreenDispatcher.fireIsCreated(slot);
    }

}
