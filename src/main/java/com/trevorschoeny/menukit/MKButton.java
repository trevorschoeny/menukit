package com.trevorschoeny.menukit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A unified button for container screens. Supports icons, text, toggle state,
 * radio groups, and positioning relative to inventory slots.
 *
 * <p>Extends vanilla's {@link AbstractButton} — inherits click sound, hover detection,
 * keyboard activation (Space/Enter), focus management, and narration for free.
 *
 * <p>Renders a vanilla-style raised panel background that works at <b>any size</b>
 * (even 9×9), unlike vanilla's {@code Button} which assumes a minimum size and
 * specific 9-slice texture.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // Small icon button
 * MKButton plus = MKButton.builder()
 *     .icon(PLUS_ICON).size(9, 9)
 *     .onClick(btn -> expandGroup())
 *     .build();
 *
 * // Text button
 * MKButton palette = MKButton.builder()
 *     .label(Component.literal("Block Palette"))
 *     .onClick(btn -> openPalette())
 *     .build();
 *
 * // Toggle with icon
 * MKButton lock = MKButton.builder()
 *     .icon(LOCK_ICON).size(16, 16)
 *     .toggle()
 *     .onToggle((btn, pressed) -> setLocked(pressed))
 *     .build();
 *
 * // Anchored to a slot
 * MKButton expand = MKButton.builder()
 *     .icon(PLUS_ICON).size(9, 9)
 *     .anchorTo(hotbarSlot, MKAnchor.BELOW, 2)
 *     .onClick(btn -> expand())
 *     .build();
 *
 * // Radio group
 * MKButtonGroup tabs = new MKButtonGroup();
 * MKButton tab1 = MKButton.builder().icon(SWORD).toggle().group(tabs).build();
 * MKButton tab2 = MKButton.builder().icon(BOW).toggle().group(tabs).build();
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public class MKButton extends AbstractButton {

    // ── Button visual styles ─────────────────────────────────────────────────

    /** Visual style for MKButtons. */
    public enum ButtonStyle {
        /** Pure vanilla button sprites (widget/button, widget/button_highlighted,
         *  widget/button_disabled). Identical to vanilla's button appearance. */
        STANDARD,
        /** Panel-colored fill (light gray). Blue fill swap on hover. Clean, flat look. */
        SLEEK,
        /** No background — just renders the icon/label. Hover shows a translucent
         *  white overlay on the content area. */
        NONE
    }

    // ── Rendering colors (shared) ────────────────────────────────────────────
    private static final int COLOR_BASE           = 0xFFC6C6C6;  // panel base gray
    private static final int COLOR_HIGHLIGHT      = 0xFFFFFFFF;  // top/left edge
    private static final int COLOR_SHADOW         = 0xFF555555;  // bottom/right edge
    private static final int COLOR_BORDER         = 0xFF000000;  // outer border

    // ── Rendering colors (SLEEK style) ───────────────────────────────────────
    private static final int SLEEK_HIGHLIGHT_BLUE = 0xFF9099D6;  // muted periwinkle — used for BOTH hover and toggled

    // ── Visual style ─────────────────────────────────────────────────────────
    private ButtonStyle buttonStyle = ButtonStyle.STANDARD;

    // ── Content ─────────────────────────────────────────────────────────────
    private @Nullable Identifier icon;
    private @Nullable Identifier toggledIcon; // icon to show when toggled on
    private int iconSize = 16;

    // ── Toggle ──────────────────────────────────────────────────────────────
    private boolean toggleMode = false;
    private boolean pressed = false;
    private java.util.function.@




            Nullable BooleanSupplier pressedWhen; // runtime override for pressed state

    // ── Group ───────────────────────────────────────────────────────────────
    private @Nullable MKButtonGroup group;

    // ── Anchor ──────────────────────────────────────────────────────────────
    private @Nullable Slot anchorSlot;
    private MKAnchor anchorSide = MKAnchor.BELOW;
    private int anchorGap = 2;

    // ── Panel association ────────────────────────────────────────────────────
    /** Panel name -- used for collision avoidance position updates and event dispatch. */
    public @Nullable String panelName;
    /** Sequential index within the panel's button list. Used to match buttons
     *  during position updates (label matching fails when multiple buttons
     *  share the same label or have empty labels). Set during creation. */
    public int buttonIndex = -1;

    // ── Event context ────────────────────────────────────────────────────────
    /** The screen context for event dispatch. Set during button creation. */
    @Nullable MKContext eventContext;

    /** The player for event dispatch. Set during button creation. */
    net.minecraft.world.entity.player.@Nullable Player eventPlayer;

    // ── Callbacks ───────────────────────────────────────────────────────────
    private @Nullable Consumer<MKButton> onClick;
    private @Nullable BiConsumer<MKButton, Boolean> onToggle;

    // ── Constructor ─────────────────────────────────────────────────────────

    protected MKButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /** Whether this button is currently in the pressed/toggled state. */
    public boolean isPressed() {
        if (pressedWhen != null) return pressedWhen.getAsBoolean();
        return pressed;
    }

    /** Sets a runtime predicate that overrides the pressed state each frame. */
    public void setPressedWhen(java.util.function.@Nullable BooleanSupplier supplier) {
        this.pressedWhen = supplier;
    }

    /** Sets the pressed state directly (used by {@link MKButtonGroup}). */
    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    /** Whether this button is in toggle mode. */
    public boolean isToggleMode() {
        return toggleMode;
    }

    /** Returns the icon identifier, or null if no icon is set. */
    public @Nullable Identifier getIcon() {
        return icon;
    }

    /** Sets the icon at runtime. */
    public void setIcon(@Nullable Identifier icon) {
        this.icon = icon;
    }

    /**
     * Recalculates this button's position based on its anchor slot.
     * Call this during the screen's render cycle to keep the button
     * positioned correctly relative to its slot through resizes.
     *
     * @param leftPos the container screen's leftPos (x offset)
     * @param topPos  the container screen's topPos (y offset)
     */
    public void updateAnchorPosition(int leftPos, int topPos) {
        if (anchorSlot == null) return;

        int slotScreenX = leftPos + anchorSlot.x;
        int slotScreenY = topPos + anchorSlot.y;

        switch (anchorSide) {
            case ABOVE -> setPosition(
                    slotScreenX + (16 - width) / 2,
                    slotScreenY - anchorGap - height);
            case BELOW -> setPosition(
                    slotScreenX + (16 - width) / 2,
                    slotScreenY + 16 + anchorGap);
            case LEFT -> setPosition(
                    slotScreenX - anchorGap - width,
                    slotScreenY + (16 - height) / 2);
            case RIGHT -> setPosition(
                    slotScreenX + 16 + anchorGap,
                    slotScreenY + (16 - height) / 2);
        }
    }

    // ── AbstractButton overrides ────────────────────────────────────────────

    @Override
    public void onPress(InputWithModifiers input) {
        // Toggle state if in toggle mode
        if (toggleMode) {
            pressed = !pressed;

            // Radio group: unpress all others when we become pressed
            if (group != null && pressed) {
                group.select(this);
            }

            if (onToggle != null) {
                onToggle.accept(this, pressed);
            }
        }

        // Fire click callback
        if (onClick != null) {
            onClick.accept(this);
        }

        // Fire bus events for UI event listeners
        // Resolve player: prefer the pre-set eventPlayer, fall back to Minecraft.player
        net.minecraft.world.entity.player.Player player = eventPlayer;
        if (player == null) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            player = mc.player;
        }
        if (player != null) {
            MKEventBus.fire(MKUIEvent.buttonClick(this, panelName, eventContext, player));
            if (toggleMode) {
                MKEventBus.fire(MKUIEvent.buttonToggle(this, pressed, panelName, eventContext, player));
            }
        }
    }

    /**
     * Override setFocused to prevent non-toggle buttons from staying highlighted
     * after a click. Vanilla calls setFocused(true) after onPress, which keeps
     * the button highlighted until another widget is clicked. For non-toggle
     * buttons, we reject focus entirely. For toggle buttons, we only accept
     * focus when toggled ON.
     */
    @Override
    public void setFocused(boolean focused) {
        if (!toggleMode) {
            // Non-toggle buttons never hold focus
            super.setFocused(false);
        } else if (!focused || pressed) {
            // Toggle buttons: accept focus when toggled ON, always accept unfocus
            super.setFocused(focused);
        } else {
            // Toggle OFF + trying to focus → reject
            super.setFocused(false);
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY,
                                  float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused();
        boolean disabled = !this.active;

        // ── Background panel ────────────────────────────────────────────
        renderButtonBackground(graphics, x, y, w, h, hovered, disabled);

        // ── Content (icon and/or label) ─────────────────────────────────
        // Swap to toggled icon when pressed and a toggledIcon exists
        Identifier activeIcon = (isPressed() && toggleMode && toggledIcon != null) ? toggledIcon : icon;
        boolean hasIcon = activeIcon != null;
        boolean hasLabel = getMessage() != null
                && !getMessage().getString().isEmpty();

        if (hasIcon && hasLabel) {
            // Icon on the left, label to the right
            int iconX = x + 3;
            int iconY = y + (h - iconSize) / 2;
            renderIcon(graphics, activeIcon, iconX, iconY, disabled);

            int textX = iconX + iconSize + 2;
            int textY = y + (h - 9) / 2 + 1;  // 9 = font height, +1 to center visually
            int textColor;
            if (disabled) {
                textColor = (buttonStyle == ButtonStyle.SLEEK) ? 0xFF606060 : 0xFFA0A0A0;
            } else {
                textColor = (buttonStyle == ButtonStyle.STANDARD) ? 0xFFFFFFFF : 0xFF404040;
            }
            boolean shadow = (buttonStyle == ButtonStyle.STANDARD);
            graphics.drawString(
                    net.minecraft.client.Minecraft.getInstance().font,
                    getMessage(), textX, textY, textColor, shadow);

        } else if (hasIcon) {
            // Icon centered
            int iconX = x + (w - iconSize) / 2;
            int iconY = y + (h - iconSize) / 2;
            renderIcon(graphics, activeIcon, iconX, iconY, disabled);

        } else if (hasLabel) {
            // Label centered, clamped to content area (inside 2px border + 2px padding)
            var font = net.minecraft.client.Minecraft.getInstance().font;
            int textWidth = font.width(getMessage());
            int contentLeft = x + 4;   // 2px border + 2px padding
            int contentRight = x + w - 4;
            int contentW = contentRight - contentLeft;
            int textX = contentLeft + (contentW - textWidth) / 2;
            int textY = y + (h - 9) / 2 + 1;  // +1 to center visually
            int textColor;
            if (disabled) {
                textColor = (buttonStyle == ButtonStyle.SLEEK) ? 0xFF606060 : 0xFFA0A0A0;
            } else {
                textColor = (buttonStyle == ButtonStyle.STANDARD) ? 0xFFFFFFFF : 0xFF404040;
            }

            // Enable scissor to clip text that's too wide
            boolean shadow = (buttonStyle == ButtonStyle.STANDARD); // white text needs shadow
            graphics.enableScissor(contentLeft, y, contentRight, y + h);
            // Use plain string when disabled+SLEEK to bypass WithInactiveMessage's
            // hardcoded gray style that overrides our textColor
            if (disabled && buttonStyle == ButtonStyle.SLEEK) {
                graphics.drawString(font, getMessage().getString(), textX, textY, textColor, shadow);
            } else {
                graphics.drawString(font, getMessage(), textX, textY, textColor, shadow);
            }
            graphics.disableScissor();
        }

        // ── NONE style: hover overlay ON TOP of content ─────────────────
        // Use isHovered() (not isHoveredOrFocused) to avoid persistent highlight after click
        if (buttonStyle == ButtonStyle.NONE && this.isHovered() && !disabled) {
            // Overlay sized to the icon, centered within the button
            int iconX = x + (w - iconSize) / 2;
            int iconY = y + (h - iconSize) / 2;
            graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x40FFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }

    // ── Private rendering helpers ───────────────────────────────────────────

    /**
     * Renders the button background using {@link MKPanel}'s shared rendering.
     * Picks colors and style based on button state (hover, pressed, disabled).
     */
    private void renderButtonBackground(GuiGraphics graphics, int x, int y,
                                        int w, int h, boolean hovered,
                                        boolean disabled) {
        if (buttonStyle == ButtonStyle.NONE) {
            // No background — hover overlay rendered AFTER content in renderContents
            return;
        } else if (buttonStyle == ButtonStyle.SLEEK) {
            renderSleekBackground(graphics, x, y, w, h, hovered, disabled);
        } else {
            renderStandardBackground(graphics, x, y, w, h, hovered, disabled);
        }
    }

    /**
     * STANDARD style: Pure vanilla button rendering. Uses vanilla's built-in
     * widget sprites for all three states:
     * <ul>
     *   <li>{@code widget/button} — normal</li>
     *   <li>{@code widget/button_highlighted} — hovered/focused</li>
     *   <li>{@code widget/button_disabled} — inactive</li>
     * </ul>
     * No custom rendering — identical to vanilla's Button appearance.
     */
    private void renderStandardBackground(GuiGraphics graphics, int x, int y,
                                           int w, int h, boolean hovered,
                                           boolean disabled) {
        this.renderDefaultSprite(graphics);
    }

    /**
     * SLEEK style: Four distinct visual states:
     * <ul>
     *   <li><b>Disabled</b> — muted gray, not interactive</li>
     *   <li><b>Normal</b> — panel-colored gray fill</li>
     *   <li><b>Hovered</b> — lighter gray with white highlight edges</li>
     *   <li><b>Toggled on</b> — blue fill (persists until untoggled)</li>
     *   <li><b>Toggled on + hovered</b> — brighter blue fill</li>
     * </ul>
     */
    private void renderSleekBackground(GuiGraphics graphics, int x, int y,
                                        int w, int h, boolean hovered,
                                        boolean disabled) {
        boolean toggled = isPressed() && toggleMode;

        if (disabled) {
            // Dimmed — muted fill, gray border
            MKPanel.renderPanel(graphics, x, y, w, h, MKPanel.Style.RAISED,
                    0xFFB0B0B0, 0xFFD0D0D0, 0xFF909090, 0xFF808080);
        } else if (toggled && hovered) {
            // Toggled ON + hovered — brighter blue
            renderSleekFill(graphics, x, y, w, h, 0xFF6699FF, 0xFFAABBFF, 0xFF4477DD);
        } else if (toggled) {
            // Toggled ON — blue fill
            renderSleekFill(graphics, x, y, w, h, SLEEK_HIGHLIGHT_BLUE, 0xFF99BBFF, 0xFF3355AA);
        } else if (hovered) {
            // Hovered — lighter gray with white edges
            renderSleekFill(graphics, x, y, w, h, 0xFFD8D8D8, 0xFFEEEEEE, 0xFFAAAAAA);
        } else {
            // Normal — panel-colored fill (light gray)
            MKPanel.renderPanel(graphics, x, y, w, h, MKPanel.Style.RAISED,
                    COLOR_BASE, COLOR_HIGHLIGHT, COLOR_SHADOW);
        }
    }

    /** Renders a sleek button fill with border, inner edges, and solid center. */
    private void renderSleekFill(GuiGraphics graphics, int x, int y, int w, int h,
                                  int fillColor, int highlightColor, int shadowColor) {
        // Black border
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        // Inner highlight/shadow edges
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, highlightColor);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, highlightColor);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shadowColor);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, shadowColor);
        // Fill
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, fillColor);
    }

    /** Renders the icon sprite at the given position. */
    private void renderIcon(GuiGraphics graphics, Identifier iconId, int iconX, int iconY,
                            boolean disabled) {
        if (iconId == null) return;

        // Render the icon sprite; dim with a translucent overlay when disabled
        graphics.blitSprite(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                iconId, iconX, iconY, iconSize, iconSize);

        if (disabled) {
            // Overlay a semi-transparent gray to dim the icon
            graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x80C6C6C6);
        }
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    /** Creates a new builder with default size (150×20, vanilla button default). */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // Position & size
        private int x = 0, y = 0;
        private int width = -1, height = -1;  // -1 = auto-size to fit content
        private boolean sizeExplicit = false;
        private Component message = Component.empty();

        // Content
        private @Nullable Identifier icon;
        private @Nullable Identifier toggledIcon;
        private int iconSize = 16;

        // Toggle
        private boolean toggleMode = false;
        private boolean pressed = false;

        // Group
        private @Nullable MKButtonGroup group;

        // Anchor
        private @Nullable Slot anchorSlot;
        private MKAnchor anchorSide = MKAnchor.BELOW;
        private int anchorGap = 2;

        // Callbacks
        private @Nullable Consumer<MKButton> onClick;
        private @Nullable BiConsumer<MKButton, Boolean> onToggle;

        // Visual style
        private ButtonStyle buttonStyle = ButtonStyle.STANDARD;

        // Tooltip
        private @Nullable Component tooltipText;

        // Internal: callback for MKPanel to track child sizes
        private @Nullable Consumer<MKButton> buildCallback;

        Builder() {}

        // ── Position & size ─────────────────────────────────────────────

        public Builder pos(int x, int y) {
            this.x = x; this.y = y; return this;
        }

        public Builder size(int width, int height) {
            this.width = width; this.height = height;
            this.sizeExplicit = true;
            return this;
        }

        // ── Content ─────────────────────────────────────────────────────

        /** Sets the button's icon sprite. */
        public Builder icon(Identifier icon) {
            this.icon = icon; return this;
        }

        /** Sets the icon to show when the button is toggled on. */
        public Builder toggledIcon(Identifier icon) {
            this.toggledIcon = icon; return this;
        }

        /** Sets the icon render size (default 16). */
        public Builder iconSize(int size) {
            this.iconSize = size; return this;
        }

        /** Sets the button's text label. */
        public Builder label(Component label) {
            this.message = label; return this;
        }

        /** Sets the button's text label from a string. */
        public Builder label(String label) {
            this.message = Component.literal(label); return this;
        }

        // ── Visual Style ───────────────────────────────────────────────

        /** Sets the button visual style. Default is {@link ButtonStyle#STANDARD}. */
        public Builder buttonStyle(ButtonStyle style) {
            this.buttonStyle = style; return this;
        }

        /** Shortcut for {@code buttonStyle(ButtonStyle.SLEEK)} — panel-colored fill with blue hover. */
        public Builder sleek() {
            this.buttonStyle = ButtonStyle.SLEEK; return this;
        }

        // ── Toggle ──────────────────────────────────────────────────────

        /** Enables toggle mode — clicking alternates pressed/unpressed state. */
        public Builder toggle() {
            this.toggleMode = true; return this;
        }

        /** Sets the initial pressed state (only meaningful with toggle mode). */
        public Builder pressed(boolean pressed) {
            this.pressed = pressed; return this;
        }

        // ── Group ───────────────────────────────────────────────────────

        /** Joins a radio group. Implies toggle mode. */
        public Builder group(MKButtonGroup group) {
            this.group = group;
            this.toggleMode = true;  // groups require toggle mode
            return this;
        }

        // ── Anchor ──────────────────────────────────────────────────────

        /** Positions this button relative to a slot. */
        public Builder anchorTo(Slot slot, MKAnchor side, int gap) {
            this.anchorSlot = slot;
            this.anchorSide = side;
            this.anchorGap = gap;
            return this;
        }

        /** Positions this button relative to a slot with default 2px gap. */
        public Builder anchorTo(Slot slot, MKAnchor side) {
            return anchorTo(slot, side, 2);
        }

        // ── Callbacks ───────────────────────────────────────────────────

        /** Called on every click (both regular and toggle buttons). */
        public Builder onClick(Consumer<MKButton> handler) {
            this.onClick = handler; return this;
        }

        /** Called when toggle state changes. Only fires in toggle mode. */
        public Builder onToggle(BiConsumer<MKButton, Boolean> handler) {
            this.onToggle = handler; return this;
        }

        // ── Tooltip ─────────────────────────────────────────────────────

        /** Shows a tooltip on hover. */
        public Builder tooltip(Component text) {
            this.tooltipText = text; return this;
        }

        /** Internal: called by {@link MKPanel} to receive the built button for size tracking. */
        Builder onBuild(Consumer<MKButton> callback) {
            this.buildCallback = callback; return this;
        }

        // ── Build ───────────────────────────────────────────────────────

        public MKButton build() {
            // Auto-size if no explicit size was set
            if (!sizeExplicit) {
                var font = net.minecraft.client.Minecraft.getInstance().font;
                boolean hasIcon = icon != null;
                boolean hasLabel = message != null && !message.getString().isEmpty();
                int pad = 8; // 2px border + 2px padding on each side

                if (hasIcon && hasLabel) {
                    int textW = font.width(message);
                    width = 3 + iconSize + 2 + textW + 3 + 4; // icon + gap + text + padding
                    height = Math.max(iconSize, 9) + pad;
                } else if (hasIcon) {
                    width = iconSize + pad;
                    height = iconSize + pad;
                } else if (hasLabel) {
                    int textW = font.width(message);
                    width = textW + pad;
                    height = 9 + pad; // 9 = font height
                } else {
                    width = 20;
                    height = 20;
                }
            }

            MKButton button = new MKButton(x, y, width, height, message);

            // Visual style
            button.buttonStyle = buttonStyle;

            // Content
            button.icon = icon;
            button.toggledIcon = toggledIcon;
            button.iconSize = iconSize;

            // Toggle
            button.toggleMode = toggleMode;
            button.pressed = pressed;

            // Group
            button.group = group;
            if (group != null) {
                group.register(button);
                if (pressed) group.select(button);
            }

            // Anchor
            button.anchorSlot = anchorSlot;
            button.anchorSide = anchorSide;
            button.anchorGap = anchorGap;

            // Callbacks
            button.onClick = onClick;
            button.onToggle = onToggle;

            // For STANDARD toggle buttons: use vanilla's override to show
            // highlighted sprite when toggled ON OR hovered (same pattern as recipe book)
            if (toggleMode && buttonStyle == ButtonStyle.STANDARD) {
                button.setOverrideRenderHighlightedSprite(
                        () -> button.pressed || button.isHoveredOrFocused());
            }

            // Tooltip
            if (tooltipText != null) {
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltipText));
            }

            // Notify MKPanel if this button was created through one
            if (buildCallback != null) {
                buildCallback.accept(button);
            }

            return button;
        }
    }
}
