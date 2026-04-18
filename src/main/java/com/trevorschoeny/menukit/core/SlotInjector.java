package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.mixin.AbstractContainerMenuAccessor;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Grafts {@link MenuKitSlot} instances onto a vanilla handler at construction
 * time. Consumers call this from their handler-construction mixins
 * ({@code <init>} RETURN).
 *
 * <p>Calls {@code addSlot()} for each slot, which correctly updates all three
 * parallel lists ({@code slots}, {@code lastSlots}, {@code remoteSlots}) on
 * {@link AbstractContainerMenu}. Client and server must both graft the same
 * slots in the same order — since both sides run the same mixin, symmetry
 * holds naturally.
 *
 * <p>Returns a {@link GraftedRegion} handle carrying the slot index range
 * and Panel reference, which the consumer's {@code quickMoveStack} mixin
 * uses for shift-click routing.
 *
 * <p>Design: {@code menukit/Design Docs/Phase 12/M4_VANILLA_SLOT_INJECTION.md}.
 *
 * @see GraftedRegion
 * @see MenuKitSlot
 */
public final class SlotInjector {

    private SlotInjector() {}

    /**
     * Grafts a single SlotGroup's slots onto a vanilla handler.
     *
     * @param handler the vanilla handler (call from its {@code <init>} RETURN mixin)
     * @param panel   the Panel these slots belong to (drives visibility / inertness)
     * @param group   the SlotGroup defining storage, policy, and shift-click participation
     * @param startX  slot X coordinate relative to the container's origin (leftPos)
     * @param startY  slot Y coordinate relative to the container's origin (topPos)
     * @return GraftedRegion handle for quickMoveStack routing
     */
    public static GraftedRegion graft(AbstractContainerMenu handler,
                                      Panel panel, SlotGroup group,
                                      int startX, int startY) {
        return graft(handler, panel, List.of(group), startX, startY);
    }

    /**
     * Grafts multiple SlotGroups' slots onto a vanilla handler. Each group's
     * slots are laid out in a grid; groups stack vertically below each other.
     *
     * @param handler the vanilla handler
     * @param panel   the Panel these slots belong to
     * @param groups  the SlotGroups to graft, in order
     * @param startX  X coordinate of the first slot (relative to container origin)
     * @param startY  Y coordinate of the first slot
     * @return GraftedRegion spanning all grafted slots
     */
    public static GraftedRegion graft(AbstractContainerMenu handler,
                                      Panel panel, List<SlotGroup> groups,
                                      int startX, int startY) {
        AbstractContainerMenuAccessor acc = (AbstractContainerMenuAccessor) handler;
        List<SlotGroup> groupsCopy = new ArrayList<>(groups);
        int regionStart = handler.slots.size();
        int currentY = startY;

        for (SlotGroup group : groupsCopy) {
            StorageContainerAdapter adapter = new StorageContainerAdapter(group.getStorage());
            int groupStart = handler.slots.size();
            int cols = group.getColumns();

            for (int local = 0; local < group.getStorage().size(); local++) {
                int col = local % cols;
                int row = local / cols;
                int x = startX + col * SlotRendering.DEFAULT_SIZE;
                int y = currentY + row * SlotRendering.DEFAULT_SIZE;

                MenuKitSlot slot = new MenuKitSlot(
                        adapter, local, x, y,
                        group, panel, group.getId(), local
                );
                acc.menuKit$addSlot(slot);
            }

            group.setFlatIndexRange(groupStart, handler.slots.size());

            int rows = (group.getStorage().size() + cols - 1) / cols;
            currentY += rows * SlotRendering.DEFAULT_SIZE;
        }

        return new GraftedRegion(regionStart, handler.slots.size(), panel, groupsCopy);
    }
}
