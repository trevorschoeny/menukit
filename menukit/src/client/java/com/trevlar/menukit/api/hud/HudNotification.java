package com.trevlar.menukit.api.hud;

import com.trevlar.menukit.api.MK;

import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.core.RegionMath;
import com.trevlar.menukit.api.panel.ScreenOrigin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * A timed HUD popup that slides in, stays for a while, then slides back out past the
 * screen edge it came from, as vanilla's toasts do.
 *
 * <p>Built as a template at mod init and triggered at runtime with
 * {@link MK#notify(String, String)} or {@link MK#notify(String, String, ItemStack)}.
 * The animation state lives in {@link MK}'s active notifications, not on this
 * object.
 *
 * <p>Usage:
 * <pre>{@code
 * // Define the template
 * HudNotification.builder("alert")
 *     .region(InsideRegion.TOP_CENTER).offset(0, 6)   // the default
 *     .duration(3000)
 *     .slideFrom(SlideDirection.TOP)
 *     .style(PanelStyle.RAISED)
 *     .padding(6)
 *     .build();
 *
 * // Trigger at runtime
 * MK.notify("alert", "First Diamond!");
 * }</pre>
 */
public class HudNotification {

    /** Direction the notification slides in from. */
    public enum SlideDirection { TOP, BOTTOM, LEFT, RIGHT }

    private final String key;
    // Where it pops up: an InsideRegion spot resolved by RegionMath.resolveInside
    // with the HUD's insets (4 px in, CENTER below the crosshair), then nudged by
    // the offset. Only one shows at a spot, so it resolves with no stacking prefix,
    // and it always shows: a toast that doesn't fit overhangs rather than vanishing.
    private final InsideRegion region;
    private final int offsetX, offsetY;
    private final int durationMs;
    private final int fadeMs;
    private final SlideDirection slideFrom;
    private final int slideDistance;
    private final PanelStyle style;
    private final int padding;
    private final int width, height;

    private static final int SLIDE_IN_MS = 200;

    HudNotification(String key, InsideRegion region,
                      int offsetX, int offsetY,
                      int durationMs, int fadeMs, SlideDirection slideFrom,
                      int slideDistance, PanelStyle style, int padding,
                      int width, int height) {
        this.key = key;
        this.region = region;
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

    /** The unique key this notification was registered under. */
    public String id() { return key; }

    /** Total display duration in milliseconds (includes the slide-out). */
    public int getDurationMs() { return durationMs; }

    /**
     * Draws this notification at its point in the animation.
     *
     * @param screenW  GUI-scaled screen width
     * @param screenH  GUI-scaled screen height
     * @param elapsed  milliseconds since the notification was triggered
     * @param text     the text passed to notify()
     * @param item     the item passed to notify(), or null
     */
    public void render(GuiGraphicsExtractor graphics, DeltaTracker dt,
                       int screenW, int screenH, long elapsed,
                       @Nullable String text, @Nullable ItemStack item) {
        var mc = Minecraft.getInstance();
        int contentW = width > 0 ? width : computeContentWidth(mc, text, item);
        int contentH = height > 0 ? height : padding * 2 + 9; // 9 = font height
        int panelW = contentW + padding * 2;
        int panelH = contentH;

        // Base position: the region spot (HUD insets, zero prefix), then the offset.
        ScreenOrigin origin = RegionMath.resolveInside(region, screenW, screenH,
                panelW, panelH, /*prefix=*/ 0, RegionMath.Insets.NOTIFICATION).orElseThrow();
        int baseX = origin.x() + offsetX;
        int baseY = origin.y() + offsetY;

        ScreenOrigin at = drawnAt(elapsed, baseX, baseY, panelW, panelH, screenW, screenH,
                slideFrom, slideDistance, durationMs, fadeMs);
        int drawX = at.x(), drawY = at.y();

        if (style != PanelStyle.NONE) {
            PanelRendering.renderPanel(graphics, drawX, drawY, panelW, panelH, style);
        }

        int contentX = drawX + padding;
        int contentY = drawY + padding;

        if (item != null && !item.isEmpty()) {
            graphics.item(item, contentX, contentY);
            contentX += 20; // 16px icon + 4px gap
        }

        if (text != null && !text.isEmpty()) {
            graphics.text(mc.font, Component.literal(text),
                    contentX, contentY, 0xFFFFFFFF, true);
        }
    }

    /**
     * Where the panel, {@code panelW} x {@code panelH} with its place at {@code x, y}, is drawn
     * {@code elapsed} ms into {@code durationMs}: sliding in from {@code slideDistance} px toward
     * its edge {@code from} (eased out), then over the last {@code outMs} toward it again (eased in),
     * until it is wholly off the screen. The panel, its item and its text move as one, all
     * opaque: nothing fades, so nothing is left behind (vanilla can't fade an item icon).
     */
    @ApiStatus.Internal
    @VisibleForTesting
    public static ScreenOrigin drawnAt(long elapsed, int x, int y, int panelW, int panelH, int screenW, int screenH,
                                       SlideDirection from, int slideDistance, int durationMs, int outMs) {
        float in = Math.min(1f, (float) elapsed / SLIDE_IN_MS);
        // Ease out: 1 - (1 - t)^2
        float away = slideDistance * (1f - in) * (1f - in);

        long outStart = durationMs - outMs;
        if (outMs > 0 && elapsed > outStart) {
            float t = Math.min(1f, (float) (elapsed - outStart) / outMs);
            // How far it goes to be off the screen past its edge.
            int off = switch (from) {
                case TOP -> y + panelH;
                case BOTTOM -> screenH - y;
                case LEFT -> x + panelW;
                case RIGHT -> screenW - x;
            };
            // Ease in: t^2, so it leaves as it came, slow near its place and quick at the edge.
            away = Math.max(away, Math.max(0, off) * t * t);
        }

        int d = (int) away;
        return switch (from) {
            case TOP -> new ScreenOrigin(x, y - d);
            case BOTTOM -> new ScreenOrigin(x, y + d);
            case LEFT -> new ScreenOrigin(x - d, y);
            case RIGHT -> new ScreenOrigin(x + d, y);
        };
    }

    private int computeContentWidth(Minecraft mc, @Nullable String text, @Nullable ItemStack item) {
        int w = 0;
        if (item != null && !item.isEmpty()) w += 20; // 16px icon + 4px gap
        if (text != null && !text.isEmpty()) w += mc.font.width(text);
        return Math.max(w, 40); // minimum width
    }

    /**
     * Starts a notification builder.
     *
     * @param key unique identifier used to trigger this notification at runtime
     *            (see {@link MK#notify(String, String)})
     */
    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static class Builder {
        private final String key;
        private InsideRegion region = InsideRegion.TOP_CENTER;
        private int offsetX = 0, offsetY = 6;   // 10px from the top edge, as before 6.0.0
        private int durationMs = 3000;
        private int fadeMs = 500;
        private SlideDirection slideFrom = SlideDirection.TOP;
        private int slideDistance = 20;
        private PanelStyle style = PanelStyle.RAISED;
        private int padding = 6;
        private int width = 0, height = 0;

        Builder(String key) { this.key = key; }

        /**
         * Where it pops up: an {@link InsideRegion} spot of the game window, 4px in
         * from the edges it touches (default {@code TOP_CENTER}). Resolved the same
         * way as HUD panels, by {@link RegionMath#resolveInside}.
         */
        public Builder region(InsideRegion region) {
            this.region = region;
            return this;
        }

        /**
         * A pixel nudge after placement (default {@code 0, 6}: 10px below the top
         * edge). Migrating from {@code .anchor(MKHudAnchor.X, dx, dy)}: see
         * {@link HudPanel.Builder#offset}.
         */
        public Builder offset(int dx, int dy) {
            this.offsetX = dx;
            this.offsetY = dy;
            return this;
        }

        /** Total display duration in milliseconds; includes the slide-out (default 3000). */
        public Builder duration(int ms) { this.durationMs = ms; return this; }

        /**
         * How long it takes to slide out, at the tail end of the total duration (default 500).
         * The name is from when notifications faded out; they slide out now, as vanilla's toasts do.
         */
        public Builder fadeOut(int ms) { this.fadeMs = ms; return this; }

        /** Direction the notification slides in from (default TOP). */
        public Builder slideFrom(SlideDirection dir) { this.slideFrom = dir; return this; }

        /** Slide-in animation distance in pixels (default 20). */
        public Builder slideDistance(int pixels) { this.slideDistance = pixels; return this; }

        /** Background style (default RAISED). */
        public Builder style(PanelStyle style) { this.style = style; return this; }

        /** Inner padding between panel edge and content (default 6). */
        public Builder padding(int padding) { this.padding = padding; return this; }

        /** Explicit panel size; if 0, auto-sized from content. */
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }

        /**
         * Builds and registers the notification template with MenuKit.
         *
         * @throws IllegalStateException after MenuKit's declarations froze at client
         *         start, or for a second notification with this key
         */
        public void build() {
            HudNotification notification = new HudNotification(
                    key, region, offsetX, offsetY,
                    durationMs, fadeMs, slideFrom, slideDistance,
                    style, padding, width, height
            );
            MK.registerNotification(notification);
        }
    }
}
