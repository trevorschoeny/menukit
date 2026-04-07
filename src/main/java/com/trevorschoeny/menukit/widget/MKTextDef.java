package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.panel.MKPanel;
import com.trevorschoeny.menukit.panel.MKPanelDef;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Immutable blueprint for a text label inside an {@link MKPanelDef}.
 * Created at mod init time by {@link MKPanel.Builder#text}, rendered
 * during the panel background pass.
 *
 * <p>Text can be static or dynamic (via a {@link Supplier}). Dynamic text
 * re-evaluates each frame — suitable for titles, status indicators, or
 * any label that changes at runtime.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public record MKTextDef(
        int childX,                                 // panel-relative x
        int childY,                                 // panel-relative y
        Supplier<Component> content,                // text content (evaluated each frame)
        int color,                                  // text color (ARGB, e.g., 0x404040)
        boolean shadow,                             // render with drop shadow
        boolean vertical,                           // render rotated -90° (bottom-to-top)
        @Nullable BooleanSupplier disabledWhen      // hidden when true
) {

    /** Default text color — dark gray, matches vanilla container labels. */
    public static final int DEFAULT_COLOR = 0xFF404040;

    /** Estimated height of a text element (font height 9px). */
    public static final int TEXT_HEIGHT = 9;

    /** Average character width in pixels — used as fallback when Font is unavailable. */
    private static final int AVG_CHAR_WIDTH = 6;

    /**
     * Returns the raw pixel width of the text string (always horizontal measurement).
     * Uses Font.width() on the render thread; falls back to a character-count
     * estimate on the server thread (where the render system is unavailable).
     */
    public int estimateWidth() {
        Component text = content.get();
        if (text == null) return 0;
        // Font.width() triggers glyph baking which requires the render thread.
        // On the server thread, fall back to a rough character-count estimate.
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            return text.getString().length() * AVG_CHAR_WIDTH;
        }
        return net.minecraft.client.Minecraft.getInstance().font.width(text);
    }

    /**
     * Returns the layout width — for vertical text this is the font height
     * (since the string is rotated 90°), for horizontal it's the string width.
     */
    public int layoutWidth() {
        return vertical ? TEXT_HEIGHT : estimateWidth();
    }

    /**
     * Returns the layout height — for vertical text this is the string width
     * (since the string is rotated 90°), for horizontal it's the font height.
     */
    public int layoutHeight() {
        return vertical ? estimateWidth() : TEXT_HEIGHT;
    }
}
