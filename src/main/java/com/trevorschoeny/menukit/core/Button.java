package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * An interactive button within a {@link Panel}. Renders a raised panel
 * background with centered text. Supports hover detection, a click handler,
 * and an optional disabled predicate.
 *
 * <p>This is MenuKit's core button abstraction. It handles text-based buttons
 * only — consumer mods can implement {@link PanelElement} directly for icon
 * buttons, toggles, or other specialized interactive elements.
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

    // Hover state — updated each render frame. Not persisted.
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

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int contentX, int contentY,
                       int mouseX, int mouseY) {
        int sx = contentX + childX;
        int sy = contentY + childY;

        // Update hover state from current mouse position
        hovered = mouseX >= sx && mouseX < sx + width
                && mouseY >= sy && mouseY < sy + height;

        // Background
        boolean disabled = isDisabled();
        if (disabled) {
            PanelRendering.renderPanel(graphics, sx, sy, width, height, PanelStyle.DARK);
        } else {
            PanelRendering.renderPanel(graphics, sx, sy, width, height, PanelStyle.RAISED);
            if (hovered) {
                // Translucent highlight overlay (inside the border)
                graphics.fill(sx + 1, sy + 1, sx + width - 1, sy + height - 1,
                        0x30FFFFFF);
            }
        }

        // Text — centered within the button bounds
        // 1.21.11 ARGB requirement: colors must have a non-zero alpha byte
        // or drawString silently discards the text (ARGB.alpha(color) != 0 guard).
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int textX = sx + (width - textWidth) / 2;
        int textY = sy + (height - font.lineHeight) / 2;
        int textColor = disabled ? 0xFF808080 : 0xFFFFFFFF;
        graphics.drawString(font, text, textX, textY, textColor, true);
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
        if (!hovered) return false;

        onClick.accept(this);
        return true;
    }
}
