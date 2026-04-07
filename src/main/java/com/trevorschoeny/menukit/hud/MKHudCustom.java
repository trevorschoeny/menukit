package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD custom render element — a blank canvas where the developer draws
 * whatever they want using full {@link GuiGraphics} access.
 *
 * <p>Usage:
 * <pre>{@code
 * .custom(0, 0, 80, 80, (graphics, x, y, w, h, dt) -> {
 *     MyMinimapRenderer.draw(graphics, x, y, w, h);
 * })
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudCustom implements MKHudElement {

    /** Functional interface for custom HUD rendering. */
    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphics graphics, int x, int y, int width, int height,
                    DeltaTracker deltaTracker);
    }

    private final int relX, relY;
    private final int customWidth, customHeight;
    private final Renderer renderer;

    MKHudCustom(int relX, int relY, int customWidth, int customHeight,
                Renderer renderer) {
        this.relX = relX;
        this.relY = relY;
        this.customWidth = customWidth;
        this.customHeight = customHeight;
        this.renderer = renderer;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, DeltaTracker dt) {
        renderer.render(graphics, x + relX, y + relY,
                customWidth, customHeight, dt);
    }

    @Override
    public int getWidth() {
        return relX + customWidth;
    }

    @Override
    public int getHeight() {
        return relY + customHeight;
    }
}
