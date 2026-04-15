package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An interactive button within a {@link Panel}. Renders a raised panel
 * background with centered text. Supports hover detection, a click handler,
 * and an optional disabled predicate.
 *
 * <p>This is MenuKit's core button abstraction. The default form renders
 * centered text on a raised panel background. An icon-only variant is
 * available via {@link #icon(int, int, int, Identifier, Consumer)} and its
 * supplier overload.
 *
 * <p>Left-click only by default. Right-clicks and middle-clicks fall through
 * to vanilla's slot handling. Custom element implementations can handle
 * any mouse button.
 *
 * <p>Rendering styles:
 * <ul>
 *   <li><b>Normal:</b> raised panel background, white text with shadow</li>
 *   <li><b>Hovered:</b> raised panel background + translucent highlight, white text</li>
 *   <li><b>Disabled:</b> dark panel background, gray text</li>
 * </ul>
 *
 * <h3>Extension points for consumer subclasses</h3>
 *
 * Consumer code can customize Button rendering by subclassing and overriding
 * the protected hooks: {@link #renderBackground(RenderContext, int, int)} and
 * {@link #renderContent(RenderContext, int, int)}. Those methods carry a
 * stability contract — their signatures and semantic contracts are maintained
 * across MenuKit versions. See their javadocs for the contract details.
 *
 * <p>The top-level {@link #render(RenderContext)} is {@code final} — it
 * orchestrates hover-state update, background paint, content paint, and
 * tooltip dispatch in that order. The extension surface is the hooks, not
 * the orchestration.
 *
 * @see PanelElement  The interface this implements
 * @see TextLabel     Non-interactive text element
 */
public class Button implements PanelElement {

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final Component text;
    private final Consumer<Button> onClick;
    private final @Nullable BooleanSupplier disabledWhen;

    // Optional hover-triggered tooltip. Set via .tooltip(...) during
    // construction chain; post-construction configuration per the Tooltip
    // design doc's setter convention (optional orthogonal feature).
    private @Nullable Supplier<Component> tooltipSupplier;

    // Hover state — updated each render frame. Not persisted.
    // Read by mouseClicked to gate the click on hover.
    private boolean hovered = false;

    /**
     * @param childX       X position within panel content area
     * @param childY       Y position within panel content area
     * @param width        button width in pixels
     * @param height       button height in pixels
     * @param text         button label
     * @param onClick      fired on left-click when enabled
     * @param disabledWhen returns true when the button should be disabled (grayed, non-clickable),
     *                     or null for always enabled
     */
    public Button(int childX, int childY, int width, int height,
                  Component text, Consumer<Button> onClick,
                  @Nullable BooleanSupplier disabledWhen) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.text = text;
        this.onClick = onClick;
        this.disabledWhen = disabledWhen;
    }

    /** Convenience: always-enabled button. */
    public Button(int childX, int childY, int width, int height,
                  Component text, Consumer<Button> onClick) {
        this(childX, childY, width, height, text, onClick, null);
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    /** Returns the button's display text. */
    public Component getText() { return text; }

    /** Returns whether the button is currently disabled. */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Returns whether the mouse is currently over this button (updated each frame). */
    public boolean isHovered() { return hovered; }

    // ── Tooltip (optional hover-triggered configuration) ───────────────

    /**
     * Attaches a hover-triggered tooltip with fixed text. Returns this
     * Button for method chaining. Tooltip renders at the mouse position
     * using vanilla's tooltip styling.
     *
     * <p>Post-construction configuration setter — intended to be called
     * once during the construction chain. See
     * {@code Design Docs/Element Design Docs/TOOLTIP_DESIGN_DOC.md}.
     */
    public Button tooltip(Component text) {
        return tooltip(() -> text);
    }

    /**
     * Attaches a hover-triggered tooltip with supplier-driven text. The
     * supplier is invoked each frame while hovered. Returns this Button
     * for method chaining.
     */
    public Button tooltip(Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    // ── Rendering ──────────────────────────────────────────────────────

    /**
     * Orchestrates the render pass: coordinate compute, hover-state update,
     * background paint, content paint, and tooltip dispatch in that order.
     * Final by design — the extension surface for consumer subclasses is the
     * two protected hooks ({@link #renderBackground} and {@link #renderContent}),
     * not this orchestration method.
     */
    @Override
    public final void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;

        // Update hover state from current mouse position. In contexts without
        // input dispatch (HUDs) isHovered() returns false, so `hovered` stays
        // false regardless of where the mouse cursor actually is.
        hovered = isHovered(ctx);

        renderBackground(ctx, sx, sy);
        renderContent(ctx, sx, sy);

        // Hover-triggered tooltip — setTooltipForNextFrame defers the tooltip
        // draw to end-of-frame (correct z-ordering above items and other
        // elements). The 1.21.11 method name is setTooltipForNextFrame;
        // earlier versions called this renderTooltip.
        if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                ctx.graphics().setTooltipForNextFrame(
                        Minecraft.getInstance().font, ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    /**
     * Paints the button's panel background — raised when enabled, dark when
     * disabled, plus a translucent highlight overlay when hovered. Called
     * before {@link #renderContent}.
     *
     * <p><b>Stable extension point for consumer Button subclasses.</b> The
     * signature {@code (RenderContext ctx, int sx, int sy)} and the semantic
     * contract — {@code sx}/{@code sy} are the absolute screen-space top-left
     * of the button, this hook runs before {@link #renderContent}, this hook
     * does not mutate Button state — are maintained across MenuKit versions.
     * Consumer subclasses may rely on these properties.
     *
     * <p>Override this hook to paint a custom background while keeping the
     * default content rendering, or call {@code super.renderBackground(...)}
     * and layer additional painting on top.
     */
    protected void renderBackground(RenderContext ctx, int sx, int sy) {
        boolean disabled = isDisabled();
        if (disabled) {
            PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, PanelStyle.DARK);
        } else {
            PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, PanelStyle.RAISED);
            if (hovered) {
                // Translucent highlight overlay (inside the border)
                ctx.graphics().fill(sx + 1, sy + 1, sx + width - 1, sy + height - 1,
                        0x30FFFFFF);
            }
        }
    }

    /**
     * Paints the button's content — by default, the centered text label.
     * Called after {@link #renderBackground}, before tooltip dispatch.
     *
     * <p><b>Stable extension point for consumer Button subclasses.</b> The
     * signature {@code (RenderContext ctx, int sx, int sy)} and the semantic
     * contract — {@code sx}/{@code sy} are the absolute screen-space top-left
     * of the button, this hook runs after {@link #renderBackground}, this hook
     * does not mutate Button state — are maintained across MenuKit versions.
     * Consumer subclasses may rely on these properties.
     *
     * <p>Override this hook to paint custom content (icon, multi-line text,
     * composite visuals) while keeping the default panel-style background.
     */
    protected void renderContent(RenderContext ctx, int sx, int sy) {
        // Text — centered within the button bounds
        // 1.21.11 ARGB requirement: colors must have a non-zero alpha byte
        // or drawString silently discards the text (ARGB.alpha(color) != 0 guard).
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int textX = sx + (width - textWidth) / 2;
        int textY = sy + (height - font.lineHeight) / 2;
        int textColor = isDisabled() ? 0xFF808080 : 0xFFFFFFFF;
        ctx.graphics().drawString(font, text, textX, textY, textColor, true);
    }

    // ── Click Handling ─────────────────────────────────────────────────

    /**
     * Handles mouse clicks. Only left-click (button 0) is consumed.
     * Right-clicks and middle-clicks fall through to vanilla handling.
     * Disabled buttons don't consume clicks either.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left-click only
        if (button != 0) return false;
        if (isDisabled()) return false;
        // The screen hit-tests before dispatching, so `hovered` should always
        // be true here. Keep the check as defensive symmetry.
        if (!hovered) return false;

        onClick.accept(this);
        return true;
    }

    // ── Icon-only Button variant ───────────────────────────────────────

    /**
     * Creates a square Button that renders a centered sprite instead of a
     * text label. Size is a single int (square only).
     *
     * <p>The sprite is inset 2px from the button's edges so the panel-style
     * border (RAISED bevel, INSET sunken edge) remains visible around the
     * icon. When the button is disabled, the sprite is dimmed to ~40% alpha
     * as an accessibility signal; hover/pressed states are communicated by
     * the panel-style background shift.
     *
     * <p><b>Accessibility:</b> Icon-only buttons convey meaning through sprite
     * alone, which can be less discoverable than text labels. Pairing
     * {@code Button.icon} with a tooltip via {@link #tooltip(Component)} or
     * {@link #tooltip(Supplier)} is strongly recommended for accessibility
     * and discoverability. Users hovering over an icon button should learn
     * its purpose from the tooltip.
     *
     * <p>For a sprite that swaps based on consumer state, use the
     * {@link #icon(int, int, int, Supplier, Consumer) supplier overload}.
     *
     * @param childX  X position within panel content area
     * @param childY  Y position within panel content area
     * @param size    width and height in pixels (square)
     * @param sprite  the sprite identifier to render
     * @param onClick fired on left-click when enabled
     */
    public static Button icon(int childX, int childY, int size,
                              Identifier sprite, Consumer<Button> onClick) {
        return icon(childX, childY, size, (Supplier<Identifier>) () -> sprite, onClick);
    }

    /**
     * Creates a square Button whose sprite is driven by a supplier. The
     * supplier is invoked each frame, enabling state-swap-by-icon patterns
     * (e.g., {@code () -> isActive ? iconOn : iconOff}).
     *
     * <p>See {@link #icon(int, int, int, Identifier, Consumer)} for the
     * accessibility recommendation on pairing with a tooltip.
     *
     * @param childX  X position within panel content area
     * @param childY  Y position within panel content area
     * @param size    width and height in pixels (square)
     * @param sprite  supplier invoked each frame to produce the sprite identifier
     * @param onClick fired on left-click when enabled
     */
    public static Button icon(int childX, int childY, int size,
                              Supplier<Identifier> sprite, Consumer<Button> onClick) {
        return new IconButton(childX, childY, size, sprite, onClick);
    }

    /**
     * Icon-only Button specialization. Overrides {@link #renderContent} to
     * paint a centered sprite instead of the default centered text.
     * Package-private — consumers use the {@link #icon(int, int, int, Identifier, Consumer)}
     * factory methods, which return {@code Button}.
     */
    static final class IconButton extends Button {
        /** Sprite inset on all sides so the panel-style border stays visible. */
        private static final int INSET = 2;
        /** Sprite alpha when the button is disabled (~40% for accessibility dim). */
        private static final float DISABLED_ALPHA = 0.4f;

        private final Supplier<Identifier> spriteSupplier;

        IconButton(int childX, int childY, int size,
                   Supplier<Identifier> sprite, Consumer<Button> onClick) {
            super(childX, childY, size, size, Component.empty(), onClick);
            this.spriteSupplier = sprite;
        }

        @Override
        protected void renderContent(RenderContext ctx, int sx, int sy) {
            Identifier id = spriteSupplier.get();
            if (id == null) return;

            int iconSize = getWidth() - INSET * 2;
            float alpha = isDisabled() ? DISABLED_ALPHA : 1.0f;
            ctx.graphics().blitSprite(
                    RenderPipelines.GUI_TEXTURED, id,
                    sx + INSET, sy + INSET, iconSize, iconSize,
                    alpha);
        }
    }
}
