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
 * a Default-look screen ({@code ScreenKind.DEFAULT}, 6.1.0): the popup frame, the
 * slot, the separator lines and the darker list background of every Options screen.
 * Vanilla's button, in both looks, is {@link ControlLook}'s.
 *
 * <p>Checked against 26.2: the popup frame is
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

    /** Vanilla's 18 x 18 slot at {@code (x, y)} (the Superflat layer list's), faded while disabled as vanilla fades a widget. */
    public static void slot(GuiGraphicsExtractor g, int x, int y, boolean disabled) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.SLOT.vanilla(0), x, y, 18, 18, disabled ? 0x80FFFFFF : 0xFFFFFFFF);
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
