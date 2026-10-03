package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * Vanilla's menu chrome, drawn the way vanilla draws it, for the elements' vanilla
 * look (6.1.0): the separator lines and the darker list background of every
 * Options screen. Verified against 26.2: the separators are {@link Screen}'s own
 * public identifiers, the list backgrounds are
 * {@code AbstractSelectionList.MENU_LIST_BACKGROUND} and
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
     * tile seamlessly where they meet.
     */
    public static void listBackground(GuiGraphicsExtractor g, int x, int y, int width, int height, int u, int v) {
        if (width <= 0 || height <= 0) return;
        Identifier tex = inWorld() ? INWORLD_LIST_BACKGROUND : LIST_BACKGROUND;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) u, (float) v, width, height, 32, 32);
    }
}
