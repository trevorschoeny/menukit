package com.trevlar.menukit.hud;

import com.trevlar.menukit.MK;
import com.trevlar.menukit.core.InsideRegion;
import com.trevlar.menukit.core.ItemDisplay;
import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.PanelPosition;
import com.trevlar.menukit.core.PanelStyle;
import com.trevlar.menukit.core.ProgressBar;
import com.trevlar.menukit.core.RenderContext;
import com.trevlar.menukit.core.TextLabel;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Builder entry point for HUD panels — visual elements rendered on the
 * game's heads-up display.
 *
 * <p>A HUD panel is a {@link Panel} in the HUD's host (§0065): render-only (no
 * input), placed on an {@link InsideRegion} spot of the game window, stacked with
 * the other panels on that spot in priority order, sized to its content. Build once
 * from your client initializer (registration freezes at client start); MenuKit
 * renders it every frame the HUD draws.
 *
 * <p>Holds {@link PanelElement}s — the same abstraction used in inventory
 * menus and standalone screens. Elements are context-neutral; the HUD panel
 * supplies their {@link RenderContext} with {@code mouseX = -1} at render
 * time to signal "no input dispatch."
 *
 * <p>Usage:
 * <pre>{@code
 * MKHudPanel.builder("coords")
 *     .region(InsideRegion.TOP_LEFT)
 *     .padding(4)
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
        private @Nullable InsideRegion region;      // required: where on the window
        private int offsetX = 0, offsetY = 0;
        private int priority = PanelPosition.DEFAULT_PRIORITY;
        private int padding = 0;
        private int width = -1, height = -1;        // outer size; -1 = size to content
        private PanelStyle style = PanelStyle.NONE;
        private BooleanSupplier showWhen = () -> true;
        private final List<PanelElement> elements = new ArrayList<>();

        Builder(String name) {
            this.name = name;
        }

        // ── Placement ────────────────────────────────────────────────

        /**
         * Where on the game window the panel sits: one of the nine
         * {@link InsideRegion} spots, 4px in from the edges it touches. Panels on
         * one spot stack (2px apart) in {@link #priority} order; {@code CENTER}
         * starts just below the crosshair. Required.
         */
        public Builder region(InsideRegion region) {
            this.region = region;
            return this;
        }

        /**
         * A pixel nudge after placement (positive x right, positive y down). Only this
         * panel moves; its siblings on the spot stack as if it had not.
         *
         * <p>Migrating from {@code .anchor(MKHudAnchor.X, dx, dy)}: the anchor sat on
         * the window edge, a region sits 4px in. Keep the old pixel position with
         * {@code .region(InsideRegion.X).offset(dx', dy')} where each axis that
         * touched an edge gives back the inset: {@code dx' = dx + 4} on a right
         * spot, {@code dx - 4} on a left one; the same for {@code dy} on bottom and
         * top spots. A centred axis keeps its offset. The one exception is
         * {@code CENTER}: the old anchor centred the panel on the window, while
         * {@code InsideRegion.CENTER} on the HUD starts 16px below the centre (clear
         * of the crosshair), so the old {@code CENTER, dx, dy} is
         * {@code offset(dx, dy - 16 - panelHeight / 2)}; for "just below the
         * crosshair", {@code .region(InsideRegion.CENTER)} alone says it.
         */
        public Builder offset(int dx, int dy) {
            this.offsetX = dx;
            this.offsetY = dy;
            return this;
        }

        /**
         * Stacking order among panels on the same spot (lower is nearer the spot's
         * edge), and render order (lower draws first). Default
         * {@link PanelPosition#DEFAULT_PRIORITY}. Ties break by mod id, then by
         * registration order.
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        // ── Panel configuration ──────────────────────────────────────

        /** Sets inner padding (space between panel edge and content). */
        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }

        /**
         * Sizes the panel to its content. This is the default (a HUD panel is a
         * {@link Panel}, which measures its elements); kept so existing builders
         * read the same.
         */
        public Builder autoSize() {
            this.width = -1;
            this.height = -1;
            return this;
        }

        /** Sets an explicit outer panel size, padding included. */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /** Sets the panel background style (RAISED, DARK, INSET, NONE). Default NONE. */
        public Builder style(PanelStyle style) {
            this.style = style;
            return this;
        }

        /**
         * The panel shows only while this is true (the panel's own
         * {@link Panel#showWhen}). A hidden panel is not measured and takes no room
         * in its spot's stack. To hide while a screen is open (the old
         * {@code hideInScreen()}), include it:
         * {@code .showWhen(() -> Minecraft.getInstance().gui.screen() == null && ...)}.
         */
        public Builder showWhen(BooleanSupplier condition) {
            this.showWhen = condition;
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
            // HUD default styling: white, shadow on (1× scale, no backdrop, no
            // wrap are the TextLabel defaults). Folded onto TextLabel — the former
            // HUD-only MKHudText is gone; HUD text is now a TextLabel variant.
            elements.add(TextLabel.builder().at(x, y)
                    .text(() -> Component.literal(text.get()))
                    .color(0xFFFFFFFF).shadow(true)
                    .build());
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
            elements.add(ItemDisplay.builder().at(x, y).item(item).build());
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
         * Builds the HUD panel and registers it; from then on it renders every frame
         * the in-game HUD draws (never with input: the HUD routes none). A HUD panel
         * is an ordinary {@link Panel} positioned
         * {@code screenAnchor(region).offset(dx, dy).priority(p)} in the HUD's host.
         *
         * @return the panel (for its id, or to toggle its visibility)
         * @throws IllegalStateException if no {@link #region} was set, or after
         *         MenuKit's declarations froze at client start
         */
        public Panel build() {
            if (region == null) {
                throw new IllegalStateException("MenuKit: HUD panel '" + name
                        + "' has no region. Call .region(InsideRegion.X) (and .offset(dx, dy) to nudge).");
            }
            Panel panel = Panel.builder(name)
                    .elements(elements)
                    .style(style)
                    .position(PanelPosition.screenAnchor(region)
                            .offset(offsetX, offsetY)
                            .priority(priority))
                    .build();
            panel.showWhen(showWhen);
            if (width >= 0 && height >= 0) {
                panel.size(Math.max(0, width - 2 * padding), Math.max(0, height - 2 * padding));
            }
            MK.registerHud(panel, padding);
            return panel;
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
        private int wrapWidth = 0;
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

        /**
         * Wraps this HUD text to {@code maxWidth} pixels: it renders multi-line and
         * the auto-sized HUD panel grows to fit the wrapped height. Zero (default) =
         * single line. A HUD panel is consumer-anchored and consumer-sized, so the
         * wrap width is declared here directly rather than computed from a
         * screen-edge budget — the adaptive auto-wrap (which reacts to the room a
         * panel's anchor leaves) is a Panel-context feature; a HUD author specifies
         * the width. Folded-in TextLabel wrapping is what makes this possible (the
         * former MKHudText could not wrap).
         *
         * @param maxWidth wrap width in pixels (font space), or 0 to disable
         * @return this text builder, for chaining
         */
        public TextBuilder wrapWidth(int maxWidth) { this.wrapWidth = maxWidth; return this; }

        public TextBuilder onRender(Runnable callback) { this.onRender = callback; return this; }

        public Builder done() {
            // HUD text is a TextLabel variant now (the former MKHudText is folded
            // away). Configure scale/backdrop/onRender via the fluent chain; wrap
            // width is set directly (it's consumer-declared for the HUD, not
            // Panel-budget-driven).
            TextLabel.Builder label = TextLabel.builder().at(x, y).text(text).color(color).shadow(shadow)
                    .scale(scale).backdrop(backdrop).wrapWidth(wrapWidth);
            if (onRender != null) label.onRender(onRender);
            parent.elements.add(label.build());
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
        // Count + durability overlays show by default; these suppress them (icon-only),
        // mirroring SlotBuilder. (The old showCount()/showDurability() only re-set the
        // default — they could never actually hide — so the hide verbs replace them.)
        public ItemBuilder hideCount() { this.showCount = false; return this; }
        public ItemBuilder hideDurability() { this.showDurability = false; return this; }

        public Builder done() {
            ItemDisplay.Builder display = ItemDisplay.builder().at(x, y).item(item).size(size, size);
            if (!showCount) display.hideCount();
            if (!showDurability) display.hideDurability();
            parent.elements.add(display.build());
            return parent;
        }
    }

    /** Builder for customizing progress bar elements. */
    public static class BarBuilder {
        private final Builder parent;
        private final int x, y, barW, barH;
        // Normalized 0.0–1.0 bar value as a DoubleSupplier — the canonical
        // numeric-supplier shape ProgressBar (and Slider/ScrollContainer) read,
        // so a double-valued source needs no box-and-cast here.
        private DoubleSupplier value = () -> 0.0;
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

        public BarBuilder value(DoubleSupplier value) { this.value = value; return this; }
        public BarBuilder color(int color) { this.fillColor = color; return this; }
        public BarBuilder bgColor(int color) { this.bgColor = color; return this; }
        public BarBuilder direction(ProgressBar.Direction dir) { this.direction = dir; return this; }
        public BarBuilder label(Supplier<Component> label) { this.label = label; return this; }

        public Builder done() {
            ProgressBar.Builder bar = ProgressBar.builder().at(x, y).size(barW, barH)
                    .value(value).direction(direction).fillColor(fillColor).bgColor(bgColor);
            if (label != null) bar.label(label);
            parent.elements.add(bar.build());
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
