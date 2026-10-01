package com.trevlar.menukit.api.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import com.trevlar.menukit.api.window.Declarations;
import com.trevlar.menukit.mixin.ClientTextTooltipAccessor;

import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The single library entry point for queuing a hover-float tooltip, the one
 * place a {@code Component} tooltip becomes pixels on screen. Every element
 * tooltip and the panel-level tooltip route through {@link #queue} instead of
 * calling vanilla's {@code setTooltipForNextFrame} directly.
 *
 * <h3>Why this exists (the §0029 walk)</h3>
 *
 * Hover tooltips had NO maximum width: a long tooltip drew as one unwrapped
 * line stretching across the screen, annoying. The fix is a width cap, and the
 * cap must be a library DEFAULT that every tooltip inherits automatically, never
 * patched per element or per call site. The width decision is not element-specific
 * (every tooltip should wrap the same way), and it is part of the <em>emit</em>
 * call, not the per-widget <em>trigger</em> logic (which legitimately varies.
 * Button suppresses on press, Dropdown gates trigger-vs-popover). The one
 * substrate all ~16 tooltip sites already shared was the single vanilla call
 * {@code graphics.setTooltipForNextFrame(font, component, mx, my)}. So the missing
 * primitive is a thin wrapper over exactly that call, this class.
 *
 * <h3>The exception to adaptive-width-as-default</h3>
 *
 * Pass 3 made adaptive (grow-to-content) width the default for laid-out panel
 * content. A hover tooltip is the deliberate exception: it is a mouse-follower
 * float with no containing layout to bound it, so "grow to content" is exactly
 * what produces the across-the-screen line. Capping the tooltip is the
 * tooltip-surface analogue of the screen-edge ceiling that already bounds panels
 * same intent (don't let a float run to the screen edge), applied to the one
 * surface the panel machinery doesn't reach. The cap reuses {@code Font.split},
 * the same vanilla wrapper {@code TextLabel}'s adaptive path uses, so the two
 * layers share a wrapping vocabulary without sharing code.
 */
public final class MKTooltip {

    private MKTooltip() {}

    // ── Hiding every tooltip ─────────────────────────────────────────────
    // MenuKit owns the one seam every tooltip in the game is queued through
    // (MKTooltipSuppressMixin, on vanilla's setTooltipForNextFrameInternal), and
    // §0064 allows one seam per vanilla method, so a mod that wants tooltips gone
    // registers a predicate here instead of adding a second mixin. MenuKit supplies
    // the mechanism; the mod supplies the policy (Inventory Plus: "a setting is on
    // and Ctrl is held").

    /** Registered "hide while" predicates. Written only during init, read every frame. */
    private static final Set<BooleanSupplier> HIDE_WHEN = new LinkedHashSet<>();

    /**
     * Hides every tooltip in the game, on any screen and from any source (vanilla,
     * MenuKit elements, other mods), on every frame {@code condition} is true.
     * Several mods may register; any one returning true hides. Registering the
     * same supplier instance twice is a no-op. Call from client init.
     *
     * @throws IllegalStateException after MenuKit's declarations froze
     */
    public static void hideWhen(BooleanSupplier condition) {
        Declarations.requireOpen("MKTooltip.hideWhen");
        HIDE_WHEN.add(condition);
    }

    /** True when any registered {@link #hideWhen} predicate holds this frame. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static boolean hidden() {
        return anyHolds(HIDE_WHEN);
    }

    /** Any one registered predicate holding switches the behaviour on (hide, wrap, clamp). */
    private static boolean anyHolds(Set<BooleanSupplier> conditions) {
        for (BooleanSupplier c : conditions) {
            if (c.getAsBoolean()) return true;
        }
        return false;
    }

    // ── Keeping a tooltip on screen ──────────────────────────────────────
    // Vanilla 26.2's positioner only moves a tooltip's corner: a line wider than the
    // screen runs off the right, a tooltip taller than the screen runs off the top.
    // Two switches, same split as hideWhen (MenuKit places, the mod decides when;
    // Inventory Plus: "keep tooltips on screen"), both off unless a mod registers.

    /** Registered "wrap while" and "clamp while" predicates. Written only during init, read every frame. */
    private static final Set<BooleanSupplier> WRAP_WHEN = new LinkedHashSet<>();
    private static final Set<BooleanSupplier> CLAMP_WHEN = new LinkedHashSet<>();

    /**
     * Wraps every tooltip to the screen's width on every frame {@code condition} is
     * true: a text line wider than {@link #wrapWidth} breaks onto more lines (vanilla's
     * splitter, breaking at spaces) and the tooltip is kept inside the screen's sides.
     * Images (a bundle's grid, a map) are left as they are. A wrapped first line stays
     * one title: the gap under it and the scroll pin take all of its lines. Several mods
     * may register; any one returning true wraps. Call from client init.
     *
     * @throws IllegalStateException after MenuKit's declarations froze
     */
    public static void wrapWhen(BooleanSupplier condition) {
        Declarations.requireOpen("MKTooltip.wrapWhen");
        WRAP_WHEN.add(condition);
    }

    /**
     * Keeps every tooltip's top at least {@link #EDGE} below the top of the screen on
     * every frame {@code condition} is true. A tooltip taller than the screen then
     * starts at the top, title first, and runs off the bottom; {@link #scrollBy} with a
     * negative y brings up the rest, as far as its last line. Several mods may register;
     * any one returning true clamps. Call from client init.
     *
     * @throws IllegalStateException after MenuKit's declarations froze
     */
    public static void clampWhen(BooleanSupplier condition) {
        Declarations.requireOpen("MKTooltip.clampWhen");
        CLAMP_WHEN.add(condition);
    }

    /** True when any registered {@link #wrapWhen} predicate holds this frame. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static boolean wraps() {
        return anyHolds(WRAP_WHEN);
    }

    /**
     * The widest a tooltip's text may be on a screen {@code screenWidth} wide: the screen
     * less {@link #EDGE} on each side and the background's padding around the text.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static int wrapWidth(int screenWidth) {
        return screenWidth - 2 * EDGE - TooltipRenderUtil.PADDING_LEFT - TooltipRenderUtil.PADDING_RIGHT;
    }

    /**
     * Where a tooltip's text starts so its background stays {@link #EDGE} inside both
     * sides of the screen. Vanilla flips a wide tooltip to the cursor's left but lets one
     * just under the screen's width hang off the right; this pulls it in. One wider than
     * {@link #wrapWidth} (an image) goes to the left edge, as vanilla puts it.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static int fitX(int x, int width, int screenWidth) {
        int left = EDGE + TooltipRenderUtil.PADDING_LEFT;
        return Math.max(left, Math.min(x, screenWidth - EDGE - TooltipRenderUtil.PADDING_RIGHT - width));
    }

    /**
     * {@code lines} with every text line wider than {@link #wrapWidth} split by vanilla's
     * {@code Font.split}. A split line stays one component holding vanilla's text line per
     * piece, so a wrapped title is still the first line. Returns {@code lines} itself when
     * nothing is too wide.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static List<ClientTooltipComponent> wrapped(Font font, List<ClientTooltipComponent> lines, int screenWidth) {
        int max = wrapWidth(screenWidth);
        List<ClientTooltipComponent> out = null;  // made on the first line that needs it
        for (int i = 0; i < lines.size(); i++) {
            ClientTooltipComponent line = lines.get(i);
            ClientTooltipComponent kept = line;
            if (line instanceof ClientTextTooltip text && line.getWidth(font) > max) {
                List<ClientTooltipComponent> pieces = new ArrayList<>();
                for (FormattedCharSequence piece : font.split(asText(((ClientTextTooltipAccessor) text).mk$text()), max)) {
                    pieces.add(ClientTooltipComponent.create(piece));
                }
                kept = new WrappedLine(List.copyOf(pieces));
            }
            if (kept != line && out == null) out = new ArrayList<>(lines.subList(0, i));
            if (out != null) out.add(kept);
        }
        return out == null ? lines : out;
    }

    /**
     * A tooltip line back as splittable text: each run of one style becomes a styled
     * piece. ponytail: Font.split reorders right-to-left text for display, and the line
     * already was, so an Arabic or Hebrew line wider than the screen may wrap reversed.
     * Keep the source Component per line if that ever shows.
     */
    private static FormattedText asText(FormattedCharSequence line) {
        List<FormattedText> runs = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Style[] style = {Style.EMPTY};
        line.accept((index, s, codePoint) -> {
            if (!s.equals(style[0]) && !run.isEmpty()) {
                runs.add(FormattedText.of(run.toString(), style[0]));
                run.setLength(0);
            }
            style[0] = s;
            run.appendCodePoint(codePoint);
            return true;
        });
        if (!run.isEmpty()) runs.add(FormattedText.of(run.toString(), style[0]));
        return FormattedText.composite(runs);
    }

    /** One line wrapped onto several, drawn as vanilla's text lines stacked: vanilla sees one line. */
    private record WrappedLine(List<ClientTooltipComponent> pieces) implements ClientTooltipComponent {
        @Override public int getHeight(Font font) {
            int h = 0;
            for (ClientTooltipComponent p : pieces) h += p.getHeight(font);
            return h;
        }

        @Override public int getWidth(Font font) {
            int w = 0;
            for (ClientTooltipComponent p : pieces) w = Math.max(w, p.getWidth(font));
            return w;
        }

        @Override public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
            for (ClientTooltipComponent p : pieces) {
                p.extractText(graphics, font, x, y);
                y += p.getHeight(font);
            }
        }
    }

    // ── Scrolling a tooltip ──────────────────────────────────────────────
    // Same split as hideWhen: MenuKit owns where a tooltip lands (MKTooltipSuppressMixin
    // wraps vanilla's GuiGraphicsExtractor.tooltip, the one method that positions and
    // draws every tooltip), the mod owns when and how far to move it (Inventory Plus:
    // "scroll long tooltips", the wheel scrolls a tooltip taller than the screen).
    //
    // The state is one offset for "the tooltip on screen now". MenuKit clears it
    // whenever that tooltip changes: different text (another stack, a line added) or
    // a frame with no tooltip in between. Render thread only, like everything that draws.

    /** Gap kept between a scrolled tooltip and the screen edge, in GUI pixels (vanilla's own minimum). */
    public static final int EDGE = 4;

    /** Listener for {@link #onWheel}. Amounts are what screens get: after the wheel sensitivity and discrete-scroll options. */
    @FunctionalInterface
    public interface Wheel {
        void moved(double horizontal, double vertical);
    }

    /** Registered wheel listeners. Written only during init. */
    private static final List<Wheel> WHEEL = new ArrayList<>();
    /** The offset of the tooltip on screen now, in GUI pixels, already clamped by the last draw. */
    private static int scrollX, scrollY;
    /** {@link #contentKey} of the last tooltip drawn. */
    private static int lastKey;
    /** A tooltip drew in the frame being built ({@code shown}) and in the one before ({@code shownBefore}). */
    private static boolean shown, shownBefore;

    /**
     * Moves the tooltip on screen now by {@code dx, dy} GUI pixels (positive y moves
     * it down, bringing lines above the screen into view). MenuKit keeps it inside
     * its own extent: a tooltip that fits on screen does not move, and one taller or
     * wider than the screen moves only until its far edge is in view. While a tooltip
     * is scrolled vertically with its top above the screen, its first line (the
     * title) stays pinned at the top edge and the rest scrolls under it. The offset
     * resets by itself when the tooltip changes or goes away.
     */
    public static void scrollBy(int dx, int dy) {
        scrollX += dx;
        scrollY += dy;
    }

    /** Puts the tooltip on screen now back where vanilla placed it. */
    public static void resetScroll() {
        scrollX = 0;
        scrollY = 0;
    }

    /**
     * Calls {@code listener} when the mouse wheel moves on a screen while a tooltip is
     * shown. It runs before anything else sees the scroll, and whether or not
     * something then consumes it (a list under the cursor still scrolls). Pair it with
     * {@link #scrollBy}. Call from client init.
     *
     * @throws IllegalStateException after MenuKit's declarations froze
     */
    public static void onWheel(Wheel listener) {
        Declarations.requireOpen("MKTooltip.onWheel");
        WHEEL.add(listener);
    }

    /** The wheel moved (MouseHandler.onScroll HEAD). Fires the listeners when the last frame drew a tooltip. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void wheelMoved(double horizontal, double vertical) {
        if (!shown) return;
        for (Wheel w : WHEEL) w.moved(horizontal, vertical);
    }

    /** A screen frame is about to draw its tooltip, if any (GuiGraphicsExtractor.extractDeferredElements HEAD). */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void frameStarts() {
        shownBefore = shown;
        shown = false;
    }

    /**
     * The frame's tooltip is about to be placed. Its scroll carries over only when the
     * previous frame drew a tooltip with the same text; anything else is a new hover.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void tooltipStarts(int contentKey) {
        if (!shownBefore || contentKey != lastKey) resetScroll();
        lastKey = contentKey;
        shown = true;
    }

    /**
     * Where the tooltip draws: vanilla's placement, pulled inside the sides while
     * {@link #wrapWhen} holds and below the top edge while {@link #clampWhen} holds, plus
     * the offset, clamped (and stored clamped) by {@link #clampScroll} around that spot.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static Vector2ic scrolled(Vector2ic vanilla, int width, int height, int screenWidth, int screenHeight) {
        int x = wraps() ? fitX(vanilla.x(), width, screenWidth) : vanilla.x();
        int y = anyHolds(CLAMP_WHEN) ? Math.max(vanilla.y(), EDGE) : vanilla.y();
        scrollX = clampScroll(x, width, screenWidth, scrollX);
        scrollY = clampScroll(y, height, screenHeight, scrollY);
        return new Vector2i(x + scrollX, y + scrollY);
    }

    /** The title draws pinned at the top edge: scrolled vertically, and the tooltip's top is above the screen. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static boolean pinsTitle(int drawnY) {
        return scrollY != 0 && drawnY < EDGE;
    }

    /**
     * One axis of a scroll. Vanilla put a tooltip of {@code size} at {@code origin} on a
     * screen {@code screen} long; returns {@code offset} clamped so the tooltip moves at
     * most until its far edge sits {@link #EDGE} inside the screen, and back no further
     * than until its near edge does (or vanilla's spot, if that is further). A tooltip
     * that fits gets 0; one that runs off an end can only scroll toward the hidden part.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static int clampScroll(int origin, int size, int screen, int offset) {
        int lowest = Math.min(origin, screen - size - EDGE);  // far (bottom/right) edge in view
        int highest = Math.max(origin, EDGE);                 // near (top/left) edge in view
        return Math.clamp((long) origin + offset, lowest, highest) - origin;
    }

    /**
     * A hash of what a tooltip says, line by line, so "the hovered stack changed"
     * reads as "the text changed" for any tooltip, item or not. Text lines hash their
     * characters; other lines (a bundle's item grid) their kind and size.
     * ponytail: 32-bit hash; a collision between two consecutive tooltips would keep
     * the old scroll for one hover. Compare the strings if that ever shows.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static int contentKey(Font font, List<ClientTooltipComponent> lines) {
        int[] h = {1};
        for (ClientTooltipComponent line : lines) {
            if (line instanceof ClientTextTooltip text) {
                ((ClientTextTooltipAccessor) text).mk$text()
                        .accept((index, style, codePoint) -> { h[0] = 31 * h[0] + codePoint; return true; });
            } else {
                h[0] = 31 * (31 * (31 * h[0] + line.getClass().hashCode()) + line.getWidth(font)) + line.getHeight(font);
            }
            h[0] = 31 * h[0] + '\n';
        }
        return h[0];
    }

    /**
     * Library-default maximum hover-tooltip width, in GUI pixels. Sized to
     * about seven average words: ~7 words averaging ~5 chars + a space ≈ 40
     * chars, and Minecraft's default font averages ~6px per glyph (incl. the
     * 1px gap), so ≈ 200px. A tooltip wider than this wraps onto multiple lines
     * rather than drawing as one long horizontal line. Tunable, the sane band
     * is roughly 180 to 220; widen if seven words feels too tight in-game.
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
     * Queues a hover tooltip with an explicit max width, the per-tooltip escape
     * hatch from the default. Pass {@code 0} (or any non-positive value) to
     * disable wrapping entirely and restore the old single-line behavior.
     *
     * <p>Wrapping uses {@code Font.split} (the same splitter as in-panel text),
     * which breaks on spaces and only force-breaks within a single
     * unbreakable word as a last resort, so a single ~40-char word can still
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
        // budget, hand the single Component straight through (no split cost).
        if (maxWidthPx <= 0 || font.width(text) <= maxWidthPx) {
            graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
            return;
        }
        // Over budget, wrap to the budget and hand the pre-split lines to the
        // List overload. Same vanilla call chain (and the same private
        // setTooltipForNextFrameInternal that MKTooltipSuppressMixin guards),
        // so modal/opaque-panel tooltip suppression keeps working unchanged.
        List<FormattedCharSequence> lines = font.split(text, maxWidthPx);
        graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
