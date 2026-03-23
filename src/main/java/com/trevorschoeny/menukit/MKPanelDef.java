package com.trevorschoeny.menukit;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Immutable definition of a panel — its contexts, position, style, slot blueprints,
 * button blueprints, and persistence callbacks. Created once at mod init
 * by {@link MKPanel.Builder#build()}, stored in {@link MenuKit}'s registry.
 *
 * <p>Live objects (MKContainer, MKSlot, MKButton) are created per-player
 * from this definition during menu construction and screen initialization.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public record MKPanelDef(
        String name,                                            // unique ID + NBT persist key
        Set<MKContext> contexts,                                 // which contexts this panel appears in
        int x, int y,                                           // container-relative position (used for ABSOLUTE mode)
        int width, int height,                                  // explicit size (0 = auto-sized)
        int padding,                                            // inner padding before children
        boolean autoSize,                                       // calculate size from children
        MKPanel.Style style,                                    // visual style (RAISED/DARK/INSET/FLAT/CUSTOM/NONE)
        @Nullable Identifier customSprite,                      // user-provided 9-slice sprite (for CUSTOM style)
        List<MKSlotDef> slotDefs,                               // slot blueprints
        List<MKButtonDef> buttonDefs,                           // button blueprints
        @Nullable Consumer<ValueOutput> onSave,                 // custom persistence: save hook
        @Nullable Consumer<ValueInput> onLoad,                  // custom persistence: load hook
        PosMode posMode,                                        // how to resolve position
        int posArg1, int posArg2,                               // meaning depends on posMode
        Map<MKContext, int[]> posOverrides,                     // per-context absolute position overrides
        boolean isStandaloneScreen,                             // opens as its own screen (not attached to an existing menu)
        @Nullable Component title,                              // screen title (standalone screens)
        int screenWidth, int screenHeight,                      // screen dimensions (standalone screens)
        boolean startHidden,                                    // starts hidden (panel visibility)
        boolean includePlayerInventory,                         // standalone screen: show player inventory at bottom
        boolean allowOverlap,                                   // disables automatic collision avoidance
        @Nullable BooleanSupplier disabledWhen,                 // runtime predicate: panel hidden when true
        LayoutMode layoutMode,                                  // child layout: MANUAL, COLUMN, or ROW
        int layoutGap                                           // gap between children in flow layout
) {

    // ── Position Mode ──────────────────────────────────────────────────────

    /** How a panel's position is calculated relative to the container. */
    public enum PosMode {
        /** Absolute container-relative coordinates (x, y fields used directly). */
        ABSOLUTE,
        /** Right of container: x = containerWidth + posArg1, y = posArg2. */
        RIGHT,
        /** Left of container: x = -posArg1 - panelWidth, y = posArg2. */
        LEFT,
        /** Above container: x = posArg1, y = -posArg2 - panelHeight. */
        ABOVE,
        /** Below container: x = posArg1, y = containerHeight + posArg2. */
        BELOW
    }

    // ── Layout Mode ──────────────────────────────────────────────────────

    /** How a panel's children are positioned. */
    public enum LayoutMode {
        /** Manual — children use their explicit childX/childY coordinates. */
        MANUAL,
        /** Column — children flow top-to-bottom, positions computed dynamically. */
        COLUMN,
        /** Row — children flow left-to-right, positions computed dynamically. */
        ROW
    }

    // ── Context Matching ─────────────────────────────────────────────────────

    /** Returns true if this panel should appear in the given context. */
    public boolean appliesTo(MKContext context) {
        return contexts.contains(context);
    }

    /** Returns true if any of this panel's contexts map to the given menu class. */
    public boolean needsMenuClass(Class<? extends AbstractContainerMenu> cls) {
        for (MKContext ctx : contexts) {
            if (ctx.menuClass() == cls) return true;
        }
        return false;
    }

    // ── Effective Padding / Content Offset ──────────────────────────────────

    /**
     * Returns the effective padding for this panel.
     * NONE-style panels return 0 (no visual border = no padding needed).
     */
    public int effectivePadding() {
        return style == MKPanel.Style.NONE ? 0 : padding;
    }

    /**
     * Returns the content offset for slots within this panel.
     * Slots always get +1 because the 18×18 slot background renders at
     * (slotX-1, slotY-1) — the +1 prevents the background from extending
     * past the panel's left/top edge.
     */
    public int slotContentOffset() {
        return 1;
    }

    // ── Position Resolution ────────────────────────────────────────────────

    /**
     * Resolves the panel's position for the given context.
     * Checks per-context overrides first, then falls back to the default posMode.
     *
     * @param context the active context (provides container width/height)
     * @return int[]{x, y} container-relative position
     */
    public int[] resolvePosition(MKContext context) {
        // Check for per-context absolute override
        int[] override = posOverrides.get(context);
        if (override != null) {
            return new int[]{ override[0], override[1] };
        }

        // Fall back to default posMode using this context's container dimensions
        return resolvePosition(context.containerWidth(), context.containerHeight());
    }

    /**
     * Resolves position using explicit container dimensions.
     * Used internally and by standalone screens (which don't have an MKContext).
     */
    public int[] resolvePosition(int containerWidth, int containerHeight) {
        int[] size = computeSize();
        // -1 on each relative mode so the panel sits 1px closer to the container edge
        // (visually aligns the panel border with the container border)
        return switch (posMode) {
            case RIGHT    -> new int[]{ containerWidth + posArg1 - 1, posArg2 };
            case LEFT     -> new int[]{ -posArg1 - size[0] + 1, posArg2 };
            case ABOVE    -> new int[]{ posArg1, -posArg2 - size[1] + 1 };
            case BELOW    -> new int[]{ posArg1, containerHeight + posArg2 - 1 };
            case ABSOLUTE -> new int[]{ x, y };
        };
    }

    // ── Button Size Estimation ─────────────────────────────────────────────

    /**
     * Estimates a button's width when it's auto-sized (width=0 in the def).
     * Uses label text length × average character width + padding.
     * Not pixel-perfect, but much better than the 20px fallback.
     */
    static int estimateButtonWidth(MKButtonDef btn) {
        if (btn.width() > 0) return btn.width();
        int textWidth = btn.label() != null ? btn.label().getString().length() * 6 : 0;
        int iconWidth = btn.icon() != null ? btn.iconSize() + 4 : 0; // icon + gap
        return Math.max(20, textWidth + iconWidth + 8); // 4px padding each side
    }

    static int estimateButtonHeight(MKButtonDef btn) {
        if (btn.height() > 0) return btn.height();
        return 20; // vanilla default button height
    }

    // ── Flow Layout ───────────────────────────────────────────────────────

    /**
     * Computes dynamic positions for all children (slots first, then buttons)
     * based on the panel's layout mode. Evaluates {@code disabledWhen} predicates
     * each call — disabled children get sentinel positions (-9999) and don't
     * affect the flow cursor.
     *
     * <p>For {@code MANUAL} mode, returns original childX/childY unchanged.
     * For {@code COLUMN}/{@code ROW}, positions are computed sequentially with
     * the configured gap between active elements.
     *
     * @return array of [x, y] pairs — slots at indices 0..N-1, buttons at N..N+M-1
     */
    public int[][] computeFlowPositions() {
        int total = slotDefs.size() + buttonDefs.size();
        int[][] positions = new int[total][2];

        if (layoutMode == LayoutMode.MANUAL) {
            // Manual: use original positions from defs, but still respect disabledWhen
            for (int i = 0; i < slotDefs.size(); i++) {
                MKSlotDef slot = slotDefs.get(i);
                if (slot.disabledWhen() != null && slot.disabledWhen().getAsBoolean()) {
                    positions[i] = new int[]{ -9999, -9999 };
                } else {
                    positions[i] = new int[]{ slot.childX(), slot.childY() };
                }
            }
            for (int i = 0; i < buttonDefs.size(); i++) {
                MKButtonDef btn = buttonDefs.get(i);
                if (btn.disabledWhen() != null && btn.disabledWhen().getAsBoolean()) {
                    positions[slotDefs.size() + i] = new int[]{ -9999, -9999 };
                } else {
                    positions[slotDefs.size() + i] = new int[]{ btn.childX(), btn.childY() };
                }
            }
            return positions;
        }

        // Flow layout: COLUMN (top-to-bottom) or ROW (left-to-right)
        int cursor = 0;
        boolean hasActiveChild = false;

        // Slots (18×18 each)
        for (int i = 0; i < slotDefs.size(); i++) {
            MKSlotDef slot = slotDefs.get(i);
            if (slot.disabledWhen() != null && slot.disabledWhen().getAsBoolean()) {
                positions[i] = new int[]{ -9999, -9999 };
                continue;
            }
            // Add gap before this child (not before the first one)
            if (hasActiveChild) cursor += layoutGap;
            hasActiveChild = true;

            if (layoutMode == LayoutMode.COLUMN) {
                positions[i] = new int[]{ 0, cursor };
                cursor += 18;
            } else { // ROW
                positions[i] = new int[]{ cursor, 0 };
                cursor += 18;
            }
        }

        // Buttons (def width/height or 20×20 default)
        for (int i = 0; i < buttonDefs.size(); i++) {
            MKButtonDef btn = buttonDefs.get(i);
            if (btn.disabledWhen() != null && btn.disabledWhen().getAsBoolean()) {
                positions[slotDefs.size() + i] = new int[]{ -9999, -9999 };
                continue;
            }
            if (hasActiveChild) cursor += layoutGap;
            hasActiveChild = true;

            int btnW = estimateButtonWidth(btn);
            int btnH = estimateButtonHeight(btn);

            if (layoutMode == LayoutMode.COLUMN) {
                positions[slotDefs.size() + i] = new int[]{ 0, cursor };
                cursor += btnH;
            } else { // ROW
                positions[slotDefs.size() + i] = new int[]{ cursor, 0 };
                cursor += btnW;
            }
        }

        return positions;
    }

    // ── Size Computation ───────────────────────────────────────────────────

    /**
     * Calculates the auto-sized dimensions from flow-computed child positions.
     * Evaluates {@code disabledWhen} predicates each call, so the panel
     * shrinks dynamically when elements are disabled.
     * Returns {width, height}. If not auto-sized, returns the explicit dimensions.
     */
    public int[] computeSize() {
        if (!autoSize || (slotDefs.isEmpty() && buttonDefs.isEmpty())) {
            return new int[]{ width, height };
        }

        int[][] flowPos = computeFlowPositions();
        int maxRight = 0;
        int maxBottom = 0;
        boolean hasActiveSlots = false;
        boolean hasActiveElements = false;

        // Slots are 18×18 — offset by slotContentOffset() relative to buttons,
        // so add that offset to their extents for accurate sizing
        int so = slotContentOffset();
        for (int i = 0; i < slotDefs.size(); i++) {
            if (flowPos[i][0] == -9999) continue; // disabled
            maxRight = Math.max(maxRight, flowPos[i][0] + so + 18);
            maxBottom = Math.max(maxBottom, flowPos[i][1] + so + 18);
            hasActiveElements = true;
        }

        // Buttons — no content offset
        for (int i = 0; i < buttonDefs.size(); i++) {
            int fi = slotDefs.size() + i;
            if (flowPos[fi][0] == -9999) continue; // disabled
            MKButtonDef btn = buttonDefs.get(i);
            int btnW = estimateButtonWidth(btn);
            int btnH = estimateButtonHeight(btn);
            maxRight = Math.max(maxRight, flowPos[fi][0] + btnW);
            maxBottom = Math.max(maxBottom, flowPos[fi][1] + btnH);
            hasActiveElements = true;
        }

        // No active elements → zero size (panel won't render)
        if (!hasActiveElements) return new int[]{ 0, 0 };

        int ep = effectivePadding();
        return new int[]{
                maxRight + ep * 2,
                maxBottom + ep * 2
        };
    }
}
