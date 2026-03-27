package com.trevorschoeny.menukit;

import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Sealed root interface for all MenuKit events -- both slot-level (clicks,
 * hovers, transfers) and UI-level (button clicks, panel show/hide, element
 * visibility changes).
 *
 * <p>Every event carries a {@link Type}, an optional {@link MKContext}, and the
 * {@link Player} who triggered it. Slot events ({@link MKSlotEvent}) add slot,
 * region, and cursor context. UI events ({@link MKUIEvent}) add button, panel,
 * and element context.
 *
 * <p>The unified {@link Type} enum lets consumers register a single handler for
 * both slot and UI events via {@link MenuKit#on(Type...)}.
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public sealed interface MKEvent permits MKSlotEvent, MKUIEvent {

    /** Returns the type of event that occurred. */
    Type getType();

    /** Returns the screen context where this event occurred, or null. */
    @Nullable MKContext getContext();

    /** Returns the player who triggered the event. */
    Player getPlayer();

    /**
     * Unified event type enum covering both slot-level and UI-level events.
     *
     * <p>Slot events are dispatched from mixins that intercept vanilla click,
     * hover, transfer, and lifecycle actions. UI events are dispatched from
     * MenuKit's button, panel, and element visibility systems.
     */
    enum Type {

        // ── Click Events (slot) ─────────────────────────────────────────
        /** Left-click on a slot. Vanilla: ClickType.PICKUP, button=0. */
        LEFT_CLICK,
        /** Right-click on a slot. Vanilla: ClickType.PICKUP, button=1. */
        RIGHT_CLICK,
        /** Shift-click (left or right). Vanilla: ClickType.QUICK_MOVE. */
        SHIFT_CLICK,
        /** Number key or offhand swap. Vanilla: ClickType.SWAP. */
        SWAP,
        /** Middle-click on a slot. Fires in ALL game modes, not just creative. */
        MIDDLE_CLICK,
        /** Click outside the window to throw. Vanilla: ClickType.THROW. */
        THROW,
        /** Double-click to collect matching items. Vanilla: ClickType.PICKUP_ALL. */
        DOUBLE_CLICK,

        // ── Interaction Events (slot) ───────────────────────────────────
        /** Cursor moves onto a slot (was not hovering, now is). */
        HOVER_ENTER,
        /** Cursor leaves a slot (was hovering, now isn't). */
        HOVER_EXIT,
        /** Cursor drags across a slot while holding items. */
        DRAG_OVER,

        // ── State Change Events (slot) ──────────────────────────────────
        /** Item in a slot changes (any modification). */
        SLOT_CHANGED,
        /** Slot transitions from having an item to being empty. */
        SLOT_EMPTIED,
        /** Slot transitions from empty to having an item. */
        SLOT_FILLED,

        // ── Lifecycle Events (slot) ─────────────────────────────────────
        /** Container screen opens. slot and state are null. */
        MENU_OPEN,
        /** Container screen closes. slot and state are null. */
        MENU_CLOSE,
        /** A region's slots are fully resolved after menu construction. */
        REGION_POPULATED,

        // ── Keyboard Events (slot) ──────────────────────────────────────
        /** Key pressed while cursor hovers a slot. Check keyCode for which key. */
        KEY_PRESS,

        // ── Scroll Events (slot) ────────────────────────────────────────
        /** Mouse wheel scrolled while cursor hovers a slot. */
        SCROLL,

        // ── Transfer Events (slot) ──────────────────────────────────────
        /** Item is about to be placed INTO a slot. Mutable via transformStack(). */
        ITEM_TRANSFER_IN,
        /** Item is about to be taken FROM a slot. Mutable via transformStack(). */
        ITEM_TRANSFER_OUT,

        // ── UI Events (button) ──────────────────────────────────────────
        /** An MKButton was clicked. */
        BUTTON_CLICK,
        /** An MKButton toggle state changed. */
        BUTTON_TOGGLE,

        // ── UI Events (panel visibility) ────────────────────────────────
        /** A panel became visible. */
        PANEL_SHOW,
        /** A panel became hidden. */
        PANEL_HIDE,

        // ── UI Events (element visibility) ──────────────────────────────
        /** An element within a panel became visible. */
        ELEMENT_SHOW,
        /** An element within a panel became hidden. */
        ELEMENT_HIDE,

        // ── UI Events (tabs) ─────────────────────────────────────────────
        /** The active tab in a tabs container changed. */
        TAB_CHANGED
    }
}
