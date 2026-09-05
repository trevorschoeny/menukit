package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The single library entry point for queuing a hover-float tooltip — the one
 * place a {@code Component} tooltip becomes pixels on screen. Every element
 * tooltip and the panel-level tooltip route through {@link #queue} instead of
 * calling vanilla's {@code setTooltipForNextFrame} directly.
 *
 * <h3>Why this exists (the §0029 walk)</h3>
 *
 * Hover tooltips had NO maximum width: a long tooltip drew as one unwrapped
 * line stretching across the screen — annoying. The fix is a width cap, and the
 * cap must be a library DEFAULT that every tooltip inherits automatically, never
 * patched per element or per call site. The width decision is not element-specific
 * (every tooltip should wrap the same way), and it is part of the <em>emit</em>
 * call, not the per-widget <em>trigger</em> logic (which legitimately varies —
 * Button suppresses on press, Dropdown gates trigger-vs-popover). The one
 * substrate all ~16 tooltip sites already shared was the single vanilla call
 * {@code graphics.setTooltipForNextFrame(font, component, mx, my)}. So the missing
 * primitive is a thin wrapper over exactly that call — this class.
 *
 * <h3>The exception to adaptive-width-as-default</h3>
 *
 * Pass 3 made adaptive (grow-to-content) width the default for laid-out panel
 * content. A hover tooltip is the deliberate exception: it is a mouse-follower
 * float with no containing layout to bound it, so "grow to content" is exactly
 * what produces the across-the-screen line. Capping the tooltip is the
 * tooltip-surface analogue of the screen-edge ceiling that already bounds panels
 * — same intent (don't let a float run to the screen edge), applied to the one
 * surface the panel machinery doesn't reach. The cap reuses {@code Font.split},
 * the same vanilla wrapper {@code TextLabel}'s adaptive path uses, so the two
 * layers share a wrapping vocabulary without sharing code.
 */
public final class MKTooltip {

    private MKTooltip() {}

    /**
     * Library-default maximum hover-tooltip width, in GUI pixels. Sized to
     * about seven average words: ~7 words averaging ~5 chars + a space ≈ 40
     * chars, and Minecraft's default font averages ~6px per glyph (incl. the
     * 1px gap), so ≈ 200px. A tooltip wider than this wraps onto multiple lines
     * rather than drawing as one long horizontal line. Tunable — the sane band
     * is roughly 180–220; widen if seven words feels too tight in-game.
     */
    public static final int DEFAULT_MAX_WIDTH = 200;

    /**
     * Queues a hover tooltip for end-of-frame draw at the mouse position,
     * wrapping at the library-default max width ({@link #DEFAULT_MAX_WIDTH}).
     * This is the form every element/panel tooltip site calls.
     *
     * @param graphics the active GuiGraphicsExtractor
     * @param text     the tooltip text (may span multiple wrapped lines)
     * @param mouseX   screen-space mouse X
     * @param mouseY   screen-space mouse Y
     */
    public static void queue(GuiGraphicsExtractor graphics, Component text, int mouseX, int mouseY) {
        queue(graphics, text, mouseX, mouseY, DEFAULT_MAX_WIDTH);
    }

    /**
     * Queues a hover tooltip with an explicit max width — the per-tooltip escape
     * hatch from the default. Pass {@code 0} (or any non-positive value) to
     * disable wrapping entirely and restore the old single-line behavior.
     *
     * <p>Wrapping uses {@code Font.split} (the same splitter as in-panel text),
     * which breaks on spaces and only force-breaks within a single
     * unbreakable word as a last resort — so a single ~40-char word can still
     * exceed the budget on one line, exactly as vanilla behaves.
     *
     * @param graphics   the active GuiGraphicsExtractor
     * @param text       the tooltip text
     * @param mouseX     screen-space mouse X
     * @param mouseY     screen-space mouse Y
     * @param maxWidthPx the wrap budget in pixels, or {@code <= 0} to not wrap
     */
    public static void queue(GuiGraphicsExtractor graphics, Component text,
                             int mouseX, int mouseY, int maxWidthPx) {
        if (text == null) return;
        Font font = Minecraft.getInstance().font;
        // Fast path: wrapping disabled, or the whole line already fits the
        // budget — hand the single Component straight through (no split cost).
        if (maxWidthPx <= 0 || font.width(text) <= maxWidthPx) {
            graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
            return;
        }
        // Over budget — wrap to the budget and hand the pre-split lines to the
        // List overload. Same vanilla call chain (and the same private
        // setTooltipForNextFrameInternal that MKTooltipSuppressMixin guards),
        // so modal/opaque-panel tooltip suppression keeps working unchanged.
        List<FormattedCharSequence> lines = font.split(text, maxWidthPx);
        graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
