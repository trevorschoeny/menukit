package com.trevlar.menukit.core;

import com.trevlar.menukit.api.hud.HudCycleStrip.Axis;
import com.trevlar.menukit.api.hud.HudCycleStrip.Direction;
import com.trevlar.menukit.api.hud.HudCycleStrip.Source;
import com.trevlar.menukit.api.hud.HudCycleStrip.View;
import com.trevlar.menukit.api.hud.HudPanel;
import com.trevlar.menukit.api.panel.InsideRegion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What stands behind {@link com.trevlar.menukit.api.hud.HudCycleStrip}: the registered
 * sources, each source's slide clock, the pure layout and easing math, and the drawing.
 * Lifted from Inventory Plus's cycle HUD (its {@code CycleHud}, {@code CycleSlide} and
 * {@code CycleHudRegistry}) with the look and timing unchanged, so a ring drawn here
 * looks exactly as Inventory Plus drew it.
 *
 * <h3>Three layouts</h3>
 *
 * <ul>
 *   <li><b>A row alone:</b> a mini hotbar (vanilla's {@code hud/hotbar} sprite) right of
 *       the real hotbar, at the same height.</li>
 *   <li><b>A column alone:</b> a one-cell bar at that same spot holding the held stack,
 *       with the column rising above it. A column always reads as a column.</li>
 *   <li><b>The cross</b>, a row and a column on the same slot: the row is that mini
 *       hotbar, and the column floats straight up from the row's held cell. The held
 *       cell is shared, so exactly one arm draws the held stack: whichever one is
 *       sliding (it draws last, on top). A column turn slides it vertically, a row turn
 *       horizontally, and the held stack is never drawn twice.</li>
 * </ul>
 *
 * Slot frames stay fixed; only stacks slide. The pure parts (ring order, easing, slide
 * offsets, cell positions) are public statics so validator-mk's elementCheck can assert
 * them with no game window.
 *
 * <p>Single-threaded: sources register from client init and everything else runs on
 * the client thread (key handling and the HUD draw).
 */
@ApiStatus.Internal
public final class CycleStrip {

    private CycleStrip() {}

    // Layout constants, in GUI-scaled pixels. These are vanilla's hotbar measurements.

    /** One hotbar cell. Also the distance a slide travels. */
    public static final int SLOT_PX = 20;
    /** The hotbar sprite's border, left and right of the cells. */
    public static final int OUTER_BORDER_PX = 1;
    /** The hotbar sprite's height. */
    public static final int STRIP_HEIGHT_PX = 22;
    /** The gap between the vanilla hotbar's right edge and the strip. */
    public static final int STRIP_GAP_FROM_HOTBAR_PX = 4;
    /** Half the vanilla hotbar's width (182 / 2). */
    public static final int HOTBAR_HALF_WIDTH_PX = 91;
    private static final int ITEM_OFFSET_PX = 3;
    /** Item x inset is 1 px less than y; otherwise the icons sit a pixel right of centre. */
    private static final int ITEM_OFFSET_X_PX = ITEM_OFFSET_PX - 1;
    /** The floating part of a column clears the hotbar-height cell by this many px. */
    public static final int COLUMN_RISE_PX = 1;
    /** The floating part of a column sits this many px right of the held cell. */
    public static final int COLUMN_NUDGE_X_PX = 1;

    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final int HOTBAR_SPRITE_H = 22;
    private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final int SELECTION_SPRITE_W = 24;
    private static final int SELECTION_SPRITE_H = 23;

    /** One slide: 220 ms on an ease-out-back spring, Inventory Plus's timing. */
    public static final long SLIDE_MILLIS = 220L;

    // Registry

    private static final List<Source> SOURCES = new ArrayList<>();

    /**
     * Adds a source (the caller checked the freeze). The first one also declares the
     * strip's HUD panel, so a game with no source has no strip panel at all and nothing
     * changes for mods that never use it.
     */
    public static void register(Source source) {
        if (source == null || SOURCES.contains(source)) return;
        if (SOURCES.isEmpty()) declarePanel();
        SOURCES.add(source);
    }

    private static void declarePanel() {
        HudPanel.builder("menukit:cycle-strip")
                .region(InsideRegion.BOTTOM_CENTER)
                .visibleWhen(CycleStrip::shouldShow)
                // A zero-size element that draws the strip. render places it from the
                // window size, not from the panel, because the strip sits beside the
                // vanilla hotbar rather than in a region's stack.
                .custom(0, 0, 0, 0, ctx -> render(ctx.graphics()))
                .build();
    }

    /** A ring active on a hotbar slot: the source it came from (for its slide) and its view. */
    public record Active(Source source, View view) {}

    /**
     * Every source's ring on {@code hotbarSlot}, in registration order. A source that
     * returns null, or a ring of fewer than two stacks, is left out.
     */
    public static List<Active> active(int hotbarSlot) {
        List<Active> out = new ArrayList<>(SOURCES.size());
        for (Source s : SOURCES) {
            View view = s.viewFor(hotbarSlot);
            if (view != null && view.ring().size() >= 2) out.add(new Active(s, view));
        }
        return out;
    }

    // Slide clocks, one per source. Each ring slides on its own: in the cross, turning
    // the row must slide only the row, not the column. A clock keyed by hotbar slot
    // would slide every ring on that slot at once.

    /**
     * One slide in flight for a source. {@code heldOnly}: only the held cell changed,
     * from {@code heldBefore}, because something else moved the held stack; that cell
     * slides up or down, as the vanilla hotbar would, and the rest of the strip stays.
     */
    public record Slide(int hotbarSlot, long startMillis, Direction direction,
                        boolean heldOnly, ItemStack heldBefore) {}

    private static final Map<Source, Slide> SLIDES = new HashMap<>();

    public static void slide(Source source, int hotbarSlot, Direction direction) {
        if (source == null || direction == null) return;
        SLIDES.put(source, new Slide(hotbarSlot, System.currentTimeMillis(), direction, false, ItemStack.EMPTY));
    }

    public static Map<Source, @Nullable View> snapshot(int hotbarSlot) {
        Map<Source, @Nullable View> out = new HashMap<>();
        for (Source s : SOURCES) out.put(s, s.viewFor(hotbarSlot));
        return out;
    }

    public static void slideChanged(int hotbarSlot, Direction direction, Map<Source, @Nullable View> before) {
        if (direction == null || before == null) return;
        for (Source s : SOURCES) {
            View was = before.get(s);
            View now = s.viewFor(hotbarSlot);
            if (changed(was, now)) {
                SLIDES.put(s, new Slide(hotbarSlot, System.currentTimeMillis(), direction,
                        true, was == null || was.ring().isEmpty() ? ItemStack.EMPTY : was.ring().get(was.current())));
            }
        }
    }

    /** The slide running for {@code source} on {@code activeSlot} right now, or null. */
    private static @Nullable Slide running(Source source, int activeSlot) {
        Slide slide = SLIDES.get(source);
        if (slide == null || slide.hotbarSlot() != activeSlot) return null;
        return done(slide.startMillis(), System.currentTimeMillis()) ? null : slide;
    }

    // Pure: ring order. Generic over the element type so the order can be checked
    // with plain values; the strip passes a view's stacks.

    /**
     * A row, left to right: previous, held, next, the one after, ... For two: held,
     * other. Index {@link #rowHeld} is the held one. {@code current} is already wrapped
     * into the ring (a {@code View} does that).
     */
    public static <T> List<T> rowOrder(List<T> ring, int current) {
        int n = ring.size();
        List<T> out = new ArrayList<>(n);
        if (n == 0) return out;
        if (n == 2) {
            out.add(ring.get(current));
            out.add(ring.get((current + 1) % 2));
            return out;
        }
        // Previous first, then from the held one onward until the one before previous.
        out.add(ring.get(Math.floorMod(current - 1, n)));
        for (int k = 0; k < n - 1; k++) out.add(ring.get((current + k) % n));
        return out;
    }

    /** Index in {@link #rowOrder} of the held one, for a ring of {@code size}. */
    public static int rowHeld(int size) {
        return size == 2 ? 0 : 1;
    }

    /**
     * A column, top to bottom, held last: the one furthest from the hand at the top, so
     * a forward turn moves every one down a cell toward the hotbar.
     */
    public static <T> List<T> columnOrder(List<T> ring, int current) {
        int n = ring.size();
        List<T> out = new ArrayList<>(n);
        for (int k = 0; k < n; k++) out.add(ring.get((current + n - 1 - k) % n));
        return out;
    }

    /** Whether two views show different contents. ItemStack has no content equals. */
    public static boolean changed(@Nullable View was, @Nullable View now) {
        if (was == null || now == null) return was != now;
        List<ItemStack> a = columnOrder(was.ring(), was.current()), b = columnOrder(now.ring(), now.current());
        if (a.size() != b.size()) return true;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return true;
        }
        return false;
    }

    // Pure: timing

    /** Ease-out-back: overshoots about 10% then settles to exactly 1. */
    public static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        float tm1 = t - 1f;
        return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1;
    }

    /** Eased progress at {@code nowMillis} of a slide that started at {@code startMillis}. */
    public static float progress(long startMillis, long nowMillis) {
        return easeOutBack((float) (nowMillis - startMillis) / SLIDE_MILLIS);
    }

    public static boolean done(long startMillis, long nowMillis) {
        return nowMillis - startMillis >= SLIDE_MILLIS;
    }

    /**
     * How far right of its rest cell a row's stacks draw at eased progress {@code p}.
     * Forward, they start one cell right and move left; backward, the mirror.
     */
    public static int rowSlideOffset(Direction direction, float p) {
        int px = Math.round((1f - p) * SLOT_PX);
        return direction == Direction.FORWARD ? px : -px;
    }

    // Pure: placement

    /** The strip's left edge: just right of the vanilla hotbar. */
    public static int stripX(int screenW) {
        return screenW / 2 + HOTBAR_HALF_WIDTH_PX + STRIP_GAP_FROM_HOTBAR_PX;
    }

    /** The strip's top: the vanilla hotbar's own height on the window. */
    public static int stripY(int screenH) {
        return screenH - STRIP_HEIGHT_PX;
    }

    /**
     * The held cell's left edge, for a row of {@code rowSize} stacks, or 0 for no row (a
     * column alone sits in the strip's first cell). The column rises from this cell.
     */
    public static int heldCellX(int stripX, int rowSize) {
        return stripX + OUTER_BORDER_PX + (rowSize == 0 ? 0 : rowHeld(rowSize)) * SLOT_PX;
    }

    /**
     * Top of a column's cell {@code j} (0 is the top, {@code n - 1} the held cell on the
     * hotbar row at {@code heldTop}). Every cell above the held one is lifted
     * {@link #COLUMN_RISE_PX} so the floating stack clears the bar.
     */
    public static int columnCellY(int heldTop, int j, int n) {
        return heldTop - (n - 1 - j) * SLOT_PX - (j < n - 1 ? COLUMN_RISE_PX : 0);
    }

    /**
     * Where column stack {@code i} slides from. Forward (down) pulls each stack out of
     * the cell above, the top one wrapping in from off the top. Backward (up) pulls each
     * out of the cell below, the held one wrapping in from below the hand. With no slide,
     * its rest cell.
     */
    public static int columnFromY(int heldTop, int i, int n, @Nullable Direction direction) {
        if (direction == null) return columnCellY(heldTop, i, n);
        if (direction == Direction.FORWARD) {
            return (i == 0) ? columnCellY(heldTop, 0, n) - SLOT_PX : columnCellY(heldTop, i - 1, n);
        }
        return (i == n - 1) ? heldTop + SLOT_PX : columnCellY(heldTop, i + 1, n);
    }

    /**
     * Left edge of the selection frame ({@code hud/hotbar_selection}, 24 x 23) around the
     * cell whose left edge is {@code cellX}. Vanilla draws it at {@code hotbarLeft - 1 +
     * slot * 20}, and its cells start one pixel inside the bar ({@code hotbarLeft + 1 +
     * slot * 20}), so the frame starts two pixels left of the cell: it covers the bar's
     * own outline and hangs one pixel past it, and its 16 px hole sits exactly over the
     * cell's dark inside. One pixel less (the old {@code cellX - 1}) put the hole a pixel
     * right of the cell and the item a pixel left in it, and the frame no longer covered
     * the strip's outer edge, so it read as clipped there.
     *
     * <p>Only X takes the two: a cell's {@code y} is the bar's top (its outline row, not
     * the cell's first row), so the frame's top is {@code y - 1}, as vanilla's
     * {@code guiHeight - 23} is. Items take the same asymmetry ({@code ITEM_OFFSET_X_PX}).
     */
    public static int selectionX(int cellX) {
        return cellX - OUTER_BORDER_PX - 1;
    }

    /**
     * Left edge of the frame drawn behind a floating column cell whose left edge is
     * {@code cellX}: one pixel left, so the frame is a whole 22 x 22 hotbar cell with
     * its black outline on all four sides (the bar's left cap, one cell, the right cap).
     * A bare 20 x 20 cell slice has no outline on its left, right or bottom, so the
     * column's outer square looked cut off a pixel at each side.
     */
    public static int cellFrameX(int cellX) {
        return cellX - OUTER_BORDER_PX;
    }

    /** Width (and height) of one framed cell: the cell plus the outline each side. */
    public static final int CELL_FRAME_PX = SLOT_PX + 2 * OUTER_BORDER_PX;

    // Drawing

    private static boolean shouldShow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected < 0 || selected > 8) return false;
        return !active(selected).isEmpty();
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        int activeSlot = mc.player.getInventory().getSelectedSlot();
        if (activeSlot < 0 || activeSlot > 8) return;

        // One ring per axis: the first registered source of each wins.
        Active row = null, column = null;
        for (Active a : active(activeSlot)) {
            if (a.source().axis() == Axis.COLUMN) {
                if (column == null) column = a;
            } else if (row == null) {
                row = a;
            }
        }
        if (row == null && column == null) return;

        int stripX = stripX(mc.getWindow().getGuiScaledWidth());
        int stripY = stripY(mc.getWindow().getGuiScaledHeight());

        // A row alone is the plain mini hotbar.
        if (column == null) {
            renderRow(graphics, mc, row, activeSlot, stripX, stripY, true);
            return;
        }

        // From here the column is present, alone or in the cross with a row.
        int heldCellX = heldCellX(stripX, row == null ? 0 : row.view().ring().size());

        // The arm that is sliding draws last (on top) and owns the shared held cell's
        // stack, so only one copy of the held stack is ever drawn. The other arm skips
        // it; otherwise it would draw a still second copy for the moving one to slide
        // over, a brief duplicate near the end. With no row, nobody else can own the
        // held cell, so the column does.
        boolean rowSliding = row != null && running(row.source(), activeSlot) != null;
        boolean columnSliding = running(column.source(), activeSlot) != null;
        boolean columnOwnsHeld = row == null || (columnSliding && !rowSliding);

        // A column alone still needs a frame under its held cell. In the cross the
        // row's bar is that frame; alone, a one-cell bar is.
        if (row == null) {
            renderBar(graphics, stripX, stripY, CELL_FRAME_PX);
        }

        if (!columnOwnsHeld) renderColumn(graphics, mc, column, activeSlot, heldCellX, stripY, false);
        if (row != null) renderRow(graphics, mc, row, activeSlot, stripX, stripY, !columnOwnsHeld);
        if (columnOwnsHeld) renderColumn(graphics, mc, column, activeSlot, heldCellX, stripY, true);
        renderHighlight(graphics, heldCellX, stripY);
    }

    /**
     * The held cell's vertical slide, as the vanilla hotbar's would be: forward drops the
     * new stack in from a cell above while the old one leaves below; backward is the
     * mirror. Clipped to the cell.
     */
    private static void renderHeldVertical(GuiGraphicsExtractor graphics, Minecraft mc,
                                           Slide slide, ItemStack now, int cellX, int stripY) {
        float p = progress(slide.startMillis(), System.currentTimeMillis());
        int travelled = Math.round(SLOT_PX * p);
        int remaining = Math.round(SLOT_PX * (1f - p));
        boolean down = slide.direction() == Direction.FORWARD;
        graphics.enableScissor(cellX, stripY, cellX + SLOT_PX, stripY + STRIP_HEIGHT_PX);
        try {
            renderItem(graphics, mc, slide.heldBefore(), cellX, down ? stripY + travelled : stripY - travelled);
            renderItem(graphics, mc, now, cellX, down ? stripY - remaining : stripY + remaining);
        } finally {
            graphics.disableScissor();
        }
    }

    // The row: alone, and the cross's horizontal arm.
    private static void renderRow(GuiGraphicsExtractor graphics, Minecraft mc, Active cycle, int activeSlot,
                                  int stripX, int stripY, boolean includeHeld) {
        List<ItemStack> items = rowOrder(cycle.view().ring(), cycle.view().current());
        int n = items.size();
        int stripWidth = n * SLOT_PX + 2 * OUTER_BORDER_PX;
        int hi = rowHeld(n);
        Slide slide = running(cycle.source(), activeSlot);
        // A held-only change moved only the held stack, up or down: the strip stays
        // still and that one cell slides vertically.
        boolean heldOnly = slide != null && slide.heldOnly();
        int offset = (slide == null || heldOnly) ? 0
                : rowSlideOffset(slide.direction(), progress(slide.startMillis(), System.currentTimeMillis()));

        renderBar(graphics, stripX, stripY, stripWidth);

        int cellsLeft = stripX + OUTER_BORDER_PX;
        graphics.enableScissor(cellsLeft, stripY, stripX + stripWidth - OUTER_BORDER_PX, stripY + STRIP_HEIGHT_PX);
        try {
            for (int k = 0; k < n; k++) {
                // The column owns the held cell's stack while it is the one sliding.
                // Skip it here so the two never both draw it.
                if (k == hi && (!includeHeld || heldOnly)) continue;
                renderItem(graphics, mc, items.get(k), cellsLeft + k * SLOT_PX + offset, stripY);
            }
            if (heldOnly && includeHeld) {
                renderHeldVertical(graphics, mc, slide, items.get(hi), cellsLeft + hi * SLOT_PX, stripY);
            } else if (slide != null) {
                // The stack that wrapped around enters from the far end: forward, the
                // last one slides in from off the left; backward, the first from the right.
                boolean fwd = slide.direction() == Direction.FORWARD;
                int wrapIndex = fwd ? n - 1 : 0;
                int wrapBase = fwd ? -SLOT_PX : n * SLOT_PX;
                renderItem(graphics, mc, items.get(wrapIndex), cellsLeft + wrapBase + offset, stripY);
            }
        } finally {
            graphics.disableScissor();
        }

        renderHighlight(graphics, cellsLeft + hi * SLOT_PX, stripY);
    }

    /**
     * The column rising from the held cell at {@code heldTop}. Still frames for the cells
     * above the held one; the held cell's frame is the caller's (the row's bar in the
     * cross, a one-cell bar alone).
     *
     * <p>The slide is per cell: each stack moves from the cell it held one step ago to its
     * cell now, and the wrapped stack leaves through the opposite end. Every position is a
     * real cell, so the lift needs no special case: the gap between the held cell and the
     * first floating cell is wider than the others, and a slide spans whichever gap it
     * crosses. The held cell always rests exactly at {@code heldTop}, so handing it back to
     * the row when a slide ends never snaps.
     *
     * @param includeHeld draw the held stack and its exit; false when the row owns the
     *                    held cell, so the held stack is not drawn twice
     */
    private static void renderColumn(GuiGraphicsExtractor graphics, Minecraft mc, Active cycle, int activeSlot,
                                     int cellX, int heldTop, boolean includeHeld) {
        List<ItemStack> visual = columnOrder(cycle.view().ring(), cycle.view().current());
        int n = visual.size();
        Slide slide = running(cycle.source(), activeSlot);
        Direction direction = slide == null ? null : slide.direction();
        float p = slide == null ? 1f : progress(slide.startMillis(), System.currentTimeMillis());
        int topY = columnCellY(heldTop, 0, n);
        // The floating cells sit a hair right of the held cell; the held stack itself
        // stays on the bar, so handing it back never snaps.
        int floatX = cellX + COLUMN_NUDGE_X_PX;

        // Top to bottom, so each cell's outline row covers the bottom edge of the one
        // above it: the floating cells read as one framed column. The lowest one's
        // bottom outline lands on the bar's top row, under the selection frame.
        for (int j = 0; j <= n - 2; j++) {
            renderBar(graphics, cellFrameX(floatX), columnCellY(heldTop, j, n), CELL_FRAME_PX);
        }

        // Clip to the column plus one cell below the hand, so a forward exit can slide
        // out of the bottom.
        graphics.enableScissor(cellX, topY, floatX + SLOT_PX, heldTop + SLOT_PX);
        try {
            for (int i = 0; i < n; i++) {
                if (i == n - 1 && !includeHeld) continue; // the row owns the held stack
                int toY = columnCellY(heldTop, i, n);
                int fromY = columnFromY(heldTop, i, n, direction);
                int x = (i == n - 1) ? cellX : floatX;
                renderItem(graphics, mc, visual.get(i), x, Math.round(fromY + (toY - fromY) * p));
            }
            // The wrapped stack leaves through the shared held end, so only when this arm
            // owns that cell. Forward: the old held stack (now at the top) slides out of
            // the bottom. Backward: the stack that wrapped into the hand slides out of the top.
            if (direction != null && includeHeld) {
                boolean fwd = direction == Direction.FORWARD;
                int wrapIndex = fwd ? 0 : n - 1;
                int fromY = fwd ? heldTop : columnCellY(heldTop, 0, n);
                int toY = fwd ? heldTop + SLOT_PX : columnCellY(heldTop, 0, n) - SLOT_PX;
                int x = fwd ? cellX : floatX;
                renderItem(graphics, mc, visual.get(wrapIndex), x, Math.round(fromY + (toY - fromY) * p));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    // Vanilla-texture drawing

    /**
     * A horizontal hotbar bar {@code width} px wide: left cap, tiled cells, right cap,
     * the sprite's full 22 px height, so its outline shows on every side. One cell wide
     * ({@link #CELL_FRAME_PX}) it is the frame of a single cell: a column's floating cells
     * and a column alone's held cell.
     */
    private static void renderBar(GuiGraphicsExtractor graphics, int x, int y, int width) {
        int cells = (width - 2 * OUTER_BORDER_PX) / SLOT_PX;
        int cellsLeft = x + OUTER_BORDER_PX;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, 0, 0,
                x, y, OUTER_BORDER_PX, STRIP_HEIGHT_PX);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, OUTER_BORDER_PX, 0,
                cellsLeft, y, cells * SLOT_PX, STRIP_HEIGHT_PX);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, HOTBAR_SPRITE_W - OUTER_BORDER_PX, 0,
                cellsLeft + cells * SLOT_PX, y, OUTER_BORDER_PX, STRIP_HEIGHT_PX);
    }

    private static void renderItem(GuiGraphicsExtractor graphics, Minecraft mc, ItemStack stack, int cellX, int cellY) {
        if (stack.isEmpty()) return;
        int itemX = cellX + ITEM_OFFSET_X_PX;
        int itemY = cellY + ITEM_OFFSET_PX;
        graphics.item(stack, itemX, itemY);
        graphics.itemDecorations(mc.font, stack, itemX, itemY);
    }

    /** The selection frame around the held cell, placed as vanilla places it (see {@link #selectionX}). */
    private static void renderHighlight(GuiGraphicsExtractor graphics, int cellX, int cellY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION_SPRITE,
                SELECTION_SPRITE_W, SELECTION_SPRITE_H, 0, 0,
                selectionX(cellX), cellY - 1, SELECTION_SPRITE_W, SELECTION_SPRITE_H);
    }
}
