package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MenuKit;
import com.trevorschoeny.menukit.core.ItemDisplay;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.ProgressBar;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builder entry point for HUD panels — visual elements rendered on the
 * game's heads-up display.
 *
 * <p>HUD panels are render-only (no input dispatch). They anchor to screen
 * edges, update dynamically via {@code Supplier<T>}, and auto-size to fit
 * their content. Build once at mod init — MenuKit handles rendering.
 *
 * <p>Holds {@link PanelElement}s — the same abstraction used in inventory
 * menus and standalone screens. Elements are context-neutral; the HUD panel
 * supplies their {@link RenderContext} with {@code mouseX = -1} at render
 * time to signal "no input dispatch."
 *
 * <p>Usage:
 * <pre>{@code
 * MKHudPanel.builder("coords")
 *     .anchor(MKHudAnchor.TOP_LEFT, 4, 4)
 *     .padding(4).autoSize()
 *     .style(PanelStyle.RAISED)
 *     .text(0, 0, () -> "X: " + (int) player.getX())
 *     .text(0, 12, () -> "Y: " + (int) player.getY())
 *     .build();
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudPanel {

    private MKHudPanel() {} // static API only

    /**
     * Creates a new HUD panel builder.
     *
     * @param name unique identifier for this panel (used for visibility toggling)
     * @return the builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════

    public static class Builder {
        private final String name;
        private MKHudAnchor anchor = MKHudAnchor.TOP_LEFT;
        private int offsetX = 0, offsetY = 0;
        private int padding = 0;
        private boolean autoSize = false;
        private int width = 0, height = 0;
        private PanelStyle style = PanelStyle.NONE;
        private Supplier<Boolean> showWhen = () -> true;
        private boolean hideInScreen = false; // default: stay visible like vanilla HUD
        private MKHudPanelDef.HudRenderCallback onRender; // nullable
        private final List<PanelElement> elements = new ArrayList<>();

        Builder(String name) {
            this.name = name;
        }

        // ── Panel configuration ──────────────────────────────────────

        /** Sets the screen-edge anchor and offset. */
        public Builder anchor(MKHudAnchor anchor, int offsetX, int offsetY) {
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            return this;
        }

        /** Sets inner padding (space between panel edge and content). */
        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }

        /** Enables auto-sizing — panel grows to fit its children. */
        public Builder autoSize() {
            this.autoSize = true;
            return this;
        }

        /** Sets explicit panel size (ignored if autoSize is enabled). */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /** Sets the panel background style (RAISED, DARK, INSET, NONE). */
        public Builder style(PanelStyle style) {
            this.style = style;
            return this;
        }

        /** Sets a visibility condition — panel only renders when true. */
        public Builder showWhen(Supplier<Boolean> condition) {
            this.showWhen = condition;
            return this;
        }

        /** Panel hides when a screen is open (default). */
        public Builder hideInScreen() {
            this.hideInScreen = true;
            return this;
        }

        /** Panel stays visible even when a screen is open. */
        public Builder showInScreen() {
            this.hideInScreen = false;
            return this;
        }

        /** Adds a custom render callback that fires each frame. */
        public Builder onRender(MKHudPanelDef.HudRenderCallback callback) {
            this.onRender = callback;
            return this;
        }

        // ── Child elements (simple shortcuts) ─────────────────────────

        /**
         * Adds a text element with default styling (white, shadow, 1x scale).
         *
         * @param x    panel-relative X position
         * @param y    panel-relative Y position
         * @param text supplier that returns the text to display each frame
         */
        public Builder text(int x, int y, Supplier<String> text) {
            elements.add(new MKHudText(x, y,
                    () -> Component.literal(text.get()),
                    0xFFFFFFFF, true, 1.0f, false, null));
            return this;
        }

        /**
         * Adds a text element and returns a TextBuilder for customization.
         */
        public TextBuilder text(int x, int y) {
            return new TextBuilder(this, x, y);
        }

        /**
         * Adds an item icon at native 16×16 size. Count and durability
         * overlays default to visible (matching vanilla item rendering).
         */
        public Builder item(int x, int y, Supplier<ItemStack> item) {
            elements.add(new ItemDisplay(x, y, item));
            return this;
        }

        /**
         * Adds an item icon and returns an ItemBuilder for customization.
         */
        public ItemBuilder item(int x, int y) {
            return new ItemBuilder(this, x, y);
        }

        /**
         * Adds a hotbar-style slot with an item inside.
         * Uses the vanilla hotbar sprite for authentic look.
         *
         * @param x    panel-relative X position
         * @param y    panel-relative Y position
         * @param item supplier that returns the ItemStack to display each frame
         */
        public Builder slot(int x, int y, Supplier<ItemStack> item) {
            elements.add(new MKHudSlot(x, y, item, true, true, null));
            return this;
        }

        /**
         * Adds a hotbar-style slot and returns a SlotBuilder for customization.
         */
        public SlotBuilder slot(int x, int y) {
            return new SlotBuilder(this, x, y);
        }

        /**
         * Adds a progress bar and returns a BarBuilder for configuration.
         */
        public BarBuilder bar(int x, int y, int barWidth, int barHeight) {
            return new BarBuilder(this, x, y, barWidth, barHeight);
        }

        /**
         * Adds a sprite icon.
         */
        public Builder icon(int x, int y, Identifier sprite, int w, int h) {
            elements.add(new MKHudIcon(x, y, sprite, w, h, null));
            return this;
        }

        /**
         * Adds a custom render region. The lambda receives the per-frame
         * {@link RenderContext} for its position and graphics handle.
         * The declared width/height are the element's bounds for layout.
         *
         * @param childX panel-relative X position
         * @param childY panel-relative Y position
         * @param width  region width (used for auto-sizing)
         * @param height region height (used for auto-sizing)
         * @param renderFn render callback invoked each frame
         */
        public Builder custom(int childX, int childY, int width, int height,
                              Consumer<RenderContext> renderFn) {
            elements.add(new PanelElement() {
                @Override public int getChildX() { return childX; }
                @Override public int getChildY() { return childY; }
                @Override public int getWidth() { return width; }
                @Override public int getHeight() { return height; }
                @Override public void render(RenderContext ctx) { renderFn.accept(ctx); }
            });
            return this;
        }

        /**
         * Adds any panel element directly. Escape hatch for consumers that
         * implement {@link PanelElement} themselves.
         */
        public Builder element(PanelElement element) {
            elements.add(element);
            return this;
        }

        // ── Build ─────────────────────────────────────────────────────

        /**
         * Builds the HUD panel definition and registers it with MenuKit.
         * After this call, the panel renders automatically each frame.
         */
        public void build() {
            MKHudPanelDef def = new MKHudPanelDef(
                    name, anchor, offsetX, offsetY,
                    padding, autoSize, width, height, style,
                    List.copyOf(elements),
                    showWhen, hideInScreen, onRender
            );
            MenuKit.registerHud(def);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Sub-builders
    // ═══════════════════════════════════════════════════════════════════

    /** Builder for customizing text elements. */
    public static class TextBuilder {
        private final Builder parent;
        private final int x, y;
        private Supplier<Component> text = Component::empty;
        private int color = 0xFFFFFFFF;
        private boolean shadow = true;
        private float scale = 1.0f;
        private boolean backdrop = false;
        private @Nullable Runnable onRender;

        TextBuilder(Builder parent, int x, int y) {
            this.parent = parent;
            this.x = x;
            this.y = y;
        }

        public TextBuilder text(Supplier<String> supplier) {
            this.text = () -> Component.literal(supplier.get());
            return this;
        }

        public TextBuilder component(Supplier<Component> supplier) {
            this.text = supplier;
            return this;
        }

        public TextBuilder color(int color) { this.color = color; return this; }
        public TextBuilder noShadow() { this.shadow = false; return this; }
        public TextBuilder scale(float scale) { this.scale = scale; return this; }
        public TextBuilder backdrop() { this.backdrop = true; return this; }
        public TextBuilder onRender(Runnable callback) { this.onRender = callback; return this; }

        public Builder done() {
            parent.elements.add(new MKHudText(x, y, text, color, shadow, scale, backdrop, onRender));
            return parent;
        }
    }

    /** Builder for customizing item display elements. */
    public static class ItemBuilder {
        private final Builder parent;
        private final int x, y;
        private Supplier<ItemStack> item = () -> ItemStack.EMPTY;
        private int size = 16;
        private boolean showCount = true;    // default: show item count
        private boolean showDurability = true; // default: show durability bar

        ItemBuilder(Builder parent, int x, int y) {
            this.parent = parent;
            this.x = x;
            this.y = y;
        }

        public ItemBuilder item(Supplier<ItemStack> item) { this.item = item; return this; }
        public ItemBuilder size(int size) { this.size = size; return this; }
        public ItemBuilder showCount() { this.showCount = true; return this; }
        public ItemBuilder showDurability() { this.showDurability = true; return this; }

        public Builder done() {
            parent.elements.add(new ItemDisplay(x, y, size, item, showCount, showDurability));
            return parent;
        }
    }

    /** Builder for customizing progress bar elements. */
    public static class BarBuilder {
        private final Builder parent;
        private final int x, y, barW, barH;
        private Supplier<Float> value = () -> 0f;
        private int fillColor = ProgressBar.DEFAULT_FILL_COLOR;
        private int bgColor = ProgressBar.DEFAULT_BG_COLOR;
        private ProgressBar.Direction direction = ProgressBar.DEFAULT_DIRECTION;
        private @Nullable Supplier<Component> label;

        BarBuilder(Builder parent, int x, int y, int w, int h) {
            this.parent = parent;
            this.x = x;
            this.y = y;
            this.barW = w;
            this.barH = h;
        }

        public BarBuilder value(Supplier<Float> value) { this.value = value; return this; }
        public BarBuilder color(int color) { this.fillColor = color; return this; }
        public BarBuilder bgColor(int color) { this.bgColor = color; return this; }
        public BarBuilder direction(ProgressBar.Direction dir) { this.direction = dir; return this; }
        public BarBuilder label(Supplier<Component> label) { this.label = label; return this; }

        public Builder done() {
            parent.elements.add(new ProgressBar(x, y, barW, barH,
                    value, direction, fillColor, bgColor, label));
            return parent;
        }
    }

    /** Builder for customizing HUD slot elements. */
    public static class SlotBuilder {
        private final Builder parent;
        private final int x, y;
        private Supplier<ItemStack> item = () -> ItemStack.EMPTY;
        private boolean showCount = true;
        private boolean showDurability = true;
        private @Nullable Runnable onRender;

        SlotBuilder(Builder parent, int x, int y) {
            this.parent = parent;
            this.x = x;
            this.y = y;
        }

        public SlotBuilder item(Supplier<ItemStack> item) { this.item = item; return this; }
        public SlotBuilder hideCount() { this.showCount = false; return this; }
        public SlotBuilder hideDurability() { this.showDurability = false; return this; }
        public SlotBuilder onRender(Runnable callback) { this.onRender = callback; return this; }

        public Builder done() {
            parent.elements.add(new MKHudSlot(x, y, item, showCount, showDurability, onRender));
            return parent;
        }
    }
}
