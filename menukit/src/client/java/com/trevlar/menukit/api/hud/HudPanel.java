package com.trevlar.menukit.api.hud;

import com.trevlar.menukit.api.MK;
import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.element.ItemDisplay;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.element.ProgressBar;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.TextLabel;

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
 * Builds HUD panels: panels drawn on the game's heads-up display.
 *
 * <p>A HUD panel is a {@link Panel} in the HUD's host: render only (no input),
 * placed on an {@link InsideRegion} spot of the game window or in an
 * {@link OutsideRegion} around the vanilla hotbar, stacked with the other panels
 * there in priority order, sized to its content. Build once
 * from your client initializer (registration freezes at client start); MenuKit
 * renders it every frame the HUD draws.
 *
 * <p>It holds the same {@link PanelElement}s as inventory menus and standalone
 * screens. The HUD gives them a {@link RenderContext} with {@code mouseX = -1},
 * meaning no input.
 *
 * <p>Usage:
 * <pre>{@code
 * HudPanel.builder("mymod:coords", ModeScope.BOTH)
 *     .region(InsideRegion.TOP_LEFT)
 *     .padding(4)
 *     .style(PanelStyle.RAISED)
 *     .text(0, 0, () -> "X: " + (int) player.getX())
 *     .text(0, 12, () -> "Y: " + (int) player.getY())
 *     .build();
 * }</pre>
 */
public class HudPanel {

    private HudPanel() {} // static API only

    /**
     * The scale for a {@link SlotBuilder#scale scaled} hotbar slot in the
     * {@link Builder#statusColumn status column} (since 7.3.0): 6/11, so the 20 x 22
     * slot takes 11 x 12 and its item is about a heart's size. Use it rather than a
     * number of your own, so every mod's icons in the column match.
     */
    public static final float STATUS_SCALE = 6f / 11f;

    /**
     * Starts a HUD panel builder for one mode scope (7.4.0): the panel shows only in that
     * mode ({@link ModeScope#BOTH} for either), read every frame, so it comes and goes with
     * {@code /gamemode}.
     *
     * @param name  unique id for this panel, namespaced ({@code "mymod:coords"})
     * @param scope the mode it shows in
     */
    public static Builder builder(String name, ModeScope scope) {
        return new Builder(name, java.util.Objects.requireNonNull(scope, "scope"));
    }

    /**
     * Starts a HUD panel builder, for both modes.
     *
     * @param name unique id for this panel
     * @deprecated Replaced in 7.4.0 by {@link #builder(String, ModeScope)}, which picks the
     *             mode scope; removed in 8.0.0. {@code builder(name, ModeScope.BOTH)} is this call.
     */
    @Deprecated(since = "7.4.0", forRemoval = true)
    public static Builder builder(String name) {
        return new Builder(name, ModeScope.BOTH);
    }

    public static class Builder {
        private final String name;
        private final ModeScope scope;
        // Required, exactly one: a spot on the window, or a region around the hotbar.
        private @Nullable InsideRegion region;
        private @Nullable OutsideRegion aroundHotbar;
        private boolean statusColumn;
        private int offsetX = 0, offsetY = 0;
        private int priority = PanelPosition.DEFAULT_PRIORITY;
        private @Nullable Integer gap;              // null = the HUD's 2px
        private int padding = 0;
        private int width = -1, height = -1;        // outer size; -1 = size to content
        private PanelStyle style = PanelStyle.NONE;
        private BooleanSupplier visibleWhen = () -> true;
        private boolean fixed = false;
        private final List<PanelElement> elements = new ArrayList<>();

        Builder(String name, ModeScope scope) {
            this.name = name;
            this.scope = scope;
        }

        /**
         * Where on the game window the panel sits: one of the nine
         * {@link InsideRegion} spots, 4px in from the edges it touches. Panels on
         * one spot stack (2px apart) in {@link #priority} order; {@code CENTER}
         * starts just below the crosshair. Required.
         */
        public Builder region(InsideRegion region) {
            this.region = region;
            this.aroundHotbar = null;
            this.statusColumn = false;
            return this;
        }

        /**
         * Places the panel around the vanilla hotbar instead of on a window spot
         * (since 7.1.0). The hotbar is the reference: {@code LEFT_ALIGN_BOTTOM} sits
         * 2px left of it with its bottom on the hotbar's bottom,
         * {@code RIGHT_ALIGN_BOTTOM} the same on the right, {@code TOP_CENTER} above it.
         * Panels in one region stack away from the aligned end (2px apart) in
         * {@link #priority} order, and a hidden one ({@link #visibleWhen}) takes no
         * room, so the icons of several mods line up beside the hotbar with no gaps
         * and none on top of another, whichever mods are installed. It follows the
         * hotbar at every GUI scale and window size.
         *
         * <p>The hotbar here is vanilla's 182 x 22 bar. While vanilla draws the offhand
         * slot beside it (the offhand holds something: on the left for a right-handed
         * player, on the right for a left-handed one), the side regions on that side
         * start 2px beyond the slot instead, so a panel there never covers it. The
         * other side, and the regions above and below the hotbar, do not move.
         */
        public Builder region(OutsideRegion aroundHotbar) {
            this.aroundHotbar = aroundHotbar;
            this.region = null;
            this.statusColumn = false;
            return this;
        }

        /**
         * Places the panel in the status column (since 7.3.0): the 20 px gap between
         * the hearts and the hunger bar, centred on the hotbar, from a fixed origin
         * pinned to it: the lowest panel rests right on the XP level number, level
         * with the hearts and armour rows. Panels there stack upward in
         * {@link #priority} order (lowest nearest the number), centred, 2px apart
         * unless {@link #gap} says otherwise, and a hidden one takes no room. Where the
         * game mode shows no status bars (creative, spectator) the column starts 2px
         * above the hotbar. It stays put at level 0, when vanilla hides the number.
         *
         * <p>While the column reaches them, vanilla's held-item name, its action-bar
         * message and the chat move up by exactly as far as it rises above their
         * bottom edge, and back when it doesn't, whichever mods put panels there.
         *
         * <p>Meant for small things such as a status icon in a {@link #slot hotbar
         * slot} scaled by {@link HudPanel#STATUS_SCALE}: a panel wider than 20 px
         * overlaps the hearts and hunger.
         */
        public Builder statusColumn() {
            this.statusColumn = true;
            this.aroundHotbar = OutsideRegion.TOP_CENTER;
            this.region = null;
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
         * edge; around the hotbar, nearer the region's aligned end), and render order
         * (lower draws first). Default {@link PanelPosition#DEFAULT_PRIORITY}. Ties
         * break by mod id, then by registration order.
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * The space before this panel in its stack, in pixels (since 7.1.0): the
         * {@link PanelPosition#gap(int)} of the panel's position. Around the hotbar it
         * is the gap off the hotbar (or the offhand slot) and off the sibling before
         * it; on a spot, the gap off the sibling before it (the first keeps the 4px
         * edge inset). Default 2. A negative gap is refused when {@link #build} runs.
         */
        public Builder gap(int gap) {
            this.gap = gap;
            return this;
        }

        /** Inner padding between the panel edge and its content. */
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
         * {@link Panel#visibleWhen}). A hidden panel is not measured and takes no room
         * in its spot's stack. To hide while a screen is open (the old
         * {@code hideInScreen()}), include it:
         * {@code .visibleWhen(() -> Minecraft.getInstance().gui.screen() == null && ...)}.
         */
        public Builder visibleWhen(BooleanSupplier condition) {
            this.visibleWhen = condition;
            return this;
        }

        /**
         * The player can't move this panel (7.4.0), as {@link Panel.Builder#fixed()}: for a
         * panel whose place is part of what it shows, or one that draws from the window
         * rather than from where it is placed. Without it a player may move the panel on the
         * HUD screen of MenuKit's edit mode.
         */
        public Builder fixed() {
            this.fixed = true;
            return this;
        }

        /**
         * Adds a text element with default styling (white, shadow, 1x scale).
         *
         * @param x    panel-relative X position
         * @param y    panel-relative Y position
         * @param text supplier that returns the text to display each frame
         */
        public Builder text(int x, int y, Supplier<String> text) {
            // HUD default: white with a shadow. Scale 1, no backdrop and no wrap
            // are TextLabel's own defaults.
            elements.add(TextLabel.builder().at(x, y)
                    .text(() -> Component.literal(text.get()))
                    .color(0xFFFFFFFF).shadow(true)
                    .build());
            return this;
        }

        /** Adds a text element and returns a TextBuilder to configure it. */
        public TextBuilder text(int x, int y) {
            return new TextBuilder(this, x, y);
        }

        /** Adds a 16x16 item icon, with count and durability shown as vanilla shows them. */
        public Builder item(int x, int y, Supplier<ItemStack> item) {
            elements.add(ItemDisplay.builder().at(x, y).item(item).build());
            return this;
        }

        /** Adds an item icon and returns an ItemBuilder to configure it. */
        public ItemBuilder item(int x, int y) {
            return new ItemBuilder(this, x, y);
        }

        /**
         * Adds a hotbar slot, drawn with vanilla's hotbar sprite, holding an item.
         *
         * @param x    panel-relative X position
         * @param y    panel-relative Y position
         * @param item supplier that returns the ItemStack to display each frame
         */
        public Builder slot(int x, int y, Supplier<ItemStack> item) {
            elements.add(new HudSlot(x, y, item, true, true, null, 1f));
            return this;
        }

        /** Adds a hotbar slot and returns a SlotBuilder to configure it. */
        public SlotBuilder slot(int x, int y) {
            return new SlotBuilder(this, x, y);
        }

        /** Adds a progress bar and returns a BarBuilder to configure it. */
        public BarBuilder bar(int x, int y, int barWidth, int barHeight) {
            return new BarBuilder(this, x, y, barWidth, barHeight);
        }

        /** Adds a sprite icon. */
        public Builder icon(int x, int y, Identifier sprite, int w, int h) {
            elements.add(new HudIcon(x, y, sprite, w, h, null));
            return this;
        }

        /**
         * Adds a custom drawn area. The callback gets each frame's
         * {@link RenderContext} for its position and graphics. The width and height
         * are the element's bounds for layout.
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
         * Adds any panel element, for mods that implement {@link PanelElement}
         * themselves.
         */
        public Builder element(PanelElement element) {
            elements.add(element);
            return this;
        }

        /**
         * Builds the HUD panel and registers it; from then on it renders every frame
         * the in-game HUD draws (never with input: the HUD routes none). A HUD panel
         * is an ordinary {@link Panel} positioned
         * {@code screenAnchor(region).offset(dx, dy).priority(p)} in the HUD's host, or
         * {@code region(outsideRegion)...} for one around the hotbar.
         *
         * @return the panel (for its id, or to toggle its visibility)
         * @throws IllegalStateException if no {@link #region} was set, or after
         *         MenuKit's declarations froze at client start
         */
        public Panel build() {
            if (region == null && aroundHotbar == null) {
                throw new IllegalStateException("MenuKit: HUD panel '" + name
                        + "' has no region. Call .region(InsideRegion.X) for a spot on the window or"
                        + " .region(OutsideRegion.X) for one around the hotbar (and .offset(dx, dy) to nudge).");
            }
            PanelPosition where = region != null
                    ? PanelPosition.screenAnchor(region)
                    : PanelPosition.region(aroundHotbar);
            // PanelPosition.gap validates (and throws on a negative gap) in one place.
            if (gap != null) where = where.gap(gap);
            Panel.Builder builder = Panel.builder(name)
                    .elements(elements)
                    .style(style)
                    .in(scope, Placement.screen(where.offset(offsetX, offsetY).priority(priority)));
            if (fixed) builder.fixed();
            Panel panel = builder.build();
            panel.visibleWhen(visibleWhen);
            if (width >= 0 && height >= 0) {
                panel.size(Math.max(0, width - 2 * padding), Math.max(0, height - 2 * padding));
            }
            if (statusColumn) MK.registerHudStatus(panel, padding);
            else MK.registerHud(panel, padding);
            return panel;
        }
    }

    /** Configures a text element. */
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
         * Wraps this text at {@code maxWidth} pixels, so it runs over several lines
         * and an auto-sized HUD panel grows to fit. Zero (the default) keeps one
         * line. On the HUD you set the width yourself; the automatic wrapping that
         * follows the room a panel's anchor leaves applies to other panels only.
         *
         * @param maxWidth wrap width in pixels (font space), or 0 to disable
         * @return this text builder, for chaining
         */
        public TextBuilder wrapWidth(int maxWidth) { this.wrapWidth = maxWidth; return this; }

        public TextBuilder onRender(Runnable callback) { this.onRender = callback; return this; }

        public Builder done() {
            TextLabel.Builder label = TextLabel.builder().at(x, y).text(text).color(color).shadow(shadow)
                    .scale(scale).backdrop(backdrop).wrapWidth(wrapWidth);
            if (onRender != null) label.onRender(onRender);
            parent.elements.add(label.build());
            return parent;
        }
    }

    /** Configures an item display element. */
    public static class ItemBuilder {
        private final Builder parent;
        private final int x, y;
        private Supplier<ItemStack> item = () -> ItemStack.EMPTY;
        private int size = 16;
        private boolean showCount = true;
        private boolean showDurability = true;

        ItemBuilder(Builder parent, int x, int y) {
            this.parent = parent;
            this.x = x;
            this.y = y;
        }

        public ItemBuilder item(Supplier<ItemStack> item) { this.item = item; return this; }
        public ItemBuilder size(int size) { this.size = size; return this; }
        // Count and durability show by default; these hide them, as on SlotBuilder.
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

    /** Configures a progress bar element. */
    public static class BarBuilder {
        private final Builder parent;
        private final int x, y, barW, barH;
        // The bar's 0.0 to 1.0 value, as the DoubleSupplier ProgressBar reads.
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

    /** Configures a HUD slot element. */
    public static class SlotBuilder {
        private final Builder parent;
        private final int x, y;
        private Supplier<ItemStack> item = () -> ItemStack.EMPTY;
        private boolean showCount = true;
        private boolean showDurability = true;
        private @Nullable Runnable onRender;
        private float scale = 1f;

        SlotBuilder(Builder parent, int x, int y) {
            this.parent = parent;
            this.x = x;
            this.y = y;
        }

        public SlotBuilder item(Supplier<ItemStack> item) { this.item = item; return this; }
        public SlotBuilder hideCount() { this.showCount = false; return this; }
        public SlotBuilder hideDurability() { this.showDurability = false; return this; }
        public SlotBuilder onRender(Runnable callback) { this.onRender = callback; return this; }

        /**
         * Draws the slot, item and decorations together at {@code scale} (since 7.3.0),
         * 1 by default. The element's size follows, rounded up: at
         * {@link HudPanel#STATUS_SCALE}, the {@link Builder#statusColumn status
         * column}'s size, the 20 x 22 slot takes 11 x 12.
         *
         * @throws IllegalArgumentException unless {@code scale} is above 0
         */
        public SlotBuilder scale(float scale) {
            if (!(scale > 0f)) throw new IllegalArgumentException("MenuKit: a HUD slot's scale must be above 0, got " + scale);
            this.scale = scale;
            return this;
        }

        public Builder done() {
            parent.elements.add(new HudSlot(x, y, item, showCount, showDurability, onRender, scale));
            return parent;
        }
    }
}
