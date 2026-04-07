package com.trevorschoeny.menukit.event;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.region.MKRegion;
import com.trevorschoeny.menukit.widget.MKSlotState;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Carries all context for a MenuKit event — what happened, where, and to what.
 *
 * <p>Every slot interaction in a container screen is captured as an {@code MKSlotEvent}
 * and dispatched through the {@link MKEventBus}. Consumers register handlers via
 * {@link MenuKit#on(Type...)} and receive these events with full context: the slot,
 * its {@link MKSlotState}, which {@link MKRegion} and panel it belongs to, what's
 * on the cursor, and the player.
 *
 * <p><b>Immutability:</b> All fields are final except {@code cursorStack}, which is
 * mutable for {@link Type#ITEM_TRANSFER_IN} and {@link Type#ITEM_TRANSFER_OUT} events.
 * Handlers can call {@link #transformStack(ItemStack)} to replace the item being
 * transferred (e.g., convert ore to ingot on insertion into a smelter slot).
 *
 * <p><b>Nullability:</b> For slot-level events, {@code slot}, {@code state}, and
 * {@code slotStack} are always present. For lifecycle events ({@link Type#MENU_OPEN},
 * {@link Type#MENU_CLOSE}), {@code slot}, {@code state}, {@code region}, and
 * {@code panelName} may be null.
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public final class MKSlotEvent implements MKEvent {

    // ── Fields ────────────────────────────────────────────────────────────────
    //
    // Private fields + getters (not a Java record) because transfer events
    // need mutable cursorStack and the transform mechanism.

    private final MKEvent.Type type;

    /** Raw mouse button: 0=left, 1=right, 2=middle. -1 for non-click events. */
    private final int button;

    /** The vanilla slot involved. Null for MENU_OPEN/MENU_CLOSE. */
    private final @Nullable Slot slot;

    /** MenuKit state for this slot. Null for MENU_OPEN/MENU_CLOSE. */
    private final @Nullable MKSlotState state;

    /** Which screen context this event occurred in. Null for unknown menu types. */
    private final @Nullable MKContext context;

    /** The region this slot belongs to. Null for lifecycle events or unregioned slots. */
    private final @Nullable MKRegion region;

    /** The panel this slot belongs to. Null for lifecycle events or unpaneled slots. */
    private final @Nullable String panelName;

    /** Unified position in the container. -1 if not applicable. */
    private final int containerSlot;

    /** Current contents of the slot at event time. */
    private final ItemStack slotStack;

    /** What's on the cursor. Mutable for transfer events. */
    private ItemStack cursorStack;

    /** The player who triggered the event. */
    private final Player player;

    /** GLFW key code for KEY_PRESS events. -1 otherwise. */
    private final int keyCode;

    /** Mouse wheel scroll delta for SCROLL events. Positive = up, negative = down. 0.0 otherwise. */
    private final double scrollDelta;

    /**
     * GLFW modifier key bitfield at the time of the event.
     * Populated for click events ({@code buildSlotEvent}) and KEY_PRESS events
     * ({@code buildKeyEvent}). 0 for hover, scroll, transfer, and lifecycle events.
     * <ul>
     *   <li>0x1 = Shift held</li>
     *   <li>0x2 = Ctrl held</li>
     *   <li>0x4 = Alt held</li>
     *   <li>0x8 = Super/Cmd held</li>
     * </ul>
     */
    private final int modifiers;

    // ── Transform Mechanism ───────────────────────────────────────────────────
    //
    // For ITEM_TRANSFER_IN / ITEM_TRANSFER_OUT, handlers can replace the item
    // being transferred. The transformed stack is stored separately so the
    // dispatch code can detect whether a transform was applied.

    private @Nullable ItemStack transformedStack;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MKSlotEvent(MKEvent.Type type, int button,
                       @Nullable Slot slot, @Nullable MKSlotState state,
                       @Nullable MKContext context, @Nullable MKRegion region,
                       @Nullable String panelName, int containerSlot,
                       ItemStack slotStack, ItemStack cursorStack,
                       Player player, int keyCode, double scrollDelta, int modifiers) {
        this.type = type;
        this.button = button;
        this.slot = slot;
        this.state = state;
        this.context = context;
        this.region = region;
        this.panelName = panelName;
        this.containerSlot = containerSlot;
        this.slotStack = slotStack;
        this.cursorStack = cursorStack;
        this.player = player;
        this.keyCode = keyCode;
        this.scrollDelta = scrollDelta;
        this.modifiers = modifiers;
    }

    /**
     * Backwards-compatible constructor — defaults modifiers to 0.
     * Used by scroll, key, hover, transfer, and lifecycle event builders.
     */
    public MKSlotEvent(MKEvent.Type type, int button,
                       @Nullable Slot slot, @Nullable MKSlotState state,
                       @Nullable MKContext context, @Nullable MKRegion region,
                       @Nullable String panelName, int containerSlot,
                       ItemStack slotStack, ItemStack cursorStack,
                       Player player, int keyCode, double scrollDelta) {
        this(type, button, slot, state, context, region, panelName,
             containerSlot, slotStack, cursorStack, player, keyCode, scrollDelta, 0);
    }

    /**
     * Backwards-compatible constructor — defaults scrollDelta to 0.0 and modifiers to 0.
     * Used by all legacy call sites (click, hover, key, transfer, lifecycle).
     */
    public MKSlotEvent(MKEvent.Type type, int button,
                       @Nullable Slot slot, @Nullable MKSlotState state,
                       @Nullable MKContext context, @Nullable MKRegion region,
                       @Nullable String panelName, int containerSlot,
                       ItemStack slotStack, ItemStack cursorStack,
                       Player player, int keyCode) {
        this(type, button, slot, state, context, region, panelName,
             containerSlot, slotStack, cursorStack, player, keyCode, 0.0, 0);
    }

    // ── Static Factories ───────────────────────────────────────────────────────
    //
    // Convenience constructors for events that don't involve a specific slot.
    // Lifecycle events (MENU_OPEN, MENU_CLOSE, REGION_POPULATED) have no slot,
    // state, or cursor — just a type, context, and player.

    /**
     * Creates a lifecycle event with no slot context. Used for
     * {@link Type#MENU_OPEN}, {@link Type#MENU_CLOSE}, and
     * {@link Type#REGION_POPULATED}.
     *
     * @param type    the lifecycle event type
     * @param context the menu context (may be null for unknown menu types)
     * @param player  the player involved
     * @return a new MKSlotEvent with null slot/state/region/panel
     */
    public static MKSlotEvent lifecycle(MKEvent.Type type, @Nullable MKContext context,
                                         Player player) {
        return new MKSlotEvent(
                type, -1,
                null, null,
                context, null,
                null, -1,
                ItemStack.EMPTY, ItemStack.EMPTY,
                player, -1
        );
    }

    /**
     * Creates a lifecycle event for a specific region. Used for
     * {@link Type#REGION_POPULATED} where we want to identify which region
     * was resolved.
     *
     * @param type    the event type (typically REGION_POPULATED)
     * @param context the menu context
     * @param region  the region that was populated
     * @param player  the player involved
     * @return a new MKSlotEvent with the region set but null slot/state
     */
    public static MKSlotEvent lifecycleWithRegion(MKEvent.Type type, @Nullable MKContext context,
                                                    MKRegion region, Player player) {
        return new MKSlotEvent(
                type, -1,
                null, null,
                context, region,
                null, -1,
                ItemStack.EMPTY, ItemStack.EMPTY,
                player, -1
        );
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** The type of event that occurred. */
    @Override
    public MKEvent.Type getType() { return type; }

    /** Raw mouse button (0=left, 1=right, 2=middle). -1 for non-click events. */
    public int getButton() { return button; }

    /** The vanilla slot involved, or null for lifecycle events (MENU_OPEN/CLOSE). */
    public @Nullable Slot getSlot() { return slot; }

    /** MenuKit state for this slot, or null for lifecycle events. */
    public @Nullable MKSlotState getState() { return state; }

    /** The screen context where this event occurred, or null for unknown menu types. */
    @Override
    public @Nullable MKContext getContext() { return context; }

    /** The region this slot belongs to, or null. */
    public @Nullable MKRegion getRegion() { return region; }

    /** The panel this slot belongs to, or null. */
    public @Nullable String getPanelName() { return panelName; }

    /** Unified position in the container (-1 if N/A). */
    public int getContainerSlot() { return containerSlot; }

    /** Contents of the slot at event time. */
    public ItemStack getSlotStack() { return slotStack; }

    /** Contents of the cursor at event time. */
    public ItemStack getCursorStack() { return cursorStack; }

    /** The player who triggered the event. */
    @Override
    public Player getPlayer() { return player; }

    /** GLFW key code for KEY_PRESS events (-1 otherwise). */
    public int getKeyCode() { return keyCode; }

    /**
     * Mouse wheel scroll delta for SCROLL events. Positive = scroll up,
     * negative = scroll down. Returns 0.0 for all non-SCROLL events.
     */
    public double getScrollDelta() { return scrollDelta; }

    /**
     * Raw GLFW modifier bitfield (0x1=Shift, 0x2=Ctrl, 0x4=Alt, 0x8=Super).
     * Populated for click and KEY_PRESS events; 0 for hover, scroll,
     * transfer, and lifecycle events.
     */
    public int getModifiers() { return modifiers; }

    /** Whether the Shift key was held when this event fired. */
    public boolean isShiftPressed() { return (modifiers & 0x1) != 0; }

    /** Whether the Ctrl key was held when this event fired. */
    public boolean isCtrlPressed() { return (modifiers & 0x2) != 0; }

    /** Whether the Alt key was held when this event fired. */
    public boolean isAltPressed() { return (modifiers & 0x4) != 0; }

    // ── Transform API (for transfer events) ───────────────────────────────────
    //
    // Only meaningful for ITEM_TRANSFER_IN and ITEM_TRANSFER_OUT.
    // Allows handlers to replace the item being transferred.

    /**
     * Replaces the item being transferred. Only valid for
     * {@link Type#ITEM_TRANSFER_IN} and {@link Type#ITEM_TRANSFER_OUT}.
     *
     * @param replacement the item to use instead of the original
     * @throws IllegalStateException if the event type is not a transfer event
     */
    public void transformStack(ItemStack replacement) {
        if (type != MKEvent.Type.ITEM_TRANSFER_IN && type != MKEvent.Type.ITEM_TRANSFER_OUT) {
            throw new IllegalStateException(
                    "transformStack() is only valid for transfer events, not " + type);
        }
        this.transformedStack = replacement;
    }

    /**
     * Returns the transformed stack set by a handler, or null if no
     * transform was applied.
     */
    public @Nullable ItemStack getTransformedStack() {
        return transformedStack;
    }

    /** Whether any handler has called {@link #transformStack(ItemStack)}. */
    public boolean hasTransform() {
        return transformedStack != null;
    }

    // ── Convenience Methods ───────────────────────────────────────────────────

    /**
     * Whether this event's slot belongs to the player's own inventory container.
     * Saves consumers from having to inspect the slot's container type themselves.
     *
     * <p>Returns false for lifecycle events where slot is null.
     */
    public boolean isPlayerInventorySlot() {
        // Slot's container is the player's Inventory when it's the player inv section
        return slot != null && slot.container instanceof Inventory;
    }

    /**
     * Returns the unified container position for player inventory slots,
     * or -1 if this slot is not a player inventory slot.
     *
     * <p>Useful for handlers that need to identify specific player inventory
     * positions (e.g., "hotbar slot 3", "armor slot 2") without inspecting
     * the vanilla slot directly.
     */
    public int getUnifiedPlayerPos() {
        return isPlayerInventorySlot() ? containerSlot : -1;
    }

    // ── Server-Safe Slot Index ───────────────────────────────────────────────

    /**
     * Returns the slot index as the server's {@code containerMenu} expects it.
     *
     * <p>In most contexts, the client and server share the same menu, so
     * {@code slot.index} (the slot's position in the menu's {@code slots} list)
     * is valid on both sides.
     *
     * <p>In <b>creative item tabs</b>, however, the client uses
     * {@code ItemPickerMenu} (item picker grid + hotbar) while the server uses
     * {@code InventoryMenu}. Their {@code slots} lists have completely different
     * layouts, so a raw {@code slot.index} from the client would point to the
     * wrong slot on the server — or be out of bounds entirely.
     *
     * <p>This method handles the translation: for creative tabs, it finds the
     * equivalent slot in the player's {@code inventoryMenu} (which matches the
     * server's layout) by matching on the backing container and container-local
     * slot index. For all other contexts, it returns {@code slot.index} directly.
     *
     * <p><b>Use this whenever sending a slot index in a C2S packet.</b> Using
     * {@code event.getSlot().index} directly is only safe when you know the
     * client and server share the same menu.
     *
     * @return the server-compatible menu slot index, or -1 if the slot has no
     *         equivalent in the server's menu (e.g., creative item picker slots)
     */
    public int getMenuSlotIndex() {
        if (slot == null) return -1;

        // Non-creative-tabs contexts: same menu on both sides
        if (context != MKContext.CREATIVE_TABS) return slot.index;

        // Creative item tabs: ItemPickerMenu ≠ InventoryMenu.
        // Find the slot in inventoryMenu that backs the same container position.
        // This handles ALL slot types (hotbar, main, armor, offhand, MKSlots)
        // without hardcoding the InventoryMenu layout.
        if (player != null) {
            for (Slot menuSlot : player.inventoryMenu.slots) {
                if (menuSlot.container == slot.container
                        && menuSlot.getContainerSlot() == slot.getContainerSlot()) {
                    return menuSlot.index;
                }
            }
        }

        // Slot has no equivalent in InventoryMenu (e.g., creative item picker)
        return -1;
    }
}
