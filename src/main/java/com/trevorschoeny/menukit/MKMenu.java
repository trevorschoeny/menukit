package com.trevorschoeny.menukit;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Generic container menu for standalone MenuKit screens.
 *
 * <p>Used for ALL standalone MenuKit screens — the panel name determines
 * which slots and layout to use. Auto-calculates screen height to fit
 * panel content + player inventory.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public class MKMenu extends AbstractContainerMenu {

    // Standard player inventory dimensions
    private static final int INV_ROWS = 3;       // 3 rows of 9
    private static final int HOTBAR_COLS = 9;
    private static final int INV_HEIGHT = 76;     // 3 rows (54) + gap (4) + hotbar (18)
    private static final int INV_GAP = 14;        // gap between panel content and player inv
    private static final int TITLE_HEIGHT = 14;   // space for title text at top

    private final String panelName;
    private final int panelSlotCount;
    private final int calculatedScreenHeight;
    private final int playerInvY;

    /**
     * Creates the menu for the given panel name. Works on both server
     * (called from openMenu) and client (called from ExtendedScreenHandlerType).
     */
    public MKMenu(int syncId, Inventory playerInventory, String panelName) {
        super(MenuKit.getMKMenuType(), syncId);
        this.panelName = panelName;

        MKPanelDef def = MenuKit.getPanelDef(panelName);

        // Create MKSlots from the panel definition
        var mkSlots = MenuKit.createSlotsForStandaloneScreen(panelName, playerInventory.player);
        for (MKSlot slot : mkSlots) {
            this.addSlot(slot);
        }
        this.panelSlotCount = mkSlots.size();

        // Calculate panel content area height
        int contentHeight = 0;
        if (def != null) {
            int[] size = def.computeSize();
            contentHeight = size[1]; // panel auto-sized height (includes padding)
        }

        // Determine whether to include the player inventory
        boolean showPlayerInv = def != null && def.includePlayerInventory();

        // Calculate screen dimensions
        int panelAreaHeight = TITLE_HEIGHT + contentHeight;

        if (showPlayerInv) {
            if (def.screenHeight() > 0) {
                // User-specified height — place player inventory at the bottom
                this.playerInvY = def.screenHeight() - INV_HEIGHT - 7;
                this.calculatedScreenHeight = def.screenHeight();
            } else {
                // Auto-calculate: panel content + gap + player inventory
                this.playerInvY = panelAreaHeight + INV_GAP;
                this.calculatedScreenHeight = playerInvY + INV_HEIGHT + 7;
            }
            // Add standard player inventory (27 main + 9 hotbar)
            this.addStandardInventorySlots(playerInventory, 8, playerInvY);
        } else {
            // No player inventory — screen height is just the panel content + padding
            this.playerInvY = -1;
            if (def != null && def.screenHeight() > 0) {
                this.calculatedScreenHeight = def.screenHeight();
            } else {
                this.calculatedScreenHeight = panelAreaHeight + 7;
            }
        }
    }

    /** Returns the panel name this menu was created for. */
    public String getPanelName() { return panelName; }

    /** Returns the calculated or specified screen height. */
    public int getScreenHeight() {
        MKPanelDef def = MenuKit.getPanelDef(panelName);
        if (def != null && def.screenHeight() > 0) return def.screenHeight();
        return calculatedScreenHeight;
    }

    /** Returns the Y position where the player inventory starts. */
    public int getPlayerInvY() { return playerInvY; }

    @Override
    public boolean stillValid(Player player) { return true; }

    /**
     * Standard quick-move (shift-click): moves items between
     * the panel slots and the player inventory.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index < panelSlotCount) {
                // Panel slot → player inventory
                if (!this.moveItemStackTo(slotStack, panelSlotCount, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player inventory → panel slots
                if (!this.moveItemStackTo(slotStack, 0, panelSlotCount, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }
}
