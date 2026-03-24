package com.trevorschoeny.menukit;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.player.Player;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Immutable blueprint for an {@link MKSlot}. Created at mod init time by
 * {@link MKPanel.Builder#slot}, materialized into a live MKSlot during
 * menu construction.
 *
 * <p>Each slot references a {@link MKContainerDef} by name and specifies
 * which index within that container it displays. Multiple slots (even
 * across different panels) can reference the same container — like
 * windows into the same storage.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public record MKSlotDef(
        int childX,                                 // panel-relative x
        int childY,                                 // panel-relative y
        String containerName,                       // name of the MKContainerDef this slot reads from
        int containerIndex,                         // index within that container
        @Nullable Predicate<ItemStack> filter,      // item type restriction
        int maxStack,                               // max stack size for this slot
        @Nullable Supplier<Identifier> ghostIcon,   // icon shown when slot is empty
        @Nullable BooleanSupplier disabledWhen,      // runtime predicate: slot hidden when true
        int vanillaInventoryIndex,                   // >=0: use player.getInventory() at this index instead of MKContainer
        @Nullable Consumer<net.minecraft.world.inventory.Slot> onEmptyClick,     // callback: player clicked empty slot with empty cursor
        @Nullable Supplier<Component> emptyTooltip,  // tooltip: shown when hovering empty slot with empty cursor
        // ── Visual Decorations ───────────────────────────────────────────
        int backgroundTint,                          // ARGB tint rendered BEHIND the item (0 = none)
        @Nullable Identifier overlayIcon,            // icon rendered ON TOP of the slot item
        int borderColor                              // ARGB border drawn ON TOP of the item (0 = none)
) {

    /** Whether this slot mirrors a vanilla Inventory slot (hotbar, armor, etc.). */
    public boolean isVanillaSlot() { return vanillaInventoryIndex >= 0; }

    /**
     * Creates a live {@link MKSlot} from this definition, positioned absolutely
     * based on the parent panel's position and padding.
     *
     * @param containerLookup resolves a container name to a live MKContainer instance
     * @param panelX          the panel's container-relative x position
     * @param panelY          the panel's container-relative y position
     * @param padding         the panel's effective padding (0 for NONE style)
     * @param contentOffset   slot content offset (1 for bordered panels, 0 for NONE)
     * @param panelName       the panel name (used for visibility checks), or null
     * @return a fully configured MKSlot ready to be added to a menu, or null if container not found
     */
    public @Nullable MKSlot createSlot(Function<String, MKContainer> containerLookup,
                                       int panelX, int panelY,
                                       int padding, int contentOffset,
                                       @Nullable String panelName) {
        MKContainer container = containerLookup.apply(containerName);
        if (container == null) {
            MenuKit.LOGGER.warn(
                    "[MenuKit] Slot references container '{}' but it was not found", containerName);
            return null;
        }

        // Content offset aligns the 18×18 slot background (at slotX-1, slotY-1)
        // with the padding boundary. 0 for NONE-style panels (no border).
        int absX = panelX + padding + contentOffset + childX;
        int absY = panelY + padding + contentOffset + childY;
        MKSlot slot = new MKSlot(container, containerIndex, absX, absY,
                filter, maxStack, ghostIcon, disabledWhen);
        if (panelName != null) {
            slot.setPanelName(panelName);
        }
        if (onEmptyClick != null) {
            slot.setOnEmptyClick(onEmptyClick);
        }
        if (emptyTooltip != null) {
            slot.setEmptyTooltip(emptyTooltip);
        }
        // Propagate visual decorations from the blueprint to the runtime state
        applyDecorations(slot);
        return slot;
    }

    /**
     * Creates a live {@link MKSlot} using flow-computed positions instead of
     * this def's childX/childY. Used when the parent panel has a flow layout
     * (COLUMN/ROW) that computes positions dynamically.
     *
     * <p>The slot content offset (+1px for vanilla border rendering) is already
     * baked into flowChildX/flowChildY by the layout engine, so positions
     * are treated uniformly with buttons and text.
     *
     * @param containerLookup resolves a container name to a live MKContainer instance
     * @param panelX          the panel's container-relative x position
     * @param panelY          the panel's container-relative y position
     * @param padding         the panel's effective padding
     * @param flowChildX      flow-computed x position (replaces this.childX)
     * @param flowChildY      flow-computed y position (replaces this.childY)
     * @param panelName       the panel name (used for visibility checks), or null
     * @return a fully configured MKSlot, or null if container not found
     */
    public @Nullable MKSlot createSlotAt(Function<String, MKContainer> containerLookup,
                                          int panelX, int panelY,
                                          int padding,
                                          int flowChildX, int flowChildY,
                                          @Nullable String panelName,
                                          @Nullable Player player) {
        int absX = panelX + padding + flowChildX;
        int absY = panelY + padding + flowChildY;

        MKSlot slot;
        if (isVanillaSlot()) {
            // Vanilla slot — backed by the player's Inventory (hotbar, armor, etc.)
            if (player == null) {
                MenuKit.LOGGER.warn(
                        "[MenuKit] Vanilla slot requires a player reference");
                return null;
            }
            slot = new MKSlot(player.getInventory(), vanillaInventoryIndex, absX, absY,
                    filter, maxStack, ghostIcon, disabledWhen);
        } else {
            // MKContainer slot — backed by a registered container's delegate
            MKContainer mkContainer = containerLookup.apply(containerName);
            if (mkContainer == null) {
                MenuKit.LOGGER.warn(
                        "[MenuKit] Slot references container '{}' but it was not found", containerName);
                return null;
            }
            // Use the delegate Container directly with region-remapped index
            net.minecraft.world.Container delegate = mkContainer.getDelegate();
            int realIndex = mkContainer.getRegion() != null
                    ? mkContainer.getRegion().toContainerIndex(containerIndex)
                    : containerIndex;
            slot = new MKSlot(delegate, realIndex, absX, absY,
                    filter, maxStack, ghostIcon, disabledWhen);
        }

        if (panelName != null) {
            slot.setPanelName(panelName);
        }
        if (onEmptyClick != null) {
            slot.setOnEmptyClick(onEmptyClick);
        }
        if (emptyTooltip != null) {
            slot.setEmptyTooltip(emptyTooltip);
        }
        // Propagate visual decorations from the blueprint to the runtime state
        applyDecorations(slot);
        return slot;
    }

    // ── Decoration Propagation ──────────────────────────────────────────────

    /**
     * Copies visual decoration values from this immutable blueprint into the
     * live slot's MKSlotState. Called by both createSlot and createSlotAt so
     * declaratively-set decorations take effect at runtime.
     */
    private void applyDecorations(MKSlot slot) {
        if (backgroundTint == 0 && overlayIcon == null && borderColor == 0) return;

        MKSlotState state = MKSlotStateRegistry.getOrCreate(slot);
        if (backgroundTint != 0) state.setBackgroundTint(backgroundTint);
        if (overlayIcon != null) state.setOverlayIcon(overlayIcon);
        if (borderColor != 0)    state.setBorderColor(borderColor);
    }
}
