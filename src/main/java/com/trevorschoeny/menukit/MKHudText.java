package com.trevorschoeny.menukit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudText implements MKHudElement {

    private final int relX, relY;
    private final Supplier<Component> text;
    private final int color;
    private final boolean shadow;
    private final float scale;
    private final boolean backdrop;
    private final @Nullable Runnable onRender;

    MKHudText(int relX, int relY, Supplier<Component> text,
              int color, boolean shadow, float scale,
              boolean backdrop, @Nullable Runnable onRender) {
        this.relX = relX;
        this.relY = relY;
        this.text = text;
        this.color = color;
        this.shadow = shadow;
        this.scale = scale;
        this.backdrop = backdrop;
        this.onRender = onRender;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, DeltaTracker dt) {
        if (onRender != null) onRender.run();

        var mc = Minecraft.getInstance();
        Component comp = text.get();
        if (comp == null) return;

        int drawX = x + relX;
        int drawY = y + relY;

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

    @Override
    public int getWidth() {
        Component comp = text.get();
        if (comp == null) return relX;
        return relX + (int) (Minecraft.getInstance().font.width(comp) * scale);
    }

    @Override
    public int getHeight() {
        return relY + (int) (9 * scale); // 9 = MC font height
    }
}
