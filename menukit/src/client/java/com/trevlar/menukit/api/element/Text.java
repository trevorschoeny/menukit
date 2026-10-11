package com.trevlar.menukit.api.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Text drawing shared by MenuKit's elements: one line in a bounded width, and
 * wrapped text.
 *
 * <p>A single line that fits is drawn at the requested alignment. One that is
 * wider than its bounds scrolls back and forth inside them, as vanilla's button
 * labels do, so no text is ever cut off. Vanilla's own
 * {@code GuiGraphicsExtractor.textRenderer().acceptScrolling(...)} always uses its
 * centred model, so it cannot draw text left-aligned when it fits and scrolling
 * when it does not; {@link #render} can.
 *
 * <p>Button, Toggle and ProgressBar centre their label with it, and the dropdowns
 * draw their trigger and popover rows with it. An element whose label sizes the
 * element (Checkbox, Radio) never overflows, and multi-line text wraps instead
 * ({@link #drawWrapped}).
 */
public final class Text {

    private Text() {}

    /**
     * Where to draw one line of text so it looks centred in the box from
     * {@code top} to {@code bottom}: vanilla's rule, {@code (top + bottom -
     * lineHeight) / 2 + 1}, the same arithmetic as its button labels and its
     * scrolling-text helper ({@code ActiveTextCollector.defaultScrollingHelper}).
     *
     * <p><b>Why the {@code + 1}.</b> Minecraft's font line is 9 px, but a glyph
     * does not fill it evenly. Capitals and most lowercase sit in the top 7 rows,
     * the drop shadow adds one row under them, and the 9th row is only reached by
     * the shadow of a descender (g, p, y). Centring the whole 9 px line therefore
     * puts what the eye reads, the capitals, a pixel too high; one pixel down
     * centres the capitals instead. Every MenuKit control that draws text in a box
     * goes through this, so a 16 px button and a 16 px toggle side by side put
     * their text on the same row.
     */
    public static int centeredTextY(int top, int bottom) {
        return centeredBlockY(top, bottom, 1);
    }

    /**
     * {@link #centeredTextY} for a block of {@code lines} lines, stacked at
     * {@code lineHeight}: where the first line goes. One line is the same answer.
     */
    public static int centeredBlockY(int top, int bottom, int lines) {
        Font font = Minecraft.getInstance().font;
        return (top + bottom - Math.max(1, lines) * font.lineHeight) / 2 + 1;
    }

    /**
     * Draws {@code text} in the box from {@code (x1, y1)} to {@code (x2, y2)}. Text
     * that fits is drawn at {@code align}; wider text scrolls back and forth
     * across the bounds with vanilla's animation, where the alignment no longer
     * shows.
     *
     * <p>The text is centred vertically between {@code y1} and {@code y2}, usually
     * {@code (textY, textY + font.lineHeight)}. {@code color} is ARGB and needs a
     * non-zero alpha, or vanilla silently drops the draw.
     *
     * @param shadow whether to draw a text shadow. Only the fitting path uses it;
     *               scrolling text always draws with vanilla's default shadow.
     */
    public static void render(GuiGraphicsExtractor graphics, Component text,
                              TextAlignment align,
                              int x1, int x2, int y1, int y2,
                              int color, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        int boundsW = x2 - x1;
        int textW = font.width(text);
        if (textW <= boundsW) {
            // Not TextAlignment.calculateLeft: it takes (anchorPoint, textWidth),
            // where the anchor is the left edge, the centre or the right edge
            // depending on the alignment, and passing (containerWidth, textWidth)
            // to it is an easy mistake. The arithmetic here is plainer.
            int textX = switch (align) {
                case LEFT -> x1;
                case CENTER -> x1 + (boundsW - textW) / 2;
                case RIGHT -> x2 - textW;
            };
            // Vanilla's centring rule, the same one the scrolling branch gets from
            // vanilla, so fitting and overflowing labels sit on the same row.
            int textY = centeredTextY(y1, y2);
            graphics.text(font, text, textX, textY, color, shadow);
        } else {
            // Vanilla's scroll call is (text, centerX, x1, x2, y1, y2) with no
            // colour parameter, so the colour goes on the Component's style.
            // centerX only matters for text that fits, handled above; scrolling
            // text moves across x1..x2. acceptScrolling does not scissor itself,
            // so the scissor here clips the animation to the bounds.
            int centerX = switch (align) {
                case LEFT -> x1;
                case CENTER -> (x1 + x2) / 2;
                case RIGHT -> x2;
            };
            // TextColor.fromRgb takes RGB only, so the alpha is masked off. The
            // existing style is kept, so bold or italic still applies.
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
     * {@link #render} centred in the box at {@code (x, y)} of {@code width} by
     * {@code height}, the shape of a vanilla button label.
     */
    public static void renderCentered(GuiGraphicsExtractor graphics, Component text,
                                       int x, int y, int width, int height,
                                       int color, boolean shadow) {
        render(graphics, text, TextAlignment.CENTER, x, x + width, y, y + height,
                color, shadow);
    }

    /**
     * {@link #render} left-aligned, with the text drawn at {@code y} and
     * scrolling inside {@code [x, x + width)} when it overflows. The vertical
     * bounds come from the font's line height.
     */
    public static void renderLeft(GuiGraphicsExtractor graphics, Component text,
                                   int x, int y, int width,
                                   int color, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        render(graphics, text, TextAlignment.LEFT, x, x + width, y - 1, y - 1 + font.lineHeight,
                color, shadow);
    }

    // Vanilla's ActiveTextCollector.acceptScrolling times its scroll cycle from
    // global system time (Util.getMillis() % cyclePeriod), so text can be anywhere
    // in its scroll when it first appears. That is fine for text that is always
    // on screen (a button label), but in a popover that just opened the player
    // would see rows already mid-scroll instead of their beginning.
    // renderFromOpenTime uses vanilla's exact formula (cos and sin smoothing,
    // 0.5 s per pixel, a 3 s minimum cycle) with its own time origin.

    /**
     * {@link #render}, with the scroll cycle starting at {@code openTimeMillis}:
     * at that instant the text shows its beginning, then it scrolls to the end
     * and back, repeating every {@code max(scrollDistance * 0.5, 3.0)} seconds
     * (vanilla's cycle period). Text that fits is drawn as {@link #render} draws
     * it, and {@code openTimeMillis} is ignored.
     *
     * <p>Use it for text that appears at a moment the player notices, such as a
     * popover's rows. For text that is always shown, {@link #render} keeps every
     * scrolling label in step.
     *
     * @param openTimeMillis the {@link Util#getMillis} value when the surface
     *                       opened; the text is at its beginning then
     */
    public static void renderFromOpenTime(GuiGraphicsExtractor graphics, Component text,
                                           TextAlignment align,
                                           int x1, int x2, int y1, int y2,
                                           int color, boolean shadow,
                                           long openTimeMillis) {
        Font font = Minecraft.getInstance().font;
        int boundsW = x2 - x1;
        int textW = font.width(text);
        if (textW <= boundsW) {
            // Fits: drawn as render() draws it, with no scroll to time.
            int textX = switch (align) {
                case LEFT -> x1;
                case CENTER -> x1 + (boundsW - textW) / 2;
                case RIGHT -> x2 - textW;
            };
            int textY = centeredTextY(y1, y2);
            graphics.text(font, text, textX, textY, color, shadow);
            return;
        }

        // Vanilla's scroll math from ActiveTextCollector.defaultScrollingHelper,
        // with an explicit time origin.
        int scrollDistance = textW - boundsW;
        double cyclePeriod = Math.max(scrollDistance * 0.5, 3.0);  // sec
        // A half-period offset, so that elapsedSec = 0 gives phase 0 (the text's
        // beginning). Vanilla's formula has phase 1 (the end) at t = 0.
        double elapsedSec = (Util.getMillis() - openTimeMillis) / 1000.0
                          + cyclePeriod / 2.0;
        double cosT = Math.cos(2.0 * Math.PI * elapsedSec / cyclePeriod);
        double phase = Math.sin(Math.PI / 2.0 * cosT) / 2.0 + 0.5;
        int scrollOffset = (int) Mth.lerp(phase, 0.0, scrollDistance);

        int textX = x1 - scrollOffset;
        int textY = centeredTextY(y1, y2);
        graphics.enableScissor(x1, y1, x2, y2);
        graphics.text(font, text, textX, textY, color, shadow);
        graphics.disableScissor();
    }

    // Wrapping. Every element with text wraps it the same way: only when the
    // text is wider than its room, split by vanilla's font.split (the splitter
    // chat, tooltips and book pages use, so the breaks match what a player
    // expects), one line every lineHeight. An element keeps only the wrap width,
    // recomputed on every layout pass, so a wider pass returns 0 and the text is
    // one line again.

    /**
     * The width {@code text} should wrap to inside {@code available} pixels:
     * {@code 0} (don't wrap) when it fits on one line, else {@code available},
     * floored at 1 so the splitter never sees a non-positive width.
     */
    public static int wrapWidth(Component text, int available) {
        if (text == null) return 0;
        return Minecraft.getInstance().font.width(text) > available ? Math.max(1, available) : 0;
    }

    /** The lines {@code text} breaks into at {@code wrapWidth}; one line when {@code wrapWidth <= 0}. */
    public static List<FormattedCharSequence> lines(Component text, int wrapWidth) {
        Font font = Minecraft.getInstance().font;
        if (wrapWidth <= 0) return List.of(text.getVisualOrderText());
        List<FormattedCharSequence> lines = font.split(text, wrapWidth);
        return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
    }

    /** How many lines {@code text} takes at {@code wrapWidth} (at least 1). */
    public static int lineCount(Component text, int wrapWidth) {
        return wrapWidth <= 0 ? 1 : lines(text, wrapWidth).size();
    }

    /**
     * Draws {@code text} broken at {@code wrapWidth}, the first line at
     * {@code y}, one line every {@code lineHeight}. Each line starts at {@code x};
     * with {@code centreIn > 0} each is centred in {@code [x, x + centreIn)}
     * instead (a button's label).
     */
    public static void drawWrapped(GuiGraphicsExtractor graphics, Component text, int wrapWidth,
                                   int x, int y, int centreIn, int color, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        int lineY = y;
        for (FormattedCharSequence line : lines(text, wrapWidth)) {
            int lineX = centreIn > 0 ? x + (centreIn - font.width(line)) / 2 : x;
            graphics.text(font, line, lineX, lineY, color, shadow);
            lineY += font.lineHeight;
        }
    }
}
