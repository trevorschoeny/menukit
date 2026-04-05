package com.trevorschoeny.menukit;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Context for an active drag operation. Provides modifier key state,
 * menu access, and tracks which slots have been visited.
 *
 * <p>Part of the <b>MenuKit</b> drag mode API.
 */
public final class MKDragContext {
    private final int modifiers;  // GLFW bitfield
    private final Player player;
    private final AbstractContainerMenu menu;
    private final List<Slot> visitedSlots = new ArrayList<>();

    public MKDragContext(int modifiers, Player player, AbstractContainerMenu menu) {
        this.modifiers = modifiers;
        this.player = player;
        this.menu = menu;
    }

    /** True if Shift is held (GLFW modifier bit 0x1). */
    public boolean isShiftHeld() { return (modifiers & 0x1) != 0; }

    /** True if Ctrl is held (GLFW modifier bit 0x2). */
    public boolean isCtrlHeld() { return (modifiers & 0x2) != 0; }

    /** True if Alt is held (GLFW modifier bit 0x4). */
    public boolean isAltHeld() { return (modifiers & 0x4) != 0; }

    /** The raw GLFW modifier bitfield. */
    public int modifiers() { return modifiers; }

    /** The player performing the drag. */
    public Player player() { return player; }

    /** The container menu being interacted with. */
    public AbstractContainerMenu menu() { return menu; }

    /** The item currently on the cursor. */
    public ItemStack cursorStack() { return menu.getCarried(); }

    /** Unmodifiable list of slots visited so far in this drag. */
    public List<Slot> visitedSlots() { return Collections.unmodifiableList(visitedSlots); }

    /** Records a slot as visited (called internally by the drag mixin). */
    public void addVisitedSlot(Slot slot) { visitedSlots.add(slot); }
}
