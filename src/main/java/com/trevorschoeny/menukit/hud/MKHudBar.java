package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * HUD progress bar element — renders a filled bar based on a 0.0–1.0 value.
 *
 * <p>Uses vanilla's {@code graphics.fill()} for rendering. Supports
 * horizontal and vertical orientations with configurable fill direction,
 * colors, and optional label text.
 *
 * <p>Implements {@link PanelElement}. Phase 8 will subsume this into the
 * core {@code ProgressBar} element.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudBar implements PanelElement {

    /** Fill direction for the progress bar. */
    public enum Direction {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        TOP_TO_BOTTOM
    }

    private final int childX, childY;
    private final int barWidth, barHeight;
    private final Supplier<Float> value;
    private final int fillColor;
    private final int bgColor;
    private final Direction direction;
    private final @Nullable Supplier<Component> label;
    private final @Nullable Runnable onRender;

    MKHudBar(int childX, int childY, int barWidth, int barHeight,
             Supplier<Float> value, int fillColor, int bgColor,
             Direction direction, @Nullable Supplier<Component> label,
             @Nullable Runnable onRender) {
        this.childX = childX;
        this.childY = childY;
        this.barWidth = barWidth;
        this.barHeight = barHeight;
        this.value = value;
        this.fillColor = fillColor;
        this.bgColor = bgColor;
        this.direction = direction;
        this.label = label;
        this.onRender = onRender;
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return barWidth; }
    @Override public int getHeight() { return barHeight; }

    @Override
    public void render(RenderContext ctx) {
        if (onRender != null) onRender.run();

        var graphics = ctx.graphics();
        int drawX = ctx.originX() + childX;
        int drawY = ctx.originY() + childY;

        // Background
        graphics.fill(drawX, drawY, drawX + barWidth, drawY + barHeight, bgColor);

        // Fill based on value (clamped 0.0–1.0)
        float v = Math.max(0f, Math.min(1f, value.get()));

        switch (direction) {
            case LEFT_TO_RIGHT -> {
                int filled = (int) (v * barWidth);
                graphics.fill(drawX, drawY, drawX + filled, drawY + barHeight, fillColor);
            }
            case RIGHT_TO_LEFT -> {
                int filled = (int) (v * barWidth);
                graphics.fill(drawX + barWidth - filled, drawY,
                        drawX + barWidth, drawY + barHeight, fillColor);
            }
            case BOTTOM_TO_TOP -> {
                int filled = (int) (v * barHeight);
                graphics.fill(drawX, drawY + barHeight - filled,
                        drawX + barWidth, drawY + barHeight, fillColor);
            }
            case TOP_TO_BOTTOM -> {
                int filled = (int) (v * barHeight);
                graphics.fill(drawX, drawY, drawX + barWidth, drawY + filled, fillColor);
            }
        }

        // Optional label (centered on bar)
        if (label != null) {
            Component comp = label.get();
            if (comp != null) {
                var mc = Minecraft.getInstance();
                int tw = mc.font.width(comp);
                int tx = drawX + (barWidth - tw) / 2;
                int ty = drawY + (barHeight - 9) / 2; // 9 = font height
                graphics.drawString(mc.font, comp, tx, ty, 0xFFFFFFFF, true);
            }
        }
    }
}
