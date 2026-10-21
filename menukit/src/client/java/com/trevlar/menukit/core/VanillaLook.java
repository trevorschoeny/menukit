package com.trevlar.menukit.core;

import com.trevlar.menukit.api.panel.PanelStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * Vanilla's menu chrome, drawn the way vanilla draws it: what an element draws on
 * a Default-look screen ({@code ScreenKind.DEFAULT}, 6.1.0). The button sprites, the
 * popup frame, the slot, the separator lines and the darker list background of
 * every Options screen, plus MenuKit's pressed overlay on a vanilla button
 * (MenuKit's one styling exception, which also draws on vanilla's and YACL's own
 * buttons).
 *
 * <p>Checked against 26.2: the button sprites are {@code AbstractButton.SPRITES}
 * (enabled, disabled, highlighted); the popup frame is
 * {@code PopupScreen.BACKGROUND_SPRITE}; the slot is
 * {@code CreateFlatWorldScreen.SLOT_SPRITE}, the slot the Superflat layer list
 * draws its items in; the separators are {@link Screen}'s own public identifiers;
 * the list backgrounds are {@code AbstractSelectionList.MENU_LIST_BACKGROUND} and
 * {@code INWORLD_MENU_LIST_BACKGROUND} (private there, so named here), and each
 * draw is the same {@code blit} vanilla makes in
 * {@code AbstractSelectionList.extractListSeparators} and {@code extractListBackground}.
 *
 * <p>Like vanilla, the in-world textures are used while a world is loaded (a
 * screen opened over the game) and the opaque ones on the title screen.
 */
@ApiStatus.Internal
public final class VanillaLook {

    private VanillaLook() {}

    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");

    /**
     * Vanilla's button at the given bounds, the sprite picked by state: disabled
     * wins (as vanilla's does), else highlighted on hover or press (vanilla has no
     * separate pressed sprite). The highlighted sprite is the hover affordance; no
     * overlay is added.
     */
    public static void button(GuiGraphicsExtractor g, int x, int y, int width, int height,
                              boolean enabled, boolean hoveredOrPressed) {
        Identifier sprite = !enabled ? BUTTON_DISABLED : hoveredOrPressed ? BUTTON_HIGHLIGHTED : BUTTON;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, 0xFFFFFFFF);
    }

    /**
     * MenuKit's pressed look on vanilla's button as it draws under the mouse (its
     * highlighted sprite): the press on a vanilla, YACL or Default-look MenuKit button.
     */
    public static void pressedOverlay(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        pressedOverlay(g, x, y, width, height, true);
    }

    /** MenuKit's pressed look on vanilla's button, highlighted or not (an open dropdown's trigger). */
    public static void pressedOverlay(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean highlighted) {
        pressedOverlay(g, x, y, width, height, ControlLook.faceLuma(highlighted ? BUTTON_HIGHLIGHTED : BUTTON));
    }

    /** The inverted bevel: translucent black over the top and left, translucent white over the bottom and right. */
    public static final int PRESSED_SHADE = 0xAE000000, PRESSED_LIGHT = 0x45FFFFFF;
    /** The wash inside it: a quarter of the way to black on a light face, to white on a dark one. */
    public static final int PRESSED_DARKEN = 0x40000000, PRESSED_LIGHTEN = 0x40FFFFFF;
    /**
     * A face whose luma (0 to 255, its fill, {@link ControlLook#faceLuma}) is below this is lit
     * by the press instead of darkened. Between a typical dark-mode pack's button (about 80)
     * and vanilla's (about 110), so vanilla's own darkens as it always has.
     */
    public static final int DARK_FACE = 96;

    /**
     * MenuKit's one pressed transform, for every pressed control in both looks and on every
     * pack, drawn over the art the control shows under it. It is relative to
     * that art, so it reads on light, dark and coloured faces alike:
     * <ul>
     *   <li>the 1 px bevel inside the outline inverted: translucent black over the top and
     *       left, translucent white over the bottom and right, so the light edge goes dark
     *       and the dark edge light whatever the art;</li>
     *   <li>a wash inside the bevel that darkens a light face by a quarter and lightens a dark
     *       one by a quarter ({@link #DARK_FACE}), since a near-black face can't be darkened
     *       any further.</li>
     * </ul>
     * The alphas are tuned so vanilla's highlighted button comes out as it did under the
     * fixed greys drawn before (0xFF373737 top and left, 0xFF888888 bottom and right, the same
     * wash): the bevel lines within a few levels, the wash exact. The outer outline is left
     * alone. {@code faceLuma}: the face's luma, {@link ControlLook#faceLuma}.
     */
    public static void pressedOverlay(GuiGraphicsExtractor g, int x, int y, int width, int height, int faceLuma) {
        // Each bevel pixel is washed once: the top row stops short of the right column and the
        // left column short of the bottom row, which take those corners, as the greys did.
        g.fill(x + 1, y + 1, x + width - 2, y + 2, PRESSED_SHADE);
        g.fill(x + 1, y + 2, x + 2, y + height - 2, PRESSED_SHADE);
        g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, PRESSED_LIGHT);
        g.fill(x + width - 2, y + 1, x + width - 1, y + height - 2, PRESSED_LIGHT);
        g.fill(x + 2, y + 2, x + width - 2, y + height - 2, faceLuma < DARK_FACE ? PRESSED_LIGHTEN : PRESSED_DARKEN);
    }

    /**
     * An open dropdown's list: vanilla's disabled button sprite, its darker and
     * flatter grey, so the list reads as a surface under the trigger. Nine-sliced,
     * so it stretches to any size with crisp corners.
     */
    public static void popoverBackground(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON_DISABLED, x, y, width, height, 0xFFFFFFFF);
    }

    // Vanilla's popup frame: the dark box its warning and confirm popups sit in. Nine-sliced.
    private static final Identifier POPUP = Identifier.withDefaultNamespace("popup/background");

    /**
     * A panel's background in the Default look. A raised panel (a dialog, say) is
     * vanilla's popup frame, the box its own popups use, so white text reads on it;
     * an inset panel is the darker list background, vanilla's recessed surface; a
     * dark panel keeps its dark sprite, which already suits white text.
     * {@link PanelStyle#NONE} draws nothing, so vanilla's backdrop shows.
     */
    public static void panelBackground(GuiGraphicsExtractor g, int x, int y, int width, int height, PanelStyle style) {
        if (width <= 0 || height <= 0) return;
        switch (style) {
            case RAISED -> g.blitSprite(RenderPipelines.GUI_TEXTURED, POPUP, x, y, width, height);
            case INSET -> listBackground(g, x, y, width, height, x, y);
            case DARK -> PanelRendering.renderPanel(g, x, y, width, height, PanelStyle.DARK);
            case NONE -> { }
        }
    }

    // The slot vanilla draws items in in the Default look (the Superflat layer list).
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");

    /** Vanilla's 18 x 18 slot at {@code (x, y)}, faded while disabled as vanilla fades a widget. */
    public static void slot(GuiGraphicsExtractor g, int x, int y, boolean disabled) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18, disabled ? 0x80FFFFFF : 0xFFFFFFFF);
    }

    /** Vanilla's separator is 2 px tall: a light line over a dark one (header), or the reverse (footer). */
    public static final int SEPARATOR_HEIGHT = 2;

    private static final Identifier LIST_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");
    private static final Identifier INWORLD_LIST_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");

    /** The separator's two lines as colours, for a line vanilla never draws (a vertical divider). */
    public static final int SEPARATOR_LIGHT = 0x33FFFFFF;
    public static final int SEPARATOR_DARK = 0xBF000000;

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null;
    }

    /** The separator at the top of a list (light over dark), {@code width} long from {@code (x, y)}. */
    public static void headerSeparator(GuiGraphicsExtractor g, int x, int y, int width) {
        Identifier tex = inWorld() ? Screen.INWORLD_HEADER_SEPARATOR : Screen.HEADER_SEPARATOR;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, width, SEPARATOR_HEIGHT, 32, SEPARATOR_HEIGHT);
    }

    /** The separator at the bottom of a list (dark over light), {@code width} long from {@code (x, y)}. */
    public static void footerSeparator(GuiGraphicsExtractor g, int x, int y, int width) {
        Identifier tex = inWorld() ? Screen.INWORLD_FOOTER_SEPARATOR : Screen.FOOTER_SEPARATOR;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, width, SEPARATOR_HEIGHT, 32, SEPARATOR_HEIGHT);
    }

    /**
     * The darker list background over a rectangle, tiled from texture point
     * {@code (u, v)}. Two areas that pass their own screen position as {@code (u, v)}
     * tile without a seam where they meet.
     */
    public static void listBackground(GuiGraphicsExtractor g, int x, int y, int width, int height, int u, int v) {
        if (width <= 0 || height <= 0) return;
        Identifier tex = inWorld() ? INWORLD_LIST_BACKGROUND : LIST_BACKGROUND;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) u, (float) v, width, height, 32, 32);
    }
}
