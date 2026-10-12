package com.trevlar.menukit.api.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.MenuTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.mixin.ClientTextTooltipAccessor;

import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The one place a hover tooltip is queued: every element tooltip and the panel
 * tooltip go through {@link #queue} instead of calling vanilla's
 * {@code setTooltipForNextFrame} directly.
 *
 * <h3>Why it exists</h3>
 *
 * Vanilla's hover tooltips have no maximum width, so a long one draws as a single
 * line across the screen. The cap belongs in one default that every tooltip
 * inherits, not in each element: how wide a tooltip may be is the same for every
 * element, while when to show one varies (a button hides it while pressed, a
 * dropdown chooses between the trigger's and a row's). Every tooltip site already
 * made the same vanilla call, {@code graphics.setTooltipForNextFrame(font,
 * component, mx, my)}, so this class is a thin wrapper over that call.
 *
 * <h3>Why a tooltip is capped when panels are not</h3>
 *
 * Laid-out panel content grows to fit what it holds. A tooltip follows the mouse
 * with no layout around it to bound it, so growing to fit is exactly what draws
 * the line across the screen. The cap does for tooltips what the screen edge
 * does for panels. It wraps with {@code Font.split}, the splitter
 * {@code TextLabel} uses, so both wrap the same way.
 *
 * <p>This class also holds the switches a mod can turn on for every tooltip in the
 * game: {@link #hideWhen}, {@link #wrapWhen}, {@link #clampWhen},
 * {@link #avoidCoverWhen} (docking) and {@link #onWheel}.
 */
public final class MKTooltip {

    private MKTooltip() {}

    // Hiding every tooltip. MenuKit owns the one hook every tooltip in the game is
    // queued through (MKTooltipSuppressMixin, on vanilla's
    // setTooltipForNextFrameInternal), and a vanilla method gets only one such hook,
    // so a mod that wants tooltips gone registers a predicate here instead of adding
    // a second mixin. MenuKit supplies the mechanism; the mod supplies the policy
    // (Inventory Plus: "a setting is on and Ctrl is held").

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

    // Keeping a tooltip on screen. Vanilla 26.2's positioner only moves a tooltip's
    // corner: a line wider than the screen runs off the right, a tooltip taller than
    // the screen runs off the top. Two switches, same split as hideWhen (MenuKit
    // places, the mod decides when; Inventory Plus: "keep tooltips on screen"), both
    // off unless a mod registers.

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
     * nothing is too wide. A text line is one MenuKit can read the text of: every
     * {@code ClientTextTooltip} in game, through its accessor.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static List<ClientTooltipComponent> wrapped(Font font, List<ClientTooltipComponent> lines, int screenWidth) {
        int max = wrapWidth(screenWidth);
        List<ClientTooltipComponent> out = null;  // made on the first line that needs it
        for (int i = 0; i < lines.size(); i++) {
            ClientTooltipComponent line = lines.get(i);
            ClientTooltipComponent kept = line;
            if (line instanceof ClientTextTooltipAccessor text && line.getWidth(font) > max) {
                List<ClientTooltipComponent> pieces = new ArrayList<>();
                for (FormattedCharSequence piece : font.split(asText(text.mk$text()), max)) {
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
     * piece. Font.split reorders right-to-left text for display, and the line already
     * was, so an Arabic or Hebrew line wider than the screen may wrap reversed. Keep the
     * source Component per line if that ever shows.
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

    // Docking a tooltip. Vanilla puts a tooltip beside the cursor, so a wide one near an
    // inventory lands over the slots the player is reading. Docked, it sits at the bottom
    // centre of the screen instead, or the top centre when the cursor is where the bottom
    // one would be. Same split as hideWhen: MenuKit places, the mod decides when
    // (Inventory Plus: "keep long tooltips out of the way").

    /** Registered "dock while" predicates. Written only during init, read every frame. */
    private static final Set<BooleanSupplier> DOCK_WHEN = new LinkedHashSet<>();

    /**
     * Docks every tooltip vanilla places beside the cursor on every frame {@code condition}
     * is true: it sits centred at the bottom of the screen, {@link #EDGE} above the edge,
     * or centred at the top when the cursor is within {@link #DOCK_MARGIN} of where the
     * bottom one would sit, not only directly over it.
     * Nothing wraps or changes side. {@link #clampWhen}, {@link #wrapWhen},
     * {@link #scrollBy} and {@link #hideWhen} work on a docked tooltip as on any other,
     * the title still first; one taller than the screen behaves as it does under
     * {@code clampWhen}. Tooltips vanilla places somewhere fixed (one anchored to a widget
     * for the keyboard) stay there. Several mods may register; any one returning true
     * docks. Call from client init.
     *
     * <p>Before 6.2.0 this put a tooltip on the side of the cursor that covered less of
     * the screen; the intent (keep tooltips off what the player is looking at) is the same.
     *
     * @throws IllegalStateException after MenuKit's declarations froze
     */
    public static void avoidCoverWhen(BooleanSupplier condition) {
        Declarations.requireOpen("MKTooltip.avoidCoverWhen");
        DOCK_WHEN.add(condition);
    }

    /**
     * True when a tooltip placed by {@code positioner} docks this frame: a registered
     * {@link #avoidCoverWhen} predicate holds and vanilla places it beside the cursor (its
     * default and menu positioners), not somewhere fixed.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static boolean docks(ClientTooltipPositioner positioner) {
        return (positioner instanceof DefaultTooltipPositioner || positioner instanceof MenuTooltipPositioner)
                && anyHolds(DOCK_WHEN);
    }

    /**
     * How far outside the bottom-docked tooltip's background the cursor still flips it to the
     * top, in GUI pixels: a bit more than a slot. With no margin the cursor could sit just
     * past the tooltip's edge, close enough to still read it, and the tooltip would not move
     * out of the way.
     */
    public static final int DOCK_MARGIN = 20;

    /**
     * Where a docked tooltip's text starts, given the text's {@code width} and {@code height}
     * as vanilla measures them (a positioner's arguments). Centred, at the bottom with
     * {@link #EDGE} under it; at the top, {@link #EDGE} down, when the cursor is within
     * {@link #DOCK_MARGIN} of the bottom one's background (the text plus its padding, grown by
     * the margin on every side). Pure: no screen is read.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static Vector2ic docked(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height) {
        int x = (screenWidth - width) / 2;
        int bottom = screenHeight - EDGE - height;
        boolean underCursor = mouseX >= x - TooltipRenderUtil.PADDING_LEFT - DOCK_MARGIN
                && mouseX < x + width + TooltipRenderUtil.PADDING_RIGHT + DOCK_MARGIN
                && mouseY >= bottom - TooltipRenderUtil.PADDING_TOP - DOCK_MARGIN
                && mouseY < bottom + height + TooltipRenderUtil.PADDING_BOTTOM + DOCK_MARGIN;
        return new Vector2i(x, underCursor ? EDGE : bottom);
    }

    // Scrolling a tooltip. Same split as hideWhen: MenuKit owns where a tooltip lands
    // (MKTooltipSuppressMixin wraps vanilla's GuiGraphicsExtractor.tooltip, the one
    // method that positions and draws every tooltip), the mod owns when and how far
    // to move it (Inventory Plus: "scroll long tooltips", the wheel scrolls a tooltip
    // taller than the screen).
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
     * A 32-bit hash: a collision between two consecutive tooltips would keep the old
     * scroll for one hover. Compare the strings if that ever shows.
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
     * The default maximum tooltip width, in GUI pixels: about seven average words.
     * Seven words of about 5 characters plus a space is about 40 characters, and
     * Minecraft's default font averages about 6 px per glyph (the 1 px gap
     * included), so about 200 px. A wider tooltip wraps onto more lines. Values from
     * about 180 to 220 work; widen it if seven words feels tight in game.
     */
    public static final int DEFAULT_MAX_WIDTH = 200;

    /**
     * Queues a hover tooltip for end-of-frame draw at the mouse position,
     * wrapping at the library-default max width ({@link #DEFAULT_MAX_WIDTH}).
     * This is the form every element and panel tooltip calls.
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
     * Queues a hover tooltip with its own maximum width instead of the default.
     * Pass {@code 0} (or any non-positive value) for no wrapping: one line, as
     * vanilla draws it.
     *
     * <p>Wrapping uses {@code Font.split}, the splitter in-panel text uses. It breaks
     * at spaces and splits a word only as a last resort, so one very long word can
     * still run past the budget, as in vanilla.
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
        // No wrapping, or the line already fits: hand the Component straight
        // through without splitting it.
        if (maxWidthPx <= 0 || font.width(text) <= maxWidthPx) {
            graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
            return;
        }
        // Too wide: split to the budget and use the List overload. It goes through
        // the same private setTooltipForNextFrameInternal that MKTooltipSuppressMixin
        // guards, so tooltip suppression under a modal or opaque panel still works.
        List<FormattedCharSequence> lines = font.split(text, maxWidthPx);
        graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
