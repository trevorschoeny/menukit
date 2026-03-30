package com.trevorschoeny.menukit;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Factory for building {@link MKSlotEvent}s from raw mixin parameters.
 *
 * <p>Every click mixin needs to resolve the same set of context objects
 * (MKSlotState, MKRegion, MKContext, panelName, containerSlot, etc.) before
 * firing an event through the {@link MKEventBus}. This helper centralizes
 * that resolution logic so each mixin is just a thin dispatch layer.
 *
 * <p>Part of the <b>MenuKit</b> event system (internal).
 */
public final class MKEventHelper {

    // No instances — all static methods
    private MKEventHelper() {}

    // ── Active Player Tracking ────────────────────────────────────────────────
    //
    // Transfer events fire at the Slot level (safeInsert, setByPlayer) where
    // vanilla doesn't always pass a Player parameter. For player inventory
    // slots we can resolve the player from the Inventory container, but for
    // NON-player containers (chests, furnaces, etc.) there's no Player
    // reference on the Container.
    //
    // Solution: MKReadOnlyMixin already injects at AbstractContainerMenu.clicked()
    // HEAD and has the Player parameter. We save it here at the start of clicked()
    // and clear it at the end. Transfer events that fire during clicked() can then
    // use this as a fallback when resolvePlayerFromSlot returns null.
    //
    // This is safe because clicked() is always called on the server tick thread
    // (one player per tick, no concurrency within a single clicked() call).

    /** The player currently executing a clicked() call, or null outside clicked(). */
    private static Player currentClickPlayer = null;

    /**
     * Called at the start of {@code AbstractContainerMenu.clicked()} to track
     * the active player for transfer event resolution.
     *
     * @param player the player performing the click
     */
    public static void setCurrentClickPlayer(Player player) {
        currentClickPlayer = player;
    }

    /**
     * Called at the end of {@code AbstractContainerMenu.clicked()} to clear
     * the active player reference.
     */
    public static void clearCurrentClickPlayer() {
        currentClickPlayer = null;
    }

    // ── ClickType -> MKEvent.Type Mapping ─────────────────────────────
    //
    // Maps vanilla's ClickType + button combination to our unified event type.
    // Returns null for click types we don't (yet) handle as bus events.

    /**
     * Converts a vanilla ClickType + button pair to an {@link MKEvent.Type}.
     *
     * <p>Mapping:
     * <ul>
     *   <li>PICKUP, button 0 -> LEFT_CLICK</li>
     *   <li>PICKUP, button 1 -> RIGHT_CLICK</li>
     *   <li>QUICK_MOVE -> SHIFT_CLICK</li>
     *   <li>SWAP -> SWAP</li>
     *   <li>CLONE -> MIDDLE_CLICK</li>
     *   <li>THROW -> THROW</li>
     *   <li>PICKUP_ALL -> DOUBLE_CLICK</li>
     * </ul>
     *
     * @param clickType vanilla click type
     * @param button    mouse button (0=left, 1=right, 2=middle)
     * @return the matching MKEvent.Type, or null if not mapped
     */
    public static MKSlotEvent.@Nullable Type mapClickType(ClickType clickType, int button) {
        return switch (clickType) {
            case PICKUP -> button == 1 ? MKEvent.Type.RIGHT_CLICK : MKEvent.Type.LEFT_CLICK;
            case QUICK_MOVE -> MKEvent.Type.SHIFT_CLICK;
            case SWAP -> MKEvent.Type.SWAP;
            case CLONE -> MKEvent.Type.MIDDLE_CLICK;
            case THROW -> MKEvent.Type.THROW;
            case PICKUP_ALL -> MKEvent.Type.DOUBLE_CLICK;
            default -> null; // QUICK_CRAFT (drag) not handled as a click event
        };
    }

    // ── Event Builder ─────────────────────────────────────────────────────
    //
    // Resolves all context from the raw mixin parameters and builds an
    // MKSlotEvent ready for dispatch. Handles null slots gracefully
    // (THROW events click outside the window).

    /**
     * Builds a fully-resolved {@link MKSlotEvent} from raw mixin parameters.
     *
     * <p>Resolves:
     * <ul>
     *   <li>{@link MKSlotState} from the slot via {@link MKSlotStateRegistry}</li>
     *   <li>{@link MKRegion} from the menu via {@link MKRegionRegistry}</li>
     *   <li>{@link MKContext} from the screen via {@link MKContext#fromScreen}</li>
     *   <li>Panel name from the slot state</li>
     *   <li>Container slot index from the vanilla slot</li>
     *   <li>Slot stack and cursor stack snapshots</li>
     * </ul>
     *
     * @param type   the event type (already mapped from ClickType)
     * @param slot   the vanilla slot, or null for THROW (click outside window)
     * @param button the mouse button (0=left, 1=right, 2=middle)
     * @param screen the container screen where the click occurred
     * @param player the player who clicked
     * @return a fully-populated MKSlotEvent
     */
    public static MKSlotEvent buildSlotEvent(MKEvent.Type type,
                                              @Nullable Slot slot,
                                              int button,
                                              AbstractContainerScreen<?> screen,
                                              Player player) {

        // ── Sample modifier key state via GLFW ──────────────────────────
        // Click events fire rarely (on actual input), so sampling here is cheap.
        // Bitfield: 0x1=Shift, 0x2=Ctrl, 0x4=Alt
        // mc.getWindow().handle() returns the LWJGL GLFW window handle.
        int modifiers = 0;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            long wnd = mc.getWindow().handle();
            if (org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)  == org.lwjgl.glfw.GLFW.GLFW_PRESS
             || org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                modifiers |= 0x1;
            }
            if (org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)  == org.lwjgl.glfw.GLFW.GLFW_PRESS
             || org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                modifiers |= 0x2;
            }
            if (org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)  == org.lwjgl.glfw.GLFW.GLFW_PRESS
             || org.lwjgl.glfw.GLFW.glfwGetKey(wnd, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                modifiers |= 0x4;
            }
        }

        // ── Resolve screen context ──────────────────────────────────────
        // MKContext identifies which screen type we're in (survival, creative,
        // chest, etc.). May be null for unrecognized screens — we still build
        // the event so bus listeners with no context filter can receive it.
        MKContext context = MKContext.fromScreen(screen);

        // ── Resolve slot-level state ────────────────────────────────────
        // For THROW events (click outside window), slot is null. All slot-
        // dependent fields get safe defaults.
        @Nullable MKSlotState state = null;
        @Nullable MKRegion region = null;
        @Nullable String panelName = null;
        int containerSlot = -1;
        ItemStack slotStack = ItemStack.EMPTY;

        if (slot != null) {
            // Look up MenuKit state for this slot (may be null for vanilla
            // slots that were never registered with MenuKit)
            state = MKSlotStateRegistry.get(slot);

            // Container slot index — the slot's position within its backing Container
            containerSlot = slot.getContainerSlot();

            // Snapshot the slot's current contents
            slotStack = slot.getItem().copy();

            // Resolve region from the menu's region registry.
            // In creative mode, screen.getMenu() returns ItemPickerMenu which
            // has no regions — fall back to player.inventoryMenu where regions
            // were resolved during InventoryMenu construction.
            AbstractContainerMenu menu = screen.getMenu();
            region = MKRegionRegistry.getRegionForSlot(menu, slot.index);
            if (region == null && player != null) {
                region = MKRegionRegistry.getRegionForSlot(player.inventoryMenu, slot.index);
            }

            // Panel name comes from the slot state (set during slot injection)
            if (state != null) {
                panelName = state.getPanelName();
            }
        }

        // ── Cursor stack snapshot ───────────────────────────────────────
        // What the player is holding on the cursor at event time
        ItemStack cursorStack = screen.getMenu().getCarried().copy();

        // ── Build the event ─────────────────────────────────────────────
        // keyCode is -1 for click events (only used by KEY_PRESS).
        // Context may be null — the bus and listeners handle that gracefully.
        return new MKSlotEvent(
                type,
                button,
                slot,
                state,
                context,      // may be null for unrecognized screens
                region,       // null for THROW or unregioned slots
                panelName,    // null for THROW or unpaneled slots
                containerSlot,
                slotStack,
                cursorStack,
                player,
                -1,           // keyCode — not applicable for click events
                0.0,          // scrollDelta — not applicable for click events
                modifiers     // GLFW modifier bitfield sampled above
        );
    }

    // ── Fire Helper ───────────────────────────────────────────────────────
    //
    // Convenience method that maps, builds, and fires in one call.
    // Returns true if the event was consumed (caller should cancel vanilla).

    /**
     * Maps the vanilla click to an event type, builds the event, and fires
     * it through the {@link MKEventBus}.
     *
     * <p>Returns true if any bus handler consumed the event, meaning the
     * caller should cancel vanilla behavior.
     *
     * @param clickType vanilla click type
     * @param slot      the slot clicked (null for THROW)
     * @param button    mouse button
     * @param screen    the container screen
     * @param player    the player
     * @return true if consumed by a bus handler
     */
    public static boolean fireClickEvent(ClickType clickType,
                                          @Nullable Slot slot,
                                          int button,
                                          AbstractContainerScreen<?> screen,
                                          Player player) {
        // Map vanilla ClickType to our event type
        MKEvent.Type eventType = mapClickType(clickType, button);
        if (eventType == null) return false; // unmapped click type (e.g., drag)

        // Build the fully-resolved event
        MKSlotEvent event = buildSlotEvent(eventType, slot, button, screen, player);

        // Dispatch through the bus — returns true if any handler consumed
        return MKEventBus.fire(event);
    }

    // ── Hover Event Builder ──────────────────────────────────────────────────
    //
    // Used by MKHoverTrackingMixin for HOVER_ENTER, HOVER_EXIT, and DRAG_OVER.
    // Same resolution logic as click events, but button=-1 and keyCode=-1.

    /**
     * Builds a fully-resolved {@link MKSlotEvent} for a hover or drag event.
     *
     * <p>Uses the same context resolution as click events (state, region,
     * panel, cursor stack) but sets button to -1 and keyCode to -1.
     *
     * @param type   HOVER_ENTER, HOVER_EXIT, or DRAG_OVER
     * @param slot   the slot being hovered (never null for hover events)
     * @param screen the container screen
     * @param player the player
     * @return a fully-populated MKSlotEvent
     */
    public static MKSlotEvent buildHoverEvent(MKEvent.Type type,
                                               Slot slot,
                                               AbstractContainerScreen<?> screen,
                                               Player player) {
        // Reuse the click event builder with button=-1 (not a click)
        return buildSlotEvent(type, slot, -1, screen, player);
    }

    // ── Transfer Event Builder ──────────────────────────────────────────────
    //
    // Used by MKTransferMixin for ITEM_TRANSFER_IN and ITEM_TRANSFER_OUT.
    // These fire at the Slot level (inside doClick), so there's no screen
    // reference. Context is resolved from the menu class, and regions from
    // the slot's state. Player may be null for insertion paths where vanilla
    // doesn't provide one.

    /**
     * Builds a fully-resolved {@link MKSlotEvent} for an item transfer event.
     *
     * <p>Unlike click/hover events, transfer events fire at the {@code Slot} level
     * rather than the screen level. This means we don't have an
     * {@link AbstractContainerScreen} reference. Context is resolved from the
     * slot's menu class via {@link MKContext#defaultForMenuClass}, and regions
     * from the slot's MKSlotState.
     *
     * <p>For ITEM_TRANSFER_IN, {@code slotStack} is the slot's current contents
     * and {@code cursorStack} is the incoming item about to be inserted.
     *
     * <p>For ITEM_TRANSFER_OUT, {@code slotStack} is the slot's current contents
     * (about to be taken) and {@code cursorStack} is empty.
     *
     * @param type       ITEM_TRANSFER_IN or ITEM_TRANSFER_OUT
     * @param slot       the slot being transferred into/from
     * @param slotStack  snapshot of current slot contents
     * @param cursorStack the item being inserted (IN) or empty (OUT)
     * @param player     the player involved, or null if unavailable
     * @return a fully-populated MKSlotEvent, or null if player is null
     */
    public static @Nullable MKSlotEvent buildTransferEvent(MKEvent.Type type,
                                                            Slot slot,
                                                            ItemStack slotStack,
                                                            ItemStack cursorStack,
                                                            @Nullable Player player) {
        // Transfer events require a player to be meaningful — if we can't
        // identify the player, skip the event rather than fire with null.
        if (player == null) return null;

        // ── Resolve slot-level state ────────────────────────────────────
        @Nullable MKSlotState state = MKSlotStateRegistry.get(slot);
        @Nullable MKRegion region = null;
        @Nullable String panelName = null;
        int containerSlot = slot.getContainerSlot();

        // ── Resolve context from the menu class ─────────────────────────
        // We don't have a screen reference at the Slot level. Instead,
        // walk the player's open menu to resolve context. The player's
        // containerMenu is the menu that called doClick -> safeTake/safeInsert.
        @Nullable MKContext context = null;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null) {
            context = MKContext.defaultForMenuClass(menu.getClass());

            // Resolve region from the menu's region registry
            region = MKRegionRegistry.getRegionForSlot(menu, slot.index);
        }

        // Panel name from slot state
        if (state != null) {
            panelName = state.getPanelName();
        }

        // ── Build the event ─────────────────────────────────────────────
        // button=-1 and keyCode=-1 — these are not click or key events.
        return new MKSlotEvent(
                type,
                -1,            // button — not applicable for transfer events
                slot,
                state,
                context,       // resolved from menu class, may be null
                region,
                panelName,
                containerSlot,
                slotStack,
                cursorStack,
                player,
                -1             // keyCode — not applicable for transfer events
        );
    }

    /**
     * Resolves a Player reference from a Slot, for transfer paths where
     * vanilla doesn't provide the player directly (safeInsert, setByPlayer).
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If the slot's container is a player Inventory, use its player field</li>
     *   <li>If a clicked() call is in progress, use the active click player
     *       (set by {@link #setCurrentClickPlayer}). This covers non-player
     *       containers like chests, furnaces, etc.</li>
     *   <li>Otherwise, return null (caller should skip the event)</li>
     * </ol>
     *
     * @param slot the slot to resolve a player from
     * @return the player, or null if not resolvable
     */
    public static @Nullable Player resolvePlayerFromSlot(Slot slot) {
        // Direct resolution: player inventory slots have a player reference
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory inv) {
            return inv.player;
        }
        // Fallback: use the player from the active clicked() call.
        // This covers non-player containers (chests, furnaces, hoppers, etc.)
        // where the Container has no player field but clicked() knows who clicked.
        if (currentClickPlayer != null) {
            return currentClickPlayer;
        }
        return null;
    }

    // ── Key Event Builder ────────────────────────────────────────────────────
    //
    // Used by MKKeyPressMixin for KEY_PRESS events. Same resolution as click
    // events, but carries the GLFW keyCode instead of a mouse button.

    /**
     * Builds a fully-resolved {@link MKSlotEvent} for a KEY_PRESS event.
     *
     * <p>Uses the same context resolution as click events (state, region,
     * panel, cursor stack) but sets button to -1 and carries the keyCode
     * plus the GLFW modifier bitmask (Shift/Ctrl/Alt/Super). Consumers
     * can use {@link MKSlotEvent#getModifiers()} and the convenience
     * helpers ({@code isShiftPressed()}, etc.) to check modifier state,
     * or pass keyCode + modifiers directly to
     * {@link com.trevorschoeny.menukit.MKKeybind#matches(int, int, int)}.
     *
     * <p>Returns null if the slot is null (no slot hovered when key was pressed).
     *
     * @param slot      the slot being hovered when the key was pressed, or null
     * @param screen    the container screen
     * @param player    the player
     * @param keyCode   GLFW key code (e.g., GLFW_KEY_Q = 81)
     * @param modifiers GLFW modifier bitmask (Shift=1, Ctrl=2, Alt=4, Super=8)
     * @return the event, or null if slot is null
     */
    public static @Nullable MKSlotEvent buildKeyEvent(@Nullable Slot slot,
                                                       AbstractContainerScreen<?> screen,
                                                       Player player,
                                                       int keyCode,
                                                       int modifiers) {
        // Key events require a hovered slot to be meaningful
        if (slot == null) return null;

        // ── Resolve screen context ──────────────────────────────────────
        MKContext context = MKContext.fromScreen(screen);

        // ── Resolve slot-level state ────────────────────────────────────
        @Nullable MKSlotState state = MKSlotStateRegistry.get(slot);
        @Nullable MKRegion region = null;
        @Nullable String panelName = null;
        int containerSlot = slot.getContainerSlot();
        ItemStack slotStack = slot.getItem().copy();

        // Resolve region from the menu's region registry.
        // Creative mode fallback: ItemPickerMenu has no regions, use inventoryMenu.
        AbstractContainerMenu menu = screen.getMenu();
        region = MKRegionRegistry.getRegionForSlot(menu, slot.index);
        if (region == null) {
            region = MKRegionRegistry.getRegionForSlot(player.inventoryMenu, slot.index);
        }

        // Panel name from slot state
        if (state != null) {
            panelName = state.getPanelName();
        }

        // Cursor stack snapshot
        ItemStack cursorStack = menu.getCarried().copy();

        // Build the event with keyCode AND modifiers. Uses the 4-arg
        // constructor so the modifier bitmask is preserved for KEY_PRESS
        // handlers that need to check multi-key combos (e.g., Ctrl+K).
        return new MKSlotEvent(
                MKEvent.Type.KEY_PRESS,
                -1,            // button — not applicable for key events
                slot,
                state,
                context,       // may be null for unrecognized screens
                region,
                panelName,
                containerSlot,
                slotStack,
                cursorStack,
                player,
                keyCode,
                0.0,           // scrollDelta — not applicable for key events
                modifiers
        );
    }

    // ── Scroll Event Builder ─────────────────────────────────────────────────
    //
    // Used by MKScrollMixin for SCROLL events. Same resolution as click events,
    // but carries scrollDelta instead of a mouse button or key code.
    // scrollDelta is the vertical scroll amount: positive = up, negative = down.

    /**
     * Builds a fully-resolved {@link MKSlotEvent} for a SCROLL event.
     *
     * <p>Uses the same context resolution as click events (state, region,
     * panel, cursor stack) but sets button to -1 and keyCode to -1,
     * and carries the scrollDelta instead.
     *
     * <p>Returns null if the slot is null (no slot hovered when scroll occurred).
     *
     * @param slot        the slot being hovered when the scroll occurred, or null
     * @param scrollDelta vertical scroll amount — positive = up, negative = down
     * @param screen      the container screen
     * @param player      the player
     * @return the event, or null if slot is null
     */
    public static @Nullable MKSlotEvent buildScrollEvent(@Nullable Slot slot,
                                                           double scrollDelta,
                                                           AbstractContainerScreen<?> screen,
                                                           Player player) {
        // Scroll events require a hovered slot to be meaningful
        if (slot == null) return null;

        // ── Resolve screen context ──────────────────────────────────────
        MKContext context = MKContext.fromScreen(screen);

        // ── Resolve slot-level state ────────────────────────────────────
        @Nullable MKSlotState state = MKSlotStateRegistry.get(slot);
        @Nullable MKRegion region = null;
        @Nullable String panelName = null;
        int containerSlot = slot.getContainerSlot();
        ItemStack slotStack = slot.getItem().copy();

        // Resolve region from the menu's region registry.
        // Creative mode fallback: ItemPickerMenu has no regions, use inventoryMenu.
        AbstractContainerMenu menu = screen.getMenu();
        region = MKRegionRegistry.getRegionForSlot(menu, slot.index);
        if (region == null) {
            region = MKRegionRegistry.getRegionForSlot(player.inventoryMenu, slot.index);
        }

        // Panel name from slot state
        if (state != null) {
            panelName = state.getPanelName();
        }

        // Cursor stack snapshot
        ItemStack cursorStack = menu.getCarried().copy();

        // Build the event with scrollDelta (button and keyCode are -1 for scroll events)
        return new MKSlotEvent(
                MKEvent.Type.SCROLL,
                -1,            // button — not applicable for scroll events
                slot,
                state,
                context,       // may be null for unrecognized screens
                region,
                panelName,
                containerSlot,
                slotStack,
                cursorStack,
                player,
                -1,            // keyCode — not applicable for scroll events
                scrollDelta    // vertical scroll amount
        );
    }
}
