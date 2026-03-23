package com.trevorschoeny.menukit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * HUD sprite icon element — renders a texture sprite at configurable size.
 *
 * <p>Uses vanilla's {@code graphics.blitSprite()} for rendering, which
 * reads from the GUI texture atlas.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudIcon implements MKHudElement {

    private final int relX, relY;
    private final Identifier sprite;
    private final int iconWidth, iconHeight;
    private final @Nullable Runnable onRender;

    MKHudIcon(int relX, int relY, Identifier sprite,
              int iconWidth, int iconHeight,
              @Nullable Runnable onRender) {
        this.relX = relX;
        this.relY = relY;
        this.sprite = sprite;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.onRender = onRender;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, DeltaTracker dt) {
        if (onRender != null) onRender.run();

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                sprite, x + relX, y + relY, iconWidth, iconHeight);
    }

    @Override
    public int getWidth() {
        return relX + iconWidth;
    }

    @Override
    public int getHeight() {
        return relY + iconHeight;
    }
}
