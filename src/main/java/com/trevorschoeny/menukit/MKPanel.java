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
                    aligned);
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
        private @Nullable Consumer<MKSlot> onEmptyClick;
        private @Nullable Supplier<net.minecraft.network.chat.Component> emptyTooltip;

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
        public SlotBuilder onEmptyClick(Consumer<MKSlot> callback) {
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
                    onEmptyClick, emptyTooltip));
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
                    resolvedStyle, disabled, disabledWhen, pressedWhen));
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
        private MKGroupDef.LayoutMode mode;
        private int gap = 2;
        private int cellSize = 18;
        private int maxRows = 9;
        private boolean fillRight = false;
        private @Nullable BooleanSupplier disabledWhen;
        private final List<MKGroupChild> children = new ArrayList<>();

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

        /** Disables this group and all its children when the predicate returns true. */
        public GroupBuilder disabledWhen(BooleanSupplier predicate) {
            this.disabledWhen = predicate; return this;
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

        // ── Terminal Methods ─────────────────────────────────────────────

        /** Closes this nested group and returns to the parent group. */
        public GroupBuilder done() {
            if (parentGroup == null) {
                throw new IllegalStateException(
                        "[MenuKit] .done() called on root group — use .build() instead");
            }
            parentGroup.children.add(new MKGroupChild.Group(buildDef()));
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

        private MKGroupDef buildDef() {
            return new MKGroupDef(mode, gap, cellSize, maxRows, fillRight,
                    List.copyOf(children), disabledWhen);
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
            private @Nullable Consumer<MKSlot> onEmptyClick;
            private @Nullable Supplier<Component> emptyTooltip;
            private int vanillaInventoryIndex = -1;

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
            public GSlotBuilder onEmptyClick(Consumer<MKSlot> callback) {
                this.onEmptyClick = callback; return this;
            }
            public GSlotBuilder emptyTooltip(Supplier<Component> tooltip) {
                this.emptyTooltip = tooltip; return this;
            }

            public GroupBuilder done() {
                if (containerName == null || containerIndex < 0) {
                    throw new IllegalStateException(
                            "[MenuKit] Group slot must call .container(name, index) before .done()");
                }
                // childX/childY are 0 — the group's layout mode determines position
                parent.children.add(new MKGroupChild.Slot(new MKSlotDef(
                        0, 0, containerName, containerIndex,
                        filter, maxStack, ghostIcon, disabledWhen,
                        vanillaInventoryIndex, onEmptyClick, emptyTooltip)));
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
                parent.children.add(new MKGroupChild.Button(new MKButtonDef(
                        0, 0, width, height,
                        icon, toggledIcon, iconSize, label,
                        toggleMode, initialPressed, groupName,
                        finalOnClick, onToggle, tooltip,
                        opensScreenName, opensScreenFactory, togglesPanelName,
                        resolvedStyle, disabled, disabledWhen, pressedWhen)));
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

            public GroupBuilder done() {
                if (content == null) {
                    throw new IllegalStateException(
                            "[MenuKit] Group text must call .content(...) before .done()");
                }
                parent.children.add(new MKGroupChild.Text(new MKTextDef(
                        0, 0, content, color, shadow, disabledWhen)));
                return parent;
            }
        }
    }
}
