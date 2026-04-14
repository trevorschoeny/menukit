package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * HUD sprite icon element — renders a texture sprite at configurable size.
 *
 * <p>Uses vanilla's {@code graphics.blitSprite()} for rendering, which
 * reads from the GUI texture atlas.
 *
 * <p>Implements {@link PanelElement}. Phase 8 will subsume this into the
 * core {@code Icon} element.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudIcon implements PanelElement {

    private final int childX, childY;
    private final Identifier sprite;
    private final int iconWidth, iconHeight;
    private final @Nullable Runnable onRender;

    MKHudIcon(int childX, int childY, Identifier sprite,
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
