package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.VanillaLook;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * MenuKit 6.0.0's per-control look. Since 6.1.0 nothing picks a look: every
 * element draws for the kind of screen it is on ({@link ScreenKind}).
 *
 * <p>Kept, and inert, only so a mod built against 6.0.0 still loads on 6.1.0:
 * code built against an earlier minor of the same major keeps working, and
 * anything deprecated in a minor is removed in the next major. A
 * {@code style(ControlStyle)} call on {@link Button} or a dropdown does nothing;
 * on an options screen the control already draws in vanilla's look. Removed in
 * 7.0.0.
 *
 * @deprecated delete the {@code style(...)} call; the screen's kind decides.
 */
@Deprecated(since = "6.1.0", forRemoval = true)
public enum ControlStyle {
    MK,
    VANILLA;

    // 6.0.0's drawing helpers, kept for the same reason, now vanilla's own drawing in
    // VanillaLook (internal).

    /** @deprecated 6.0.0's; the button sprite for a state. Removed in 7.0.0. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static Identifier vanillaButtonSprite(boolean enabled, boolean hoveredOrPressed) {
        return Identifier.withDefaultNamespace(!enabled ? "widget/button_disabled"
                : hoveredOrPressed ? "widget/button_highlighted" : "widget/button");
    }

    /** @deprecated 6.0.0's; vanilla's button at the given bounds. Removed in 7.0.0. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void renderVanillaButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                           boolean enabled, boolean hoveredOrPressed) {
        VanillaLook.button(graphics, x, y, width, height, enabled, hoveredOrPressed);
    }

    /** @deprecated 6.0.0's; MenuKit's pressed look on a vanilla button. Removed in 7.0.0. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void renderVanillaPressedOverlay(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        VanillaLook.pressedOverlay(graphics, x, y, width, height);
    }

    /** @deprecated 6.0.0's; an open dropdown's list on vanilla's darker button grey. Removed in 7.0.0. */
    @Deprecated(since = "6.1.0", forRemoval = true)
    public static void renderVanillaPopoverBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        VanillaLook.popoverBackground(graphics, x, y, width, height);
    }
}
