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
import java.util.function.Supplier;

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
        List<MKTextDef> textDefs,                               // text label blueprints
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
        boolean exclusive,                                      // when visible, suppresses other auto-stacked panels on same side
        @Nullable BooleanSupplier disabledWhen,                 // runtime predicate: panel hidden when true
        LayoutMode layoutMode,                                  // child layout: MANUAL, COLUMN, or ROW (legacy flat path)
        int layoutGap,                                          // gap between children in flow layout (legacy)
        @Nullable MKGroupDef rootGroup,                         // layout tree root (null = legacy flat path)
        boolean rightAligned,                                   // children align right within the panel (auto-derived from posMode)
        boolean shiftClickIn,                                   // items can be shift-clicked INTO this panel's slots (default: false)
        boolean shiftClickOut                                   // items can be shift-clicked OUT OF this panel's slots (default: false)
) {

    // ── Position Mode ──────────────────────────────────────────────────────

    /** How a panel's position is calculated relative to the container. */
    public enum PosMode {
        /** Absolute container-relative coordinates (x, y fields used directly). */
        ABSOLUTE,
        /** Right of container with manual Y offset: x = containerWidth + margin, y = posArg2. */
        RIGHT,
        /** Left of container with manual Y offset: x = -margin - panelWidth, y = posArg2. */
        LEFT,
        /** Above container with manual X offset: x = posArg1, y = -margin - panelHeight. */
        ABOVE,
        /** Below container with manual X offset: x = posArg1, y = containerHeight + margin. */
        BELOW,

        // ── Auto-stacking modes ────────────────────────────────────────────
        // Panels using these modes are automatically stacked in order of
        // registration, with DEFAULT_MARGIN gaps between each panel.

        /** Auto-stack right of container, top-aligned, stacking downward. */
        RIGHT_AUTO,
        /** Auto-stack left of container, top-aligned, stacking downward. */
        LEFT_AUTO,
        /** Auto-stack above container, left-aligned, stacking rightward. */
        ABOVE_LEFT,
        /** Auto-stack above container, right-aligned, stacking leftward. */
        ABOVE_RIGHT,
        /** Auto-stack below container, left-aligned, stacking rightward. */
        BELOW_LEFT,
        /** Auto-stack below container, right-aligned, stacking leftward. */
        BELOW_RIGHT
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
        int m = MKPanel.Builder.DEFAULT_MARGIN;
        return switch (posMode) {
            // Manual modes — explicit gap and offset
            case RIGHT    -> new int[]{ containerWidth + posArg1, posArg2 };
            case LEFT     -> new int[]{ -posArg1 - size[0], posArg2 };
            case ABOVE    -> new int[]{ posArg1, -posArg2 - size[1] };
            case BELOW    -> new int[]{ posArg1, containerHeight + posArg2 };
            case ABSOLUTE -> new int[]{ x, y };
            // Auto-stacking modes — return base anchor position.
            // The actual stacking offset is computed by resolvePositionsWithAvoidance().
            case RIGHT_AUTO  -> new int[]{ containerWidth + m, 0 };
            case LEFT_AUTO   -> new int[]{ -m - size[0], 0 };
            case ABOVE_LEFT  -> new int[]{ 0, -m - size[1] };
            case ABOVE_RIGHT -> new int[]{ containerWidth - size[0], -m - size[1] };
            case BELOW_LEFT  -> new int[]{ 0, containerHeight + m };
            case BELOW_RIGHT -> new int[]{ containerWidth - size[0], containerHeight + m };
        };
    }

    /** Returns true if this panel uses an auto-stacking position mode. */
    public boolean isAutoStacked() {
        return posMode == PosMode.RIGHT_AUTO || posMode == PosMode.LEFT_AUTO
                || posMode == PosMode.ABOVE_LEFT || posMode == PosMode.ABOVE_RIGHT
                || posMode == PosMode.BELOW_LEFT || posMode == PosMode.BELOW_RIGHT;
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
     * @return array of [x, y] pairs — slots at 0..S-1, buttons at S..S+B-1, texts at S+B..S+B+T-1
     */
    public int[][] computeFlowPositions() {
        // If we have a layout tree, delegate to the recursive computation
        if (rootGroup != null) {
            return computeFlowPositionsFromTree();
        }
        // Otherwise use the legacy flat algorithm
        return computeFlowPositionsFlat();
    }

    /** Recursive tree-based layout computation. */
    private int[][] computeFlowPositionsFromTree() {
        int total = slotDefs.size() + buttonDefs.size() + textDefs.size();
        int[][] positions = new int[total][2];
        // Initialize all to disabled
        for (int[] pos : positions) { pos[0] = -9999; pos[1] = -9999; }
        // Recursive layout fills in enabled positions
        int[] counters = { 0, 0, 0 }; // slot, button, text
        rootGroup.computeLayout(positions, counters,
                slotDefs.size(), buttonDefs.size(),
                0, 0, false, rightAligned);
        return positions;
    }

    /** Legacy flat layout algorithm (for panels without groups). */
    private int[][] computeFlowPositionsFlat() {
        int total = slotDefs.size() + buttonDefs.size() + textDefs.size();
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
            for (int i = 0; i < textDefs.size(); i++) {
                MKTextDef text = textDefs.get(i);
                if (text.disabledWhen() != null && text.disabledWhen().getAsBoolean()) {
                    positions[slotDefs.size() + buttonDefs.size() + i] = new int[]{ -9999, -9999 };
                } else {
                    positions[slotDefs.size() + buttonDefs.size() + i] = new int[]{ text.childX(), text.childY() };
                }
            }

            return positions;
        }

        // Flow layout: COLUMN (top-to-bottom) or ROW (left-to-right)
        int cursor = 0;
        boolean hasActiveChild = false;

        // Text labels first (typically headers at the top)
        for (int i = 0; i < textDefs.size(); i++) {
            MKTextDef text = textDefs.get(i);
            if (text.disabledWhen() != null && text.disabledWhen().getAsBoolean()) {
                positions[slotDefs.size() + buttonDefs.size() + i] = new int[]{ -9999, -9999 };
                continue;
            }
            if (hasActiveChild) cursor += layoutGap;
            hasActiveChild = true;

            if (layoutMode == LayoutMode.COLUMN) {
                positions[slotDefs.size() + buttonDefs.size() + i] = new int[]{ 0, cursor };
                cursor += MKTextDef.TEXT_HEIGHT;
            } else { // ROW
                positions[slotDefs.size() + buttonDefs.size() + i] = new int[]{ cursor, 0 };
                cursor += text.estimateWidth();
            }
        }

        // Slots (18×18 each)
        for (int i = 0; i < slotDefs.size(); i++) {
            MKSlotDef slot = slotDefs.get(i);
            if (slot.disabledWhen() != null && slot.disabledWhen().getAsBoolean()) {
                positions[i] = new int[]{ -9999, -9999 };
                continue;
            }
            if (hasActiveChild) cursor += layoutGap;
            hasActiveChild = true;

            // +1 offset: vanilla slot border renders at (x-1,y-1),
            // bake the compensation into the position itself.
            if (layoutMode == LayoutMode.COLUMN) {
                positions[i] = new int[]{ 1, cursor + 1 };
                cursor += 18;
            } else { // ROW
                positions[i] = new int[]{ cursor + 1, 1 };
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
        if (!autoSize || (slotDefs.isEmpty() && buttonDefs.isEmpty() && textDefs.isEmpty())) {
            return new int[]{ width, height };
        }

        int ep = effectivePadding();

        // Tree-based layout: the root group already computes exact content size.
        // Slot content offset (+1) is already baked into positions by the layout engine.
        if (rootGroup != null) {
            int total = slotDefs.size() + buttonDefs.size() + textDefs.size();
            int[][] positions = new int[total][2];
            for (int[] pos : positions) { pos[0] = -9999; pos[1] = -9999; }
            int[] counters = { 0, 0, 0 };
            int[] contentSize = rootGroup.computeLayout(positions, counters,
                    slotDefs.size(), buttonDefs.size(), 0, 0, false, rightAligned);
            return new int[]{
                    contentSize[0] + ep * 2,
                    contentSize[1] + ep * 2
            };
        }

        // Legacy flat layout: derive size from element positions.
        // Slot content offset (+1) is already baked into flowPos.
        int[][] flowPos = computeFlowPositions();
        int maxRight = 0;
        int maxBottom = 0;
        boolean hasActiveElements = false;

        for (int i = 0; i < slotDefs.size(); i++) {
            if (flowPos[i][0] == -9999) continue;
            maxRight = Math.max(maxRight, flowPos[i][0] + 18);
            maxBottom = Math.max(maxBottom, flowPos[i][1] + 18);
            hasActiveElements = true;
        }

        for (int i = 0; i < buttonDefs.size(); i++) {
            int fi = slotDefs.size() + i;
            if (flowPos[fi][0] == -9999) continue;
            MKButtonDef btn = buttonDefs.get(i);
            maxRight = Math.max(maxRight, flowPos[fi][0] + estimateButtonWidth(btn));
            maxBottom = Math.max(maxBottom, flowPos[fi][1] + estimateButtonHeight(btn));
            hasActiveElements = true;
        }

        for (int i = 0; i < textDefs.size(); i++) {
            int fi = slotDefs.size() + buttonDefs.size() + i;
            if (flowPos[fi][0] == -9999) continue;
            MKTextDef text = textDefs.get(i);
            maxRight = Math.max(maxRight, flowPos[fi][0] + text.estimateWidth());
            maxBottom = Math.max(maxBottom, flowPos[fi][1] + MKTextDef.TEXT_HEIGHT);
            hasActiveElements = true;
        }

        if (!hasActiveElements) return new int[]{ 0, 0 };

        return new int[]{
                maxRight + ep * 2,
                maxBottom + ep * 2
        };
    }
}
