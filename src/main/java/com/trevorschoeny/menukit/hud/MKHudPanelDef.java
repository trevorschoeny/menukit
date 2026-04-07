package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.panel.MKPanel;
import com.trevorschoeny.menukit.panel.MKPanelDef;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Immutable definition of a HUD panel — created at mod init, stored in
 * MenuKit's registry, rendered each frame by the HUD dispatch.
 *
 * <p>Parallel to {@link MKPanelDef} (screen panels) but simpler — no
 * interactivity, no menu, no server sync.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public record MKHudPanelDef(
        String name,
        MKHudAnchor anchor,
        int offsetX,
        int offsetY,
        int padding,
        boolean autoSize,
        int width,
        int height,
        MKPanel.Style style,
        List<MKHudElement> elements,
        Supplier<Boolean> showWhen,
        boolean hideInScreen,
        @Nullable HudRenderCallback onRender
) {
    /**
     * Callback for custom rendering on a HUD panel.
     */
    @FunctionalInterface
    public interface HudRenderCallback {
        void render(GuiGraphics graphics, int x, int y, int width, int height,
                    net.minecraft.client.DeltaTracker deltaTracker);
    }

    /**
     * Computes the panel's size from its children + padding.
     * Used for auto-sized panels.
     */
    public int[] computeSize() {
        if (!autoSize || elements.isEmpty()) {
            return new int[]{width, height};
        }

        int maxRight = 0;
        int maxBottom = 0;

        for (MKHudElement el : elements) {
            maxRight = Math.max(maxRight, el.getWidth());
            maxBottom = Math.max(maxBottom, el.getHeight());
        }

        return new int[]{
                maxRight + padding * 2,
                maxBottom + padding * 2
        };
    }
}
