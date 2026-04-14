package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * HUD text element — renders dynamic text with configurable style.
 *
 * <p>Uses vanilla's {@code graphics.drawString()} for rendering, matching
 * vanilla's text appearance exactly. Supports scaling via the JOML matrix
 * stack, optional backdrop (semi-transparent background behind text),
 * and shadow.
 *
 * <p>Implements {@link PanelElement} — Phase 8 will subsume this into a
 * scale-variant of the core TextLabel.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudText implements PanelElement {

    private final int childX, childY;
    private final Supplier<Component> text;
    private final int color;
    private final boolean shadow;
    private final float scale;
    private final boolean backdrop;
    private final @Nullable Runnable onRender;

    MKHudText(int childX, int childY, Supplier<Component> text,
              int color, boolean shadow, float scale,
              boolean backdrop, @Nullable Runnable onRender) {
        this.childX = childX;
        this.childY = childY;
        this.text = text;
        this.color = color;
        this.shadow = shadow;
        this.scale = scale;
        this.backdrop = backdrop;
        this.onRender = onRender;
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }

    @Override
    public int getWidth() {
        Component comp = text.get();
        if (comp == null) return 0;
        return (int) (Minecraft.getInstance().font.width(comp) * scale);
    }

    @Override
    public int getHeight() {
        return (int) (9 * scale); // 9 = MC font height
    }

    @Override
    public void render(RenderContext ctx) {
        if (onRender != null) onRender.run();

        var mc = Minecraft.getInstance();
        Component comp = text.get();
        if (comp == null) return;

        var graphics = ctx.graphics();
        int drawX = ctx.originX() + childX;
        int drawY = ctx.originY() + childY;

        if (scale != 1.0f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) drawX, (float) drawY);
            graphics.pose().scale(scale, scale);
            drawX = 0;
            drawY = 0;
        }

        if (backdrop) {
            int tw = mc.font.width(comp);
            int th = 9; // vanilla font height
            graphics.fill(drawX - 1, drawY - 1,
                    drawX + tw + 1, drawY + th + 1, 0xBB000000);
        }

        graphics.drawString(mc.font, comp, drawX, drawY, color, shadow);

        if (scale != 1.0f) {
            graphics.pose().popMatrix();
        }
    }
}
