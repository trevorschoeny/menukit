package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MK;

import com.trevorschoeny.menukit.core.HudRegion;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.RegionAnchor;
import com.trevorschoeny.menukit.core.RegionMath;
import com.trevorschoeny.menukit.inject.ScreenOrigin;

import java.util.Optional;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * HUD notification element — a timed popup that slides in, displays for
 * a duration, then fades out.
 *
 * <p>Built as a template at mod init, triggered at runtime via
 * {@link MK#notify(String, String)} or {@link MK#notify(String, String, ItemStack)}.
 *
 * <p>This is the only stateful HUD element — animation state is tracked
 * in {@link MK}'s active notification map, not on this object.
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
 * MK.notify("alert", "First Diamond!");
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudNotification {

    /** Direction the notification slides in from. */
    public enum SlideDirection { TOP, BOTTOM, LEFT, RIGHT }

    private final String key;
    private final MKHudAnchor anchor;
    // Region positioning (N7 parity with MKHudPanel). When non-null, position
    // resolves through RegionMath.resolveHud (the same HudRegion system panels
    // use) instead of the legacy MKHudAnchor.resolve path; offsetX/offsetY
    // still nudge the resolved origin. A notification is a singular popup, so
    // it resolves with a zero stacking prefix (no region registry).
    private final @Nullable HudRegion region;
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

    MKHudNotification(String key, MKHudAnchor anchor, @Nullable HudRegion region,
                      int offsetX, int offsetY,
                      int durationMs, int fadeMs, SlideDirection slideFrom,
                      int slideDistance, PanelStyle style, int padding,
                      int width, int height) {
        this.key = key;
        this.anchor = anchor;
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
    public String getKey() { return key; }

    /** Total display duration in milliseconds (excludes slide-in but includes fade-out). */
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
    public void render(GuiGraphicsExtractor graphics, DeltaTracker dt,
                       int screenW, int screenH, long elapsed,
                       @Nullable String text, @Nullable ItemStack item) {
        // Compute content size
        var mc = Minecraft.getInstance();
        int contentW = width > 0 ? width : computeContentWidth(mc, text, item);
        int contentH = height > 0 ? height : padding * 2 + 9; // 9 = font height
        int panelW = contentW + padding * 2;
        int panelH = contentH;

        // Resolve base position. Region mode (N7) routes through
        // RegionMath.resolveHud — the same HudRegion system MKHudPanel uses —
        // with a zero stacking prefix (a notification is a singular popup, not
        // a registry-stacked panel); offsetX/offsetY then nudge the resolved
        // origin. Legacy anchor mode falls back to MKHudAnchor.resolve.
        int baseX, baseY;
        if (region != null) {
            Optional<ScreenOrigin> origin = RegionMath.resolveHud(
                    region, screenW, screenH, panelW, panelH, /*prefix=*/ 0);
            if (origin.isPresent()) {
                baseX = origin.get().x() + offsetX;
                baseY = origin.get().y() + offsetY;
            } else {
                // Overflow (panel taller than the region's axial capacity) —
                // fall back to a top-center anchor so the notification still
                // shows rather than silently vanishing.
                int[] pos = anchor.resolve(screenW, screenH, panelW, panelH, offsetX, offsetY);
                baseX = pos[0];
                baseY = pos[1];
            }
        } else {
            int[] pos = anchor.resolve(screenW, screenH, panelW, panelH, offsetX, offsetY);
            baseX = pos[0];
            baseY = pos[1];
        }

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
            graphics.item(item, contentX, contentY);
            contentX += 20; // 16px icon + 4px gap
        }

        if (text != null && !text.isEmpty()) {
            int textColor = (alphaInt & 0xFF000000) | 0xFFFFFF;
            graphics.text(mc.font, Component.literal(text),
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

    /**
     * Creates a new notification builder.
     *
     * @param key unique identifier used to trigger this notification at runtime
     *            (see {@link MK#notify(String, String)})
     */
    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static class Builder {
        private final String key;
        private MKHudAnchor anchor = MKHudAnchor.TOP_CENTER;
        private @Nullable HudRegion region;  // null unless .region() called
        private int offsetX = 0, offsetY = 10;
        private int durationMs = 3000;
        private int fadeMs = 500;
        private SlideDirection slideFrom = SlideDirection.TOP;
        private int slideDistance = 20;
        private PanelStyle style = PanelStyle.RAISED;
        private int padding = 6;
        private int width = 0, height = 0;

        Builder(String key) { this.key = key; }

        /**
         * Sets the screen-edge anchor and offset (default: TOP_CENTER, 0, 10).
         *
         * <p><b>Legacy positioning path.</b> {@link com.trevorschoeny.menukit.core.HudRegion}
         * (via {@link #region(HudRegion)}) is the intended primary system,
         * matching {@link MKHudPanel}; it routes through the same
         * {@link RegionMath#resolveHud} math panels use. {@code anchor(...)}
         * remains for back-compat and for the {@link MKHudAnchor#CENTER_LEFT}/
         * {@link MKHudAnchor#CENTER_RIGHT} vertical-center positions that
         * HudRegion spells differently. Setting both is allowed; {@code region}
         * wins when present.
         */
        public Builder anchor(MKHudAnchor anchor, int offsetX, int offsetY) {
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            return this;
        }

        /**
         * Positions this notification via a named
         * {@link com.trevorschoeny.menukit.core.HudRegion} — the parity path
         * with {@link MKHudPanel#builder(String)}'s {@code .region(...)}.
         * Position resolves through {@link RegionMath#resolveHud} (the same
         * math HUD panels use) with a zero stacking prefix, since a
         * notification is a singular popup rather than a registry-stacked
         * panel. The builder's offset (default {@code 0, 10}) nudges the
         * resolved origin — call {@link #anchor(MKHudAnchor, int, int)}'s
         * offset args are reused, or set a custom offset by also calling
         * {@code anchor(...)} for the offset only (region still wins for
         * placement).
         *
         * @param region the HUD region anchor
         * @return this builder, for chaining
         */
        public Builder region(HudRegion region) {
            this.region = region;
            return this;
        }

        /**
         * Region overload accepting a
         * {@link com.trevorschoeny.menukit.core.RegionAnchor} — region paired
         * with a priority. Notifications don't stack in a region registry, so
         * the priority is accepted for signature parity with
         * {@link MKHudPanel} but is not consulted (a notification resolves at
         * the region's anchor with a zero prefix). Equivalent to
         * {@code .region(anchor.region())}.
         */
        public Builder region(RegionAnchor<HudRegion> anchor) {
            this.region = anchor.region();
            return this;
        }

        /** Total display duration in milliseconds; includes fade-out (default 3000). */
        public Builder duration(int ms) { this.durationMs = ms; return this; }

        /** Fade-out duration at the tail end of the total duration (default 500). */
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

        /** Builds and registers the notification template with MenuKit. */
        public void build() {
            MKHudNotification notification = new MKHudNotification(
                    key, anchor, region, offsetX, offsetY,
                    durationMs, fadeMs, slideFrom, slideDistance,
                    style, padding, width, height
            );
            MK.registerNotification(notification);
        }
    }
}
