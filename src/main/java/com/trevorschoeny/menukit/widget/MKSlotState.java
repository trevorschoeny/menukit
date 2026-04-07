package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerDef;
import com.trevorschoeny.menukit.panel.MKPanelState;
import com.trevorschoeny.menukit.panel.MKPanelStateRegistry;

import com.trevorschoeny.menukit.container.MKContainerDef.Persistence;
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
 * <p>State is split into backend (item rules, persistence, region) and
 * frontend (visuals, decorations) concerns. All existing getters/setters
 * delegate to the appropriate inner state object. New code can access
 * focused state via {@link #backend()} and {@link #frontend()}.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
public class MKSlotState {

    // ── Composed State (v2) ─────────────────────────────────────────────
    private final MKSlotBackendState backend = new MKSlotBackendState();
    private final MKSlotFrontendState frontend = new MKSlotFrontendState();

    /** Returns the backend state (item rules, persistence, region). */
    public MKSlotBackendState backend() { return backend; }

    /** Returns the frontend state (ghost icon, decorations, visual). */
    public MKSlotFrontendState frontend() { return frontend; }

    // ── Panel & Element Association (structural — shared by both sides) ──
    private @Nullable String panelName;
    private @Nullable String elementId;  // element ID for setElementVisible overrides

    // ── Slot Index in Panel ──────────────────────────────────────────────
    private int slotIndexInPanel = -1;

    // ── Empty Slot Callbacks ─────────────────────────────────────────────
    private @Nullable Consumer<Slot> onEmptyClick;

    // ── Empty Slot Tooltip ───────────────────────────────────────────────
    private @Nullable Supplier<Component> emptyTooltip;

    // ── Right-Click Callbacks ────────────────────────────────────────────
    private @Nullable List<SlotRightClickHandler> rightClickHandlers;

    // ── Wrapped Slot ──────────────────────────────────────────────────────
    private @Nullable Slot wrappedSlot;

    // ── MenuKit-managed flag ─────────────────────────────────────────────
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

    // ══════════════════════════════════════════════════════════════════════
    // Delegating getters/setters — same signatures as before, but now
    // read/write from the composed backend/frontend state objects.
    // ══════════════════════════════════════════════════════════════════════

    // ── Region (→ backend) ──────────────────────────────────────────────

    public @Nullable String getRegionName() { return backend.getRegionName(); }
    public void setRegionName(@Nullable String name) { backend.setRegionName(name); }

    public int getRegionIndex() { return backend.getRegionIndex(); }
    public void setRegionIndex(int index) { backend.setRegionIndex(index); }

    // ── Panel & Element (structural — stays on MKSlotState) ─────────────

    public @Nullable String getPanelName() { return panelName; }
    public void setPanelName(@Nullable String name) { this.panelName = name; }

    public @Nullable String getElementId() { return elementId; }
    public void setElementId(@Nullable String id) { this.elementId = id; }

    // ── Lock (→ frontend — visual Ctrl+click lock) ──────────────────────

    public boolean isLocked() { return frontend.isLocked(); }
    public void setLocked(boolean locked) { frontend.setLocked(locked); }
    public void toggleLocked() { frontend.toggleLocked(); }

    // ── Sort Lock (→ backend — affects sorting and shift-click routing) ──

    public boolean isSortLocked() { return backend.isSortLocked(); }
    public void setSortLocked(boolean sortLocked) { backend.setSortLocked(sortLocked); }
    public void toggleSortLocked() { backend.toggleSortLocked(); }

    // ── Filter (→ backend — item validation) ────────────────────────────

    public @Nullable Predicate<ItemStack> getFilter() { return backend.getFilter(); }
    public void setFilter(@Nullable Predicate<ItemStack> filter) { backend.setFilter(filter); }

    /** Tests whether the given item passes the filter (true = allowed). */
    public boolean passesFilter(ItemStack stack) {
        return backend.passesFilter(stack);
    }

    // ── Ghost Icon (→ frontend — visual) ────────────────────────────────

    public @Nullable Identifier getGhostIcon() { return frontend.getGhostIcon(); }
    public void setGhostIcon(@Nullable Identifier icon) { frontend.setGhostIcon(icon); }

    // ── Disabled (→ frontend — visual hiding) ───────────────────────────

    public @Nullable BooleanSupplier getDisabledWhen() { return frontend.getDisabledWhen(); }
    public void setDisabledWhen(@Nullable BooleanSupplier predicate) { frontend.setDisabledWhen(predicate); }

    /** Whether this slot is currently disabled. */
    public boolean isDisabled() {
        return frontend.isDisabled();
    }

    /** Whether this slot is currently active (not disabled). */
    public boolean isActive() {
        return frontend.isActive();
    }

    // ── Shift-Click (→ backend — transfer policy) ───────────────────────

    public boolean canShiftClickIn() { return backend.canShiftClickIn(); }
    public void setShiftClickIn(boolean value) { backend.setShiftClickIn(value); }

    public boolean canShiftClickOut() { return backend.canShiftClickOut(); }
    public void setShiftClickOut(boolean value) { backend.setShiftClickOut(value); }

    // ── Max Stack Size (→ backend — item rules) ─────────────────────────

    /** Returns per-slot max stack override, or 0 if using vanilla default. */
    public int getMaxStackSize() { return backend.getMaxStackSize(); }
    public void setMaxStackSize(int max) { backend.setMaxStackSize(max); }

    /** Returns the effective max stack size — per-slot override or the given default. */
    public int getEffectiveMaxStackSize(int vanillaDefault) {
        return backend.getEffectiveMaxStackSize(vanillaDefault);
    }

    // ── Slot Index in Panel (structural) ────────────────────────────────

    public int getSlotIndexInPanel() { return slotIndexInPanel; }
    public void setSlotIndexInPanel(int index) { this.slotIndexInPanel = index; }

    // ── Empty Slot Click (structural — interaction callback) ────────────

    public @Nullable Consumer<Slot> getOnEmptyClick() { return onEmptyClick; }
    public void setOnEmptyClick(@Nullable Consumer<Slot> callback) { this.onEmptyClick = callback; }

    // ── Empty Slot Tooltip (structural) ─────────────────────────────────

    public @Nullable Supplier<Component> getEmptyTooltip() { return emptyTooltip; }
    public void setEmptyTooltip(@Nullable Supplier<Component> tooltip) { this.emptyTooltip = tooltip; }

    // ── Persistence (→ backend) ─────────────────────────────────────────

    /** Returns the persistence mode, or null if not set. */
    public @Nullable Persistence getPersistence() { return backend.getPersistence(); }
    public void setPersistence(@Nullable Persistence persistence) { backend.setPersistence(persistence); }

    /** Convenience: true if this slot's persistence is OUTPUT (take-only). */
    public boolean isPersistenceOutput() {
        return backend.isPersistenceOutput();
    }

    // ── Visual Decorations (→ frontend) ─────────────────────────────────

    /** Returns the ARGB background tint color, or 0 if no tint. */
    public int getBackgroundTint() { return frontend.getBackgroundTint(); }

    /** Sets the ARGB background tint. 0 clears the tint. Rendered BEHIND the item. */
    public void setBackgroundTint(int argb) { frontend.setBackgroundTint(argb); }

    /** Returns the overlay icon rendered ON TOP of the slot item, or null. */
    public @Nullable Identifier getOverlayIcon() { return frontend.getOverlayIcon(); }

    /** Sets an overlay icon drawn ON TOP of the slot item. null clears it. */
    public void setOverlayIcon(@Nullable Identifier icon) { frontend.setOverlayIcon(icon); }

    /** Returns the ARGB border color, or 0 if no border. */
    public int getBorderColor() { return frontend.getBorderColor(); }

    /** Sets the ARGB border color. 0 clears the border. Rendered ON TOP of the item. */
    public void setBorderColor(int argb) { frontend.setBorderColor(argb); }

    /**
     * Returns true if this slot has any visual decoration set (tint, overlay, or border).
     * Used as a fast gate in the rendering path — undecorated slots skip all
     * decoration logic for zero overhead.
     */
    public boolean hasDecoration() {
        return frontend.hasDecoration();
    }

    // ── Wrapped Slot (structural) ───────────────────────────────────────

    /** Returns the original vanilla slot being wrapped, or null if not a wrapper. */
    public @Nullable Slot getWrappedSlot() { return wrappedSlot; }
    public void setWrappedSlot(@Nullable Slot slot) { this.wrappedSlot = slot; }

    // ── MenuKit-managed (structural) ────────────────────────────────────

    /** Whether this slot was created by MenuKit (vs a vanilla slot with state attached). */
    public boolean isMenuKitSlot() { return menuKitSlot; }
    public void setMenuKitSlot(boolean value) { this.menuKitSlot = value; }

    // ── Panel Visibility Check ──────────────────────────────────────────

    /**
     * Full activity check: slot is active if not disabled, panel is visible,
     * AND element-level visibility is not overridden to hidden.
     */
    public boolean isSlotActive() {
        if (isDisabled()) return false;
        if (panelName != null) {
            if (MenuKit.isPanelInactive(panelName)) return false;
            if (elementId != null) {
                MKPanelState state = MKPanelStateRegistry.get(panelName);
                if (state != null) {
                    Boolean override = state.getVisible(elementId);
                    if (override != null && !override) return false;
                }
            }
        }
        return true;
    }

    // ── Right-Click (structural — interaction callbacks) ────────────────

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
