package com.trevlar.menukit.api.hud;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A HUD element that draws a sprite from the GUI atlas at a given size, using
 * vanilla's {@code blitSprite}.
 */
public class HudIcon implements PanelElement {

    private final int childX, childY;
    private final Identifier sprite;
    private final int iconWidth, iconHeight;
    private final @Nullable Runnable onRender;

    HudIcon(int childX, int childY, Identifier sprite,
              int iconWidth, int iconHeight,
              @Nullable Runnable onRender) {
        this.childX = childX;
        this.childY = childY;
        this.sprite = sprite;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.onRender = onRender;
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return iconWidth; }
    @Override public int getHeight() { return iconHeight; }

    @Override
    public void render(RenderContext ctx) {
        if (onRender != null) onRender.run();

        ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED,
                sprite, ctx.originX() + childX, ctx.originY() + childY,
                iconWidth, iconHeight);
    }
}
