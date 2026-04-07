package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * HUD layout group — arranges child elements in a row or column.
 *
 * <p>Automatically advances the cursor by each child's width (ROW) or
 * height (COLUMN) plus spacing. Children can be any {@link MKHudElement}.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudGroup implements MKHudElement {

    /** Layout direction. */
    public enum Layout { ROW, COLUMN }

    private final int relX, relY;
    private final Layout layout;
    private final int spacing;
    private final List<MKHudElement> children;
    private final @Nullable Runnable onRender;

    MKHudGroup(int relX, int relY, Layout layout, int spacing,
               List<MKHudElement> children, @Nullable Runnable onRender) {
        this.relX = relX;
        this.relY = relY;
        this.layout = layout;
        this.spacing = spacing;
        this.children = children;
        this.onRender = onRender;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, DeltaTracker dt) {
        if (onRender != null) onRender.run();

        int cursorX = x + relX;
        int cursorY = y + relY;

        for (MKHudElement child : children) {
            child.render(graphics, cursorX, cursorY, dt);

            if (layout == Layout.ROW) {
                cursorX += child.getWidth() + spacing;
            } else {
                cursorY += child.getHeight() + spacing;
            }
        }
    }

    @Override
    public int getWidth() {
        if (layout == Layout.ROW) {
            int total = 0;
            for (MKHudElement child : children) {
                total += child.getWidth();
            }
            return relX + total + Math.max(0, (children.size() - 1) * spacing);
        } else {
            int max = 0;
            for (MKHudElement child : children) {
                max = Math.max(max, child.getWidth());
            }
            return relX + max;
        }
    }

    @Override
    public int getHeight() {
        if (layout == Layout.COLUMN) {
            int total = 0;
            for (MKHudElement child : children) {
                total += child.getHeight();
            }
            return relY + total + Math.max(0, (children.size() - 1) * spacing);
        } else {
            int max = 0;
            for (MKHudElement child : children) {
                max = Math.max(max, child.getHeight());
            }
            return relY + max;
        }
    }
}
