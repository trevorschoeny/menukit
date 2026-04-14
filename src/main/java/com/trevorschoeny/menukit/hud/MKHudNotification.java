package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelStyle;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD notification element — a timed popup that slides in, displays for
 * a duration, then fades out.
 *
 * <p>Built as a template at mod init, triggered at runtime via
 * {@link MenuKit#notify(String, String)} or {@link MenuKit#notify(String, String, ItemStack)}.
 *
 * <p>This is the only stateful HUD element — animation state is tracked
 * in {@link MenuKit}'s active notification map, not on this object.
 *
 * <p>Usage:
 * <pre>{@code
 * // Define the template
 * MKHudNotification.builder("alert")
 *     .anchor(MKHudAnchor.TOP_CENTER, 0, 10)
 *     .duration(3000)
 *     .slideFrom(SlideDirection.TOP)
 *     .style(PanelStyle.RAISED)
 *     .padding(6)
 *     .build();
 *
 * // Trigger at runtime
 * MenuKit.notify("alert", "First Diamond!");
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudNotification {

    /** Direction the notification slides in from. */
    public enum SlideDirection { TOP, BOTTOM, LEFT, RIGHT }

    private final String key;
    private final MKHudAnchor anchor;
    private final int offsetX, offsetY;
    private final int durationMs;
    private final int fadeMs;
    private final SlideDirection slideFrom;
    private final int slideDistance;
    private final PanelStyle style;
    private final int padding;
    private final int width, height;

    // Slide-in duration in milliseconds
    private static final int SLIDE_IN_MS = 200;

    MKHudNotification(String key, MKHudAnchor anchor, int offsetX, int offsetY,
                      int durationMs, int fadeMs, SlideDirection slideFrom,
                      int slideDistance, PanelStyle style, int padding,
                      int width, int height) {
        this.key = key;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.durationMs = durationMs;
        this.fadeMs = fadeMs;
        this.slideFrom = slideFrom;
        this.slideDistance = slideDistance;
        this.style = style;
        this.padding = padding;
        this.width = width;
        this.height = height;
    }

    public String getKey() { return key; }
    public int getDurationMs() { return durationMs; }

    /**
     * Renders this notification given its active state.
     *
     * @param graphics the GUI graphics context
     * @param dt       tick delta
     * @param screenW  GUI-scaled screen width
     * @param screenH  GUI-scaled screen height
     * @param elapsed  milliseconds since the notification was triggered
     * @param text     the text data passed to notify()
     * @param item     the item data passed to notify() (may be null)
     */
    public void render(GuiGraphics graphics, DeltaTracker dt,
                       int screenW, int screenH, long elapsed,
                       @Nullable String text, @Nullable ItemStack item) {
        // Compute content size
        var mc = Minecraft.getInstance();
        int contentW = width > 0 ? width : computeContentWidth(mc, text, item);
        int contentH = height > 0 ? height : padding * 2 + 9; // 9 = font height
        int panelW = contentW + padding * 2;
        int panelH = contentH;

        // Resolve base position
        int[] pos = anchor.resolve(screenW, screenH, panelW, panelH, offsetX, offsetY);
        int baseX = pos[0];
        int baseY = pos[1];

        // Slide animation
        float slideProgress = Math.min(1f, (float) elapsed / SLIDE_IN_MS);
        // Ease-out: 1 - (1 - t)^2
        slideProgress = 1f - (1f - slideProgress) * (1f - slideProgress);

        int slideOffsetX = 0, slideOffsetY = 0;
        float remaining = 1f - slideProgress;
        switch (slideFrom) {
            case TOP -> slideOffsetY = (int) (-slideDistance * remaining);
            case BOTTOM -> slideOffsetY = (int) (slideDistance * remaining);
            case LEFT -> slideOffsetX = (int) (-slideDistance * remaining);
            case RIGHT -> slideOffsetX = (int) (slideDistance * remaining);
        }

        int drawX = baseX + slideOffsetX;
        int drawY = baseY + slideOffsetY;

        // Fade-out during last fadeMs
        float alpha = 1f;
        long fadeStart = durationMs - fadeMs;
        if (elapsed > fadeStart && fadeMs > 0) {
            alpha = 1f - (float) (elapsed - fadeStart) / fadeMs;
            alpha = Math.max(0f, alpha);
        }

        // Apply alpha to colors
        int alphaInt = (int) (alpha * 255) << 24;

        // Render panel background
        if (style != PanelStyle.NONE && alpha > 0.01f) {
            PanelRendering.renderPanel(graphics, drawX, drawY, panelW, panelH, style);
        }

        // Render content
        int contentX = drawX + padding;
        int contentY = drawY + padding;

        if (item != null && !item.isEmpty()) {
            graphics.renderItem(item, contentX, contentY);
            contentX += 20; // 16px icon + 4px gap
        }

        if (text != null && !text.isEmpty()) {
            int textColor = (alphaInt & 0xFF000000) | 0xFFFFFF;
            graphics.drawString(mc.font, Component.literal(text),
                    contentX, contentY, textColor, true);
        }
    }

    private int computeContentWidth(Minecraft mc, @Nullable String text, @Nullable ItemStack item) {
        int w = 0;
        if (item != null && !item.isEmpty()) w += 20; // 16px icon + 4px gap
        if (text != null && !text.isEmpty()) w += mc.font.width(text);
        return Math.max(w, 40); // minimum width
    }

    // ═══════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════

    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static class Builder {
        private final String key;
        private MKHudAnchor anchor = MKHudAnchor.TOP_CENTER;
        private int offsetX = 0, offsetY = 10;
        private int durationMs = 3000;
        private int fadeMs = 500;
        private SlideDirection slideFrom = SlideDirection.TOP;
        private int slideDistance = 20;
        private PanelStyle style = PanelStyle.RAISED;
        private int padding = 6;
        private int width = 0, height = 0;

        Builder(String key) { this.key = key; }

        public Builder anchor(MKHudAnchor anchor, int offsetX, int offsetY) {
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            return this;
        }

        public Builder duration(int ms) { this.durationMs = ms; return this; }
        public Builder fadeOut(int ms) { this.fadeMs = ms; return this; }
        public Builder slideFrom(SlideDirection dir) { this.slideFrom = dir; return this; }
        public Builder slideDistance(int pixels) { this.slideDistance = pixels; return this; }
        public Builder style(PanelStyle style) { this.style = style; return this; }
        public Builder padding(int padding) { this.padding = padding; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }

        public void build() {
            MKHudNotification notification = new MKHudNotification(
                    key, anchor, offsetX, offsetY,
                    durationMs, fadeMs, slideFrom, slideDistance,
                    style, padding, width, height
            );
            MenuKit.registerNotification(notification);
        }
    }
}
