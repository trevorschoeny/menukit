package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.MKContainerDef.Persistence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * External state attached to any {@link Slot} via the mixin layer.
 * Stored in {@link MKSlotStateRegistry}, keyed by slot identity.
 *
 * <p>This enables per-slot features on ALL slots globally — vanilla hotbar,
 * chest slots, furnace slots, custom mod slots, everything. Features include:
 * <ul>
 *   <li>Lock — excluded from auto-sort, visual indicator</li>
 *   <li>Filter — restricts what items can be placed (via mayPlace)</li>
 *   <li>Ghost icon — shows a translucent item when the slot is empty</li>
 *   <li>Disabled — hides the slot and blocks interaction</li>
 *   <li>Right-click callback — custom behavior on right-click</li>
 *   <li>Region association — which region this slot belongs to</li>
 *   <li>Panel association — which panel renders this slot</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
public class MKSlotState {

    // ── Region & Panel Association ───────────────────────────────────────
    private @Nullable String regionName;
    private @Nullable String panelName;
    private int regionIndex = -1;  // index within the region (0-based)

    // ── Lock State ───────────────────────────────────────────────────────
    private boolean locked;

    // ── Filter ───────────────────────────────────────────────────────────
    // Additional placement restriction on top of vanilla's mayPlace.
    private @Nullable Predicate<ItemStack> filter;

    // ── Ghost Icon ───────────────────────────────────────────────────────
    // Shown when the slot is empty (e.g., armor outline, elytra silhouette)
    private @Nullable Identifier ghostIcon;

    // ── Disabled ─────────────────────────────────────────────────────────
    // When disabled, the slot is hidden and blocks interaction.
    private @Nullable BooleanSupplier disabledWhen;

    // ── Shift-Click Flags ───────────────────────────────────────────────
    private boolean shiftClickIn;
    private boolean shiftClickOut;

    // ── Max Stack Size ───────────────────────────────────────────────────
    // Per-slot max stack override. 0 = use vanilla default.
    private int maxStackSize;

    // ── Slot Index in Panel ──────────────────────────────────────────────
    // Index of this slot within its panel's slot list (0-based).
    private int slotIndexInPanel = -1;

    // ── Empty Slot Callbacks ─────────────────────────────────────────────
    // Fired when the player left-clicks an empty slot with empty cursor.
    private @Nullable Consumer<Slot> onEmptyClick;

    // ── Empty Slot Tooltip ───────────────────────────────────────────────
    // Tooltip shown when hovering an empty slot with empty cursor.
    private @Nullable Supplier<Component> emptyTooltip;

    // ── Right-Click Callbacks ────────────────────────────────────────────
    // Fired when the player right-clicks this slot. Return true to consume.
    private @Nullable List<SlotRightClickHandler> rightClickHandlers;

    // ── Persistence ─────────────────────────────────────────────────────
    // How items in this slot behave (PERSISTENT, TRANSIENT, or OUTPUT).
    // null = no persistence set (vanilla default behavior).
    private @Nullable Persistence persistence;

    // ── Visual Decorations ────────────────────────────────────────────────
    // ARGB color applied as a translucent fill BEHIND the item.
    // 0 = no tint. Use alpha channel to control transparency (e.g., 0x40FF0000
    // for a semi-transparent red tint).
    private int backgroundTint = 0;

    // Icon texture rendered ON TOP of the slot item (e.g., a lock icon,
    // a status indicator). null = no overlay.
    private @Nullable Identifier overlayIcon = null;

    // ARGB color drawn as a 1px border around the 16×16 slot area, ON TOP
    // of the item. 0 = no border. Fully opaque recommended (0xFFRRGGBB).
    private int borderColor = 0;

    // ── Wrapped Slot ──────────────────────────────────────────────────────
    // Reference to the original vanilla slot being wrapped by MKSlotWrapper.
    // null for non-wrapper slots (MKSlot, vanilla slots with state attached).
    private @Nullable Slot wrappedSlot;

    // ── MenuKit-managed flag ─────────────────────────────────────────────
    // True for slots created by MenuKit (custom panel slots).
    // False for vanilla slots that just have state attached for features.
    private boolean menuKitSlot;

    // ── Functional Interface ─────────────────────────────────────────────

    @FunctionalInterface
    public interface SlotRightClickHandler {
        /**
         * Called when a slot is right-clicked.
         * @param slot the vanilla Slot that was clicked
         * @param stack the item in the slot
         * @return true if the click was consumed (cancel vanilla behavior)
         */
        boolean handle(Slot slot, ItemStack stack);
    }

    // ── Region & Panel ───────────────────────────────────────────────────

    public @Nullable String getRegionName() { return regionName; }
    public void setRegionName(@Nullable String name) { this.regionName = name; }

    public @Nullable String getPanelName() { return panelName; }
    public void setPanelName(@Nullable String name) { this.panelName = name; }

    public int getRegionIndex() { return regionIndex; }
    public void setRegionIndex(int index) { this.regionIndex = index; }

    // ── Lock ─────────────────────────────────────────────────────────────

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void toggleLocked() { this.locked = !this.locked; }

    // ── Filter ───────────────────────────────────────────────────────────

    public @Nullable Predicate<ItemStack> getFilter() { return filter; }
    public void setFilter(@Nullable Predicate<ItemStack> filter) { this.filter = filter; }

    /** Tests whether the given item passes the filter (true = allowed). */
    public boolean passesFilter(ItemStack stack) {
        return filter == null || filter.test(stack);
    }

    // ── Ghost Icon ───────────────────────────────────────────────────────

    public @Nullable Identifier getGhostIcon() { return ghostIcon; }
    public void setGhostIcon(@Nullable Identifier icon) { this.ghostIcon = icon; }

    // ── Disabled ─────────────────────────────────────────────────────────

    public @Nullable BooleanSupplier getDisabledWhen() { return disabledWhen; }
    public void setDisabledWhen(@Nullable BooleanSupplier predicate) { this.disabledWhen = predicate; }

    /** Whether this slot is currently disabled. */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Whether this slot is currently active (not disabled). */
    public boolean isActive() {
        return !isDisabled();
    }

    // ── Shift-Click ──────────────────────────────────────────────────────

    public boolean canShiftClickIn() { return shiftClickIn; }
    public void setShiftClickIn(boolean value) { this.shiftClickIn = value; }

    public boolean canShiftClickOut() { return shiftClickOut; }
    public void setShiftClickOut(boolean value) { this.shiftClickOut = value; }

    // ── Max Stack Size ──────────────────────────────────────────────────

    /** Returns per-slot max stack override, or 0 if using vanilla default. */
    public int getMaxStackSize() { return maxStackSize; }
    public void setMaxStackSize(int max) { this.maxStackSize = max; }

    /** Returns the effective max stack size — per-slot override or the given default. */
    public int getEffectiveMaxStackSize(int vanillaDefault) {
        return maxStackSize > 0 ? maxStackSize : vanillaDefault;
    }

    // ── Slot Index in Panel ─────────────────────────────────────────────

    public int getSlotIndexInPanel() { return slotIndexInPanel; }
    public void setSlotIndexInPanel(int index) { this.slotIndexInPanel = index; }

    // ── Empty Slot Click ─────────────────────────────────────────────────

    public @Nullable Consumer<Slot> getOnEmptyClick() { return onEmptyClick; }
    public void setOnEmptyClick(@Nullable Consumer<Slot> callback) { this.onEmptyClick = callback; }

    // ── Empty Slot Tooltip ───────────────────────────────────────────────

    public @Nullable Supplier<Component> getEmptyTooltip() { return emptyTooltip; }
    public void setEmptyTooltip(@Nullable Supplier<Component> tooltip) { this.emptyTooltip = tooltip; }

    // ── Persistence ─────────────────────────────────────────────────────

    /** Returns the persistence mode, or null if not set. */
    public @Nullable Persistence getPersistence() { return persistence; }
    public void setPersistence(@Nullable Persistence persistence) { this.persistence = persistence; }

    /** Convenience: true if this slot's persistence is OUTPUT (take-only). */
    public boolean isPersistenceOutput() {
        return persistence == Persistence.OUTPUT;
    }

    // ── Visual Decorations ─────────────────────────────────────────────────

    /** Returns the ARGB background tint color, or 0 if no tint. */
    public int getBackgroundTint() { return backgroundTint; }

    /** Sets the ARGB background tint. 0 clears the tint. Rendered BEHIND the item. */
    public void setBackgroundTint(int argb) { this.backgroundTint = argb; }

    /** Returns the overlay icon rendered ON TOP of the slot item, or null. */
    public @Nullable Identifier getOverlayIcon() { return overlayIcon; }

    /** Sets an overlay icon drawn ON TOP of the slot item. null clears it. */
    public void setOverlayIcon(@Nullable Identifier icon) { this.overlayIcon = icon; }

    /** Returns the ARGB border color, or 0 if no border. */
    public int getBorderColor() { return borderColor; }

    /** Sets the ARGB border color. 0 clears the border. Rendered ON TOP of the item. */
    public void setBorderColor(int argb) { this.borderColor = argb; }

    /**
     * Returns true if this slot has any visual decoration set (tint, overlay, or border).
     * Used as a fast gate in the rendering path — undecorated slots skip all
     * decoration logic for zero overhead.
     */
    public boolean hasDecoration() {
        return backgroundTint != 0 || overlayIcon != null || borderColor != 0;
    }

    // ── Wrapped Slot ──────────────────────────────────────────────────────

    /** Returns the original vanilla slot being wrapped, or null if not a wrapper. */
    public @Nullable Slot getWrappedSlot() { return wrappedSlot; }
    public void setWrappedSlot(@Nullable Slot slot) { this.wrappedSlot = slot; }

    // ── MenuKit-managed ──────────────────────────────────────────────────

    /** Whether this slot was created by MenuKit (vs a vanilla slot with state attached). */
    public boolean isMenuKitSlot() { return menuKitSlot; }
    public void setMenuKitSlot(boolean value) { this.menuKitSlot = value; }

    // ── Panel Visibility Check ──────────────────────────────────────────

    /**
     * Full activity check: slot is active if not disabled AND (no panel OR panel is visible).
     * This replaces MKSlot.isActive() logic.
     */
    public boolean isSlotActive() {
        if (isDisabled()) return false;
        if (panelName != null) {
            return !MenuKit.isPanelInactive(panelName);
        }
        return true;
    }

    // ── Right-Click ──────────────────────────────────────────────────────

    public void addRightClickHandler(SlotRightClickHandler handler) {
        if (rightClickHandlers == null) rightClickHandlers = new ArrayList<>(2);
        rightClickHandlers.add(handler);
    }

    public void removeRightClickHandler(SlotRightClickHandler handler) {
        if (rightClickHandlers != null) rightClickHandlers.remove(handler);
    }

    /**
     * Fires all right-click handlers. Returns true if any handler consumed
     * the click (and vanilla behavior should be cancelled).
     */
    public boolean fireRightClick(Slot slot, ItemStack stack) {
        if (rightClickHandlers == null) return false;
        for (SlotRightClickHandler handler : rightClickHandlers) {
            if (handler.handle(slot, stack)) return true;
        }
        return false;
    }

    public boolean hasRightClickHandlers() {
        return rightClickHandlers != null && !rightClickHandlers.isEmpty();
    }
}
