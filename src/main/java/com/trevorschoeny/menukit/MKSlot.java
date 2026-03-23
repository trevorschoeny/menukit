package com.trevorschoeny.menukit;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A vanilla-compatible {@link Slot} subclass backed by an {@link MKContainer}.
 *
 * <p>Works exactly like a regular vanilla slot — vanilla handles ALL click logic
 * (left, right, shift, drag, number keys, Q-throw, middle-click clone),
 * network sync ({@code broadcastChanges}), hover detection, item rendering,
 * and tooltips. No custom click handler needed.
 *
 * <p>Adds two optional features:
 * <ul>
 *   <li><b>Item filter</b> via {@link #mayPlace(ItemStack)} — restricts what items can go in</li>
 *   <li><b>Ghost icon</b> via {@link #getNoItemIcon()} — shows a custom icon when slot is empty</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * MKSlot slot = MKSlot.builder(container, 0, x, y)
 *     .filter(stack -> stack.is(Items.ELYTRA))
 *     .maxStack(1)
 *     .ghostIcon(() -> MY_ICON_ID)
 *     .build();
 * menu.addSlot(slot);
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public class MKSlot extends Slot {

    private final @Nullable Predicate<ItemStack> filter;
    private final int maxStack;
    private final @Nullable Supplier<Identifier> ghostIconProvider;
    private final @Nullable BooleanSupplier disabledWhen;

    // Callback fired when the player clicks this slot while both the
    // slot and the cursor are empty. Vanilla treats this as a no-op,
    // so we can safely add behavior without interfering.
    private @Nullable Consumer<MKSlot> onEmptyClick;

    // Tooltip shown when hovering an empty slot with an empty cursor.
    // Returns null to show no tooltip. Evaluated each frame so text
    // can change dynamically (e.g., "Mark empty hand" vs "Remove empty hand").
    private @Nullable Supplier<net.minecraft.network.chat.Component> emptyTooltip;

    // Panel name — used for panel visibility checks. When non-null,
    // this slot deactivates itself when its parent panel is hidden.
    private @Nullable String panelName;

    // Index of this slot within its panel's slot list (0-based).
    // Used for identity-based position matching in creative mode.
    private int slotIndexInPanel = -1;

    public MKSlot(MKContainer container, int containerSlot, int x, int y,
                  @Nullable Predicate<ItemStack> filter, int maxStack,
                  @Nullable Supplier<Identifier> ghostIconProvider,
                  @Nullable BooleanSupplier disabledWhen) {
        this((Container) container, containerSlot, x, y, filter, maxStack, ghostIconProvider, disabledWhen);
    }

    /**
     * Constructor accepting any vanilla Container (including player Inventory).
     * Used for vanilla slot references (e.g., mirroring hotbar slots in pocket panels).
     */
    public MKSlot(Container container, int containerSlot, int x, int y,
                  @Nullable Predicate<ItemStack> filter, int maxStack,
                  @Nullable Supplier<Identifier> ghostIconProvider,
                  @Nullable BooleanSupplier disabledWhen) {
        super(container, containerSlot, x, y);
        this.filter = filter;
        this.maxStack = maxStack;
        this.ghostIconProvider = ghostIconProvider;
        this.disabledWhen = disabledWhen;
    }

    /** Convenience constructor with no filter, ghost icon, or disable predicate. */
    public MKSlot(MKContainer container, int containerSlot, int x, int y) {
        this((Container) container, containerSlot, x, y, null, 64, null, null);
    }

    // ── Panel Visibility ────────────────────────────────────────────────────

    /** Sets the panel name for visibility tracking. */
    public void setPanelName(@Nullable String panelName) {
        this.panelName = panelName;
    }

    /** Returns the panel name, or null if not associated with a panel. */
    public @Nullable String panelName() {
        return panelName;
    }

    /** Sets this slot's index within its parent panel's slot list. */
    public void setSlotIndexInPanel(int index) {
        this.slotIndexInPanel = index;
    }

    /** Returns this slot's index within its parent panel's slot list. */
    public int slotIndexInPanel() {
        return slotIndexInPanel;
    }

    // ── Empty Click Callback ─────────────────────────────────────────────

    /** Sets a callback fired when the player clicks this slot with an empty cursor and empty slot. */
    public void setOnEmptyClick(@Nullable Consumer<MKSlot> callback) {
        this.onEmptyClick = callback;
    }

    /** Returns the empty-click callback, or null if none is set. */
    public @Nullable Consumer<MKSlot> getOnEmptyClick() {
        return onEmptyClick;
    }

    /** Sets a tooltip supplier shown when hovering this empty slot with an empty cursor. */
    public void setEmptyTooltip(@Nullable Supplier<net.minecraft.network.chat.Component> tooltip) {
        this.emptyTooltip = tooltip;
    }

    /** Returns the empty-slot tooltip supplier, or null if none is set. */
    public @Nullable Supplier<net.minecraft.network.chat.Component> getEmptyTooltip() {
        return emptyTooltip;
    }

    /**
     * Returns false when this slot's parent panel is hidden/disabled, or when
     * the slot's own disabledWhen predicate returns true. Vanilla automatically
     * skips rendering, hover detection, and click handling for inactive slots.
     */
    @Override
    public boolean isActive() {
        if (panelName != null && MenuKit.isPanelInactive(panelName)) return false;
        if (disabledWhen != null && disabledWhen.getAsBoolean()) return false;
        return true;
    }

    // ── Overrides ───────────────────────────────────────────────────────────
    // Everything else (getItem, set, remove) is inherited from Slot
    // and delegates to MKContainer via the Container interface.

    @Override
    public void set(ItemStack stack) {
        MenuKit.LOGGER.info(
                "[MKSlot] set() slot={} item={} containerId={} thread={}",
                this.getContainerSlot(),
                stack.getCount() + " " + stack.getItemHolder().getRegisteredName(),
                System.identityHashCode(this.container),
                Thread.currentThread().getName());
        super.set(stack);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return filter == null || filter.test(stack);
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    /**
     * Returns null — MenuKit handles ghost icon rendering itself via
     * {@code renderSlotBackgrounds}. Returning the icon here would cause
     * vanilla's {@code blitSprite} to render a broken sprite on top of
     * our pixel-art ghost icon.
     */
    @Override
    public @Nullable Identifier getNoItemIcon() {
        return null;
    }

    /** Returns the ghost icon provider for MenuKit's own rendering. */
    public @Nullable Supplier<Identifier> getGhostIconProvider() {
        return ghostIconProvider;
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static Builder builder(MKContainer container, int containerSlot, int x, int y) {
        return new Builder(container, containerSlot, x, y);
    }

    public static class Builder {
        private final MKContainer container;
        private final int containerSlot, x, y;
        private @Nullable Predicate<ItemStack> filter;
        private int maxStack = 64;
        private @Nullable Supplier<Identifier> ghostIconProvider;

        Builder(MKContainer container, int containerSlot, int x, int y) {
            this.container = container;
            this.containerSlot = containerSlot;
            this.x = x;
            this.y = y;
        }

        public Builder filter(Predicate<ItemStack> filter)       { this.filter = filter; return this; }
        public Builder maxStack(int maxStack)                     { this.maxStack = maxStack; return this; }
        public Builder ghostIcon(Supplier<Identifier> provider)   { this.ghostIconProvider = provider; return this; }

        public MKSlot build() {
            return new MKSlot(container, containerSlot, x, y,
                    filter, maxStack, ghostIconProvider, null);
        }
    }
}
