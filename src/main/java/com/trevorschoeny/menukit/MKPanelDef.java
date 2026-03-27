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
        boolean shiftClickOut,                                  // items can be shift-clicked OUT OF this panel's slots (default: false)
        @Nullable MKRegionFollowDef followsRegion               // if set, panel auto-positions itself relative to a named MKRegion
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
        BELOW_RIGHT,

        // ── Hotbar-relative mode ─────────────────────────────────────────
        // Positions the panel relative to the hotbar row. MenuKit resolves
        // the hotbar's X/Y per context so consumers don't need to know
        // screen-specific coordinates.
        //
        // posArg1 = X offset from hotbar left edge (px)
        // posArg2 = Y offset from hotbar top edge (px)
        //   Positive Y = below hotbar, negative Y = above hotbar

        /** Position relative to the hotbar row's top-left corner. */
        HOTBAR_RELATIVE
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
     * Checks per-context overrides first, then handles context-dependent
     * modes (like HOTBAR_RELATIVE), then falls back to the dimension-based resolver.
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

        // HOTBAR_RELATIVE needs per-context hotbar data that the
        // dimension-only overload can't provide — resolve it here.
        if (posMode == PosMode.HOTBAR_RELATIVE) {
            // posArg1 = X offset from hotbar left edge
            // posArg2 = Y offset from hotbar top edge
            int hotbarX = MKContextLayout.getHotbarX(context);
            int hotbarY = MKContextLayout.getHotbarY(context);
            return new int[]{ hotbarX + posArg1, hotbarY + posArg2 };
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
            // HOTBAR_RELATIVE is normally resolved by the context-aware overload.
            // Fallback: treat offsets as absolute (best effort without context data).
            case HOTBAR_RELATIVE -> new int[]{ posArg1, posArg2 };
        };
    }

    /** Returns true if this panel uses an auto-stacking position mode. */
    public boolean isAutoStacked() {
        return posMode == PosMode.RIGHT_AUTO || posMode == PosMode.LEFT_AUTO
                || posMode == PosMode.ABOVE_LEFT || posMode == PosMode.ABOVE_RIGHT
                || posMode == PosMode.BELOW_LEFT || posMode == PosMode.BELOW_RIGHT;
    }

    /**
     * Returns true if this panel should auto-position itself relative to a
     * named {@link MKRegion} each frame. The panel hides automatically when
     * the target region is not present in the active menu.
     */
    public boolean isRegionFollowing() {
        return followsRegion != null;
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
     * each call — disabled children are marked inactive (active[i] = false)
     * with positions left at {0, 0}, and don't affect the flow cursor.
     *
     * <p>For {@code MANUAL} mode, returns original childX/childY unchanged.
     * For {@code COLUMN}/{@code ROW}, positions are computed sequentially with
     * the configured gap between active elements.
     *
     * @return layout result with positions and active flags for each element
     */
    public MKLayoutResult computeFlowPositions() {
        // If we have a layout tree, delegate to the recursive computation
        if (rootGroup != null) {
            return computeFlowPositionsFromTree();
        }
        // Otherwise use the legacy flat algorithm
        return computeFlowPositionsFlat();
    }

    /** Recursive tree-based layout computation. */
    private MKLayoutResult computeFlowPositionsFromTree() {
        // Count elements from the ACTUAL tree, not the original def lists.
        // Conditional element rules can insert children (e.g., sort buttons)
        // after the panel is built, so the tree may have more elements than
        // slotDefs + buttonDefs + textDefs.
        int[] typeCounts = MKGroupDef.countElementsByType(rootGroup);
        int treeSlots = typeCounts[0];
        int treeButtons = typeCounts[1];
        int treeTexts = typeCounts[2];
        int total = treeSlots + treeButtons + treeTexts;

        int[][] positions = new int[total][2];
        boolean[] active = new boolean[total];
        // Initialize all positions to {0, 0} and inactive
        // Recursive layout fills in enabled positions and marks them active
        int[] counters = { 0, 0, 0 }; // slot, button, text
        int[] contentSize = rootGroup.computeLayout(positions, active, counters,
                treeSlots, treeButtons,
                0, 0, false, rightAligned, name());
        return new MKLayoutResult(positions, active, contentSize[0], contentSize[1]);
    }

    /**
     * Returns all button definitions from the rootGroup tree in depth-first
     * traversal order. This captures conditionally-injected buttons that aren't
     * in the original flat {@link #buttonDefs()} list.
     *
     * <p>Falls back to {@link #buttonDefs()} if no rootGroup exists (legacy panels).
     */
    List<MKButtonDef> getTreeButtonDefs() {
        if (rootGroup == null) return buttonDefs;
        List<MKButtonDef> treeDefs = new java.util.ArrayList<>();
        MKGroupDef.collectButtonDefs(rootGroup, treeDefs);
        return treeDefs;
    }

    /**
     * Returns the number of slots in the rootGroup tree (not the original flat list).
     * Used as the index offset for buttons in tree-based layout results.
     *
     * <p>Falls back to {@code slotDefs.size()} if no rootGroup exists.
     */
    int getTreeSlotCount() {
        if (rootGroup == null) return slotDefs.size();
        return MKGroupDef.countElementsByType(rootGroup)[0];
    }

    /** Legacy flat layout algorithm (for panels without groups). */
    private MKLayoutResult computeFlowPositionsFlat() {
        int total = slotDefs.size() + buttonDefs.size() + textDefs.size();
        int[][] positions = new int[total][2];
        boolean[] active = new boolean[total];

        if (layoutMode == LayoutMode.MANUAL) {
            // Manual: use original positions from defs, but still respect disabledWhen
            for (int i = 0; i < slotDefs.size(); i++) {
                MKSlotDef slot = slotDefs.get(i);
                if (slot.disabledWhen() != null && slot.disabledWhen().getAsBoolean()) {
                    // active[i] remains false, position stays {0, 0}
                } else {
                    positions[i] = new int[]{ slot.childX(), slot.childY() };
                    active[i] = true;
                }
            }
            for (int i = 0; i < buttonDefs.size(); i++) {
                MKButtonDef btn = buttonDefs.get(i);
                int idx = slotDefs.size() + i;
                if (btn.disabledWhen() != null && btn.disabledWhen().getAsBoolean()) {
                    // inactive
                } else {
                    positions[idx] = new int[]{ btn.childX(), btn.childY() };
                    active[idx] = true;
                }
            }
            for (int i = 0; i < textDefs.size(); i++) {
                MKTextDef text = textDefs.get(i);
                int idx = slotDefs.size() + buttonDefs.size() + i;
                if (text.disabledWhen() != null && text.disabledWhen().getAsBoolean()) {
                    // inactive
                } else {
                    positions[idx] = new int[]{ text.childX(), text.childY() };
                    active[idx] = true;
                }
            }

            // Compute content dimensions from active elements
            int contentW = 0, contentH = 0;
            for (int i = 0; i < total; i++) {
                if (!active[i]) continue;
                int w = 0, h = 0;
                if (i < slotDefs.size()) { w = 18; h = 18; }
                else if (i < slotDefs.size() + buttonDefs.size()) {
                    int bi = i - slotDefs.size();
                    w = estimateButtonWidth(buttonDefs.get(bi));
                    h = estimateButtonHeight(buttonDefs.get(bi));
                } else {
                    int ti = i - slotDefs.size() - buttonDefs.size();
                    w = textDefs.get(ti).estimateWidth();
                    h = MKTextDef.TEXT_HEIGHT;
                }
                contentW = Math.max(contentW, positions[i][0] + w);
                contentH = Math.max(contentH, positions[i][1] + h);
            }

            return new MKLayoutResult(positions, active, contentW, contentH);
        }

        // Flow layout: COLUMN (top-to-bottom) or ROW (left-to-right)
        int cursor = 0;
        boolean hasActiveChild = false;

        // Text labels first (typically headers at the top)
        for (int i = 0; i < textDefs.size(); i++) {
            MKTextDef text = textDefs.get(i);
            int idx = slotDefs.size() + buttonDefs.size() + i;
            if (text.disabledWhen() != null && text.disabledWhen().getAsBoolean()) {
                // inactive — position stays {0, 0}
                continue;
            }
            if (hasActiveChild) cursor += layoutGap;
            hasActiveChild = true;
            active[idx] = true;

            if (layoutMode == LayoutMode.COLUMN) {
                positions[idx] = new int[]{ 0, cursor };
                cursor += MKTextDef.TEXT_HEIGHT;
            } else { // ROW
                positions[idx] = new int[]{ cursor, 0 };
                cursor += text.estimateWidth();
            }
        }

        // Slots (18x18 each)
        for (int i = 0; i < slotDefs.size(); i++) {
            MKSlotDef slot = slotDefs.get(i);
            if (slot.disabledWhen() != null && slot.disabledWhen().getAsBoolean()) {
                // inactive
                continue;
            }
            if (hasActiveChild) cursor += layoutGap;
            hasActiveChild = true;
            active[i] = true;

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

        // Buttons (def width/height or 20x20 default)
        for (int i = 0; i < buttonDefs.size(); i++) {
            MKButtonDef btn = buttonDefs.get(i);
            int idx = slotDefs.size() + i;
            if (btn.disabledWhen() != null && btn.disabledWhen().getAsBoolean()) {
                // inactive
                continue;
            }
            if (hasActiveChild) cursor += layoutGap;
            hasActiveChild = true;
            active[idx] = true;

            int btnW = estimateButtonWidth(btn);
            int btnH = estimateButtonHeight(btn);

            if (layoutMode == LayoutMode.COLUMN) {
                positions[idx] = new int[]{ 0, cursor };
                cursor += btnH;
            } else { // ROW
                positions[idx] = new int[]{ cursor, 0 };
                cursor += btnW;
            }
        }

        // Compute content dimensions
        int contentW = 0, contentH = 0;
        for (int i = 0; i < total; i++) {
            if (!active[i]) continue;
            int w = 0, h = 0;
            if (i < slotDefs.size()) { w = 18; h = 18; }
            else if (i < slotDefs.size() + buttonDefs.size()) {
                int bi = i - slotDefs.size();
                w = estimateButtonWidth(buttonDefs.get(bi));
                h = estimateButtonHeight(buttonDefs.get(bi));
            } else {
                int ti = i - slotDefs.size() - buttonDefs.size();
                w = textDefs.get(ti).estimateWidth();
                h = MKTextDef.TEXT_HEIGHT;
            }
            contentW = Math.max(contentW, positions[i][0] + w);
            contentH = Math.max(contentH, positions[i][1] + h);
        }

        return new MKLayoutResult(positions, active, contentW, contentH);
    }

    // ── Size Computation ───────────────────────────────────────────────────

    /**
     * Calculates the auto-sized dimensions from flow-computed child positions.
     * Evaluates {@code disabledWhen} predicates each call, so the panel
     * shrinks dynamically when elements are disabled.
     * Returns {width, height}. If not auto-sized, returns the explicit dimensions.
     */
    public int[] computeSize() {
        if (!autoSize || (slotDefs.isEmpty() && buttonDefs.isEmpty() && textDefs.isEmpty()
                && rootGroup == null)) {
            return new int[]{ width, height };
        }

        int ep = effectivePadding();

        // Both tree-based and flat layouts now go through computeFlowPositions(),
        // which returns an MKLayoutResult with content dimensions already computed.
        MKLayoutResult layout = computeFlowPositions();

        // Check if there are any active elements
        boolean hasActiveElements = false;
        for (int i = 0; i < layout.active().length; i++) {
            if (layout.isActive(i)) { hasActiveElements = true; break; }
        }
        if (!hasActiveElements) return new int[]{ 0, 0 };

        return new int[]{
                layout.contentWidth() + ep * 2,
                layout.contentHeight() + ep * 2
        };
    }
}
