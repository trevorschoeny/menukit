package com.trevorschoeny.menukit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The primary entry point for the <b>MenuKit</b> framework.
 *
 * <p>Declare a panel with slots and buttons in a single builder chain.
 * MenuKit handles everything — menu injection, screen widgets, rendering,
 * persistence, and client/server sync. No mixins, no lifecycle management.
 *
 * <h3>Attached Panel (existing menu)</h3>
 * <pre>{@code
 * MKPanel.builder("my_equipment")
 *     .showIn(MKContext.SURVIVAL_INVENTORY, MKContext.CREATIVE_INVENTORY)
 *     .pos(180, 30)
 *     .padding(4)
 *     .autoSize()
 *     .style(MKPanel.Style.RAISED)
 *     .slot(0, 0)
 *         .filter(stack -> stack.is(Items.ELYTRA))
 *         .maxStack(1)
 *         .done()
 *     .button(0, 22)
 *         .label("Config")
 *         .onClick(btn -> openConfig())
 *         .done()
 *     .build();
 * }</pre>
 *
 * <h3>Standalone Screen</h3>
 * <pre>{@code
 * MKPanel.builder("my_settings")
 *     .screen()
 *     .title("Settings")
 *     .screenSize(176, 120)
 *     .slot(0, 0).done()
 *     .button(0, 22).label("Save").onClick(btn -> save()).done()
 *     .build();
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKPanel {

    // ── Panel rendering colors ──────────────────────────────────────────────

    private static final int COLOR_BASE      = 0xFFC6C6C6;
    private static final int COLOR_HIGHLIGHT  = 0xFFFFFFFF;
    private static final int COLOR_SHADOW     = 0xFF555555;
    private static final int COLOR_BORDER     = 0xFF000000;

    /** Visual style for panel backgrounds. */
    public enum Style {
        /** Raised — vanilla inventory panel look (9-slice sprite with rounded corners). */
        RAISED,
        /** Dark — dark translucent panel like the effects/status background. */
        DARK,
        /** Inset — dark top/left, light bottom/right. Like a pressed button. */
        INSET,
        /** Custom — user-provided 9-slice sprite. Set via {@code .customSprite(Identifier)}. */
        CUSTOM,
        /** None — invisible background. Panel is a pure positioning/grouping tool. */
        NONE
    }

    // No public constructor — use builder()
    private MKPanel() {}

    // ── Static Rendering Utilities ──────────────────────────────────────────
    // Can be used standalone without registering a panel.

    /** Sprite ID for the raised panel — custom 9-slice texture matching inventory style. */
    private static final Identifier PANEL_RAISED_SPRITE =
            Identifier.fromNamespaceAndPath("menukit", "panel_raised");

    /** Sprite ID for the dark panel — vanilla's effect background. */
    private static final Identifier PANEL_DARK_SPRITE =
            Identifier.withDefaultNamespace("container/inventory/effect_background");

    /**
     * Renders a vanilla-style panel background at the given position and size.
     * RAISED and DARK styles use 9-slice sprites. Other styles use programmatic rendering.
     */
    public static void renderPanel(GuiGraphics graphics, int x, int y,
                                   int w, int h, Style style) {
        renderPanel(graphics, x, y, w, h, style, null);
    }

    /**
     * Renders a panel background. For sprite-based styles (RAISED, DARK, CUSTOM),
     * uses 9-slice sprite rendering. For CUSTOM, uses the provided customSprite.
     *
     * @param customSprite the sprite Identifier for CUSTOM style (ignored for other styles)
     */
    public static void renderPanel(GuiGraphics graphics, int x, int y,
                                   int w, int h, Style style,
                                   @Nullable Identifier customSprite) {
        if (w <= 0 || h <= 0) return;
        if (style == Style.NONE) return;

        // Sprite-based styles — use vanilla's 9-slice blitSprite
        if (style == Style.RAISED) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    PANEL_RAISED_SPRITE, x, y, w, h);
            return;
        }
        if (style == Style.DARK) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    PANEL_DARK_SPRITE, x, y, w, h);
            return;
        }
        if (style == Style.CUSTOM && customSprite != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    customSprite, x, y, w, h);
            return;
        }

        // Programmatic styles (INSET, FLAT)
        renderPanel(graphics, x, y, w, h, style,
                COLOR_BASE, COLOR_HIGHLIGHT, COLOR_SHADOW);
    }

    /**
     * Renders a panel background with custom colors and default black border.
     * Used internally by {@link MKButton} for state-dependent rendering.
     */
    public static void renderPanel(GuiGraphics graphics, int x, int y,
                                   int w, int h, Style style,
                                   int baseColor, int highlightColor,
                                   int shadowColor) {
        renderPanel(graphics, x, y, w, h, style, baseColor, highlightColor, shadowColor, COLOR_BORDER);
    }

    /**
     * Renders a panel background with custom colors and custom border color.
     */
    public static void renderPanel(GuiGraphics graphics, int x, int y,
                                   int w, int h, Style style,
                                   int baseColor, int highlightColor,
                                   int shadowColor, int borderColor) {
        if (w <= 0 || h <= 0) return;
        if (style == Style.NONE) return;  // invisible panel — render nothing

        int topLeft, bottomRight;
        switch (style) {
            case INSET -> {
                topLeft = shadowColor;
                bottomRight = highlightColor;
            }
            default -> { // RAISED
                topLeft = highlightColor;
                bottomRight = shadowColor;
            }
        }

        // Outer border (1px)
        graphics.fill(x, y, x + w, y + 1, borderColor);                 // top
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);         // bottom
        graphics.fill(x, y + 1, x + 1, y + h - 1, borderColor);         // left
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor); // right

        if (w > 2 && h > 2) {
            // Inner highlight/shadow edges
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, topLeft);
            graphics.fill(x + 1, y + 1, x + 2, y + h - 1, topLeft);
            graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, bottomRight);
            graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, bottomRight);

            if (w > 4 && h > 4) {
                graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, baseColor);
            }

            // Vanilla-style asymmetric corner rounding:
            // Top-left: 1 black dot (just the normal border corner — no extra needed)
            // Top-right: 2 black dots (inner corner pixel set to border color)
            graphics.fill(x + w - 2, y + 1, x + w - 1, y + 2, borderColor);
            // Bottom-left: 2 black dots (inner corner pixel set to border color)
            graphics.fill(x + 1, y + h - 2, x + 2, y + h - 1, borderColor);
            // Bottom-right: 1 black dot + 1 shadow dot (inner corner = shadow gray)
            graphics.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, bottomRight);
        }
    }

    // ── Vanilla slot colors (matched from the inventory texture) ───────────
    private static final int SLOT_FILL      = 0xFF8B8B8B;  // matches vanilla inventory texture slot fill
    private static final int SLOT_SHADOW    = 0xFF373737;  // dark top-left edge
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;  // light bottom-right edge

    /**
     * Renders a vanilla-accurate slot background (18×18 inset square).
     * Matches vanilla exactly — no outer black border, just inner
     * highlight/shadow edges with a medium gray fill.
     */
    public static void renderSlotBackground(GuiGraphics graphics, int x, int y) {
        int w = 18, h = 18;
        // Top and left edge (dark — inset look)
        graphics.fill(x, y, x + w - 1, y + 1, SLOT_SHADOW);       // top
        graphics.fill(x, y, x + 1, y + h - 1, SLOT_SHADOW);       // left
        // Bottom and right edge (light — inset look)
        graphics.fill(x + 1, y + h - 1, x + w, y + h, SLOT_HIGHLIGHT); // bottom
        graphics.fill(x + w - 1, y + 1, x + w, y + h, SLOT_HIGHLIGHT); // right
        // Fill interior (medium gray)
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_FILL);
    }

    // ── Ghost Icons (programmatic pixel art) ────────────────────────────────

    /** Color for ghost icon pixels — darker than slot fill, matching vanilla armor icons. */
    private static final int GHOST_COLOR = 0xFF555555;

    /**
     * Renders a ghost icon at the given position. For known icon paths
     * (elytra, totem), draws a pixel-art outline matching vanilla's armor
     * slot style. For other icons, renders the sprite at reduced opacity
     * as a faded ghost image.
     */
    public static void renderGhostIcon(GuiGraphics graphics, String iconPath, int x, int y) {
        int[][] pixels = getGhostIconPixels(iconPath);
        if (pixels != null) {
            // Known icon — render as pixel outline
            for (int[] px : pixels) {
                graphics.fill(x + px[0], y + px[1], x + px[0] + 1, y + px[1] + 1, GHOST_COLOR);
            }
        }
    }

    /**
     * Returns the pixel coordinates for a ghost icon outline.
     * Each entry is {x, y} relative to the icon's top-left corner (16×16 grid).
     */
    private static int @Nullable [] @Nullable [] getGhostIconPixels(String iconPath) {
        return switch (iconPath) {
            case "container/slot/elytra" -> ELYTRA_OUTLINE;
            case "container/slot/totem" -> TOTEM_OUTLINE;
            case "item/barrier" -> BARRIER_OUTLINE;
            default -> null;
        };
    }

    // Elytra outline — drawn by user in Piskel (v4)
    private static final int[][] ELYTRA_OUTLINE = {
            {4,2}, {5,2}, {6,2}, {7,2}, {8,2}, {9,2}, {10,2}, {11,2},
            {2,3}, {3,3}, {12,3}, {13,3},
            {1,4}, {14,4},
            {1,5}, {7,5}, {8,5}, {14,5},
            {1,6}, {6,6}, {9,6}, {14,6},
            {1,7}, {6,7}, {9,7}, {14,7},
            {1,8}, {6,8}, {9,8}, {14,8},
            {2,9}, {6,9}, {9,9}, {13,9},
            {2,10}, {6,10}, {9,10}, {13,10},
            {2,11}, {6,11}, {9,11}, {13,11},
            {3,12}, {5,12}, {10,12}, {12,12},
            {3,13}, {4,13}, {11,13}, {12,13},
    };

    // Totem outline — drawn by user in Piskel (v2)
    private static final int[][] TOTEM_OUTLINE = {
            {5,1}, {6,1}, {7,1}, {8,1}, {9,1}, {10,1},
            {4,2}, {11,2}, {4,3}, {11,3}, {4,4}, {11,4},
            {4,5}, {11,5}, {4,6}, {11,6}, {4,7}, {11,7},
            {1,8}, {2,8}, {3,8}, {12,8}, {13,8}, {14,8},
            {1,9}, {14,9},
            {2,10}, {3,10}, {4,10}, {11,10}, {12,10}, {13,10},
            {4,11}, {11,11}, {4,12}, {11,12},
            {5,13}, {10,13},
            {6,14}, {7,14}, {8,14}, {9,14},
    };

    // Barrier — filled shape traced from vanilla's barrier.png (16×16).
    // Circle with diagonal slash (🚫), used for disabled slots.
    private static final int[][] BARRIER_OUTLINE = {
            {3,1}, {4,1}, {5,1}, {6,1}, {7,1}, {8,1}, {9,1}, {10,1}, {11,1}, {12,1},
            {2,2}, {3,2}, {4,2}, {5,2}, {6,2}, {7,2}, {8,2}, {9,2}, {10,2}, {11,2}, {12,2}, {13,2},
            {1,3}, {2,3}, {3,3}, {4,3}, {11,3}, {12,3}, {13,3}, {14,3},
            {1,4}, {2,4}, {3,4}, {10,4}, {11,4}, {12,4}, {13,4}, {14,4},
            {1,5}, {2,5}, {9,5}, {10,5}, {11,5}, {13,5}, {14,5},
            {1,6}, {2,6}, {8,6}, {9,6}, {10,6}, {13,6}, {14,6},
            {1,7}, {2,7}, {7,7}, {8,7}, {9,7}, {13,7}, {14,7},
            {1,8}, {2,8}, {6,8}, {7,8}, {8,8}, {13,8}, {14,8},
            {1,9}, {2,9}, {5,9}, {6,9}, {7,9}, {13,9}, {14,9},
            {1,10}, {2,10}, {4,10}, {5,10}, {6,10}, {13,10}, {14,10},
            {1,11}, {2,11}, {3,11}, {4,11}, {5,11}, {12,11}, {13,11}, {14,11},
            {1,12}, {2,12}, {3,12}, {4,12}, {11,12}, {12,12}, {13,12}, {14,12},
            {2,13}, {3,13}, {4,13}, {5,13}, {6,13}, {7,13}, {8,13}, {9,13}, {10,13}, {11,13}, {12,13}, {13,13},
            {3,14}, {4,14}, {5,14}, {6,14}, {7,14}, {8,14}, {9,14}, {10,14}, {11,14}, {12,14},
    };

    // ── Builders ────────────────────────────────────────────────────────────

    /**
     * Creates a new panel builder.
     *
     * <p>For attached panels, call {@link Builder#showIn} to specify which contexts
     * (screens) the panel appears in. For standalone screens, call {@link Builder#screen()}.
     *
     * @param name unique identifier — also used as the NBT key for persistence
     * @return a panel builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Panel Builder — collects all configuration, builds MKPanelDef, registers
    // ═════════════════════════════════════════════════════════════════════════

    public static class Builder {
        private final String name;

        // Context-based visibility (replaces menuClass + survivalOnly/creativeOnly)
        private final java.util.EnumSet<MKContext> contexts = java.util.EnumSet.noneOf(MKContext.class);
        private final java.util.HashMap<MKContext, int[]> posOverrides = new java.util.HashMap<>();

        private int x = 0, y = 0;
        private int width = 0, height = 0;
        private int padding = 6;
        private boolean autoSize = false;
        private Style style = Style.RAISED;
        private @Nullable Identifier customSprite = null;

        // Relative positioning fields
        private MKPanelDef.PosMode posMode = MKPanelDef.PosMode.ABSOLUTE;
        private int posArg1 = 0, posArg2 = 0;

        // Standalone screen fields
        private boolean isStandaloneScreen = false;
        private @Nullable Component title;
        private int screenWidth = 176;
        private int screenHeight = 166;

        // Panel visibility
        private boolean startHidden = false;

        // Standalone screen: include player inventory at bottom
        private boolean includePlayerInventory = false;

        // Collision avoidance
        private boolean allowOverlap = false;
        private boolean exclusive = false;
        private boolean rightAligned = false;
        private boolean rightAlignedExplicit = false; // true if user called .rightAligned()

        // Shift-click directional flags (default: false — opt-in per panel)
        private boolean shiftClickIn = false;
        private boolean shiftClickOut = false;

        // Region-following positioning (optional — set via followsRegion() or followsGroup())
        private @Nullable String followsRegionName = null;
        private MKAnchor followsRegionDirection = MKAnchor.ABOVE;
        private int followsRegionGap = MKPanel.Builder.DEFAULT_MARGIN;
        private boolean followsIsGroup = false;
        private @Nullable MKRegionPlacement followsPlacement = null;
        private int followsOffsetX = 0;
        private int followsOffsetY = 0;

        // Runtime disable predicate
        private @Nullable BooleanSupplier disabledWhen;

        // Auto-layout
        private int gap = -1;               // -1 = manual positioning, >=0 = auto-layout with this gap
        private boolean horizontal = false;  // false = vertical (default), true = horizontal
        private int layoutCursor = 0;        // current auto-position (y for vertical, x for horizontal)

        // Flow layout mode (runtime dynamic positioning)
        private MKPanelDef.LayoutMode layoutMode = MKPanelDef.LayoutMode.MANUAL;

        private final List<MKSlotDef> slotDefs = new ArrayList<>();
        private final List<MKButtonDef> buttonDefs = new ArrayList<>();
        private final List<MKTextDef> textDefs = new ArrayList<>();

        // Root layout group — when set, computeFlowPositions delegates to the tree
        private @Nullable MKGroupDef rootGroup;

        private @Nullable Consumer<ValueOutput> onSave;
        private @Nullable Consumer<ValueInput> onLoad;

        // (Slot indices are now explicit via .container(name, index) — no auto-counter)

        Builder(String name) {
            this.name = name;
        }

        // ── Panel configuration ─────────────────────────────────────────

        /** Sets the panel's position as absolute container-relative coordinates. */
        public Builder pos(int x, int y) {
            this.posMode = MKPanelDef.PosMode.ABSOLUTE;
            this.x = x; this.y = y;
            return this;
        }

        /** Default margin between panel edge and inventory edge, and between panels (px). */
        static final int DEFAULT_MARGIN = 4;

        // ── Auto-stacking positions (preferred) ────────────────────────────

        /** Auto-stack to the right of the container, top-aligned, stacking downward. */
        public Builder posRight() {
            this.posMode = MKPanelDef.PosMode.RIGHT_AUTO;
            return this;
        }

        /** Auto-stack to the left of the container, top-aligned, stacking downward. */
        public Builder posLeft() {
            this.posMode = MKPanelDef.PosMode.LEFT_AUTO;
            return this;
        }

        /** Auto-stack above the container, left-aligned, stacking rightward. */
        public Builder posAbove() {
            this.posMode = MKPanelDef.PosMode.ABOVE_LEFT;
            return this;
        }

        /** Auto-stack above the container, right-aligned, stacking leftward. */
        public Builder posAboveRight() {
            this.posMode = MKPanelDef.PosMode.ABOVE_RIGHT;
            return this;
        }

        /** Auto-stack below the container, left-aligned, stacking rightward. */
        public Builder posBelow() {
            this.posMode = MKPanelDef.PosMode.BELOW_LEFT;
            return this;
        }

        /** Auto-stack below the container, right-aligned, stacking leftward. */
        public Builder posBelowRight() {
            this.posMode = MKPanelDef.PosMode.BELOW_RIGHT;
            return this;
        }

        // ── Manual positions (for pixel-perfect placement) ─────────────────

        /** Manually position to the right of the container with explicit Y offset. */
        public Builder posRight(int gap, int y) {
            this.posMode = MKPanelDef.PosMode.RIGHT;
            this.posArg1 = gap; this.posArg2 = y;
            return this;
        }

        /** Manually position to the left of the container with explicit Y offset. */
        public Builder posLeft(int gap, int y) {
            this.posMode = MKPanelDef.PosMode.LEFT;
            this.posArg1 = gap; this.posArg2 = y;
            return this;
        }

        /** Manually position above the container with explicit X offset and gap. */
        public Builder posAbove(int x, int gap) {
            this.posMode = MKPanelDef.PosMode.ABOVE;
            this.posArg1 = x; this.posArg2 = gap;
            return this;
        }

        /** Manually position below the container with explicit X offset and gap. */
        public Builder posBelow(int x, int gap) {
            this.posMode = MKPanelDef.PosMode.BELOW;
            this.posArg1 = x; this.posArg2 = gap;
            return this;
        }

        // ── Hotbar-relative positioning ──────────────────────────────────────
        //
        // These methods position the panel relative to the hotbar row.
        // MenuKit resolves the hotbar's actual X/Y per-context at runtime,
        // so the consumer never needs to know per-screen coordinates.

        /**
         * Positions the panel relative to the hotbar row's top-left corner.
         * MenuKit resolves the hotbar position per-context (survival=8,
         * beacon=36, villager=108, creative=9, etc.) so the consumer only
         * specifies the offset from the hotbar.
         *
         * <p>Positive xOffset moves right; positive yOffset moves down.
         * Common patterns:
         * <ul>
         *   <li>{@code .posRelativeToHotbar(0, 18)} — directly below the hotbar</li>
         *   <li>{@code .posRelativeToHotbar(0, -20)} — directly above the hotbar</li>
         *   <li>{@code .posRelativeToHotbar(36, 20)} — below, offset to slot 2</li>
         * </ul>
         *
         * @param xOffset pixels right of the hotbar's left edge
         * @param yOffset pixels below the hotbar's top edge (negative = above)
         */
        public Builder posRelativeToHotbar(int xOffset, int yOffset) {
            this.posMode = MKPanelDef.PosMode.HOTBAR_RELATIVE;
            this.posArg1 = xOffset;
            this.posArg2 = yOffset;
            return this;
        }

        /**
         * Positions the panel relative to a specific hotbar slot (0-8).
         * Convenience method that computes the X offset from the slot index.
         *
         * <p>Example: to place a panel below hotbar slot 3 with a 2px gap:
         * <pre>{@code
         * .posRelativeToHotbarSlot(3, 0, 18 + 2)  // slotX + 0, hotbarY + 20
         * }</pre>
         *
         * @param hotbarIndex the hotbar slot (0 = leftmost, 8 = rightmost)
         * @param xOffset     additional X offset from the slot's left edge
         * @param yOffset     pixels below the hotbar's top edge (negative = above)
         */
        public Builder posRelativeToHotbarSlot(int hotbarIndex, int xOffset, int yOffset) {
            this.posMode = MKPanelDef.PosMode.HOTBAR_RELATIVE;
            // Bake the slot offset into posArg1 so resolvePosition just adds hotbarX
            this.posArg1 = hotbarIndex * MKContextLayout.SLOT_SPACING + xOffset;
            this.posArg2 = yOffset;
            return this;
        }

        /**
         * Specifies which contexts (screens) this panel appears in.
         * Can be called multiple times — contexts accumulate.
         *
         * @param ctxs one or more contexts (e.g., {@code MKContext.SURVIVAL_INVENTORY})
         */
        public Builder showIn(MKContext... ctxs) {
            for (MKContext ctx : ctxs) contexts.add(ctx);
            return this;
        }

        /**
         * Specifies contexts from a group set (e.g., {@link MKContext#ALL_INVENTORIES}).
         * Can be called multiple times — contexts accumulate.
         */
        public Builder showIn(java.util.Set<MKContext> ctxs) {
            contexts.addAll(ctxs);
            return this;
        }

        /**
         * Show in ALL contexts that have the player's inventory visible.
         * Shorthand for {@code showIn(MKContext.ALL_WITH_PLAYER_INVENTORY)}.
         *
         * <p>This is the most common "show everywhere relevant" choice —
         * it covers survival, creative, all storage, crafting, processing,
         * and special screens, but excludes read-only screens like LECTERN.
         */
        public Builder showInAll() {
            contexts.addAll(MKContext.ALL_WITH_PLAYER_INVENTORY);
            return this;
        }

        /**
         * Show in all player-inventory contexts EXCEPT the specified ones.
         * Convenience for "show everywhere, but not in X."
         *
         * <p>Example — show in all screens except the lectern and beacon:
         * <pre>{@code
         * .showInAllExcept(MKContext.LECTERN, MKContext.BEACON)
         * }</pre>
         *
         * <p>Starts from {@link MKContext#ALL_WITH_PLAYER_INVENTORY} and
         * removes each exclusion. Safe to exclude contexts that aren't in
         * the base set — they're silently ignored.
         *
         * @param exclude contexts to remove from ALL_WITH_PLAYER_INVENTORY
         */
        public Builder showInAllExcept(MKContext... exclude) {
            return showInExcept(MKContext.ALL_WITH_PLAYER_INVENTORY, exclude);
        }

        /**
         * Show in a base set of contexts minus specific exclusions.
         * The most flexible subtraction method — pick any predefined set
         * (or custom set) as the base, then carve out what you don't want.
         *
         * <p>Example — show in all storage screens except hopper:
         * <pre>{@code
         * .showInExcept(MKContext.ALL_STORAGE, MKContext.HOPPER)
         * }</pre>
         *
         * <p>Does not modify the base set — creates a copy internally.
         * Safe to exclude contexts not present in the base (no-op).
         *
         * @param base    the starting set of contexts
         * @param exclude contexts to remove from the base set
         */
        public Builder showInExcept(java.util.Set<MKContext> base, MKContext... exclude) {
            // Copy into a mutable EnumSet so we don't mutate the shared constant
            java.util.EnumSet<MKContext> result = java.util.EnumSet.copyOf(base);
            for (MKContext ctx : exclude) {
                result.remove(ctx);
            }
            contexts.addAll(result);
            return this;
        }

        /**
         * Sets an absolute position override for a specific context.
         * When the panel appears in this context, it uses these coordinates
         * instead of the default posMode.
         *
         * @param context the context to override
         * @param x       absolute container-relative x
         * @param y       absolute container-relative y
         */
        public Builder posFor(MKContext context, int x, int y) {
            posOverrides.put(context, new int[]{ x, y });
            return this;
        }

        /** Sets the panel's explicit size. Ignored if autoSize is enabled. */
        public Builder size(int width, int height) {
            this.width = width; this.height = height; return this;
        }

        /** Sets inner padding between panel edge and children. Default 6. */
        public Builder padding(int padding) {
            this.padding = padding; return this;
        }

        /** Enables auto-sizing — panel expands to fit children + padding. */
        public Builder autoSize() {
            this.autoSize = true; return this;
        }

        /**
         * Enables auto-layout with the specified gap between children.
         * Children added via {@code .slot()} or {@code .button()} (no coordinates)
         * are automatically positioned in sequence with this gap between them.
         * Children with explicit coordinates ({@code .slot(x, y)}) are manually
         * positioned and don't affect the auto-layout cursor.
         *
         * <p>Default direction is vertical (top-to-bottom). Call {@link #horizontal()}
         * to switch to horizontal (left-to-right).
         *
         * @param gap pixels between children (like CSS gap)
         */
        public Builder gap(int gap) {
            this.gap = gap; return this;
        }

        /** Switches auto-layout direction to horizontal (left-to-right).
         *  Default is vertical (top-to-bottom). Only meaningful when {@link #gap} is set. */
        public Builder horizontal() {
            this.horizontal = true; return this;
        }

        /**
         * Creates a root column layout group. Children flow top-to-bottom.
         * Returns a {@link GroupBuilder} — add children with {@code .slot()},
         * {@code .text()}, {@code .button()}, or nest with {@code .grid()}.
         * Call {@code .build()} to finalize the panel.
         *
         * <p>Implies {@link #autoSize()}.
         */
        public GroupBuilder column() {
            this.autoSize = true;
            return new GroupBuilder(this, null, MKGroupDef.LayoutMode.COLUMN);
        }

        /**
         * Creates a root row layout group. Children flow left-to-right.
         * Returns a {@link GroupBuilder} — add children with {@code .slot()},
         * {@code .text()}, {@code .button()}, or nest with {@code .column()}.
         * Call {@code .build()} to finalize the panel.
         *
         * <p>Implies {@link #autoSize()}.
         */
        public GroupBuilder row() {
            this.autoSize = true;
            return new GroupBuilder(this, null, MKGroupDef.LayoutMode.ROW);
        }

        /**
         * Creates a root grid layout group. Children fill cells in a 2D grid.
         * Returns a {@link GroupBuilder} — configure with {@code .cellSize()},
         * {@code .rows()}, {@code .fillRight()}, then add children.
         * Call {@code .build()} to finalize the panel.
         *
         * <p>Implies {@link #autoSize()}.
         */
        public GroupBuilder grid() {
            this.autoSize = true;
            return new GroupBuilder(this, null, MKGroupDef.LayoutMode.GRID);
        }

        /** Sets the visual style (RAISED, DARK, INSET, FLAT, CUSTOM, or NONE). Default RAISED. */
        public Builder style(Style style) {
            this.style = style; return this;
        }

        /** Shortcut for dark panel style (like vanilla's effects background). */
        public Builder dark() {
            this.style = Style.DARK; return this;
        }

        /**
         * Uses a custom 9-slice sprite for the panel background.
         * The sprite must have a .mcmeta file defining its 9-slice parameters.
         * Automatically sets style to CUSTOM.
         *
         * @param sprite the resource identifier for the sprite (e.g., "mymod:my_panel")
         */
        public Builder customSprite(Identifier sprite) {
            this.style = Style.CUSTOM;
            this.customSprite = sprite;
            return this;
        }

        // ── Standalone screen configuration ────────────────────────────

        /**
         * Marks this panel as a standalone screen. When opened via
         * {@link MenuKit#openScreen}, it creates its own container + screen
         * rather than attaching to an existing menu.
         */
        public Builder screen() {
            this.isStandaloneScreen = true;
            return this;
        }

        /** Sets the screen title (standalone screens). */
        public Builder title(String title) {
            this.title = Component.literal(title); return this;
        }

        /** Sets the screen title (standalone screens). */
        public Builder title(Component title) {
            this.title = title; return this;
        }

        /** Sets the screen dimensions (standalone screens). */
        public Builder screenSize(int width, int height) {
            this.screenWidth = width; this.screenHeight = height; return this;
        }

        // ── Panel visibility ───────────────────────────────────────────

        /** Marks this panel as hidden by default. Can be shown with
         *  {@link MenuKit#showPanel} or toggled with {@link MenuKit#togglePanel}. */
        public Builder hidden() {
            this.startHidden = true; return this;
        }

        /** For standalone screens: includes the player's inventory at the bottom
         *  of the screen, allowing items to be moved between panel slots and inventory. */
        public Builder includePlayerInventory() {
            this.includePlayerInventory = true; return this;
        }

        /** Disables automatic collision avoidance with vanilla UI elements
         *  (status effects, creative tabs) and other MKPanels. */
        public Builder allowOverlap() {
            this.allowOverlap = true; return this;
        }

        /** When this panel is visible, suppresses all other auto-stacked panels on the same side. */
        public Builder exclusive() {
            this.exclusive = true; return this;
        }

        /** Forces children to right-align within the panel. Usually auto-derived from posMode. */
        public Builder rightAligned() {
            this.rightAligned = true; this.rightAlignedExplicit = true; return this;
        }

        /**
         * Allows items to be shift-clicked INTO this panel's slots.
         * Default is false — panels don't participate in shift-click routing
         * unless explicitly opted in. When the panel is hidden, this flag
         * is effectively false regardless of the setting.
         */
        public Builder shiftClickIn(boolean value) {
            this.shiftClickIn = value; return this;
        }

        /**
         * Allows items to be shift-clicked OUT OF this panel's slots.
         * Default is false — shift-clicking an item in this panel does nothing
         * unless explicitly opted in. When the panel is hidden, this flag
         * is effectively false regardless of the setting.
         */
        public Builder shiftClickOut(boolean value) {
            this.shiftClickOut = value; return this;
        }

        /**
         * Positions this panel automatically relative to a named {@link MKRegion}'s
         * bounding box. The panel shows/hides based on whether the region is present
         * in the currently open menu — no manual disabledWhen predicate needed.
         *
         * <p>The panel will appear on the specified {@code side} of the region,
         * offset by {@code gap} pixels from the region edge.
         *
         * <p>Centering: for ABOVE/BELOW sides the panel centers horizontally on the
         * region; for LEFT/RIGHT it centers vertically.
         *
         * @param regionName the name of the MKRegion to track (e.g., "chest", "main_inventory")
         * @param side       which side of the region the panel attaches to
         * @param gap        pixel gap between the region edge and the panel edge
         */
        public Builder followsRegion(String regionName, MKAnchor side, int gap) {
            this.followsRegionName = regionName;
            this.followsRegionDirection = side;
            this.followsRegionGap = gap;
            this.followsIsGroup = false;
            this.followsPlacement = null;
            return this;
        }

        /**
         * Positions this panel relative to a named {@link MKRegion} using the
         * 8-placement system. The panel aligns to a specific corner of the
         * region's bounding box edge and stacks along that edge when multiple
         * panels share the same placement.
         *
         * @param regionName the name of the MKRegion to track (e.g., "mk:chest")
         * @param placement  where on the region's bounding box the panel sits
         * @param gap        pixel gap between the region edge and the panel edge
         */
        public Builder followsRegion(String regionName, MKRegionPlacement placement, int gap) {
            this.followsRegionName = regionName;
            this.followsRegionGap = gap;
            this.followsIsGroup = false;
            this.followsPlacement = placement;
            return this;
        }

        /** Like {@link #followsRegion(String, MKRegionPlacement, int)} with a pixel offset. */
        public Builder followsRegion(String regionName, MKRegionPlacement placement, int gap,
                                      int offsetX, int offsetY) {
            followsRegion(regionName, placement, gap);
            this.followsOffsetX = offsetX;
            this.followsOffsetY = offsetY;
            return this;
        }

        /**
         * Positions this panel relative to a named {@link MKRegion} using the
         * 8-placement system with default gap ({@link #DEFAULT_MARGIN}).
         *
         * @param regionName the name of the MKRegion to track
         * @param placement  where on the region's bounding box the panel sits
         */
        public Builder followsRegion(String regionName, MKRegionPlacement placement) {
            return followsRegion(regionName, placement, DEFAULT_MARGIN);
        }

        /**
         * Positions this panel relative to a named {@link MKRegionGroup}'s
         * combined bounding box using the 8-placement system. The bbox is the
         * union of all member regions' slot bounding boxes.
         *
         * @param groupName the name of the MKRegionGroup to track (e.g., "player_storage")
         * @param placement where on the group's bounding box the panel sits
         * @param gap       pixel gap between the group edge and the panel edge
         */
        public Builder followsGroup(String groupName, MKRegionPlacement placement, int gap) {
            this.followsRegionName = groupName;
            this.followsRegionGap = gap;
            this.followsIsGroup = true;
            this.followsPlacement = placement;
            return this;
        }

        /**
         * Positions this panel relative to a named {@link MKRegionGroup}'s
         * combined bounding box using the 8-placement system with default gap
         * ({@link #DEFAULT_MARGIN}).
         *
         * @param groupName the name of the MKRegionGroup to track
         * @param placement where on the group's bounding box the panel sits
         */
        public Builder followsGroup(String groupName, MKRegionPlacement placement) {
            return followsGroup(groupName, placement, DEFAULT_MARGIN);
        }

        /**
         * Hides this panel at runtime when the predicate returns true.
         * When disabled, the panel background, all slots, and all buttons
         * disappear — as if the panel was never registered. Items in disabled
         * slots remain safely in their containers and reappear when re-enabled.
         *
         * <p>Composes with {@link #hidden()} / {@link MenuKit#hidePanel} —
         * a panel is inactive if EITHER hidden (imperative) OR disabled (predicate).
         */
        public Builder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate; return this;
        }

        /**
         * Injects a pre-built root group directly, bypassing the builder's
         * column/row/grid API. Used by the button attachment system to create
         * overlay panels with pre-built group trees.
         */
        public Builder injectRootGroup(MKGroupDef group) {
            this.rootGroup = group;
            this.autoSize = true;
            return this;
        }

        // ── Auto-layout helpers ──────────────────────────────────────────
        private boolean hasAutoChildren = false; // tracks if any auto-positioned child was added

        /**
         * Returns the auto-layout position for the next child and advances the cursor.
         * First child starts at 0. Subsequent children get a gap before them.
         */
        private int[] autoPosition(int childWidth, int childHeight) {
            // Add gap before this child (but not before the first child)
            if (hasAutoChildren) {
                layoutCursor += gap;
            }
            int pos = layoutCursor;
            hasAutoChildren = true;

            // Advance cursor past this child
            layoutCursor = pos + (horizontal ? childWidth : childHeight);

            return horizontal ? new int[]{pos, 0} : new int[]{0, pos};
        }

        // ── Slot sub-builder ────────────────────────────────────────────

        /**
         * Starts defining an auto-positioned slot.
         * Uses the auto-layout cursor position. Requires {@link #gap} to be set.
         * You must call {@code .container(name, index)} on the returned builder.
         */
        public SlotBuilder slot() {
            if (gap < 0) throw new IllegalStateException(
                    "slot() without coordinates requires .gap() to enable auto-layout");
            int[] pos = autoPosition(18, 18);
            return new SlotBuilder(this, pos[0], pos[1]);
        }

        /**
         * Starts defining a slot at the given panel-relative position.
         * You must call {@code .container(name, index)} on the returned builder.
         * Returns a {@link SlotBuilder} — call {@code .done()} to return here.
         */
        public SlotBuilder slot(int childX, int childY) {
            return new SlotBuilder(this, childX, childY);
        }

        // ── Button sub-builder ──────────────────────────────────────────

        /**
         * Starts defining an auto-positioned button.
         * Uses the auto-layout cursor position. Requires {@link #gap} to be set.
         */
        public ButtonBuilder button() {
            if (gap < 0) throw new IllegalStateException(
                    "button() without coordinates requires .gap() to enable auto-layout");
            // Estimate button height as 17 (9px font + 4px padding each side)
            // Actual height is computed at build time — this is for cursor advancement
            int estHeight = 17;
            int estWidth = 50; // reasonable default for text buttons
            int[] pos = autoPosition(estWidth, estHeight);
            return new ButtonBuilder(this, pos[0], pos[1]);
        }

        /**
         * Starts defining a button at the given panel-relative position.
         * Returns a {@link ButtonBuilder} — call {@code .done()} to return here.
         */
        public ButtonBuilder button(int childX, int childY) {
            return new ButtonBuilder(this, childX, childY);
        }

        // ── Text labels ──────────────────────────────────────────────────

        /**
         * Starts defining a text label at the given panel-relative position.
         * Returns a {@link TextBuilder} — call {@code .done()} to return here.
         */
        public TextBuilder text(int childX, int childY) {
            return new TextBuilder(this, childX, childY);
        }

        /**
         * Starts defining an auto-positioned text label.
         * Uses the auto-layout cursor position. Requires {@link #gap} to be set.
         */
        public TextBuilder text() {
            if (gap < 0) throw new IllegalStateException(
                    "text() without coordinates requires .gap() to enable auto-layout");
            int[] pos = autoPosition(50, MKTextDef.TEXT_HEIGHT); // estimate 50px width
            return new TextBuilder(this, pos[0], pos[1]);
        }

        // ── Persistence callbacks ───────────────────────────────────────

        /** Hook for saving custom data alongside the container items. */
        public Builder onSave(Consumer<ValueOutput> handler) {
            this.onSave = handler; return this;
        }

        /** Hook for loading custom data alongside the container items. */
        public Builder onLoad(Consumer<ValueInput> handler) {
            this.onLoad = handler; return this;
        }

        // ── Build & Register ────────────────────────────────────────────

        /**
         * Builds the panel definition and registers it with MenuKit.
         * From this point, MenuKit handles everything — slot injection,
         * button creation, rendering, and persistence.
         */
        public void build() {
            // Auto-derive right alignment from posMode unless explicitly set
            boolean aligned = rightAligned;
            if (!rightAlignedExplicit) {
                aligned = posMode == MKPanelDef.PosMode.LEFT_AUTO
                        || posMode == MKPanelDef.PosMode.LEFT
                        || posMode == MKPanelDef.PosMode.ABOVE_RIGHT
                        || posMode == MKPanelDef.PosMode.BELOW_RIGHT;
            }

            // Apply button attachments to the tree before extracting def lists.
            // This injects button rows into the tree at build time so they're
            // native children — no post-hoc conditional injection needed.
            if (rootGroup != null) {
                MenuKit.applyButtonAttachments(rootGroup, name);
            }

            MKPanelDef def = new MKPanelDef(
                    name,
                    java.util.Set.copyOf(contexts),
                    x, y, width, height,
                    padding, autoSize, style, customSprite,
                    List.copyOf(slotDefs),
                    List.copyOf(buttonDefs),
                    List.copyOf(textDefs),
                    onSave, onLoad,
                    posMode, posArg1, posArg2,
                    java.util.Map.copyOf(posOverrides),
                    isStandaloneScreen,
                    title != null ? title : Component.literal(name),
                    screenWidth, screenHeight,
                    startHidden,
                    includePlayerInventory,
                    allowOverlap,
                    exclusive,
                    disabledWhen,
                    layoutMode,
                    gap >= 0 ? gap : 0,
                    rootGroup,
                    aligned,
                    shiftClickIn,
                    shiftClickOut,
                    followsRegionName != null
                            ? new MKRegionFollowDef(followsRegionName, followsRegionDirection,
                                    followsRegionGap, followsIsGroup, followsPlacement,
                                    followsOffsetX, followsOffsetY)
                            : null);
            // ── Validation ────────────────────────────────────────────────
            // Catch configuration errors at mod init so they don't surface
            // as confusing runtime bugs later.
            for (MKSlotDef slotDef : slotDefs) {
                // Skip validation for vanilla Inventory slots — they don't use MKContainers
                if (slotDef.isVanillaSlot()) continue;

                MKContainerDef cDef = MenuKit.getContainerDef(slotDef.containerName());
                if (cDef == null) {
                    throw new IllegalStateException(
                            "[MenuKit] Panel '" + name + "' references container '" +
                            slotDef.containerName() + "' which is not registered. " +
                            "Register it with MenuKit.container(...).register() before building panels.");
                }
                if (slotDef.containerIndex() < 0 || slotDef.containerIndex() >= cDef.size()) {
                    throw new IllegalStateException(
                            "[MenuKit] Panel '" + name + "' slot references index " +
                            slotDef.containerIndex() + " in container '" + cDef.name() +
                            "' which only has " + cDef.size() + " slots.");
                }
                // Instance-bound containers can't appear in player-only contexts
                if (cDef.binding() == MKContainerDef.BindingType.INSTANCE) {
                    for (MKContext ctx : contexts) {
                        if (ctx == MKContext.SURVIVAL_INVENTORY || ctx == MKContext.CREATIVE_INVENTORY
                                || ctx == MKContext.CREATIVE_TABS) {
                            throw new IllegalStateException(
                                    "[MenuKit] Panel '" + name + "' uses instance-bound container '" +
                                    cDef.name() + "' in player-only context " + ctx +
                                    ". Instance-bound containers need a block position — " +
                                    "they can't appear in inventory screens.");
                        }
                    }
                }
            }

            MenuKit.register(def);

            // If startHidden, immediately hide this panel
            if (startHidden) {
                MenuKit.hidePanel(name);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Slot Sub-Builder — configures one slot, returns to Panel.Builder
    // ═════════════════════════════════════════════════════════════════════════

    public static class SlotBuilder {
        private final Builder parent;
        private final int childX, childY;
        private @Nullable String containerName;
        private int containerIndex = -1;
        private int vanillaInventoryIndex = -1;  // >=0 means vanilla Inventory slot
        private @Nullable Predicate<ItemStack> filter;
        private int maxStack = 64;
        private @Nullable Supplier<Identifier> ghostIcon;
        private @Nullable BooleanSupplier disabledWhen;
        private @Nullable Consumer<net.minecraft.world.inventory.Slot> onEmptyClick;
        private @Nullable Supplier<net.minecraft.network.chat.Component> emptyTooltip;
        // ── Visual Decorations ──────────────────────────────────────────
        private int backgroundTint = 0;
        private @Nullable Identifier overlayIcon = null;
        private int borderColor = 0;

        SlotBuilder(Builder parent, int childX, int childY) {
            this.parent = parent;
            this.childX = childX;
            this.childY = childY;
        }

        /**
         * Specifies which container and slot index this slot reads from.
         * The container must be registered via {@code MenuKit.container(...).register()}
         * before the panel is built.
         *
         * @param name  the container name (as registered with MenuKit)
         * @param index the slot index within that container
         */
        public SlotBuilder container(String name, int index) {
            this.containerName = name;
            this.containerIndex = index;
            return this;
        }

        /**
         * Makes this slot mirror a vanilla Inventory slot (hotbar, armor, etc.).
         * The slot reads from and writes to the player's vanilla Inventory at the
         * specified index. No MKContainer is used.
         *
         * <p>Vanilla Inventory indices: hotbar 36-44, armor 5-8, offhand 45.
         * Use this for features like pockets where a panel slot needs to show
         * the same item that's in the player's hotbar.
         *
         * @param inventoryIndex the vanilla Inventory slot index
         */
        public SlotBuilder vanillaSlot(int inventoryIndex) {
            this.vanillaInventoryIndex = inventoryIndex;
            // Set a placeholder container name so done() validation doesn't fail
            this.containerName = "__vanilla__";
            this.containerIndex = inventoryIndex;
            return this;
        }

        /** Restricts which items can be placed in this slot. */
        public SlotBuilder filter(Predicate<ItemStack> filter) {
            this.filter = filter; return this;
        }

        /** Sets the maximum stack size for this slot. Default 64. */
        public SlotBuilder maxStack(int maxStack) {
            this.maxStack = maxStack; return this;
        }

        /** Sets a ghost icon shown when the slot is empty. */
        public SlotBuilder ghostIcon(Supplier<Identifier> provider) {
            this.ghostIcon = provider; return this;
        }

        /**
         * Hides this slot at runtime when the predicate returns true.
         * Vanilla's {@code isActive()} returns false, so the slot won't render,
         * accept clicks, or participate in shift-click routing. The item
         * stays safely in the container and reappears when re-enabled.
         */
        public SlotBuilder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate; return this;
        }

        /**
         * Fires a callback when the player clicks this slot while both the
         * slot and cursor are empty. Vanilla treats this as a no-op, so
         * we can safely add behavior (like toggling an "empty hand" marker)
         * without interfering with normal item interaction.
         */
        public SlotBuilder onEmptyClick(Consumer<net.minecraft.world.inventory.Slot> callback) {
            this.onEmptyClick = callback; return this;
        }

        /**
         * Shows a tooltip when hovering this empty slot with an empty cursor.
         * The supplier is evaluated each frame, so the text can change dynamically
         * (e.g., "Mark as empty hand" vs "Remove empty hand").
         * Returns null from the supplier to suppress the tooltip.
         */
        public SlotBuilder emptyTooltip(Supplier<net.minecraft.network.chat.Component> tooltip) {
            this.emptyTooltip = tooltip; return this;
        }

        // ── Visual Decoration Builders ──────────────────────────────────

        /**
         * Sets an ARGB background tint rendered BEHIND the item.
         * Use the alpha channel to control transparency.
         * Example: {@code .tint(0x40FF0000)} for semi-transparent red.
         */
        public SlotBuilder tint(int argb) {
            this.backgroundTint = argb; return this;
        }

        /**
         * Sets an icon texture rendered ON TOP of the slot item.
         * Useful for status indicators (lock, warning, etc.).
         */
        public SlotBuilder overlayIcon(Identifier icon) {
            this.overlayIcon = icon; return this;
        }

        /**
         * Sets an ARGB border color drawn as a 1px outline ON TOP of the item.
         * Example: {@code .borderColor(0xFFFFD700)} for a gold border.
         */
        public SlotBuilder borderColor(int argb) {
            this.borderColor = argb; return this;
        }

        /** Finalizes this slot definition and returns to the panel builder. */
        public Builder done() {
            if (vanillaInventoryIndex < 0 && (containerName == null || containerIndex < 0)) {
                throw new IllegalStateException(
                        "[MenuKit] Slot at (" + childX + "," + childY + ") must call " +
                        ".container(name, index) or .vanillaSlot(index) before .done()");
            }
            parent.slotDefs.add(new MKSlotDef(
                    childX, childY,
                    containerName != null ? containerName : "__vanilla__",
                    containerIndex,
                    filter, maxStack, ghostIcon, disabledWhen,
                    vanillaInventoryIndex,
                    onEmptyClick, emptyTooltip,
                    backgroundTint, overlayIcon, borderColor));
            return parent;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Button Sub-Builder — configures one button, returns to Panel.Builder
    // ═════════════════════════════════════════════════════════════════════════

    public static class ButtonBuilder {
        private final Builder parent;
        private final int childX, childY;
        private int width = 0, height = 0;  // 0 = auto-size in MKButton
        private @Nullable Identifier icon;
        private @Nullable Identifier toggledIcon;
        private int iconSize = 16;
        private Component label = Component.empty();
        private boolean toggleMode = false;
        private boolean initialPressed = false;
        private boolean disabled = false;
        private @Nullable String groupName;
        private @Nullable Consumer<MKButton> onClick;
        private @Nullable BiConsumer<MKButton, Boolean> onToggle;
        private @Nullable Component tooltip;
        private @Nullable BooleanSupplier pressedWhen;

        // Button action sugar fields
        private @Nullable String opensScreenName;
        private @Nullable Supplier<Screen> opensScreenFactory;
        private @Nullable String togglesPanelName;
        private boolean closesScreen = false;
        private boolean goesBack = false;
        private MKButton.ButtonStyle buttonStyle = MKButton.ButtonStyle.STANDARD;
        private @Nullable BooleanSupplier disabledWhen;

        ButtonBuilder(Builder parent, int childX, int childY) {
            this.parent = parent;
            this.childX = childX;
            this.childY = childY;
        }

        /** Sets button size. Default 150×20. */
        public ButtonBuilder size(int width, int height) {
            this.width = width; this.height = height; return this;
        }

        /** Sets the button's icon sprite. */
        public ButtonBuilder icon(Identifier icon) {
            this.icon = icon; return this;
        }

        /** Sets the icon to show when the button is toggled on. */
        public ButtonBuilder toggledIcon(Identifier icon) {
            this.toggledIcon = icon; return this;
        }

        /** Sets the icon render size (default 16). */
        public ButtonBuilder iconSize(int size) {
            this.iconSize = size; return this;
        }

        /** Sets the button's text label. */
        public ButtonBuilder label(Component label) {
            this.label = label; return this;
        }

        /** Sets the button's text label from a string. */
        public ButtonBuilder label(String label) {
            this.label = Component.literal(label); return this;
        }

        /** Sets the button visual style. Default is STANDARD (raised gray, white hover border). */
        public ButtonBuilder buttonStyle(MKButton.ButtonStyle style) {
            this.buttonStyle = style; return this;
        }

        /** Shortcut: panel-colored fill with blue hover swap. Clean, flat look. */
        public ButtonBuilder sleek() {
            this.buttonStyle = MKButton.ButtonStyle.SLEEK; return this;
        }

        /** Enables toggle mode. */
        public ButtonBuilder toggle() {
            this.toggleMode = true; return this;
        }

        /** Sets initial pressed state (toggle mode only). */
        public ButtonBuilder pressed(boolean pressed) {
            this.initialPressed = pressed; return this;
        }

        /** Sets the button as disabled (grayed out, not clickable). */
        public ButtonBuilder disabled(boolean disabled) {
            this.disabled = disabled; return this;
        }

        /**
         * Drives the button's pressed visual state from a runtime predicate.
         * When the predicate returns true, the button renders as pressed (blue
         * in sleek style). Requires toggle mode to be enabled.
         * Useful for buttons that reflect external state (like panel visibility).
         */
        public ButtonBuilder pressedWhen(BooleanSupplier predicate) {
            this.pressedWhen = predicate;
            this.toggleMode = true; // needed for pressed visual to render
            return this;
        }

        /** Joins a named radio group. Implies toggle mode. */
        public ButtonBuilder group(String groupName) {
            this.groupName = groupName;
            this.toggleMode = true;
            return this;
        }

        /** Click callback (fires on every click). */
        public ButtonBuilder onClick(Consumer<MKButton> handler) {
            this.onClick = handler; return this;
        }

        /** Toggle callback (fires when toggle state changes). */
        public ButtonBuilder onToggle(BiConsumer<MKButton, Boolean> handler) {
            this.onToggle = handler; return this;
        }

        /** Tooltip shown on hover. */
        public ButtonBuilder tooltip(Component text) {
            this.tooltip = text; return this;
        }

        /** Tooltip shown on hover (string convenience). */
        public ButtonBuilder tooltip(String text) {
            this.tooltip = Component.literal(text); return this;
        }

        /**
         * Hides this button at runtime when the predicate returns true.
         * The button won't render or accept interaction.
         */
        public ButtonBuilder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate; return this;
        }

        // ── Action sugar ───────────────────────────────────────────────

        /**
         * Opens a standalone MKScreen by panel name when clicked.
         * The panel must be registered with {@code .screen()}.
         */
        public ButtonBuilder opensScreen(String panelName) {
            this.opensScreenName = panelName; return this;
        }

        /**
         * Opens an arbitrary screen when clicked via {@code mc.setScreen()}.
         * Pass {@code () -> null} to close the current screen.
         */
        public ButtonBuilder opensScreen(Supplier<Screen> factory) {
            this.opensScreenFactory = factory; return this;
        }

        /**
         * Toggles visibility of a named panel when clicked.
         * The panel can be shown/hidden without closing the current screen.
         */
        public ButtonBuilder togglesPanel(String panelName) {
            this.togglesPanelName = panelName; return this;
        }

        /** Closes the current screen and returns to the game world. */
        public ButtonBuilder closesScreen() {
            this.closesScreen = true; return this;
        }

        /** Goes back to the previous screen (whatever was open before this one). */
        public ButtonBuilder goesBack() {
            this.goesBack = true; return this;
        }

        /** Finalizes this button definition and returns to the panel builder. */
        public Builder done() {
            // Wire closesScreen/goesBack into onClick
            Consumer<MKButton> finalOnClick = onClick;
            if (closesScreen) {
                Consumer<MKButton> prev = finalOnClick;
                finalOnClick = btn -> {
                    if (prev != null) prev.accept(btn);
                    MenuKit.closeScreen();
                };
            }
            if (goesBack) {
                Consumer<MKButton> prev = finalOnClick;
                finalOnClick = btn -> {
                    if (prev != null) prev.accept(btn);
                    MenuKit.goBack();
                };
            }

            // Auto-apply NONE style for icon-only buttons (no label, custom icon)
            // — renders just the icon with a hover overlay, no background panel
            MKButton.ButtonStyle resolvedStyle = buttonStyle;
            if (icon != null && (label == null || label.getString().isEmpty())
                    && buttonStyle == MKButton.ButtonStyle.STANDARD) {
                resolvedStyle = MKButton.ButtonStyle.NONE;
            }

            parent.buttonDefs.add(new MKButtonDef(
                    childX, childY, width, height,
                    icon, toggledIcon, iconSize, label,
                    toggleMode, initialPressed, groupName,
                    finalOnClick, onToggle, tooltip,
                    opensScreenName, opensScreenFactory, togglesPanelName,
                    resolvedStyle, disabled, disabledWhen, pressedWhen, null));
            return parent;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TextBuilder — fluent definition for text labels
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Fluent builder for defining text labels inside a panel.
     *
     * <p>Usage:
     * <pre>{@code
     * .text(0, 0)
     *     .content(() -> Component.literal("Hello"))
     *     .color(0x404040)
     *     .done()
     * }</pre>
     */
    public static class TextBuilder {
        private final Builder parent;
        private final int childX, childY;
        private @Nullable Supplier<Component> content;
        private int color = MKTextDef.DEFAULT_COLOR;
        private boolean shadow = false;
        private @Nullable BooleanSupplier disabledWhen;

        TextBuilder(Builder parent, int childX, int childY) {
            this.parent = parent;
            this.childX = childX;
            this.childY = childY;
        }

        /** Sets the text content as a dynamic supplier (evaluated each frame). */
        public TextBuilder content(Supplier<Component> content) {
            this.content = content; return this;
        }

        /** Sets the text content as a static string. */
        public TextBuilder content(String text) {
            Component c = Component.literal(text);
            this.content = () -> c;
            return this;
        }

        /** Sets the text content as a static Component. */
        public TextBuilder content(Component text) {
            this.content = () -> text;
            return this;
        }

        /** Sets the text color (ARGB). Default is {@link MKTextDef#DEFAULT_COLOR}. */
        public TextBuilder color(int color) {
            this.color = color; return this;
        }

        /** Enables drop shadow on the text. */
        public TextBuilder shadow() {
            this.shadow = true; return this;
        }

        /** Hides this text when the predicate returns true. */
        public TextBuilder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate; return this;
        }

        /** Finalizes this text definition and returns to the panel builder. */
        public Builder done() {
            if (content == null) {
                throw new IllegalStateException(
                        "[MenuKit] Text at (" + childX + "," + childY + ") must call " +
                        ".content(...) before .done()");
            }
            parent.textDefs.add(new MKTextDef(
                    childX, childY, content, color, shadow, disabledWhen));
            return parent;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GroupBuilder — fluent definition for layout groups
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Fluent builder for defining layout groups (column, row, grid).
     * Groups contain slots, buttons, text, and nested groups.
     *
     * <p>Usage:
     * <pre>{@code
     * .column().gap(4)
     *     .text().content("Title").done()
     *     .grid().cellSize(18).rows(9).fillRight()
     *         .slot().container("peek", 0).done()
     *     .done()
     * .build();
     * }</pre>
     */
    public static class GroupBuilder {
        private final Builder panelBuilder;
        private final @Nullable GroupBuilder parentGroup;
        // When non-null, this GroupBuilder is the content builder for a ScrollBuilder.
        // done() will call back to the scroll owner instead of adding as a Group child.
        private @Nullable ScrollBuilder scrollOwner;
        // Tab ownership -- when non-null, this GroupBuilder is the content
        // of a tab, and endTab() should finalize the tab instead of done().
        private @Nullable TabBuilder tabOwner;
        private MKGroupDef.LayoutMode mode;
        private int gap = 2;
        private int cellSize = 18;
        private int maxRows = 9;
        private boolean fillRight = false;
        private @Nullable BooleanSupplier disabledWhen;
        private @Nullable String id;
        private @Nullable List<MKGridTrack> columnTracks;
        private @Nullable List<MKGridTrack> rowTracks;
        final List<MKGroupChild> children = new ArrayList<>();

        GroupBuilder(Builder panelBuilder, @Nullable GroupBuilder parentGroup,
                     MKGroupDef.LayoutMode mode) {
            this.panelBuilder = panelBuilder;
            this.parentGroup = parentGroup;
            this.mode = mode;
        }

        // ── Group Configuration ──────────────────────────────────────────

        /** Sets spacing between children (COLUMN/ROW modes). */
        public GroupBuilder gap(int gap) {
            this.gap = gap; return this;
        }

        /** Sets the cell size for GRID layout (default 18 for slots). */
        public GroupBuilder cellSize(int size) {
            this.cellSize = size; return this;
        }

        /** Sets max rows per column before wrapping (GRID mode, default 9). */
        public GroupBuilder rows(int rows) {
            this.maxRows = rows; return this;
        }

        /** GRID: index 0 goes in the rightmost column (closest to inventory for LEFT panels). */
        public GroupBuilder fillRight() {
            this.fillRight = true; return this;
        }

        /** Defines variable-width columns for GRID mode. Enables mixed grid layout. */
        public GroupBuilder columns(int... widths) {
            this.columnTracks = new ArrayList<>();
            for (int w : widths) columnTracks.add(new MKGridTrack(w));
            return this;
        }

        /** Defines variable-height rows for GRID mode. Enables mixed grid layout. */
        public GroupBuilder rowSizes(int... heights) {
            this.rowTracks = new ArrayList<>();
            for (int h : heights) rowTracks.add(new MKGridTrack(h));
            return this;
        }

        /** Disables this group and all its children when the predicate returns true. */
        public GroupBuilder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate; return this;
        }

        /** Sets an element ID for runtime visibility overrides via {@link MenuKit#setElementVisible}. */
        public GroupBuilder id(String id) {
            this.id = id; return this;
        }

        // ── Add Children ─────────────────────────────────────────────────

        /** Adds a nested column group. */
        public GroupBuilder column() {
            return new GroupBuilder(panelBuilder, this, MKGroupDef.LayoutMode.COLUMN);
        }

        /** Adds a nested row group. */
        public GroupBuilder row() {
            return new GroupBuilder(panelBuilder, this, MKGroupDef.LayoutMode.ROW);
        }

        /** Adds a nested grid group. */
        public GroupBuilder grid() {
            return new GroupBuilder(panelBuilder, this, MKGroupDef.LayoutMode.GRID);
        }

        /** Starts defining a slot in this group. */
        public GSlotBuilder slot() {
            return new GSlotBuilder(this);
        }

        /** Starts defining a button in this group. */
        public GButtonBuilder button() {
            return new GButtonBuilder(this);
        }

        /** Starts defining a text label in this group. */
        public GTextBuilder text() {
            return new GTextBuilder(this);
        }

        /**
         * Starts defining a dynamic repeating section with a fixed max pool.
         * At build time, the template element is expanded {@code maxItems} times.
         * At runtime, {@code activeCount} controls how many are visible.
         *
         * @param maxItems the maximum number of template copies to pre-allocate
         * @return a DynamicBuilder for configuring the dynamic section
         */
        public DynamicBuilder dynamic(int maxItems) {
            return new DynamicBuilder(this, maxItems);
        }

        /**
         * Starts defining a scrollable container with a fixed viewport.
         * Returns a {@link ScrollBuilder} -- configure scroll properties,
         * then call {@code .column()}, {@code .row()}, or {@code .grid()}
         * to define the scrollable content, then {@code .done()} to close.
         *
         * @param viewportWidth  visible area width in pixels
         * @param viewportHeight visible area height in pixels
         */
        public ScrollBuilder scroll(int viewportWidth, int viewportHeight) {
            return new ScrollBuilder(this, viewportWidth, viewportHeight);
        }

        /** Starts defining a tabbed container in this group. */
        public TabsBuilder tabs() {
            return new TabsBuilder(this);
        }

        /**
         * Starts defining a slot group with container-type metadata.
         * Creates a nested {@link GroupBuilder} just like {@code .column()} or
         * {@code .row()}, but when {@code .endSlotGroup()} is called, wraps
         * the inner {@link MKGroupDef} in a {@link MKGroupChild.SlotGroup} record.
         *
         * @param id   unique identifier for this slot group
         * @param type the container type classification
         * @return a nested GroupBuilder for defining the inner layout
         */
        public SlotGroupBuilder slotGroup(String id, MKContainerType type) {
            return new SlotGroupBuilder(this, id, type);
        }

        /**
         * Finalizes this GroupBuilder as a tab's content and returns to the
         * TabsBuilder. Only valid when this GroupBuilder was created by
         * {@link TabBuilder#column()}, {@link TabBuilder#row()}, or
         * {@link TabBuilder#grid()}.
         *
         * @return the TabsBuilder for adding more tabs or calling .done()
         * @throws IllegalStateException if this GroupBuilder is not a tab's content
         */
        public TabsBuilder endTab() {
            if (tabOwner == null) {
                throw new IllegalStateException(
                        "[MenuKit] .endTab() called on a GroupBuilder that is not a tab's content. " +
                        "Use .done() for normal nested groups or .build() for root groups.");
            }
            // Build the content group and finalize the tab
            MKGroupDef contentDef = buildDef();
            tabOwner.receiveContent(contentDef);
            return tabOwner.tabsBuilder;
        }

        // ── Terminal Methods ─────────────────────────────────────────────

        /** Closes this nested group and returns to the parent group. */
        public GroupBuilder done() {
            if (parentGroup == null) {
                throw new IllegalStateException(
                        "[MenuKit] .done() called on root group — use .build() instead");
            }
            // If this GroupBuilder is the content builder for a ScrollBuilder,
            // feed the built def back to the scroll owner which adds the Scroll
            // child to the real parent group and returns the real parent.
            if (scrollOwner != null) {
                return scrollOwner.receiveContent(buildDef());
            }
            parentGroup.children.add(new MKGroupChild.Group(buildDef(), id));
            return parentGroup;
        }

        /** Closes the root group and builds the panel. */
        public void build() {
            if (parentGroup != null) {
                throw new IllegalStateException(
                        "[MenuKit] .build() called on nested group — use .done() instead");
            }
            MKGroupDef groupDef = buildDef();
            panelBuilder.rootGroup = groupDef;
            // Collect flat element lists from the tree for indexing
            collectElements(groupDef, panelBuilder.slotDefs,
                    panelBuilder.buttonDefs, panelBuilder.textDefs);
            panelBuilder.build();
        }

        MKGroupDef buildDef() {
            return new MKGroupDef(mode, gap, cellSize, maxRows, fillRight,
                    List.copyOf(children), disabledWhen,
                    columnTracks != null ? List.copyOf(columnTracks) : null,
                    rowTracks != null ? List.copyOf(rowTracks) : null);
        }

        /** Walks the tree depth-first and collects all elements into flat lists. */
        private static void collectElements(MKGroupDef group,
                                             List<MKSlotDef> slots,
                                             List<MKButtonDef> buttons,
                                             List<MKTextDef> texts) {
            for (MKGroupChild child : group.children()) {
                switch (child) {
                    case MKGroupChild.Slot s -> slots.add(s.def());
                    case MKGroupChild.Button b -> buttons.add(b.def());
                    case MKGroupChild.Text t -> texts.add(t.def());
                    case MKGroupChild.Group g -> collectElements(g.def(), slots, buttons, texts);
                    case MKGroupChild.SlotGroup sg -> collectElements(sg.group(), slots, buttons, texts);
                    case MKGroupChild.Spanning s -> {
                        // Unwrap the spanning wrapper and collect the inner child
                        MKGroupChild inner = s.inner();
                        switch (inner) {
                            case MKGroupChild.Slot sl -> slots.add(sl.def());
                            case MKGroupChild.Button bu -> buttons.add(bu.def());
                            case MKGroupChild.Text tx -> texts.add(tx.def());
                            case MKGroupChild.Group gr -> collectElements(gr.def(), slots, buttons, texts);
                            case MKGroupChild.SlotGroup sgr -> collectElements(sgr.group(), slots, buttons, texts);
                            case MKGroupChild.Dynamic di -> collectElements(di.def().expandedGroup(), slots, buttons, texts);
                            case MKGroupChild.Scroll sc -> collectElements(sc.def().contentGroup(), slots, buttons, texts);
                            case MKGroupChild.Tabs tb -> {
                                for (MKTabDef tab : tb.def().tabs()) collectElements(tab.contentGroup(), slots, buttons, texts);
                            }
                            case MKGroupChild.Spanning nested ->
                                throw new IllegalStateException("[MenuKit] Nested Spanning is not supported");
                        }
                    }
                    case MKGroupChild.Dynamic d -> collectElements(d.def().expandedGroup(), slots, buttons, texts);
                    case MKGroupChild.Scroll sc -> collectElements(sc.def().contentGroup(), slots, buttons, texts);
                    case MKGroupChild.Tabs tb -> {
                        // Collect from ALL tabs -- not just the active one --
                        // because all slots must be registered for injection
                        for (MKTabDef tab : tb.def().tabs()) {
                            collectElements(tab.contentGroup(), slots, buttons, texts);
                        }
                    }
                }
            }
        }

        // ── Group-Scoped Sub-Builders ────────────────────────────────────
        // These mirror SlotBuilder/ButtonBuilder/TextBuilder but return
        // to GroupBuilder instead of Builder.

        /** Slot builder that returns to GroupBuilder on .done(). */
        public static class GSlotBuilder {
            private final GroupBuilder parent;
            private @Nullable String containerName;
            private int containerIndex = -1;
            private @Nullable Predicate<ItemStack> filter;
            private int maxStack = 64;
            private @Nullable Supplier<Identifier> ghostIcon;
            private @Nullable BooleanSupplier disabledWhen;
            private @Nullable Consumer<net.minecraft.world.inventory.Slot> onEmptyClick;
            private @Nullable Supplier<Component> emptyTooltip;
            private int vanillaInventoryIndex = -1;
            // ── Visual Decorations ──────────────────────────────────────
            private int backgroundTint = 0;
            private @Nullable Identifier overlayIcon = null;
            private int borderColor = 0;
            // ── Element ID for visibility overrides ─────────────────────
            private @Nullable String id;
            // ── Grid Spanning ───────────────────────────────────────────
            private int colSpan = 1, rowSpan = 1;

            GSlotBuilder(GroupBuilder parent) { this.parent = parent; }

            public GSlotBuilder container(String name, int index) {
                this.containerName = name; this.containerIndex = index; return this;
            }
            public GSlotBuilder vanillaSlot(int inventoryIndex) {
                this.vanillaInventoryIndex = inventoryIndex;
                this.containerName = "__vanilla__";
                this.containerIndex = inventoryIndex;
                return this;
            }
            public GSlotBuilder filter(Predicate<ItemStack> filter) {
                this.filter = filter; return this;
            }
            public GSlotBuilder maxStack(int max) {
                this.maxStack = max; return this;
            }
            public GSlotBuilder ghostIcon(Supplier<Identifier> icon) {
                this.ghostIcon = icon; return this;
            }
            public GSlotBuilder disabledWhen(BooleanSupplier pred) {
                this.disabledWhen = pred; return this;
            }
            public GSlotBuilder onEmptyClick(Consumer<net.minecraft.world.inventory.Slot> callback) {
                this.onEmptyClick = callback; return this;
            }
            public GSlotBuilder emptyTooltip(Supplier<Component> tooltip) {
                this.emptyTooltip = tooltip; return this;
            }
            /** ARGB background tint rendered BEHIND the item. 0 = none. */
            public GSlotBuilder tint(int argb) {
                this.backgroundTint = argb; return this;
            }
            /** Icon rendered ON TOP of the slot item. */
            public GSlotBuilder overlayIcon(Identifier icon) {
                this.overlayIcon = icon; return this;
            }
            /** ARGB border color drawn ON TOP of the item. 0 = none. */
            public GSlotBuilder borderColor(int argb) {
                this.borderColor = argb; return this;
            }
            /** Sets an element ID for runtime visibility overrides via {@link MenuKit#setElementVisible}. */
            public GSlotBuilder id(String id) {
                this.id = id; return this;
            }
            /** Sets column and row span for mixed grid layout. */
            public GSlotBuilder span(int cols, int rows) {
                this.colSpan = cols; this.rowSpan = rows; return this;
            }

            public GroupBuilder done() {
                if (containerName == null || containerIndex < 0) {
                    throw new IllegalStateException(
                            "[MenuKit] Group slot must call .container(name, index) before .done()");
                }
                // childX/childY are 0 — the group's layout mode determines position
                MKGroupChild child = new MKGroupChild.Slot(new MKSlotDef(
                        0, 0, containerName, containerIndex,
                        filter, maxStack, ghostIcon, disabledWhen,
                        vanillaInventoryIndex, onEmptyClick, emptyTooltip,
                        backgroundTint, overlayIcon, borderColor), id);
                if (colSpan > 1 || rowSpan > 1) {
                    child = new MKGroupChild.Spanning(child, colSpan, rowSpan);
                }
                parent.children.add(child);
                return parent;
            }
        }

        /** Button builder that returns to GroupBuilder on .done(). */
        public static class GButtonBuilder {
            private final GroupBuilder parent;
            private int width = 0, height = 0;
            private @Nullable Identifier icon, toggledIcon;
            private int iconSize = 0;
            private @Nullable Component label;
            private boolean toggleMode = false;
            private boolean initialPressed = false;
            private @Nullable String groupName;
            private @Nullable Consumer<MKButton> onClick;
            private @Nullable BiConsumer<MKButton, Boolean> onToggle;
            private @Nullable Component tooltip;
            private @Nullable String opensScreenName;
            private @Nullable Supplier<Screen> opensScreenFactory;
            private @Nullable String togglesPanelName;
            private boolean closesScreen = false;
            private boolean goesBack = false;
            private MKButton.ButtonStyle buttonStyle = MKButton.ButtonStyle.STANDARD;
            private boolean disabled = false;
            private @Nullable BooleanSupplier disabledWhen;
            private @Nullable BooleanSupplier pressedWhen;
            private @Nullable String id;
            private int colSpan = 1, rowSpan = 1;

            GButtonBuilder(GroupBuilder parent) { this.parent = parent; }

            public GButtonBuilder size(int w, int h) { this.width = w; this.height = h; return this; }
            public GButtonBuilder label(String text) { this.label = Component.literal(text); return this; }
            public GButtonBuilder label(Component text) { this.label = text; return this; }
            public GButtonBuilder icon(Identifier id) { this.icon = id; return this; }
            public GButtonBuilder toggledIcon(Identifier id) { this.toggledIcon = id; return this; }
            public GButtonBuilder iconSize(int size) { this.iconSize = size; return this; }
            public GButtonBuilder toggle() { this.toggleMode = true; return this; }
            public GButtonBuilder pressed(boolean pressed) { this.initialPressed = pressed; return this; }
            public GButtonBuilder group(String name) { this.groupName = name; return this; }
            public GButtonBuilder onClick(Consumer<MKButton> handler) { this.onClick = handler; return this; }
            public GButtonBuilder onToggle(BiConsumer<MKButton, Boolean> handler) { this.onToggle = handler; return this; }
            public GButtonBuilder tooltip(String text) { this.tooltip = Component.literal(text); return this; }
            public GButtonBuilder opensScreen(String name) { this.opensScreenName = name; return this; }
            public GButtonBuilder closesScreen() { this.closesScreen = true; return this; }
            public GButtonBuilder togglesPanel(String name) { this.togglesPanelName = name; return this; }
            public GButtonBuilder disabledWhen(BooleanSupplier pred) { this.disabledWhen = pred; return this; }
            public GButtonBuilder pressedWhen(BooleanSupplier pred) { this.pressedWhen = pred; return this; }
            public GButtonBuilder buttonStyle(MKButton.ButtonStyle style) { this.buttonStyle = style; return this; }
            /** Sets an element ID for runtime visibility overrides via {@link MenuKit#setElementVisible}. */
            public GButtonBuilder id(String id) { this.id = id; return this; }
            /** Sets column and row span for mixed grid layout. */
            public GButtonBuilder span(int cols, int rows) {
                this.colSpan = cols; this.rowSpan = rows; return this;
            }

            public GroupBuilder done() {
                // Resolve final onClick with closesScreen/goesBack
                Consumer<MKButton> finalOnClick = onClick;
                if (closesScreen) {
                    Consumer<MKButton> prev = finalOnClick;
                    finalOnClick = btn -> {
                        if (prev != null) prev.accept(btn);
                        MenuKit.closeScreen();
                    };
                }
                if (goesBack) {
                    Consumer<MKButton> prev = finalOnClick;
                    finalOnClick = btn -> {
                        if (prev != null) prev.accept(btn);
                        MenuKit.goBack();
                    };
                }
                MKButton.ButtonStyle resolvedStyle = buttonStyle;
                if (icon != null && (label == null || label.getString().isEmpty())
                        && buttonStyle == MKButton.ButtonStyle.STANDARD) {
                    resolvedStyle = MKButton.ButtonStyle.NONE;
                }
                MKGroupChild child = new MKGroupChild.Button(new MKButtonDef(
                        0, 0, width, height,
                        icon, toggledIcon, iconSize, label,
                        toggleMode, initialPressed, groupName,
                        finalOnClick, onToggle, tooltip,
                        opensScreenName, opensScreenFactory, togglesPanelName,
                        resolvedStyle, disabled, disabledWhen, pressedWhen, null), id);
                if (colSpan > 1 || rowSpan > 1) {
                    child = new MKGroupChild.Spanning(child, colSpan, rowSpan);
                }
                parent.children.add(child);
                return parent;
            }
        }

        /** Text builder that returns to GroupBuilder on .done(). */
        public static class GTextBuilder {
            private final GroupBuilder parent;
            private @Nullable Supplier<Component> content;
            private int color = MKTextDef.DEFAULT_COLOR;
            private boolean shadow = false;
            private @Nullable BooleanSupplier disabledWhen;
            private @Nullable String id;
            private int colSpan = 1, rowSpan = 1;

            GTextBuilder(GroupBuilder parent) { this.parent = parent; }

            public GTextBuilder content(Supplier<Component> content) {
                this.content = content; return this;
            }
            public GTextBuilder content(String text) {
                Component c = Component.literal(text);
                this.content = () -> c; return this;
            }
            public GTextBuilder content(Component text) {
                this.content = () -> text; return this;
            }
            public GTextBuilder color(int color) {
                this.color = color; return this;
            }
            public GTextBuilder shadow() {
                this.shadow = true; return this;
            }
            public GTextBuilder disabledWhen(BooleanSupplier pred) {
                this.disabledWhen = pred; return this;
            }
            /** Sets an element ID for runtime visibility overrides via {@link MenuKit#setElementVisible}. */
            public GTextBuilder id(String id) {
                this.id = id; return this;
            }
            /** Sets column and row span for mixed grid layout. */
            public GTextBuilder span(int cols, int rows) {
                this.colSpan = cols; this.rowSpan = rows; return this;
            }

            public GroupBuilder done() {
                if (content == null) {
                    throw new IllegalStateException(
                            "[MenuKit] Group text must call .content(...) before .done()");
                }
                MKGroupChild child = new MKGroupChild.Text(new MKTextDef(
                        0, 0, content, color, shadow, disabledWhen), id);
                if (colSpan > 1 || rowSpan > 1) {
                    child = new MKGroupChild.Spanning(child, colSpan, rowSpan);
                }
                parent.children.add(child);
                return parent;
            }
        }

        // ── Dynamic Group Builder ───────────────────────────────────────────

        /**
         * Builder for dynamic repeating sections within a layout group.
         *
         * <p>Declare a single template element (slot, button, text, or group),
         * then call {@code .done()} to expand it into {@code maxItems} copies.
         * Each copy gets a {@code disabledWhen} predicate that hides it when
         * its index >= {@code activeCount}. The result is a fixed slot pool
         * with runtime visibility toggling -- no runtime slot injection needed.
         *
         * <p>For slot templates, each copy auto-increments the container index
         * from the template's base index (base+0, base+1, ..., base+maxItems-1).
         */
        /**
         * Builder for a {@link MKGroupChild.SlotGroup} -- a nested group with
         * container-type metadata. Delegates to a GroupBuilder for the inner
         * layout, then wraps the result in a SlotGroup record.
         */
        public static class SlotGroupBuilder extends GroupBuilder {
            private final GroupBuilder slotGroupParent;
            private final String slotGroupId;
            private final MKContainerType slotGroupType;

            SlotGroupBuilder(GroupBuilder parent, String id, MKContainerType type) {
                // Initialize as a column by default (caller can switch via .row()/.grid() chains)
                super(parent.panelBuilder, parent, MKGroupDef.LayoutMode.COLUMN);
                this.slotGroupParent = parent;
                this.slotGroupId = id;
                this.slotGroupType = type;
            }

            /**
             * Closes this slot group and returns to the parent group.
             * Wraps the inner MKGroupDef in a SlotGroup record.
             */
            @Override
            public GroupBuilder done() {
                MKGroupDef innerDef = buildDef();
                slotGroupParent.children.add(
                        new MKGroupChild.SlotGroup(slotGroupId, slotGroupType, innerDef));
                return slotGroupParent;
            }
        }

        public static class DynamicBuilder {
            private final GroupBuilder parent;
            private final int maxItems;
            private Supplier<Integer> activeCount = () -> 0;
            private MKGroupDef.LayoutMode layoutMode = MKGroupDef.LayoutMode.COLUMN;
            private int gap = 0;
            private int cellSize = 18;
            private int maxRows = 9;
            private @Nullable String id;
            private @Nullable MKGroupChild template;

            DynamicBuilder(GroupBuilder parent, int maxItems) {
                this.parent = parent;
                this.maxItems = maxItems;
            }

            // ── Configuration ───────────────────────────────────────────────

            /** Sets the supplier that controls how many copies are visible at runtime. */
            public DynamicBuilder activeCount(Supplier<Integer> supplier) {
                this.activeCount = supplier; return this;
            }

            /** Arranges expanded children in a column (top-to-bottom). This is the default. */
            public DynamicBuilder column() {
                this.layoutMode = MKGroupDef.LayoutMode.COLUMN; return this;
            }

            /** Arranges expanded children in a row (left-to-right). */
            public DynamicBuilder row() {
                this.layoutMode = MKGroupDef.LayoutMode.ROW; return this;
            }

            /** Arranges expanded children in a grid. */
            public DynamicBuilder grid() {
                this.layoutMode = MKGroupDef.LayoutMode.GRID; return this;
            }

            /** Sets spacing between expanded children. */
            public DynamicBuilder gap(int gap) {
                this.gap = gap; return this;
            }

            /** Sets cell size for grid layout (default 18 for slots). */
            public DynamicBuilder cellSize(int cellSize) {
                this.cellSize = cellSize; return this;
            }

            /** Sets max rows per column for grid layout (default 9). */
            public DynamicBuilder rows(int maxRows) {
                this.maxRows = maxRows; return this;
            }

            /** Sets an optional ID for this dynamic section. */
            public DynamicBuilder id(String id) {
                this.id = id; return this;
            }

            // ── Template Definitions ────────────────────────────────────────
            // Each returns a sub-builder whose .done() sets the template and
            // returns back to this DynamicBuilder.

            /** Defines the template as a slot. */
            public DSlotBuilder slot() {
                return new DSlotBuilder(this);
            }

            /** Defines the template as a button. */
            public DButtonBuilder button() {
                return new DButtonBuilder(this);
            }

            /** Defines the template as a text label. */
            public DTextBuilder text() {
                return new DTextBuilder(this);
            }

            /** Defines the template as a nested group. Returns a GroupBuilder
             *  whose .done() sets the template on this DynamicBuilder. */
            public DGroupBuilder group(MKGroupDef.LayoutMode mode) {
                return new DGroupBuilder(this, mode);
            }

            // ── Terminal Method ─────────────────────────────────────────────

            /**
             * Expands the template into {@code maxItems} copies, each with a
             * {@code disabledWhen} predicate that hides it when its index >=
             * {@code activeCount.get()}. Builds the expanded group and adds
             * a {@link MKGroupChild.Dynamic} to the parent GroupBuilder.
             */
            public GroupBuilder done() {
                if (template == null) {
                    throw new IllegalStateException(
                            "[MenuKit] DynamicBuilder must define a template (slot/button/text/group) before .done()");
                }

                // Expand the template into maxItems copies with index-based disabling
                List<MKGroupChild> expandedChildren = new ArrayList<>();
                // Capture activeCount in a local so the lambda closures are clean
                Supplier<Integer> ac = this.activeCount;

                for (int i = 0; i < maxItems; i++) {
                    final int index = i;
                    expandedChildren.add(copyTemplateAtIndex(template, index, ac));
                }

                // Build the expanded group with the configured layout mode
                // (9-param constructor: mode, gap, cellSize, maxRows, fillRight, children, disabledWhen, columnTracks, rowTracks)
                MKGroupDef expandedGroup = new MKGroupDef(
                        layoutMode, gap, cellSize, maxRows, false,
                        List.copyOf(expandedChildren), null, null, null);

                // Build the dynamic group def with the expanded group
                MKDynamicGroupDef dynamicDef = new MKDynamicGroupDef(
                        maxItems, template, ac, layoutMode, gap, cellSize, maxRows,
                        expandedGroup);

                // Add the Dynamic child to the parent group
                parent.children.add(new MKGroupChild.Dynamic(dynamicDef, id));
                return parent;
            }

            // ── Template Copying ────────────────────────────────────────────

            /**
             * Creates a copy of the template at the given index. For slots,
             * auto-increments the container index. For all types, wraps the
             * existing disabledWhen with an index check against activeCount.
             */
            private static MKGroupChild copyTemplateAtIndex(
                    MKGroupChild template, int index, Supplier<Integer> activeCount) {
                return switch (template) {
                    case MKGroupChild.Slot s -> {
                        MKSlotDef orig = s.def();
                        // Compose disabledWhen: existing predicate OR index >= activeCount
                        BooleanSupplier composedDisabled = composeDisabled(
                                orig.disabledWhen(), index, activeCount);
                        // Create new slot def with incremented container index
                        yield new MKGroupChild.Slot(new MKSlotDef(
                                orig.childX(), orig.childY(),
                                orig.containerName(), orig.containerIndex() + index,
                                orig.filter(), orig.maxStack(),
                                orig.ghostIcon(), composedDisabled,
                                orig.vanillaInventoryIndex() >= 0
                                        ? orig.vanillaInventoryIndex() + index
                                        : -1,
                                orig.onEmptyClick(), orig.emptyTooltip(),
                                orig.backgroundTint(), orig.overlayIcon(), orig.borderColor()), null);
                    }
                    case MKGroupChild.Button b -> {
                        MKButtonDef orig = b.def();
                        BooleanSupplier composedDisabled = composeDisabled(
                                orig.disabledWhen(), index, activeCount);
                        yield new MKGroupChild.Button(new MKButtonDef(
                                orig.childX(), orig.childY(),
                                orig.width(), orig.height(),
                                orig.icon(), orig.toggledIcon(), orig.iconSize(),
                                orig.label(), orig.toggleMode(), orig.initialPressed(),
                                orig.groupName(), orig.onClick(), orig.onToggle(),
                                orig.tooltip(), orig.opensScreenName(),
                                orig.opensScreenFactory(), orig.togglesPanelName(),
                                orig.buttonStyle(), orig.disabled(),
                                composedDisabled, orig.pressedWhen(),
                                orig.tooltipSupplier()), null);
                    }
                    case MKGroupChild.Text t -> {
                        MKTextDef orig = t.def();
                        BooleanSupplier composedDisabled = composeDisabled(
                                orig.disabledWhen(), index, activeCount);
                        yield new MKGroupChild.Text(new MKTextDef(
                                orig.childX(), orig.childY(),
                                orig.content(), orig.color(), orig.shadow(),
                                composedDisabled), null);
                    }
                    case MKGroupChild.Group g -> {
                        // For group templates, wrap the group's disabledWhen
                        // Preserve columnTracks/rowTracks so mixed grids survive the copy
                        MKGroupDef orig = g.def();
                        BooleanSupplier composedDisabled = composeDisabled(
                                orig.disabledWhen(), index, activeCount);
                        yield new MKGroupChild.Group(new MKGroupDef(
                                orig.mode(), orig.gap(), orig.cellSize(),
                                orig.maxRows(), orig.fillRight(),
                                orig.children(), composedDisabled,
                                orig.columnTracks(), orig.rowTracks()), null);
                    }
                    case MKGroupChild.Dynamic d -> {
                        // Nested dynamics: unlikely but handle gracefully
                        // Preserve columnTracks/rowTracks so mixed grids survive the copy
                        MKGroupDef orig = d.def().expandedGroup();
                        BooleanSupplier composedDisabled = composeDisabled(
                                orig.disabledWhen(), index, activeCount);
                        MKGroupDef wrappedGroup = new MKGroupDef(
                                orig.mode(), orig.gap(), orig.cellSize(),
                                orig.maxRows(), orig.fillRight(),
                                orig.children(), composedDisabled,
                                orig.columnTracks(), orig.rowTracks());
                        MKDynamicGroupDef wrappedDef = new MKDynamicGroupDef(
                                d.def().maxItems(), d.def().template(),
                                d.def().activeCount(), d.def().layoutMode(),
                                d.def().gap(), d.def().cellSize(), d.def().maxRows(),
                                wrappedGroup);
                        yield new MKGroupChild.Dynamic(wrappedDef, d.id());
                    }
                    case MKGroupChild.Spanning s -> {
                        // Spanning in a dynamic template -- copy the inner child
                        yield new MKGroupChild.Spanning(
                                copyTemplateAtIndex(s.inner(), index, activeCount),
                                s.colSpan(), s.rowSpan());
                    }
                    case MKGroupChild.Scroll sc -> {
                        // Scroll in a dynamic template -- not typical, pass through
                        yield new MKGroupChild.Scroll(sc.def(), sc.id());
                    }
                    case MKGroupChild.Tabs tb -> {
                        // Tabs in a dynamic template -- not typical, pass through
                        yield new MKGroupChild.Tabs(tb.def(), tb.id());
                    }
                    case MKGroupChild.SlotGroup sg -> {
                        // SlotGroup in a dynamic template -- pass through with same metadata
                        MKGroupDef orig = sg.group();
                        BooleanSupplier composedDisabled = composeDisabled(
                                orig.disabledWhen(), index, activeCount);
                        yield new MKGroupChild.SlotGroup(sg.id(),
                                sg.containerType(),
                                new MKGroupDef(orig.mode(), orig.gap(), orig.cellSize(),
                                        orig.maxRows(), orig.fillRight(),
                                        orig.children(), composedDisabled,
                                        orig.columnTracks(), orig.rowTracks()));
                    }
                };
            }

            /**
             * Composes a disabledWhen predicate with an index-based activeCount check.
             * The element is disabled if: existing predicate is true OR index >= activeCount.
             * Clamps activeCount to [0, infinity) -- negative values treated as 0.
             */
            private static BooleanSupplier composeDisabled(
                    @Nullable BooleanSupplier existing,
                    int index, Supplier<Integer> activeCount) {
                if (existing != null) {
                    return () -> existing.getAsBoolean() || index >= Math.max(0, activeCount.get());
                }
                return () -> index >= Math.max(0, activeCount.get());
            }

            // ── Dynamic Sub-Builders ────────────────────────────────────────

            /** Slot builder for dynamic templates -- sets the template on done(). */
            public static class DSlotBuilder {
                private final DynamicBuilder parent;
                private @Nullable String containerName;
                private int containerIndex = -1;
                private @Nullable Predicate<ItemStack> filter;
                private int maxStack = 64;
                private @Nullable Supplier<Identifier> ghostIcon;
                private @Nullable BooleanSupplier disabledWhen;
                private @Nullable Consumer<net.minecraft.world.inventory.Slot> onEmptyClick;
                private @Nullable Supplier<Component> emptyTooltip;
                private int vanillaInventoryIndex = -1;
                private int backgroundTint = 0;
                private @Nullable Identifier overlayIcon = null;
                private int borderColor = 0;

                DSlotBuilder(DynamicBuilder parent) { this.parent = parent; }

                public DSlotBuilder container(String name, int baseIndex) {
                    this.containerName = name; this.containerIndex = baseIndex; return this;
                }
                public DSlotBuilder vanillaSlot(int baseIndex) {
                    this.vanillaInventoryIndex = baseIndex;
                    this.containerName = "__vanilla__";
                    this.containerIndex = baseIndex;
                    return this;
                }
                public DSlotBuilder filter(Predicate<ItemStack> filter) {
                    this.filter = filter; return this;
                }
                public DSlotBuilder maxStack(int max) {
                    this.maxStack = max; return this;
                }
                public DSlotBuilder ghostIcon(Supplier<Identifier> icon) {
                    this.ghostIcon = icon; return this;
                }
                public DSlotBuilder disabledWhen(BooleanSupplier pred) {
                    this.disabledWhen = pred; return this;
                }
                public DSlotBuilder onEmptyClick(Consumer<net.minecraft.world.inventory.Slot> callback) {
                    this.onEmptyClick = callback; return this;
                }
                public DSlotBuilder emptyTooltip(Supplier<Component> tooltip) {
                    this.emptyTooltip = tooltip; return this;
                }
                public DSlotBuilder tint(int argb) {
                    this.backgroundTint = argb; return this;
                }
                public DSlotBuilder overlayIcon(Identifier icon) {
                    this.overlayIcon = icon; return this;
                }
                public DSlotBuilder borderColor(int argb) {
                    this.borderColor = argb; return this;
                }

                public DynamicBuilder done() {
                    if (containerName == null || containerIndex < 0) {
                        throw new IllegalStateException(
                                "[MenuKit] Dynamic slot template must call .container(name, baseIndex) before .done()");
                    }
                    parent.template = new MKGroupChild.Slot(new MKSlotDef(
                            0, 0, containerName, containerIndex,
                            filter, maxStack, ghostIcon, disabledWhen,
                            vanillaInventoryIndex, onEmptyClick, emptyTooltip,
                            backgroundTint, overlayIcon, borderColor), null);
                    return parent;
                }
            }

            /** Button builder for dynamic templates -- sets the template on done(). */
            public static class DButtonBuilder {
                private final DynamicBuilder parent;
                private int width = 0, height = 0;
                private @Nullable Identifier icon, toggledIcon;
                private int iconSize = 0;
                private @Nullable Component label;
                private boolean toggleMode = false;
                private boolean initialPressed = false;
                private @Nullable String groupName;
                private @Nullable Consumer<MKButton> onClick;
                private @Nullable BiConsumer<MKButton, Boolean> onToggle;
                private @Nullable Component tooltip;
                private @Nullable String opensScreenName;
                private @Nullable Supplier<Screen> opensScreenFactory;
                private @Nullable String togglesPanelName;
                private MKButton.ButtonStyle buttonStyle = MKButton.ButtonStyle.STANDARD;
                private boolean disabled = false;
                private @Nullable BooleanSupplier disabledWhen;
                private @Nullable BooleanSupplier pressedWhen;

                DButtonBuilder(DynamicBuilder parent) { this.parent = parent; }

                public DButtonBuilder size(int w, int h) { this.width = w; this.height = h; return this; }
                public DButtonBuilder label(String text) { this.label = Component.literal(text); return this; }
                public DButtonBuilder label(Component text) { this.label = text; return this; }
                public DButtonBuilder icon(Identifier id) { this.icon = id; return this; }
                public DButtonBuilder toggledIcon(Identifier id) { this.toggledIcon = id; return this; }
                public DButtonBuilder iconSize(int size) { this.iconSize = size; return this; }
                public DButtonBuilder toggle() { this.toggleMode = true; return this; }
                public DButtonBuilder pressed(boolean pressed) { this.initialPressed = pressed; return this; }
                public DButtonBuilder group(String name) { this.groupName = name; return this; }
                public DButtonBuilder onClick(Consumer<MKButton> handler) { this.onClick = handler; return this; }
                public DButtonBuilder onToggle(BiConsumer<MKButton, Boolean> handler) { this.onToggle = handler; return this; }
                public DButtonBuilder tooltip(String text) { this.tooltip = Component.literal(text); return this; }
                public DButtonBuilder opensScreen(String name) { this.opensScreenName = name; return this; }
                public DButtonBuilder togglesPanel(String name) { this.togglesPanelName = name; return this; }
                public DButtonBuilder disabledWhen(BooleanSupplier pred) { this.disabledWhen = pred; return this; }
                public DButtonBuilder pressedWhen(BooleanSupplier pred) { this.pressedWhen = pred; return this; }
                public DButtonBuilder buttonStyle(MKButton.ButtonStyle style) { this.buttonStyle = style; return this; }

                public DynamicBuilder done() {
                    parent.template = new MKGroupChild.Button(new MKButtonDef(
                            0, 0, width, height,
                            icon, toggledIcon, iconSize, label,
                            toggleMode, initialPressed, groupName,
                            onClick, onToggle, tooltip,
                            opensScreenName, opensScreenFactory, togglesPanelName,
                            buttonStyle, disabled, disabledWhen, pressedWhen, null), null);
                    return parent;
                }
            }

            /** Text builder for dynamic templates -- sets the template on done(). */
            public static class DTextBuilder {
                private final DynamicBuilder parent;
                private @Nullable Supplier<Component> content;
                private int color = MKTextDef.DEFAULT_COLOR;
                private boolean shadow = false;
                private @Nullable BooleanSupplier disabledWhen;

                DTextBuilder(DynamicBuilder parent) { this.parent = parent; }

                public DTextBuilder content(Supplier<Component> content) {
                    this.content = content; return this;
                }
                public DTextBuilder content(String text) {
                    Component c = Component.literal(text);
                    this.content = () -> c; return this;
                }
                public DTextBuilder content(Component text) {
                    this.content = () -> text; return this;
                }
                public DTextBuilder color(int color) {
                    this.color = color; return this;
                }
                public DTextBuilder shadow() {
                    this.shadow = true; return this;
                }
                public DTextBuilder disabledWhen(BooleanSupplier pred) {
                    this.disabledWhen = pred; return this;
                }

                public DynamicBuilder done() {
                    if (content == null) {
                        throw new IllegalStateException(
                                "[MenuKit] Dynamic text template must call .content(...) before .done()");
                    }
                    parent.template = new MKGroupChild.Text(new MKTextDef(
                            0, 0, content, color, shadow, disabledWhen), null);
                    return parent;
                }
            }

            /** Group builder for dynamic templates -- sets the template on done(). */
            public static class DGroupBuilder {
                private final DynamicBuilder dynamicParent;
                private final GroupBuilder innerGroupBuilder;

                DGroupBuilder(DynamicBuilder dynamicParent, MKGroupDef.LayoutMode mode) {
                    this.dynamicParent = dynamicParent;
                    // Create a temporary GroupBuilder to collect the group's children.
                    // We use a dummy parent since we only need buildDef().
                    this.innerGroupBuilder = new GroupBuilder(
                            dynamicParent.parent.panelBuilder, null, mode);
                }

                // Delegate configuration to the inner GroupBuilder
                public DGroupBuilder gap(int gap) {
                    innerGroupBuilder.gap(gap); return this;
                }
                public DGroupBuilder cellSize(int size) {
                    innerGroupBuilder.cellSize(size); return this;
                }
                public DGroupBuilder rows(int rows) {
                    innerGroupBuilder.rows(rows); return this;
                }
                public DGroupBuilder fillRight() {
                    innerGroupBuilder.fillRight(); return this;
                }
                public DGroupBuilder disabledWhen(BooleanSupplier predicate) {
                    innerGroupBuilder.disabledWhen(predicate); return this;
                }

                // Child-adding methods that delegate to the inner GroupBuilder
                public GSlotBuilder slot() {
                    return innerGroupBuilder.slot();
                }

                public GButtonBuilder button() {
                    return innerGroupBuilder.button();
                }

                public GTextBuilder text() {
                    return innerGroupBuilder.text();
                }

                public GroupBuilder column() {
                    return innerGroupBuilder.column();
                }

                public GroupBuilder row() {
                    return innerGroupBuilder.row();
                }

                public GroupBuilder grid() {
                    return innerGroupBuilder.grid();
                }

                /** Finalizes the group template and returns to the DynamicBuilder. */
                public DynamicBuilder done() {
                    MKGroupDef groupDef = innerGroupBuilder.buildDef();
                    dynamicParent.template = new MKGroupChild.Group(groupDef, null);
                    return dynamicParent;
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ScrollBuilder -- fluent definition for scrollable containers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Fluent builder for defining a scrollable container within a layout group.
     * The scroll container has a fixed viewport and scrollable content group.
     *
     * <p>Usage:
     * <pre>{@code
     * .column().gap(4)
     *     .scroll(162, 90).id("item_list")
     *         .column().gap(2)
     *             .slot().container("items", 0).done()
     *             .slot().container("items", 1).done()
     *             // ... more slots than fit in viewport ...
     *         .done()  // closes the content column, returns to parent group
     *     .text().content("Footer").done()
     * .build();
     * }</pre>
     */
    public static class ScrollBuilder {
        private final GroupBuilder parentGroup;
        private final int viewportWidth;
        private final int viewportHeight;
        private boolean verticalScroll = true;
        private boolean horizontalScroll = false;
        private int scrollSpeed = 18;
        private boolean smoothScroll = false;
        private boolean showScrollbar = true;
        private @Nullable String id;

        ScrollBuilder(GroupBuilder parent, int viewportWidth, int viewportHeight) {
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                throw new IllegalArgumentException(
                        "[MenuKit] Scroll viewport must have positive dimensions, got "
                        + viewportWidth + "x" + viewportHeight);
            }
            this.parentGroup = parent;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
        }

        /** Enable/disable vertical scrolling (default: true). */
        public ScrollBuilder verticalScroll(boolean enabled) {
            this.verticalScroll = enabled; return this;
        }

        /** Enable/disable horizontal scrolling (default: false). */
        public ScrollBuilder horizontalScroll(boolean enabled) {
            this.horizontalScroll = enabled; return this;
        }

        /** Pixels per scroll wheel notch (default: 18). */
        public ScrollBuilder scrollSpeed(int pixelsPerNotch) {
            this.scrollSpeed = pixelsPerNotch; return this;
        }

        /** Enable smooth scroll interpolation (default: false). */
        public ScrollBuilder smoothScroll(boolean enabled) {
            this.smoothScroll = enabled; return this;
        }

        /** Show/hide the scrollbar indicator (default: true). */
        public ScrollBuilder showScrollbar(boolean enabled) {
            this.showScrollbar = enabled; return this;
        }

        /**
         * Sets the element ID for this scroll container (REQUIRED).
         * Used to track scroll offset in {@link MKPanelState}.
         */
        public ScrollBuilder id(String id) {
            this.id = id; return this;
        }

        // ── Content layout methods ──────────────────────────────────────────
        // These return a GroupBuilder whose done() flows back through
        // receiveContent() to add the Scroll child to the real parent.

        /**
         * Starts a column layout for the scroll content.
         * Call {@code .done()} on the returned GroupBuilder when content is complete.
         */
        public GroupBuilder column() {
            return createContentBuilder(MKGroupDef.LayoutMode.COLUMN);
        }

        /**
         * Starts a row layout for the scroll content.
         * Call {@code .done()} on the returned GroupBuilder when content is complete.
         */
        public GroupBuilder row() {
            return createContentBuilder(MKGroupDef.LayoutMode.ROW);
        }

        /**
         * Starts a grid layout for the scroll content.
         * Call {@code .done()} on the returned GroupBuilder when content is complete.
         */
        public GroupBuilder grid() {
            return createContentBuilder(MKGroupDef.LayoutMode.GRID);
        }

        /**
         * Creates the inner GroupBuilder for scroll content. Sets scrollOwner
         * so that done() calls back to receiveContent() instead of adding
         * a Group child to the parent.
         */
        private GroupBuilder createContentBuilder(MKGroupDef.LayoutMode mode) {
            // Validate before creating the content builder
            if (id == null) {
                throw new IllegalStateException(
                        "[MenuKit] Scroll container must have an id() for state tracking");
            }
            // The content builder's parentGroup is set to our parentGroup so the
            // done() call chain works. The scrollOwner field overrides the normal
            // done() behavior to route through receiveContent() instead.
            GroupBuilder contentBuilder = new GroupBuilder(
                    parentGroup.panelBuilder, parentGroup, mode);
            contentBuilder.scrollOwner = this;
            return contentBuilder;
        }

        /**
         * Called by the content GroupBuilder's done() when the content is complete.
         * Validates the scroll configuration, creates the MKScrollDef + MKGroupChild.Scroll,
         * adds it to the real parent group, and returns the parent GroupBuilder.
         */
        GroupBuilder receiveContent(MKGroupDef contentGroup) {
            // Check for nested scroll containers -- not supported
            checkNoNestedScroll(contentGroup);

            MKScrollDef scrollDef = new MKScrollDef(
                    viewportWidth, viewportHeight, contentGroup,
                    verticalScroll, horizontalScroll, scrollSpeed,
                    smoothScroll, showScrollbar);
            parentGroup.children.add(new MKGroupChild.Scroll(scrollDef, id));
            return parentGroup;
        }

        /**
         * Walks the content group tree and throws if any child is a Scroll.
         * Nested scroll containers are not supported.
         */
        private static void checkNoNestedScroll(MKGroupDef group) {
            for (MKGroupChild child : group.children()) {
                switch (child) {
                    case MKGroupChild.Scroll sc -> throw new IllegalStateException(
                            "[MenuKit] Nested scroll containers are not supported. " +
                            "Found scroll '" + sc.id() + "' inside another scroll container.");
                    case MKGroupChild.Group g -> checkNoNestedScroll(g.def());
                    case MKGroupChild.SlotGroup sg -> checkNoNestedScroll(sg.group());
                    case MKGroupChild.Dynamic d -> checkNoNestedScroll(d.def().expandedGroup());
                    case MKGroupChild.Spanning s -> {
                        // Must check the inner child -- a Spanning could wrap a Scroll
                        MKGroupChild inner = s.inner();
                        if (inner instanceof MKGroupChild.Scroll sc2) {
                            throw new IllegalStateException(
                                    "[MenuKit] Nested scroll containers are not supported. " +
                                    "Found scroll '" + sc2.id() + "' inside another scroll container (via Spanning).");
                        } else if (inner instanceof MKGroupChild.Group g2) {
                            checkNoNestedScroll(g2.def());
                        } else if (inner instanceof MKGroupChild.SlotGroup sg2) {
                            checkNoNestedScroll(sg2.group());
                        } else if (inner instanceof MKGroupChild.Dynamic d2) {
                            checkNoNestedScroll(d2.def().expandedGroup());
                        } else if (inner instanceof MKGroupChild.Tabs tb2) {
                            for (MKTabDef tab : tb2.def().tabs()) checkNoNestedScroll(tab.contentGroup());
                        }
                    }
                    case MKGroupChild.Tabs tb -> {
                        for (MKTabDef tab : tb.def().tabs()) checkNoNestedScroll(tab.contentGroup());
                    }
                    default -> {} // Slot, Button, Text -- no nesting concern
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TabsBuilder -- fluent definition for tabbed containers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Builder for defining a tabbed container within a layout group.
     * Each tab has a label/icon and a content group. Only the active tab's
     * content is visible.
     *
     * <p>Usage:
     * <pre>{@code
     * .tabs().id("my_tabs")
     *     .tab().label("Tab 1")
     *         .column().gap(4)
     *             .slot().container("items", 0).done()
     *         .endTab()
     *     .tab().label("Tab 2")
     *         .column().gap(4)
     *             .slot().container("items", 1).done()
     *         .endTab()
     * .done()
     * }</pre>
     */
    public static class TabsBuilder {
        final GroupBuilder parentGroup;
        private MKTabsDef.TabBarPosition barPosition = MKTabsDef.TabBarPosition.TOP;
        private int barThickness = 20;
        private int tabGap = 1;
        private int defaultTab = 0;
        private boolean keyboardSwitch = true;
        private @Nullable String id;
        final List<MKTabDef> tabDefs = new ArrayList<>();

        TabsBuilder(GroupBuilder parent) { this.parentGroup = parent; }

        /** Sets the tab bar position. Default TOP. */
        public TabsBuilder barPosition(MKTabsDef.TabBarPosition pos) {
            this.barPosition = pos; return this;
        }

        /** Sets the tab bar height (TOP/BOTTOM) or width (LEFT/RIGHT). Default 20. */
        public TabsBuilder barThickness(int px) {
            this.barThickness = px; return this;
        }

        /** Sets the gap between tab buttons. Default 1. */
        public TabsBuilder tabGap(int px) {
            this.tabGap = px; return this;
        }

        /** Sets the initially active tab index. Default 0. */
        public TabsBuilder defaultTab(int index) {
            this.defaultTab = index; return this;
        }

        /** Enables or disables keyboard tab switching. Default true. */
        public TabsBuilder keyboardSwitch(boolean enabled) {
            this.keyboardSwitch = enabled; return this;
        }

        /** Sets the unique ID for this tabs element (required). */
        public TabsBuilder id(String id) {
            this.id = id; return this;
        }

        /** Starts defining a new tab. */
        public TabBuilder tab() {
            return new TabBuilder(this);
        }

        /**
         * Finalizes the tabs definition and returns to the parent group builder.
         *
         * @throws IllegalStateException if id is null or no tabs were defined
         */
        public GroupBuilder done() {
            if (id == null) {
                throw new IllegalStateException("[MenuKit] Tabs must have an id()");
            }
            if (tabDefs.isEmpty()) {
                throw new IllegalStateException("[MenuKit] Tabs must have at least one tab");
            }
            // Clamp default tab to valid range
            if (defaultTab < 0 || defaultTab >= tabDefs.size()) {
                defaultTab = 0;
            }

            MKTabsDef tabsDef = new MKTabsDef(
                    List.copyOf(tabDefs), barPosition, barThickness,
                    tabGap, defaultTab, keyboardSwitch);
            parentGroup.children.add(new MKGroupChild.Tabs(tabsDef, id));
            return parentGroup;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TabBuilder -- defines a single tab (label, icon, content group)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Builder for a single tab within a {@link TabsBuilder}.
     * Define the tab's label/icon, then call {@code .column()}, {@code .row()},
     * or {@code .grid()} to define the content layout. The content builder's
     * {@code .endTab()} returns back to the TabsBuilder.
     */
    public static class TabBuilder {
        final TabsBuilder tabsBuilder;
        private @Nullable Component label;
        private @Nullable Identifier icon;
        private int iconSize = 16;
        private @Nullable MKGroupDef contentGroup;

        TabBuilder(TabsBuilder parent) { this.tabsBuilder = parent; }

        /** Sets the tab's text label from a string. */
        public TabBuilder label(String text) {
            this.label = Component.literal(text); return this;
        }

        /** Sets the tab's text label. */
        public TabBuilder label(Component text) {
            this.label = text; return this;
        }

        /** Sets the tab's icon sprite. */
        public TabBuilder icon(Identifier icon) {
            this.icon = icon; return this;
        }

        /** Sets the icon render size (default 16). */
        public TabBuilder iconSize(int size) {
            this.iconSize = size; return this;
        }

        /**
         * Creates a column content layout for this tab.
         * Call {@code .endTab()} on the returned GroupBuilder to finalize
         * the tab and return to the TabsBuilder.
         */
        public GroupBuilder column() {
            return contentBuilder(MKGroupDef.LayoutMode.COLUMN);
        }

        /**
         * Creates a row content layout for this tab.
         * Call {@code .endTab()} on the returned GroupBuilder to finalize
         * the tab and return to the TabsBuilder.
         */
        public GroupBuilder row() {
            return contentBuilder(MKGroupDef.LayoutMode.ROW);
        }

        /**
         * Creates a grid content layout for this tab.
         * Call {@code .endTab()} on the returned GroupBuilder to finalize
         * the tab and return to the TabsBuilder.
         */
        public GroupBuilder grid() {
            return contentBuilder(MKGroupDef.LayoutMode.GRID);
        }

        /**
         * Creates a GroupBuilder for this tab's content. The GroupBuilder
         * has its tabOwner set so that endTab() knows how to finalize.
         */
        private GroupBuilder contentBuilder(MKGroupDef.LayoutMode mode) {
            // parentGroup is null -- this is a standalone content group,
            // not a child of any existing GroupBuilder
            GroupBuilder gb = new GroupBuilder(
                    tabsBuilder.parentGroup.panelBuilder, null, mode);
            gb.tabOwner = this;
            return gb;
        }

        /**
         * Called by GroupBuilder.endTab() to store the content group
         * and add the finalized MKTabDef to the TabsBuilder.
         */
        void receiveContent(MKGroupDef contentDef) {
            this.contentGroup = contentDef;
            tabsBuilder.tabDefs.add(new MKTabDef(label, icon, iconSize, contentDef));
        }
    }
}
