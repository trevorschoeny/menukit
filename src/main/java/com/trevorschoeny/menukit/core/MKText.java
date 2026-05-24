package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * Shared text-rendering helper for MK widgets that render single-line
 * text inside a bounded width. Provides vanilla's overflow-scroll
 * behavior: when the text fits, it's drawn statically with the
 * requested alignment; when it doesn't, it scrolls back and forth
 * within the bounds — the same primitive vanilla's Button uses for
 * its label.
 *
 * <h3>Why a helper</h3>
 *
 * The underlying primitive is {@code GuiGraphics.textRenderer()
 * .acceptScrolling(...)}, which always uses vanilla's centered scroll
 * model when the text overflows. Many MK widgets need LEFT-aligned
 * text when it fits, then graceful overflow scrolling — a combination
 * vanilla's API doesn't directly express. This helper bridges the
 * gap: align-when-it-fits, scroll-when-it-doesn't, single call site
 * per widget.
 *
 * <h3>Where this is used</h3>
 *
 * Every MK widget rendering single-line text inside a bounded width:
 * {@code Button} (centered), {@code Dropdown}/{@code DropdownMulti}
 * (trigger header + popover items, left-aligned), {@code TextLabel}
 * (single-line mode, configurable alignment), {@code ProgressBar}
 * (value text, centered). Excluded: widgets with auto-sized labels
 * ({@code Checkbox}, {@code Radio} — labels grow the widget to fit, no
 * overflow possible), multi-line modes ({@code TextLabel} multi-line,
 * {@code Tooltip} — wrapping is the intent), and HUD widgets (display-
 * style, wrap or stay short).
 *
 * <h3>Phase 18s follow-up</h3>
 *
 * Added in response to Trev's "scroll wherever there's text" directive.
 * Replaces ad-hoc truncate-with-ellipsis in Dropdown / DropdownMulti
 * and the no-overflow-handling state on Button / TextLabel.
 */
public final class MKText {

    private MKText() {}

    /**
     * Renders {@code text} within the rectangle (x1..x2, y1..y2) with
     * the requested alignment. When the text fits within
     * (x2 - x1), it's drawn statically at the aligned position. When
     * it's wider, vanilla's scroll-back-and-forth animation kicks in
     * within the bounds (alignment doesn't apply visually when
     * scrolling — the text fills the bounds and moves).
     *
     * <p>{@code x1..x2} = the horizontal bounds. Text draws between them
     * when scrolling.
     * <br>{@code y1..y2} = the vertical bounds. Text is vertically
     * centered in this range. Typically {@code (textY, textY + font.lineHeight)}.
     * <br>{@code color} = ARGB (alpha non-zero, else vanilla silently
     * discards the draw).
     *
     * @param shadow whether to draw a text shadow (matches vanilla
     *               {@code drawString}'s {@code shadow} parameter; the
     *               static-fit path passes it through, the scroll path
     *               always renders with vanilla's default shadow
     *               behavior)
     */
    public static void render(GuiGraphics graphics, Component text,
                              TextAlignment align,
                              int x1, int x2, int y1, int y2,
                              int color, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        int boundsW = x2 - x1;
        int textW = font.width(text);
        if (textW <= boundsW) {
            // Fits — draw statically at the requested alignment.
            //
            // NOTE: not using TextAlignment.calculateLeft here because
            // its arg semantics are surprising — it takes
            // (anchorPoint, textWidth), where anchorPoint differs per
            // alignment (left edge for LEFT, center for CENTER, right
            // edge for RIGHT). Inlining the math is clearer and avoids
            // the foot-gun of passing (containerWidth, textWidth).
            int textX = switch (align) {
                case LEFT -> x1;
                case CENTER -> x1 + (boundsW - textW) / 2;
                case RIGHT -> x2 - textW;
            };
            // Vertical centering inside y1..y2.
            int boundsH = y2 - y1;
            int textY = y1 + (boundsH - font.lineHeight) / 2;
            graphics.drawString(font, text, textX, textY, color, shadow);
        } else {
            // Overflows — defer to vanilla's scroll primitive. The
            // signature is (text, centerX, x1, x2, y1, y2) — there is
            // NO color parameter on vanilla's scroll API, so color is
            // applied via Style on the Component before handoff.
            //
            // centerX is the alignment anchor — only relevant when text
            // fits (we already handled that above); for the actual
            // scrolling animation, text traverses x1..x2 left-right.
            //
            // Scissor wrapping clips the animation to widget bounds:
            // vanilla's acceptScrolling animates the text position each
            // frame but does NOT scissor itself.
            int centerX = switch (align) {
                case LEFT -> x1;
                case CENTER -> (x1 + x2) / 2;
                case RIGHT -> x2;
            };
            // Apply color via Style — strip alpha (TextColor.fromRgb
            // takes RGB only; alpha is implicit in text rendering).
            // Composes with existing Style so any bold/italic on the
            // Component carries through.
            MutableComponent colored = text.copy()
                    .withStyle(existing -> existing.withColor(
                            TextColor.fromRgb(color & 0x00FFFFFF)));
            graphics.enableScissor(x1, y1, x2, y2);
            graphics.textRenderer().acceptScrolling(
                    colored, centerX, x1, x2, y1, y2);
            graphics.disableScissor();
        }
    }

    /**
     * Convenience: centered single-line scroll, vertically centered
     * across {@code height} starting at {@code y}. Matches the
     * vanilla button-label call shape.
     */
    public static void renderCentered(GuiGraphics graphics, Component text,
                                       int x, int y, int width, int height,
                                       int color, boolean shadow) {
        render(graphics, text, TextAlignment.CENTER, x, x + width, y, y + height,
                color, shadow);
    }

    /**
     * Convenience: left-aligned single-line scroll on a single text
     * baseline. Vertical bounds are derived from font line height
     * — text draws at {@code y} with scroll-on-overflow inside
     * {@code [x, x + width)}.
     */
    public static void renderLeft(GuiGraphics graphics, Component text,
                                   int x, int y, int width,
                                   int color, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        render(graphics, text, TextAlignment.LEFT, x, x + width, y, y + font.lineHeight,
                color, shadow);
    }
}
