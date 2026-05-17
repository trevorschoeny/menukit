package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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
 *   <li><b>Pressed:</b> inset panel background (sunken bevel) while a left-click is held —
 *       a tactile "the player is pushing this button" affordance. Cleared on release</li>
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
public class Button extends AbstractPanelElement {

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final Component text;
    private final Consumer<Button> onClick;
    private final @Nullable BooleanSupplier disabledWhen;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2). Access
    // via getTooltipSupplier() in render().

    // Hover state — updated each render frame. Not persisted.
    // Read by mouseClicked to gate the click on hover.
    private boolean hovered = false;

    // Press affordance — true while a left-click is held down on this button.
    // Set in mouseClicked, cleared in mouseReleased. Drives the INSET
    // background style so the button visually depresses while held, returning
    // to RAISED on release. Pure cosmetic state; the actual click action
    // already fired on mouseClicked.
    private boolean pressed = false;

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

    // ── M8 Layout Spec ─────────────────────────────────────────────────

    /**
     * Returns an {@link com.trevorschoeny.menukit.core.layout.ElementSpec}
     * for use in {@link com.trevorschoeny.menukit.core.layout.Row} or
     * {@link com.trevorschoeny.menukit.core.layout.Column} layouts.
     * Always-enabled variant.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            int width, int height, Component text, Consumer<Button> onClick) {
        return spec(width, height, text, onClick, null);
    }

    /**
     * Layout spec with optional disabled-predicate. See
     * {@link #Button(int, int, int, int, Component, Consumer, BooleanSupplier)}.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            int width, int height, Component text, Consumer<Button> onClick,
            @Nullable BooleanSupplier disabledWhen) {
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return width; }
            @Override public int height() { return height; }
            @Override public PanelElement at(int x, int y) {
                return new Button(x, y, width, height, text, onClick, disabledWhen);
            }
        };
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

    /** Returns whether a left-click is currently held down on this button. */
    public boolean isPressed() { return pressed; }

    // ── Tooltip (optional hover-triggered configuration) ───────────────

    // ── Chainable configuration (Phase 18r-2: covariant returns over
    //    AbstractPanelElement) ───────────────────────────────────────────
    //
    // showWhen + tooltip live on AbstractPanelElement; these overrides
    // narrow the return type to Button so consumers can keep chaining
    // Button-specific helpers (none currently follow tooltip/showWhen in
    // practice, but covariance future-proofs the chain).

    @Override
    public Button tooltip(Component text) {
        super.tooltip(text);
        return this;
    }

    @Override
    public Button tooltip(@Nullable Supplier<Component> supplier) {
        super.tooltip(supplier);
        return this;
    }

    @Override
    public Button showWhen(@Nullable Supplier<Boolean> supplier) {
        super.showWhen(supplier);
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

        // Press-affordance ground-truth sync. mouseReleased dispatches
        // reliably when press and release happen on the same screen, but
        // some Buttons (e.g., validator-mk's persistent inventory Test
        // button) live across screen transitions: a click that navigates
        // to a new screen sees the release dispatched to the new screen,
        // never to this button instance. Without this sync, `pressed`
        // would stick true until the next mouseReleased on this button's
        // screen — which might never come. Polling GLFW's actual mouse
        // state at render time keeps `pressed` honest regardless of where
        // the release event was routed.
        if (pressed && Minecraft.getInstance() != null
                && Minecraft.getInstance().getWindow() != null) {
            int btnState = GLFW.glfwGetMouseButton(
                    Minecraft.getInstance().getWindow().handle(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT);
            if (btnState == GLFW.GLFW_RELEASE) {
                pressed = false;
            }
        }

        renderBackground(ctx, sx, sy);
        renderContent(ctx, sx, sy);

        // Hover-triggered tooltip — setTooltipForNextFrame defers the tooltip
        // draw to end-of-frame (correct z-ordering above items and other
        // elements). The 1.21.11 method name is setTooltipForNextFrame;
        // earlier versions called this renderTooltip.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
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
        } else if (pressed) {
            // Press affordance — INSET sprite (sunken bevel) signals "the
            // button is currently being pushed down." Skips the hover overlay
            // since the depressed look is itself the feedback.
            PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, PanelStyle.INSET);
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

        // Set press-affordance state BEFORE invoking onClick. Order matters
        // for click handlers that immediately query button visual state.
        pressed = true;
        onClick.accept(this);
        return true;
    }

    /**
     * Clears the press-affordance state on left-button release. Fires
     * regardless of cursor position (the dispatcher routes release events
     * un-hit-tested per {@link PanelElement#mouseReleased}), so a
     * press-then-drag-off still releases the visual depression. Returns
     * {@code false} — release isn't "consumed" in the usual sense; we just
     * piggyback on the dispatch to reset cosmetic state.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            pressed = false;
        }
        return false;
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

    // ── Custom-sprite Button variant ───────────────────────────────────

    /**
     * Creates a Button whose <i>entire background</i> is a consumer-supplied
     * sprite, replacing the default RAISED / INSET / DARK panel styles. The
     * sprite is responsible for the whole button look — frame, fill, any
     * decoration. Use this when the default panel-style chrome doesn't fit
     * (themed buttons, embossed buttons, sprite-art buttons, etc.).
     *
     * <p>Per-state rendering:
     * <ul>
     *   <li><b>Normal:</b> sprite drawn as-is.</li>
     *   <li><b>Hovered:</b> sprite + translucent white overlay (same hover
     *       affordance as the default Button).</li>
     *   <li><b>Pressed:</b> sprite rendered through a custom GLSL pipeline
     *       that inverts each pixel's HSL <i>lightness</i> while preserving
     *       hue and saturation. Blacks → whites, dark blues → light blues,
     *       light greys → dark greys. Reads as "this button is being pushed
     *       down" without relying on a separate pressed-state texture.</li>
     *   <li><b>Disabled:</b> sprite + dark translucent overlay (~50% black).</li>
     * </ul>
     *
     * <p>Optional text label sits on top of the sprite, centered, using the
     * same font/color rules as a default Button. Pass {@link Component#empty()}
     * for a sprite-only button.
     *
     * @param childX  X position within panel content area
     * @param childY  Y position within panel content area
     * @param width   button width in pixels
     * @param height  button height in pixels
     * @param sprite  the sprite identifier to use as the button background
     * @param label   centered text label, or {@link Component#empty()} for sprite-only
     * @param onClick fired on left-click when enabled
     */
    public static Button sprite(int childX, int childY, int width, int height,
                                Identifier sprite, Component label,
                                Consumer<Button> onClick) {
        return new SpriteButton(childX, childY, width, height, label,
                (Supplier<Identifier>) () -> sprite, onClick);
    }

    /**
     * Sprite-only convenience overload — equivalent to
     * {@link #sprite(int, int, int, int, Identifier, Component, Consumer)}
     * with {@link Component#empty()} as the label.
     */
    public static Button sprite(int childX, int childY, int width, int height,
                                Identifier sprite, Consumer<Button> onClick) {
        return sprite(childX, childY, width, height, sprite, Component.empty(), onClick);
    }

    /**
     * Supplier-driven sprite overload. The supplier is invoked each frame,
     * enabling state-swap patterns (e.g.,
     * {@code () -> isMuted ? muteOn : muteOff}). The brightness-inverted
     * pressed state still applies — the inversion runs on whatever sprite
     * the supplier currently returns.
     */
    public static Button sprite(int childX, int childY, int width, int height,
                                Supplier<Identifier> sprite, Component label,
                                Consumer<Button> onClick) {
        return new SpriteButton(childX, childY, width, height, label, sprite, onClick);
    }

    /**
     * Sprite-only supplier overload. See
     * {@link #sprite(int, int, int, int, Supplier, Component, Consumer)}.
     */
    public static Button sprite(int childX, int childY, int width, int height,
                                Supplier<Identifier> sprite, Consumer<Button> onClick) {
        return sprite(childX, childY, width, height, sprite, Component.empty(), onClick);
    }

    /**
     * Custom-sprite Button specialization. Overrides {@link #renderBackground}
     * to paint a consumer-supplied sprite (with state-driven variations)
     * instead of the default panel-style backgrounds. {@link #renderContent}
     * is inherited — the centered text label still renders on top.
     * Package-private — consumers use the {@link #sprite(int, int, int, int, Identifier, Consumer)}
     * factory methods, which return {@code Button}.
     */
    static final class SpriteButton extends Button {
        /** Hover overlay color — same translucent white as the default Button. */
        private static final int HOVER_OVERLAY = 0x30FFFFFF;
        /** Disabled overlay color — ~50% black darkens the sprite as the disabled affordance. */
        private static final int DISABLED_OVERLAY = 0x80000000;

        private final Supplier<Identifier> spriteSupplier;

        SpriteButton(int childX, int childY, int width, int height,
                     Component text, Supplier<Identifier> sprite,
                     Consumer<Button> onClick) {
            super(childX, childY, width, height, text, onClick);
            this.spriteSupplier = sprite;
        }

        @Override
        protected void renderBackground(RenderContext ctx, int sx, int sy) {
            Identifier id = spriteSupplier.get();
            if (id == null) return;
            int w = getWidth();
            int h = getHeight();

            if (isDisabled()) {
                // Sprite + dark overlay.
                ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
                ctx.graphics().fill(sx, sy, sx + w, sy + h, DISABLED_OVERLAY);
            } else if (isPressed()) {
                // Sprite through the HSL-lightness-inversion pipeline.
                // No additional overlay — the inverted lightness IS the
                // pressed-state affordance.
                ctx.graphics().blitSprite(
                        MenuKitRenderPipelines.GUI_BRIGHTNESS_INVERTED, id, sx, sy, w, h);
            } else {
                // Normal sprite.
                ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
                if (isHovered()) {
                    // Translucent white overlay — same hover affordance as
                    // the default Button. Inset by 1px (mirrors the default).
                    ctx.graphics().fill(sx + 1, sy + 1, sx + w - 1, sy + h - 1,
                            HOVER_OVERLAY);
                }
            }
        }
    }
}
