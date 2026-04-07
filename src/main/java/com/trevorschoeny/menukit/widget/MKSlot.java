package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainer;

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
 * A vanilla {@link Slot} subclass used for MenuKit-managed panel slots.
 *
 * <p>MKSlot is intentionally thin — it creates a regular Slot backed by
 * an MKContainer, and registers an {@link MKSlotState} in the mixin-layer
 * registry. All behavioral features (isActive, mayPlace, maxStackSize,
 * ghostIcon) are handled by {@link com.trevorschoeny.menukit.mixin.MKSlotMixin}
 * reading from MKSlotState, not by overrides on this class.
 *
 * <p>This means MKSlots and vanilla slots use the same code paths for
 * all features. MKSlot is just a convenience for creating slots that
 * automatically register state.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public class MKSlot extends Slot {

    /**
     * Creates an MKSlot and registers its MKSlotState.
     *
     * @param container     the backing container (MKContainer or vanilla Container)
     * @param containerSlot index within the container
     * @param x             screen x position (set by layout engine later)
     * @param y             screen y position (set by layout engine later)
     * @param filter        item placement filter (nullable)
     * @param maxStack      per-slot max stack size (64 for default)
     * @param ghostIcon     ghost icon provider (nullable)
     * @param disabledWhen  predicate that disables the slot (nullable)
     */
    public MKSlot(Container container, int containerSlot, int x, int y,
                  @Nullable Predicate<ItemStack> filter, int maxStack,
                  @Nullable Supplier<Identifier> ghostIcon,
                  @Nullable BooleanSupplier disabledWhen) {
        super(container, containerSlot, x, y);

        // Register state in the mixin-layer registry.
        // All behavioral hooks (isActive, mayPlace, etc.) read from here.
        MKSlotState state = MKSlotStateRegistry.getOrCreate(this);
        state.setMenuKitSlot(true);
        if (filter != null) state.setFilter(filter);
        if (ghostIcon != null) state.setGhostIcon(ghostIcon.get());
        if (disabledWhen != null) state.setDisabledWhen(disabledWhen);
        if (maxStack > 0 && maxStack != 64) state.setMaxStackSize(maxStack);
    }

    /** Convenience constructor with no filter, ghost icon, or disable predicate. */
    public MKSlot(Container container, int containerSlot, int x, int y) {
        this(container, containerSlot, x, y, null, 64, null, null);
    }

    // ── State Accessors (convenience, delegate to MKSlotState) ───────────

    /** Sets the panel name for this slot. */
    public void setPanelName(@Nullable String panelName) {
        MKSlotStateRegistry.getOrCreate(this).setPanelName(panelName);
    }

    /** Sets this slot's index within its parent panel's slot list. */
    public void setSlotIndexInPanel(int index) {
        MKSlotStateRegistry.getOrCreate(this).setSlotIndexInPanel(index);
    }

    /** Sets a callback fired when clicking an empty slot with empty cursor. */
    public void setOnEmptyClick(@Nullable Consumer<Slot> callback) {
        MKSlotStateRegistry.getOrCreate(this).setOnEmptyClick(callback);
    }

    /** Sets a tooltip shown when hovering an empty slot with empty cursor. */
    public void setEmptyTooltip(@Nullable Supplier<net.minecraft.network.chat.Component> tooltip) {
        MKSlotStateRegistry.getOrCreate(this).setEmptyTooltip(tooltip);
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static Builder builder(Container container, int containerSlot, int x, int y) {
        return new Builder(container, containerSlot, x, y);
    }

    public static class Builder {
        private final Container container;
        private final int containerSlot, x, y;
        private @Nullable Predicate<ItemStack> filter;
        private int maxStack = 64;
        private @Nullable Supplier<Identifier> ghostIcon;

        Builder(Container container, int containerSlot, int x, int y) {
            this.container = container;
            this.containerSlot = containerSlot;
            this.x = x;
            this.y = y;
        }

        public Builder filter(Predicate<ItemStack> filter)       { this.filter = filter; return this; }
        public Builder maxStack(int maxStack)                     { this.maxStack = maxStack; return this; }
        public Builder ghostIcon(Supplier<Identifier> provider)   { this.ghostIcon = provider; return this; }

        public MKSlot build() {
            return new MKSlot(container, containerSlot, x, y,
                    filter, maxStack, ghostIcon, null);
        }
    }
}
