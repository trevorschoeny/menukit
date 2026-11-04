package com.trevlar.menukit.core;

import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.ItemAnimation.Pose;

import com.trevlar.menukit.api.hud.HudCycleStrip;
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
import net.minecraft.util.Util;
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
 * {@code CycleHudRegistry}) with the look unchanged, so a ring drawn here looks as Inventory
 * Plus drew it; the slide's timing is MenuKit's own spring since 7.6.0.
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
 * <p>An {@link Axis#ARC} ring is drawn apart from those three (7.6.0): small icons in an
 * arc just above the hotbar slot itself, on every hotbar slot that has one, not only the
 * held one. During its turn the arc owns the vanilla slot's stack (the hotbar draws that
 * slot empty, see {@link #ownsHeld}) so the stacks can move between the slot and the arc.
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
    /** Item x inset is 1 px less than y; otherwise the icons sit a pixel right of center. */
    private static final int ITEM_OFFSET_X_PX = ITEM_OFFSET_PX - 1;
    /** The floating part of a column clears the hotbar-height cell by this many px. */
    public static final int COLUMN_RISE_PX = 1;

    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final int HOTBAR_SPRITE_H = 22;
    private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final int SELECTION_SPRITE_W = 24;
    private static final int SELECTION_SPRITE_H = 23;

    /** One slide: 150 ms on {@link Easing#SPRING} (7.6.0; 220 ms on an ease-out-back before). */
    public static final long SLIDE_MILLIS = 150L;

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
        HudPanel.builder("menukit:cycle-strip", com.trevlar.menukit.api.panel.ModeScope.BOTH)
                .region(InsideRegion.BOTTOM_CENTER)
                .visibleWhen(CycleStrip::shouldShow)
                // Drawn from the window size, not from where the panel sits: nothing to move.
                .fixed()
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
        SLIDES.put(source, new Slide(hotbarSlot, Util.getMillis(), direction, false, ItemStack.EMPTY));
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
                SLIDES.put(s, new Slide(hotbarSlot, Util.getMillis(), direction,
                        true, was == null || was.ring().isEmpty() ? ItemStack.EMPTY : was.ring().get(was.current())));
            }
        }
    }

    /** The slide running for {@code source} on {@code activeSlot} right now, or null. */
    private static @Nullable Slide running(Source source, int activeSlot) {
        Slide slide = SLIDES.get(source);
        if (slide == null || slide.hotbarSlot() != activeSlot) return null;
        return done(slide.startMillis(), Util.getMillis()) ? null : slide;
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

    /**
     * Eased progress at {@code nowMillis} of a slide that started at {@code startMillis}: the damped
     * spring ({@link Easing#SPRING}), about 9% over and 3% back before settling, exactly 0 and 1 at its ends.
     */
    public static float progress(long startMillis, long nowMillis) {
        float t = (float) (nowMillis - startMillis) / SLIDE_MILLIS;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return Easing.SPRING.apply(t);
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

    /**
     * An item's x in the cell whose left edge is {@code cellX}: two pixels in, as
     * vanilla's hotbar draws its items ({@code guiWidth / 2 - 90 + slot * 20 + 2}, its
     * cells starting at {@code guiWidth / 2 - 90}). Every cell of both axes uses it.
     */
    public static int itemX(int cellX) {
        return cellX + ITEM_OFFSET_X_PX;
    }

    /** Width (and height) of one framed cell: the cell plus the outline each side. */
    public static final int CELL_FRAME_PX = SLOT_PX + 2 * OUTER_BORDER_PX;

    // Drawing

    private static boolean shouldShow() {
        Minecraft mc = Minecraft.getInstance();
        // A spectator's hotbar is the spectator menu: no strip and no arcs beside it.
        if (mc == null || mc.player == null || mc.player.isSpectator()) return false;
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            if (arcOn(slot) != null) return true;
        }
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected < 0 || selected > 8) return false;
        return !active(selected).isEmpty();
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        int activeSlot = mc.player.getInventory().getSelectedSlot();
        if (activeSlot < 0 || activeSlot > 8) return;

        renderArcs(graphics, mc);

        // One ring per axis: the first registered source of each wins.
        Active row = null, column = null;
        for (Active a : active(activeSlot)) {
            if (a.source().axis() == Axis.COLUMN) {
                if (column == null) column = a;
            } else if (a.source().axis() == Axis.ROW && row == null) {
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
        float p = progress(slide.startMillis(), Util.getMillis());
        int traveled = Math.round(SLOT_PX * p);
        int remaining = Math.round(SLOT_PX * (1f - p));
        boolean down = slide.direction() == Direction.FORWARD;
        graphics.enableScissor(cellX, stripY, cellX + SLOT_PX, stripY + STRIP_HEIGHT_PX);
        try {
            renderItem(graphics, mc, slide.heldBefore(), cellX, down ? stripY + traveled : stripY - traveled);
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
                : rowSlideOffset(slide.direction(), progress(slide.startMillis(), Util.getMillis()));

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
        float p = slide == null ? 1f : progress(slide.startMillis(), Util.getMillis());
        int topY = columnCellY(heldTop, 0, n);
        // The floating cells share the held cell's x, so their frames stack exactly over
        // the hotbar cell below (frame at cellX - 1, 22 wide, as the bar's own outline is)
        // and their items sit at the same cellX + 2 as vanilla's. A 1 px nudge right here
        // read as the column sitting a pixel off the hotbar.

        // Top to bottom, so each cell's outline row covers the bottom edge of the one
        // above it: the floating cells read as one framed column. The lowest one's
        // bottom outline lands on the bar's top row, under the selection frame.
        for (int j = 0; j <= n - 2; j++) {
            renderBar(graphics, cellFrameX(cellX), columnCellY(heldTop, j, n), CELL_FRAME_PX);
        }

        // Clip to the column plus one cell below the hand, so a forward exit can slide
        // out of the bottom.
        graphics.enableScissor(cellX, topY, cellX + SLOT_PX, heldTop + SLOT_PX);
        try {
            for (int i = 0; i < n; i++) {
                if (i == n - 1 && !includeHeld) continue; // the row owns the held stack
                int toY = columnCellY(heldTop, i, n);
                int fromY = columnFromY(heldTop, i, n, direction);
                renderItem(graphics, mc, visual.get(i), cellX, Math.round(fromY + (toY - fromY) * p));
            }
            // The wrapped stack leaves through the shared held end, so only when this arm
            // owns that cell. Forward: the old held stack (now at the top) slides out of
            // the bottom. Backward: the stack that wrapped into the hand slides out of the top.
            if (direction != null && includeHeld) {
                boolean fwd = direction == Direction.FORWARD;
                int wrapIndex = fwd ? 0 : n - 1;
                int fromY = fwd ? heldTop : columnCellY(heldTop, 0, n);
                int toY = fwd ? heldTop + SLOT_PX : columnCellY(heldTop, 0, n) - SLOT_PX;
                renderItem(graphics, mc, visual.get(wrapIndex), cellX, Math.round(fromY + (toY - fromY) * p));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    // The arc (7.6.0). Every position is in GUI px relative to the center of the held
    // item's 16 px box, as an ItemAnimation.Pose: (0, 0) at scale 1 is the stack in the
    // slot. The hotbar's top edge is at y = -11 and the XP bar's bottom at y = -13, so the
    // middle 8 px icon's top reaches the XP bar and the arc covers the top of the slot (its
    // border and the top of its item, which Trev accepted).

    /** Hotbar slots an arc can stand over. */
    public static final int HOTBAR_SLOTS = 9;
    /** How big an arc's icons draw: the 16 px item at half, 8 px. */
    public static final float ARC_SCALE = 0.5f;
    /** An arc holds up to three members (a ring of four); a bigger ring draws no arc. */
    public static final int ARC_MAX_MEMBERS = 3;

    /** An arc icon's shadow: the darkest it gets, at its middle, as alpha over black. */
    private static final int SHADOW_ALPHA = 0x70;

    /**
     * Where each member of an arc of {@code members} rests, member 0 (the one a forward
     * turn brings to the hand) first. Member 0 is on the left, as depth 0 is leftmost in a
     * row of pockets, so a forward turn moves the arc left and drops its left end into the
     * slot. The 8 px icons sit 8 or 9 px apart, so they touch but never overlap.
     */
    public static List<Pose> arcPoints(int members) {
        return switch (members) {
            case 1 -> List.of(new Pose(ARC_SCALE, 0f, -9.5f));
            case 2 -> List.of(new Pose(ARC_SCALE, -4.5f, -9f), new Pose(ARC_SCALE, 4.5f, -9f));
            case 3 -> List.of(new Pose(ARC_SCALE, -8f, -8f), new Pose(ARC_SCALE, 0f, -9.5f),
                    new Pose(ARC_SCALE, 8f, -8f));
            default -> List.of();
        };
    }

    /** Whether a ring of {@code size} stacks draws as an arc: the held one plus one to three members. */
    public static boolean arcFits(int size) {
        return size >= 2 && size <= ARC_MAX_MEMBERS + 1;
    }

    /**
     * Each stack's pose at eased progress {@code p} of a turn in {@code direction}, or at
     * rest for a null direction, for a ring of {@code size}. Index {@code j} is the stack
     * {@code j} steps after the held one ({@code ring.get(current + j)}): 0 is the held
     * one, {@code j} is member {@code j - 1}. Each moves in a straight line from the pose
     * it held before the turn to its pose now, its scale going linearly between the two.
     * Forward, the stack now at {@code j} was at {@code j + 1}; backward, at {@code j - 1}.
     */
    public static List<Pose> arcPoses(int size, @Nullable Direction direction, float p) {
        List<Pose> rest = new ArrayList<>(size);
        rest.add(Pose.NONE);
        rest.addAll(arcPoints(size - 1));
        if (direction == null) return rest;
        int step = direction == Direction.FORWARD ? 1 : -1;
        List<Pose> out = new ArrayList<>(size);
        for (int j = 0; j < size; j++) {
            Pose from = rest.get(Math.floorMod(j + step, size)), to = rest.get(j);
            out.add(new Pose(lerp(from.scale(), to.scale(), p),
                    lerp(from.offsetX(), to.offsetX(), p), lerp(from.offsetY(), to.offsetY(), p)));
        }
        return out;
    }

    private static float lerp(float a, float b, float p) {
        return a * (1f - p) + b * p; // exact at both ends, so a turn ends exactly at rest
    }

    /** The center x of hotbar slot {@code slot}'s item, as vanilla places it ({@code w / 2 - 90 + slot * 20 + 2}, plus 8). */
    public static int hotbarItemCenterX(int screenW, int slot) {
        return screenW / 2 - 80 + slot * SLOT_PX;
    }

    /** The center y of a hotbar item ({@code h - 19}, plus 8). */
    public static int hotbarItemCenterY(int screenH) {
        return screenH - 11;
    }

    /** The arc on {@code hotbarSlot}: the first registered arc source with a ring that fits, or null. */
    public static @Nullable Active arcOn(int hotbarSlot) {
        for (Source s : SOURCES) {
            if (s.axis() != Axis.ARC) continue;
            View view = s.viewFor(hotbarSlot);
            if (view != null && arcFits(view.ring().size())) return new Active(s, view);
        }
        return null;
    }

    /**
     * The turn running for an arc on {@code hotbarSlot}, or null. A held-only slide
     * ({@link #slideChanged}) is no turn for an arc: the vanilla slot shows the held
     * stack change itself.
     */
    private static @Nullable Slide arcTurn(Source source, int hotbarSlot) {
        Slide slide = running(source, hotbarSlot);
        return slide == null || slide.heldOnly() ? null : slide;
    }

    /** Every stack of {@code view}'s arc on {@code hotbarSlot} this frame, held one first, with its pose. */
    public static List<HudCycleStrip.ArcItem> arcItems(Source source, View view, int hotbarSlot) {
        int n = view.ring().size();
        if (!arcFits(n)) return List.of();
        Slide turn = arcTurn(source, hotbarSlot);
        List<Pose> poses = turn == null ? arcPoses(n, null, 1f)
                : arcPoses(n, turn.direction(), progress(turn.startMillis(), Util.getMillis()));
        List<HudCycleStrip.ArcItem> out = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            out.add(new HudCycleStrip.ArcItem(view.ring().get((view.current() + j) % n), poses.get(j)));
        }
        return out;
    }

    /**
     * Whether the arc on {@code hotbarSlot} draws the slot's stack itself, so the vanilla
     * hotbar draws that slot empty: during a turn, and while the source's view holds a
     * stack other than {@code inSlot} (a turn it predicted that the server has not yet
     * confirmed). Asked by the hotbar for each slot as it draws.
     */
    public static boolean ownsHeld(int hotbarSlot, ItemStack inSlot) {
        Active arc = arcOn(hotbarSlot);
        if (arc == null) return false;
        if (arcTurn(arc.source(), hotbarSlot) != null) return true;
        return !ItemStack.matches(arc.view().ring().get(arc.view().current()), inSlot);
    }

    /** The arcs over every hotbar slot that has one, with the held stack where the arc owns it. */
    private static void renderArcs(GuiGraphicsExtractor graphics, Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            Active arc = arcOn(slot);
            if (arc == null) continue;
            int cx = hotbarItemCenterX(w, slot), cy = hotbarItemCenterY(h);
            renderArc(graphics, arc.source(), arc.view(), slot, cx, cy);
            // At rest but owning the slot: a predicted turn the server hasn't confirmed.
            // The hotbar drew the slot empty, so the predicted held stack goes there, as
            // vanilla would draw it.
            ItemStack held = arc.view().ring().get(arc.view().current());
            if (arcTurn(arc.source(), slot) == null && ownsHeld(slot, mc.player.getInventory().getItem(slot))) {
                graphics.item(held, cx - 8, cy - 8);
                graphics.itemDecorations(mc.font, held, cx - 8, cy - 8);
            }
        }
    }

    /**
     * One arc around the held item centered at {@code (cx, cy)}: the stacks floating on
     * their own, each over its shadow. At rest the held stack is the slot's to draw; during a
     * turn every stack moves, the held ones included, and the one coming into the hand draws
     * last. Moving stacks draw without their count: at half the size it can't be read.
     */
    public static void renderArc(GuiGraphicsExtractor graphics, Source source, View view, int hotbarSlot,
                                 float cx, float cy) {
        List<HudCycleStrip.ArcItem> items = arcItems(source, view, hotbarSlot);
        if (items.isEmpty()) return;
        boolean turning = arcTurn(source, hotbarSlot) != null;
        for (int j = items.size() - 1; j >= 0; j--) {
            if (j == 0 && !turning) continue;
            HudCycleStrip.ArcItem item = items.get(j);
            renderArcItem(graphics, item.stack(), cx + item.pose().offsetX(), cy + item.pose().offsetY(),
                    item.pose().scale());
        }
    }

    /**
     * {@code stack} centered at {@code (cx, cy)} at {@code scale} (1 is the 16 px item), without
     * its count, over its shadow. The shadow is full at {@link #ARC_SCALE} and fades out as the
     * stack grows to its slot size, where vanilla draws none. An empty stack draws nothing.
     */
    public static void renderArcItem(GuiGraphicsExtractor graphics, ItemStack stack, float cx, float cy, float scale) {
        if (stack.isEmpty()) return;
        renderArcShadow(graphics, cx, cy, scale, shadowStrength(scale));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale, scale);
            graphics.item(stack, -8, -8);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** How strong an icon's shadow is at {@code scale}: 1 at {@link #ARC_SCALE} and below, 0 at full size. */
    public static float shadowStrength(float scale) {
        return Math.clamp((1f - scale) / (1f - ARC_SCALE), 0f, 1f);
    }

    /**
     * The soft shadow under an icon centered at {@code (cx, cy)} drawn {@code scale} times
     * 16 px: a dark ellipse 3/4 of the icon wide and 1/4 tall, its middle one pixel below
     * the icon's bottom edge (8 px wide, 2 tall, at an icon's rest size). Drawn in quarter
     * pixels (the pose scaled by 1/4) as rows of an ellipse, darkest in the middle row and
     * fading toward the top and bottom, so it reads soft at any GUI scale.
     */
    private static void renderArcShadow(GuiGraphicsExtractor graphics, float cx, float cy, float scale, float strength) {
        if (strength <= 0f) return;
        float halfW = 6f * scale, halfH = 2f * scale; // in GUI px: 12 x 4 at full size, 6 x 2 at half
        float shadowY = cy + 8f * scale + 1f;
        int rowsHalf = Math.max(1, Math.round(halfH * 4f)); // quarter-pixel rows above and below the middle
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(cx, shadowY);
            graphics.pose().scale(0.25f, 0.25f);
            for (int row = -rowsHalf; row < rowsHalf; row++) {
                float v = (row + 0.5f) / rowsHalf; // -1 at the top edge, 1 at the bottom
                int half = Math.round(halfW * 4f * (float) Math.sqrt(1f - v * v));
                int alpha = Math.round(SHADOW_ALPHA * strength * (1f - v * v));
                if (half <= 0 || alpha <= 0) continue;
                graphics.fill(-half, row, half, row + 1, alpha << 24);
            }
        } finally {
            graphics.pose().popMatrix();
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
        int itemX = itemX(cellX);
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
