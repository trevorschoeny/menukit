package com.trevorschoeny.menukit;

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
public class MKSlotEvent {

    // ── Event Types ───────────────────────────────────────────────────────────
    //
    // Organized by category: click, interaction, state change, lifecycle,
    // keyboard, and transfer. Each type maps to a specific vanilla or
    // MenuKit-internal trigger.

    public enum Type {

        // ── Click Events ──────────────────────────────────────────────────
        // These map directly to vanilla's ClickType + button combinations.
        // Consumers never need to decode ClickType themselves.

        /** Left-click on a slot. Vanilla: ClickType.PICKUP, button=0. */
        LEFT_CLICK,

        /** Right-click on a slot. Vanilla: ClickType.PICKUP, button=1. */
        RIGHT_CLICK,

        /** Shift-click (left or right). Vanilla: ClickType.QUICK_MOVE. */
        SHIFT_CLICK,

        /** Number key or offhand swap. Vanilla: ClickType.SWAP. */
        SWAP,

        /**
         * Middle-click on a slot. Fires in ALL game modes, not just creative.
         *
         * <p>In creative mode, vanilla calls this ClickType.CLONE (pick block).
         * In survival mode, vanilla ignores button=2 entirely — MenuKit catches
         * it at the mouseClicked level and still fires this event so consumers
         * can use middle-click as a universal interaction (bookmarking, tagging,
         * info lookup, etc.) regardless of game mode.
         */
        MIDDLE_CLICK,

        /** Click outside the window to throw. Vanilla: ClickType.THROW. */
        THROW,

        /** Double-click to collect matching items. Vanilla: ClickType.PICKUP_ALL. */
        DOUBLE_CLICK,

        // ── Interaction Events ────────────────────────────────────────────
        // Hover and drag tracking — fired by MenuKit's own hover detection.

        /** Cursor moves onto a slot (was not hovering, now is). */
        HOVER_ENTER,

        /** Cursor leaves a slot (was hovering, now isn't). */
        HOVER_EXIT,

        /** Cursor drags across a slot while holding items. */
        DRAG_OVER,

        // ── State Change Events ───────────────────────────────────────────
        // Fired when slot contents change. Useful for reactive UI updates.

        /** Item in a slot changes (any modification). */
        SLOT_CHANGED,

        /** Slot transitions from having an item to being empty. */
        SLOT_EMPTIED,

        /** Slot transitions from empty to having an item. */
        SLOT_FILLED,

        // ── Lifecycle Events ──────────────────────────────────────────────
        // Screen open/close and region resolution. slot/state may be null.

        /** Container screen opens. slot and state are null. */
        MENU_OPEN,

        /** Container screen closes. slot and state are null. */
        MENU_CLOSE,

        /** A region's slots are fully resolved after menu construction. */
        REGION_POPULATED,

        // ── Keyboard Events ───────────────────────────────────────────────

        /** Key pressed while cursor hovers a slot. Check keyCode for which key. */
        KEY_PRESS,

        // ── Scroll Events ────────────────────────────────────────────────
        // Fired when the mouse wheel scrolls while the cursor hovers a slot.
        // scrollDelta carries the direction: positive = scroll up, negative = scroll down.

        /** Mouse wheel scrolled while cursor hovers a slot. Use getScrollDelta() for direction. */
        SCROLL,

        // ── Transfer Events ───────────────────────────────────────────────
        // Fired BEFORE the transfer happens. Handlers can call transformStack()
        // to replace the item being moved.

        /** Item is about to be placed INTO a slot. Mutable via transformStack(). */
        ITEM_TRANSFER_IN,

        /** Item is about to be taken FROM a slot. Mutable via transformStack(). */
        ITEM_TRANSFER_OUT,
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    //
    // Private fields + getters (not a Java record) because transfer events
    // need mutable cursorStack and the transform mechanism.

    private final Type type;

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

    // ── Transform Mechanism ───────────────────────────────────────────────────
    //
    // For ITEM_TRANSFER_IN / ITEM_TRANSFER_OUT, handlers can replace the item
    // being transferred. The transformed stack is stored separately so the
    // dispatch code can detect whether a transform was applied.

    private @Nullable ItemStack transformedStack;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MKSlotEvent(Type type, int button,
                       @Nullable Slot slot, @Nullable MKSlotState state,
                       @Nullable MKContext context, @Nullable MKRegion region,
                       @Nullable String panelName, int containerSlot,
                       ItemStack slotStack, ItemStack cursorStack,
                       Player player, int keyCode, double scrollDelta) {
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
    }

    /**
     * Backwards-compatible constructor — defaults scrollDelta to 0.0.
     * Used by all existing event builders (click, hover, key, transfer, lifecycle).
     */
    public MKSlotEvent(Type type, int button,
                       @Nullable Slot slot, @Nullable MKSlotState state,
                       @Nullable MKContext context, @Nullable MKRegion region,
                       @Nullable String panelName, int containerSlot,
                       ItemStack slotStack, ItemStack cursorStack,
                       Player player, int keyCode) {
        this(type, button, slot, state, context, region, panelName,
             containerSlot, slotStack, cursorStack, player, keyCode, 0.0);
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
    public static MKSlotEvent lifecycle(Type type, @Nullable MKContext context,
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
    public static MKSlotEvent lifecycleWithRegion(Type type, @Nullable MKContext context,
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
    public Type getType() { return type; }

    /** Raw mouse button (0=left, 1=right, 2=middle). -1 for non-click events. */
    public int getButton() { return button; }

    /** The vanilla slot involved, or null for lifecycle events (MENU_OPEN/CLOSE). */
    public @Nullable Slot getSlot() { return slot; }

    /** MenuKit state for this slot, or null for lifecycle events. */
    public @Nullable MKSlotState getState() { return state; }

    /** The screen context where this event occurred, or null for unknown menu types. */
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
    public Player getPlayer() { return player; }

    /** GLFW key code for KEY_PRESS events (-1 otherwise). */
    public int getKeyCode() { return keyCode; }

    /**
     * Mouse wheel scroll delta for SCROLL events. Positive = scroll up,
     * negative = scroll down. Returns 0.0 for all non-SCROLL events.
     */
    public double getScrollDelta() { return scrollDelta; }

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
        if (type != Type.ITEM_TRANSFER_IN && type != Type.ITEM_TRANSFER_OUT) {
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
}
