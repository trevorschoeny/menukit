package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.core.VanillaLook;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A clipped viewport over pre-positioned elements, scrolled vertically: the
 * single primitive for "show content larger than its bounds".
 *
 * <pre>{@code
 * ScrollContainer.builder().size(140, 80)
 *         .content(rows)                      // positioned from the viewport's top-left
 *         .build();
 * }</pre>
 *
 * <h3>Single responsibility</h3>
 *
 * It owns clipping, the scroll position, the scrollbar and routing input to its
 * children. It does not lay out: children are placed from the viewport's
 * top-left (compose them with {@code Row}/{@code Column} or give positions), and
 * the viewport's size is declared. Width reserved for the scrollbar lane is
 * {@link #viewportWidthFor}.
 *
 * <p>A child that grows pushes the children below it down, as on a panel (7.3.0): a
 * {@link ListView} a row is added to, an open {@link Section}, a wrapped label, by its
 * {@link PanelElement#extraLayoutHeight()}, and back up as it shrinks, read every frame,
 * so the scroll range follows in the same frame. A settings page that scrolls (a list,
 * then more settings) places what follows the list as if it took no room.
 *
 * <h3>The scrollbar shows only while there is something to scroll (7.3.0)</h3>
 *
 * In both looks the track and handle are drawn only while the content is taller
 * than the viewport; while everything fits the lane is empty and takes no clicks.
 * A container that lays its own children out ({@link Builder#fitWidth()}) also gives
 * them the lane's width back while it is hidden, so rows reach the right edge and
 * the margins either side match; it lays them out again the frame the content
 * starts or stops overflowing. A wider layout is never taller, and a narrower one
 * never shorter, so the choice settles at once and never flips back and forth.
 * Children placed by hand keep the width they were given.
 *
 * <h3>Scroll position: view state, or the consumer's</h3>
 *
 * By default the scroll is the container's own view state, as a {@link Tabs}
 * body's is. {@code state(get, set)} hands it to the consumer instead, in pixels
 * from the top: {@code get} is read every frame, {@code set} receives each change.
 * Pixels, not a fraction: when the content changes height while shown (a section
 * opening), what is on screen stays where it is and only the range changes. That
 * holds for a child above the window too: one that grows or shrinks there moves the
 * scroll by as much, so the first child reaching into the window stays put. A
 * position past the new end is clamped, and the clamp written back. Nothing else
 * moves it: no click on a child scrolls it into view, and it never glides (the
 * wheel and the handle move it at once), so a content change has no glide target
 * to fight. The one exception (7.3.0): a child that asks to be shown
 * ({@link PanelElement#takeRevealRequest}, a {@link ListView} whose selection moved)
 * is scrolled into view, the least distance, at once.
 *
 * <h3>Input</h3>
 *
 * The wheel over the viewport scrolls it, {@link #SCROLL_PIXELS_PER_TICK} pixels
 * a notch (after any child that scrolls itself, a nested container, had the
 * wheel); dragging the handle scrolls it, and a press on the track jumps the
 * handle there and holds it, as vanilla's lists do; clicks inside
 * the viewport reach the children at their scrolled positions; the scrollbar lane
 * eats its own clicks. An open popover of a child (a {@link Dropdown}) claims its
 * whole area, even the part hanging below the viewport, and draws unclipped on
 * the overlay pass. Disabled (its own {@code disabledWhen} or its panel's), it and
 * everything in it are grayed and inert.
 *
 * <p>Every child is dispatched through {@link ChildDispatch}, with the context
 * moved to the scrolled content origin: the same order a panel uses.
 *
 * <h3>Slots (7.2.0)</h3>
 *
 * A slot element among the children (SlotKit's) scrolls and clips like any child.
 * One wholly out of view is not rendered, so its slot is parked. One across the
 * viewport's top or bottom edge keeps its real position, so nothing beside it moves,
 * and is cut at the edge: vanilla draws only the part inside, and only that part
 * hovers and takes clicks (the element reports its slot with
 * {@code api.host.SlotClip}). Under an opaque panel, the part inside is the panel's
 * own slot; the part past the edge is the panel's, like a gap between slots.
 *
 * <h3>In the Default look</h3>
 *
 * In the Default look ({@link ScreenKind#DEFAULT}) it is the band every Options
 * screen has: vanilla's darker list
 * background behind the content, scrolling with it, vanilla's separator along
 * the top and the bottom, drawn in the 2 px just outside the container as vanilla
 * draws them, and vanilla's thin list scroller ({@code widget/scroller} on
 * {@code widget/scroller_background}) at the right of the lane, hidden while
 * everything fits. The lane, the handle's size and travel and the input are the
 * same in both looks.
 *
 * <h3>One scroller</h3>
 *
 * The scroll model (pixels, clamping, the wheel's step, the drag) and the
 * scrollbar's drawing are MenuKit's one scroller, {@code core.Scroller}, which a
 * {@link Tabs} strip scrolls by too: its sidebar's scrollbar is this one, the
 * same handle and track at the same size.
 *
 * <p>The band also insets its content as vanilla's lists do: the children's
 * origin is {@link #DEFAULT_INSET_X} in from the left and {@link #DEFAULT_INSET_Y}
 * down, with as much room past the last child, scrolling with the content. The
 * right needs no inset of its own: the scrollbar lane already keeps the content
 * clear of the scroller. So the content is narrower in the Default look: size it
 * with {@link #viewportWidthFor(int, ScreenKind)}. A {@code Tabs} page and a
 * panel's own auto-scroll do that for their children.
 *
 * <p>In a dialog (a modal panel, {@link PanelElement#inDialog}) the frame and its
 * padding are already the margin, so the band insets its content by the dialog's
 * own gap, {@link #DIALOG_INSET}, on every side it insets, not the full screen's.
 * One rule for every band ({@link #bandInsetX}, {@link #bandInsetY}): this one, a
 * {@code Tabs} page, a panel's auto-scroll.
 *
 * <h3>Sized to its content (7.2.0)</h3>
 *
 * {@link Builder#fitWidth()} makes it as wide as its widest child plus the band's
 * inset and the scrollbar lane, so a grid of whole columns is never cut off on
 * either look; given more room by its panel it takes it, and lays its
 * children out to it (a child that centers itself stays centered).
 * {@link Builder#fitHeight()} makes the declared height the most it is: as tall as
 * its content up to that, scrolling past it.
 * Its height then follows its content, so it pushes what is below it as a
 * {@link ListView} does (7.3.0): its whole height is its
 * {@link PanelElement#extraLayoutHeight()}, so place what follows as if it took no
 * room, and that moves down as the content grows and back up as it shrinks, in the
 * same frame.
 *
 * <h3>A lane outside its width (7.4.0)</h3>
 *
 * {@link Builder#laneOutside()} puts the scrollbar lane beside the declared width
 * instead of inside it: the content keeps the whole declared width, and the container
 * is a lane wider only while the lane shows. So whatever holds it makes room for the
 * scrollbar exactly while there is one, as the container list widens its frame for a
 * section's own scrolling grid.
 *
 * <h3>Fitting the room (7.4.0)</h3>
 *
 * {@link Builder#fitRoom(int)} makes it as tall as its content while there is room,
 * and shorter only when there is not, in whole steps (rows), never below its declared
 * height. In a {@link com.trevlar.menukit.api.panel.ContainerRegion} section the region
 * asks it to give back what the list would overflow the screen by
 * ({@link PanelElement#yieldHeight}), so it shows every row and no scrollbar while the
 * window has the height, and scrolls once it is too short; with {@link Builder#laneOutside()}
 * the lane takes width only while it scrolls. Elsewhere nothing asks, and it is as tall
 * as its content. It pushes what is below it as {@link Builder#fitHeight()} does.
 *
 * <h3>Filling its container (7.3.0)</h3>
 *
 * {@link Builder#fillHeight()} takes the height its container gives it instead: what
 * a panel's viewport leaves below it, what a vertical {@link Flow} has left after its
 * other children, the whole of a {@link Tabs} page. A header above it stays put while
 * only it scrolls. On a Tabs page it draws no band of its own ({@link #inBand}): the
 * page's band is behind it already.
 */
public class ScrollContainer extends AbstractPanelElement {

    // The scroller's sizes are MenuKit's one scroller's (core.Scroller), named here as before.
    /** Width of the handle sprite: vanilla's creative-inventory scroller. */
    public static final int SCROLLER_WIDTH = Scroller.THUMB_WIDTH;
    /** Height of the handle: a fixed-size handle, as vanilla's creative inventory. */
    public static final int SCROLLER_HEIGHT = Scroller.THUMB_LENGTH;
    /** Vanilla's scroller sprite. */
    public static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    /** Vanilla's disabled scroller sprite (content fits, or disabled). */
    public static final Identifier SCROLLER_DISABLED_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    /** Space between the content and the scrollbar track. */
    public static final int SCROLLER_GUTTER = 4;
    /** Padding inside the track around the handle. */
    public static final int SCROLLER_TRACK_PADDING = Scroller.TRACK_PADDING;
    /** Scrollbar track width: the handle plus its padding. */
    public static final int TRACK_WIDTH = Scroller.TRACK_WIDTH;
    /** Pixels scrolled per wheel notch. */
    public static final int SCROLL_PIXELS_PER_TICK = Scroller.PIXELS_PER_NOTCH;
    /** The scrollbar lane: the track and the gutter between it and the content. */
    private static final int LANE = TRACK_WIDTH + SCROLLER_GUTTER;

    /**
     * In the Default look, how far in from the list band's left edge its content
     * starts (and, in a {@code Tabs} page that does not scroll, how far from its
     * right edge it ends): vanilla's gap between a list row's content and the
     * list's scrollbar. 26.2's {@code AbstractSelectionList.scrollBarX()} is
     * {@code getRowRight() + scrollbarWidth() + 2} (6 + 2), and a row's content
     * ends {@code Entry.CONTENT_PADDING} (2) inside the row, so 10; Controls'
     * {@code KeyBindsList.KeyEntry} ends its last button at {@code scrollBarX() - 10}.
     * MenuKit adds 4 to that: a settings page of labels, headings and wrapped text
     * read cramped at vanilla's 10, whose lists hold only buttons.
     */
    public static final int DEFAULT_INSET_X = 14;
    /**
     * In the Default look, the room above the band's first child and below its
     * last: vanilla's list padding ({@code AbstractSelectionList.getFirstEntryY()}
     * is the list's top + 2, {@code contentHeight()} the rows + 4) plus a row's
     * {@code Entry.CONTENT_PADDING} (2), which is 4. MenuKit uses 10: at 4 the first
     * heading sat on the band's top separator. It scrolls with the
     * content, as vanilla's does.
     */
    public static final int DEFAULT_INSET_Y = 10;
    /** @deprecated Renamed {@link #DEFAULT_INSET_X} in 7.3.0 (the Default look); removed in 8.0.0. */
    @Deprecated(since = "7.3.0", forRemoval = true)
    public static final int OPTIONS_INSET_X = DEFAULT_INSET_X;
    /** @deprecated Renamed {@link #DEFAULT_INSET_Y} in 7.3.0 (the Default look); removed in 8.0.0. */
    @Deprecated(since = "7.3.0", forRemoval = true)
    public static final int OPTIONS_INSET_Y = DEFAULT_INSET_Y;
    /**
     * In a dialog in the Default look, the band's inset on every side it insets:
     * {@link DialogLayout#GAP}, the room the dialog keeps between everything else.
     * The dialog's frame and padding are already the margin, so the full screen's
     * {@link #DEFAULT_INSET_X} on top of them would be a wide empty one.
     */
    public static final int DIALOG_INSET = DialogLayout.GAP;

    private final List<PanelElement> content;
    /** The scrollable height given to the builder, or -1 to follow the children's extent every frame. */
    private final int fixedContentHeight;
    private final DoubleSupplier scrollGet;
    private final DoubleConsumer scrollSet;
    /** Whether a Default-look screen draws the list band; off where a host draws it (a Tabs body). */
    private final boolean band;
    /** Width from the content ({@link Builder#fitWidth}); height up to the declared one ({@link Builder#fitHeight}). */
    private final boolean fitWidth, fitHeight;
    /** The lane beside the declared width, taken only while it shows ({@link Builder#laneOutside}). */
    private final boolean laneOutside;
    /** Fitting the room ({@link Builder#fitRoom}): the step it gives height back in, 0 for not. */
    private final int roomStep;
    /** Fitting the room: how much shorter than its content it is this frame ({@link #yieldHeight}). */
    private int yielded = 0;
    /** For measuring only (a measure gets no context): the kind of screen and whether in a dialog, handed down. */
    private ScreenKind measuredKind = ScreenKind.PANEL;
    private boolean inDialog = false;
    /** On a band another element draws (a filling Tabs body): no band of its own ({@link #inBand}). */
    private boolean inBand = false;
    /** Takes the height its container gives it ({@link Builder#fillHeight}). */
    private final boolean fill;
    /** The height its container gave it, or -1 for none (its declared height, or its content's). */
    private int filled = -1;

    /** The scroll, in pixels, when the consumer does not own it (view state). */
    private double ownScroll = 0;

    /**
     * The children's visibility, places and heights at the last reflow, and each one's
     * bottom then: layout state, so the children are re-stacked only when one changed,
     * and the view kept on the child that reached into it ({@link #reflow}).
     */
    private int reflowSig = 0;
    private final int[] settledBottom;

    /**
     * Keeps the lane and its (grayed) handle even while everything fits, as vanilla's
     * creative inventory does: for a grid whose contents a search changes as you type
     * (the item picker), so it never shifts sideways under the cursor.
     */
    private final boolean steadyLane;
    /**
     * Fitting its width: the lane the children were last laid out beside, {@link #LANE}
     * or 0. Layout state, not a frame cache: render lays the children out again when
     * the content's overflow no longer matches it.
     */
    private int laidLane = LANE;

    // The handle drag: interaction state from press to release, not a frame cache.
    // While held, how far below the handle's top it is held (Scroller.grab).
    private boolean dragging = false;
    private double dragGrab = 0;

    protected ScrollContainer(Builder b) {
        super(b);
        this.content = List.copyOf(b.content);
        this.settledBottom = new int[content.size()];
        this.fixedContentHeight = b.contentHeight;
        this.band = b.band;
        this.fitWidth = b.fitWidth;
        this.fitHeight = b.fitHeight;
        this.laneOutside = b.laneOutside;
        this.roomStep = b.roomStep;
        this.fill = b.fill;
        this.steadyLane = b.steadyLane;
        if (fitWidth) this.width = -1;   // its content's until the panel lays it out
        if (b.scrollGet != null) {
            this.scrollGet = b.scrollGet;
            this.scrollSet = b.scrollSet;
        } else {
            this.scrollGet = () -> ownScroll;
            this.scrollSet = v -> ownScroll = v;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The content width that fits a container {@code outerWidth} wide in the
     * Panel look: its width minus the scrollbar lane. In the Default look
     * use {@link #viewportWidthFor(int, ScreenKind)}.
     */
    public static int viewportWidthFor(int outerWidth) {
        return outerWidth - TRACK_WIDTH - SCROLLER_GUTTER;
    }

    /**
     * The content width that fits a container {@code outerWidth} wide on a screen
     * of {@code kind}: its width minus the scrollbar lane and, in the Default
     * look, the list band's {@link #DEFAULT_INSET_X}.
     */
    public static int viewportWidthFor(int outerWidth, ScreenKind kind) {
        return viewportWidthFor(outerWidth, kind, false);
    }

    /** As {@link #viewportWidthFor(int, ScreenKind)}, in a dialog or not ({@link #bandInsetX}). */
    public static int viewportWidthFor(int outerWidth, ScreenKind kind, boolean inDialog) {
        return viewportWidthFor(outerWidth) - bandInsetX(kind, inDialog);
    }

    /**
     * The list band's inset from its left edge (and a {@code Tabs} page's from each
     * side): {@link #DEFAULT_INSET_X} in the Default look, {@link #DIALOG_INSET} in a
     * dialog there, none in the Panel look, which draws no band.
     */
    public static int bandInsetX(ScreenKind kind, boolean inDialog) {
        if (kind != ScreenKind.DEFAULT) return 0;
        return inDialog ? DIALOG_INSET : DEFAULT_INSET_X;
    }

    /** The band's room above its first child and below its last, by the same rule as {@link #bandInsetX}. */
    public static int bandInsetY(ScreenKind kind, boolean inDialog) {
        if (kind != ScreenKind.DEFAULT) return 0;
        return inDialog ? DIALOG_INSET : DEFAULT_INSET_Y;
    }

    /** The lowest shown child's bottom: a hidden child adds no room to scroll over, as it adds no width. */
    private static int autoContentHeight(List<PanelElement> content) {
        int max = 0;
        for (PanelElement e : content) {
            if (e.isVisible()) max = Math.max(max, e.getChildY() + e.getHeight());
        }
        return max;
    }

    // Geometry

    /**
     * The width the children see and are clipped to: the whole width less the lane
     * while it shows. Fitting its width, the lane they were laid out beside; else the
     * lane as it is now (children placed by hand never reach it either way).
     */
    int viewportWidth() {
        return getWidth() - (fitWidth ? laidLane : laneWidth(measuredKind));
    }

    /** Whether the scrollbar shows: while the content overflows, or always for a steady lane. */
    private boolean laneShown(ScreenKind kind) {
        return steadyLane || canScroll(kind);
    }

    private int laneWidth(ScreenKind kind) {
        return laneShown(kind) ? LANE : 0;
    }

    /**
     * Fitting its width, lays the children out to the viewport inside the band's inset:
     * the whole width when they fit without the lane, else less the lane.
     */
    private void layoutContent(ScreenKind kind) {
        int room = Math.max(1, getWidth() - insetX(kind));
        laidLane = 0;
        if (!steadyLane) Panel.layoutElementsWithin(content, room);   // without the lane: does it fit?
        if (laneShown(kind)) {
            laidLane = LANE;
            Panel.layoutElementsWithin(content, Math.max(1, room - LANE));
        }
    }

    /**
     * Fitting its content, as wide as it was laid out (its content's before then); else as
     * declared, and the lane beside that while it shows for a lane outside.
     */
    @Override
    public int getWidth() {
        if (laneOutside) return width + laneWidth(measuredKind);
        return width >= 0 ? width : naturalWidth();
    }

    /** Fitting its content's height, as tall as it up to the declared height; else as declared. */
    @Override
    public int getHeight() {
        return viewHeight(measuredKind);
    }

    /**
     * While its height follows its content ({@link Builder#fitHeight()}, or filling with
     * no room given and no declared height), all of it, as a {@link ListView} reports
     * (7.3.0): its baseline is nothing, so the container pushes the elements below it
     * down by its whole height, read every frame, and they follow it as it grows toward
     * its declared height and shrinks back. Place what follows as if it took no room.
     * At a fixed or given height it doesn't grow: nothing.
     */
    @Override
    public int extraLayoutHeight() {
        boolean followsContent = !(fill && filled > 0) && (fitHeight || roomStep > 0 || height <= 0);
        return followsContent ? getHeight() : 0;
    }

    /** The viewport's height on a screen of {@code kind}. */
    private int viewHeight(ScreenKind kind) {
        if (fill && filled > 0) return filled;          // the room its container gave it
        if (height <= 0) return Math.max(1, contentHeight(kind));   // filling with no room given: its content
        if (roomStep > 0) {                                          // fitting the room: its content less what it gave back,
            return Math.max(1, Math.max(height, contentHeight(kind) - yielded));   // never under its declared height
        }
        return fitHeight ? Math.max(1, Math.min(height, contentHeight(kind))) : height;
    }

    @Override
    public boolean fillsHeight() {
        return fill;
    }

    /**
     * Fitting the room: gives back {@code overflow} rounded up to whole steps, so it stays
     * whole rows and the list fits, but never below its declared height (past that the list
     * scrolls instead). Not fitting: nothing, and its content scrolls as it is.
     */
    @Override
    public int yieldHeight(int overflow) {
        if (roomStep <= 0) return 0;
        int content = contentHeight(measuredKind);
        int steps = (Math.max(0, overflow) + roomStep - 1) / roomStep * roomStep;
        yielded = Math.max(0, Math.min(steps, content - Math.min(height, content)));
        return yielded;
    }

    /** The height its container gives it, {@code -1} for none (back to its declared or content height). */
    @Override
    public void fillHeight(int height) {
        this.filled = height;
    }

    @Override
    public void inBand(boolean inBand) {
        this.inBand = inBand;
        ChildDispatch.inBand(content, inBand);
    }

    /** Whether it draws the list band in the Default look: built with one, and not on another's. */
    private boolean drawsBand() {
        return band && !inBand;
    }

    /** Fitting its width: the widest child, the band's inset and the scrollbar lane. */
    @Override
    public int naturalWidth() {
        if (laneOutside) return authoredWidth + laneWidth(measuredKind);
        if (!fitWidth) return authoredWidth;
        int widest = 0;
        for (PanelElement e : content) {
            if (e.isVisible()) widest = Math.max(widest, e.getChildX() + e.naturalWidth());
        }
        // The lane counts whether or not it shows, so the container's own width never
        // changes with its content's height; while hidden, its width goes to the children.
        return widest + insetX(measuredKind) + LANE;
    }

    @Override
    public void screenKind(ScreenKind kind) {
        measuredKind = kind;
        ChildDispatch.screenKind(content, kind);
    }

    @Override
    public void inDialog(boolean inDialog) {
        this.inDialog = inDialog;
        ChildDispatch.inDialog(content, inDialog);
    }

    // The list band's inset, by the kind of screen the context says it is on:
    // only where this container draws the band (a Tabs page insets its own).
    private int insetX(ScreenKind kind) {
        return drawsBand() ? bandInsetX(kind, inDialog) : 0;
    }

    private int insetY(ScreenKind kind) {
        return drawsBand() ? bandInsetY(kind, inDialog) : 0;
    }

    /**
     * The scrollable height: the builder's {@code contentHeight}, else the children's
     * extent, read live, so a child that grows or shrinks while shown (a section
     * opening, an item picker's grid narrowing to a search) changes the range.
     * The band's inset above and below is part of it.
     */
    private int contentHeight(ScreenKind kind) {
        reflow(kind);
        int children = fixedContentHeight >= 0 ? fixedContentHeight : autoContentHeight(content);
        return children + 2 * insetY(kind);
    }

    /**
     * Re-stacks the children from their baselines as a panel does its elements
     * ({@link Panel#reflowForWrap}), when one changed visibility, place or height: one
     * that grew pushes those below it down by its extra height, one that shrank lets
     * them back up. Scrolled down, the view stays on the topmost child that reached into
     * the window before the change: when it moved (something above the window grew or
     * shrank) the scroll moves by as much. A child across the window's top that grows at
     * its bottom doesn't move, so neither does the view. At the top it stays at the top.
     */
    private void reflow(ScreenKind kind) {
        if (signature() == reflowSig) return;
        double scroll = scrollGet.getAsDouble();
        int top = (int) scroll - insetY(kind);   // the window's top, in the children's coordinates
        int anchor = -1;
        for (int i = 0; i < content.size(); i++) {
            PanelElement e = content.get(i);
            if (!e.isVisible() || settledBottom[i] <= top) continue;
            if (anchor < 0 || e.getChildY() < content.get(anchor).getChildY()) anchor = i;
        }
        int anchorY = anchor >= 0 ? content.get(anchor).getChildY() : 0;
        Panel.reflowForWrap(content);
        for (int i = 0; i < content.size(); i++) {
            PanelElement e = content.get(i);
            settledBottom[i] = e.getChildY() + e.getHeight();
        }
        reflowSig = signature();
        if (anchor < 0 || scroll <= 0) return;
        int moved = content.get(anchor).getChildY() - anchorY;
        if (moved != 0) setScroll(kind, scroll + moved);   // re-enters with the new signature: no second reflow
    }

    private int signature() {
        int sig = 1;
        for (PanelElement e : content) {
            boolean vis = e.isVisible();
            sig = sig * 31 + (vis ? 1 : 0);
            if (vis) sig = (sig * 31 + e.getChildY()) * 31 + e.getHeight();
        }
        return sig;
    }

    private boolean canScroll(ScreenKind kind) {
        return contentHeight(kind) > viewHeight(kind);
    }

    /**
     * Whether it scrolls now: its content is taller than its viewport, so the scrollbar shows and
     * the wheel over it is its own (Fluorite). For a mod that would do something else with the
     * wheel there (switch tabs, say) while it doesn't.
     */
    public boolean scrolls() {
        return canScroll(measuredKind);
    }

    /** The scroll in pixels, clamped to the current range. */
    private double scroll(ScreenKind kind) {
        return Scroller.clamp(scrollGet.getAsDouble(), contentHeight(kind), viewHeight(kind));
    }

    private void setScroll(ScreenKind kind, double pixels) {
        scrollSet.accept(Scroller.clamp(pixels, contentHeight(kind), viewHeight(kind)));
    }

    /** The handle's top from the viewport's top: the track is the viewport's height. */
    private double handleTop(ScreenKind kind) {
        int h = viewHeight(kind);
        return Scroller.thumbTop(scroll(kind), contentHeight(kind), h, h);
    }

    /** While the handle is held, the scroll that keeps it under the mouse where it was grabbed. */
    private void followDrag(ScreenKind kind, double mouseFromTop) {
        int h = viewHeight(kind);
        setScroll(kind, Scroller.dragTo(mouseFromTop, dragGrab, contentHeight(kind), h, h));
    }

    /** Where the children's origin is: the viewport's top-left (inset in the band), moved up by the scroll. */
    private InputContext childInput(InputContext in) {
        ScreenKind k = in.kind();
        return in.at(in.originX() + childX + insetX(k), in.originY() + childY + insetY(k) - (int) scroll(k))
                .disabledIf(ownDisabled());
    }

    private RenderContext childRender(RenderContext ctx) {
        ScreenKind k = ctx.kind();
        return childContext(ctx.at(ctx.originX() + childX + insetX(k), ctx.originY() + childY + insetY(k) - (int) scroll(k)));
    }

    /** Column fill widens the viewport; floored so the scrollbar lane always fits. */
    @Override
    public void fillWidth(int width) {
        super.fillWidth(Math.max(width, LANE + 1));
    }

    /**
     * A declared viewport: the panel does not narrow it. Fitting its content's width,
     * it takes the room it is given (never less than the scrollbar lane needs) and
     * lays its children out to the viewport inside the band's inset, with the lane's
     * width while the lane is hidden.
     */
    @Override
    public void layoutWithin(int budget) {
        if (!fitWidth) return;
        width = Math.max(LANE + 1, budget);
        layoutContent(measuredKind);
    }

    @Override public boolean isInteractive() { return true; }

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        ScreenKind kind = ctx.kind();
        if (kind != measuredKind) screenKind(kind);
        if (disabled) dragging = false;
        int width = getWidth(), height = viewHeight(kind);

        // The container's own tooltip first, so a hovered child's wins.
        queueTooltip(ctx);

        // A consumer-owned position past the end (the content got shorter) is
        // clamped when read; write the clamp back, so a later growth does not jump.
        double raw = scrollGet.getAsDouble();
        if (raw != scroll(kind)) scrollSet.accept(scroll(kind));

        // A child asking to be shown (a list's selection moved by code or the keys):
        // the least scroll that brings its rectangle into the viewport (7.3.0).
        int[] reveal = ChildDispatch.takeRevealRequest(content);
        if (reveal != null) {
            setScroll(kind, Scroller.intoView(scroll(kind), reveal[0] + insetY(kind), reveal[1],
                    contentHeight(kind), viewHeight(kind)));
        }

        // Follow a held handle with the mouse.
        if (dragging && canScroll(kind) && ctx.hasMouseInput()) followDrag(kind, ctx.mouseY() - sy);

        // The content started or stopped overflowing (a row added or removed, a search
        // typed): fitting its width, lay the children out again beside the lane or across it.
        boolean shown = laneShown(kind);
        if (fitWidth && width >= 0 && (shown ? LANE : 0) != laidLane) {
            layoutContent(kind);
            shown = laneShown(kind);
        }

        // In the Default look: the list band behind the children, scrolling with them as
        // vanilla's does, and its separators just outside the top and bottom edges.
        boolean vanilla = kind == ScreenKind.DEFAULT;
        if (vanilla && drawsBand()) {
            VanillaLook.listBackground(g, sx, sy, width, height, sx, sy + (int) scroll(kind));
            VanillaLook.headerSeparator(g, sx, sy - VanillaLook.SEPARATOR_HEIGHT, width);
            VanillaLook.footerSeparator(g, sx, sy + height, width);
        }

        // The children, clipped to the viewport, at their scrolled positions. A
        // child entirely outside the window is skipped, unless its popover is open
        // (the popover draws on the overlay pass from the same origin); a skipped one
        // is told it is held out of view (renderCulled), not hidden.
        g.enableScissor(sx, sy, sx + viewportWidth(), sy + height);
        RenderContext inner = childRender(ctx);
        int top = (int) scroll(kind) - insetY(kind);  // the window's top, in the children's coordinates
        InputContext probe = inner.input();
        for (PanelElement e : content) {
            if (!e.isVisible()) continue;
            boolean open = e.getActiveOverlayBounds(probe) != null;
            if (!open && (e.getChildY() + e.getHeight() <= top || e.getChildY() >= top + height)) {
                e.renderCulled(inner);
                continue;
            }
            e.render(inner);
        }
        g.disableScissor();

        // The scrollbar at the lane's right, drawn for the kind of screen by the one
        // scroller: MenuKit's inset track and vanilla's creative handle in the Panel
        // look, vanilla's thin list scroller in the Default look. None while everything
        // fits, in either look (a steady lane keeps its grayed handle in the Panel look).
        // The handle's size, travel and drag are the same on both.
        if (shown) {
            Scroller.draw(g, kind, sx + width - Scroller.trackWidth(kind), sy, height, handleTop(kind),
                    canScroll(kind), disabled);
        }
    }

    /** Culled itself: every shown child is held out of view with it. */
    @Override
    public void renderCulled(RenderContext ctx) {
        ChildDispatch.renderCulled(content, childRender(ctx));
    }

    /** The children's overlays, unclipped, at their scrolled positions. */
    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(content, childRender(ctx));
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return ChildDispatch.activeOverlay(content, childInput(in));
    }

    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(content, childInput(in));
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        InputContext inner = childInput(in);
        // A child's open popover claims its whole area, even below the viewport.
        if (ChildDispatch.overlayOwner(content, inner) != null) return ChildDispatch.mouseClicked(content, inner, button);

        double lx = in.mouseX() - (in.originX() + childX);
        double ly = in.mouseY() - (in.originY() + childY);
        int trackLeft = getWidth() - TRACK_WIDTH;
        if (lx >= viewportWidth()) {
            // The gutter and the track eat their own clicks. A left press on the
            // track holds the handle: where it was pressed, on the handle; else by its
            // middle, so it jumps to center on the press (vanilla's lists and creative
            // inventory do the same), and the drag carries on from there.
            ScreenKind kind = in.kind();
            if (button == Click.LEFT && lx >= trackLeft && canScroll(kind)) {
                dragging = true;
                dragGrab = Scroller.grab(ly, handleTop(kind));
                followDrag(kind, ly);
            }
            return true;
        }
        return ChildDispatch.mouseClicked(content, inner, button);
    }

    /**
     * A slot among the children, only where the viewport shows it: the part of a slot
     * past the viewport's edge is not drawn, so it is not there to hover or click.
     */
    @Override
    public boolean presentsSlotAt(InputContext in) {
        if (!in.isOver(childX, childY, viewportWidth(), viewHeight(in.kind()))) return false;
        return ChildDispatch.presentsSlotAt(content, childInput(in));
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        if (disabled(in)) return false;
        InputContext inner = childInput(in);
        if (ChildDispatch.overlayOwner(content, inner) != null) {
            return ChildDispatch.mouseScrolled(content, inner, scrollX, scrollY);
        }
        // A child that scrolls itself (a nested container) goes first, if the
        // mouse is inside the viewport.
        double lx = in.mouseX() - (in.originX() + childX);
        if (lx < viewportWidth() && ChildDispatch.mouseScrolled(content, inner, scrollX, scrollY)) return true;
        ScreenKind kind = in.kind();
        if (!canScroll(kind)) return false;
        setScroll(kind, Scroller.wheel(scroll(kind), scrollY, contentHeight(kind), viewHeight(kind)));
        return true;
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        if (button == Click.LEFT) dragging = false;
        ChildDispatch.mouseReleased(content, childInput(in), button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(content, childInput(in), keyCode, scanCode, modifiers);
    }

    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        ChildDispatch.attach(content, screen);
    }

    /** Leaving a screen ends a scrollbar drag: its release may never reach this container. */
    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        dragging = false;
        ChildDispatch.detach(content, screen);
    }

    public static class Builder extends AbstractPanelElement.Builder<ScrollContainer, Builder> {
        private List<PanelElement> content = List.of();
        private int contentHeight = -1;
        private @Nullable DoubleSupplier scrollGet;
        private @Nullable DoubleConsumer scrollSet;
        private boolean band = true;
        private boolean fitWidth, fitHeight, fill, steadyLane, laneOutside;
        private int roomStep;

        protected Builder() {}

        /** No list band in the Default look: for a host that draws one around it (a Tabs body). */
        Builder bandless() {
            this.band = false;
            return this;
        }

        /** Keeps the scrollbar lane while everything fits ({@link #steadyLane}): the item picker's grid. */
        Builder steadyLane() {
            this.steadyLane = true;
            return this;
        }

        @Override protected Builder self() { return this; }

        /** Required: the outer size, scrollbar lane included. */
        @Override
        public Builder size(int width, int height) {
            int min = TRACK_WIDTH + SCROLLER_GUTTER + 1;
            if (width <= min) {
                throw new IllegalArgumentException("ScrollContainer width must be > " + min
                        + " (track + gutter + at least 1px of viewport)");
            }
            if (height <= 0) throw new IllegalArgumentException("ScrollContainer height must be > 0");
            return super.size(width, height);
        }

        /**
         * The viewport's height alone, for a container that fits its width to its
         * content ({@link #fitWidth}).
         */
        public Builder height(int height) {
            if (height <= 0) throw new IllegalArgumentException("ScrollContainer height must be > 0");
            this.height = height;
            return this;
        }

        /**
         * As wide as its content instead of a declared width: the widest child, the
         * band's inset in the Default look and the scrollbar lane. Given more room by
         * its panel, it takes it and lays its children out to it. Give it its
         * {@link #height}.
         */
        public Builder fitWidth() {
            this.fitWidth = true;
            return this;
        }

        /**
         * As tall as its content up to the declared height, scrolling past it. It pushes
         * what is below it as it grows (7.3.0): place what follows as if it took no room.
         */
        public Builder fitHeight() {
            this.fitHeight = true;
            return this;
        }

        /**
         * The scrollbar lane beside the declared width rather than inside it (7.4.0): the
         * content has the whole width, and the container is a lane wider only while the
         * content overflows, so what holds it makes room for the scrollbar just then. With
         * {@link #size}, not {@link #fitWidth()}.
         */
        public Builder laneOutside() {
            this.laneOutside = true;
            return this;
        }

        /**
         * As tall as its content while there is room, shorter by whole {@code step}s when there
         * is not, never below the declared height (7.4.0): content shorter than that leaves the
         * rest of the declared height empty, so the frame keeps its size as the content shrinks. In a container region section it
         * shows every row without a scrollbar while the window has the height and scrolls once
         * it is too short; elsewhere it is as tall as its content. It pushes what is below it,
         * as {@link #fitHeight()} does. Pair with {@link #laneOutside()} for a lane only while
         * it scrolls: {@code .size(ContainerRegion.CONTENT_WIDTH, 3 * 18).laneOutside().fitRoom(18)}.
         *
         * @param step the row height it shrinks by, {@code > 0}
         */
        public Builder fitRoom(int step) {
            if (step <= 0) throw new IllegalArgumentException("fitRoom step must be > 0");
            this.roomStep = step;
            return this;
        }

        /**
         * Takes the height its container gives it (7.3.0): the room a panel's viewport
         * leaves below it, what a vertical {@link Flow} has left after its other
         * children, a {@link Tabs} page's height. So a header stays put above it and only
         * it scrolls: a list under a title and a "New" row. Where there is no height to
         * give, it is its declared height, or without one as tall as its content. Pair
         * with {@link #size} or {@link #fitWidth()}.
         */
        public Builder fillHeight() {
            this.fill = true;
            return this;
        }

        /** Required: the children, positioned from the viewport's top-left. */
        public Builder content(List<? extends PanelElement> content) {
            this.content = List.copyOf(Objects.requireNonNull(content, "content"));
            return this;
        }

        /** The scrollable height, when not the children's extent (room past the end, say). */
        public Builder contentHeight(int pixels) {
            if (pixels < 0) throw new IllegalArgumentException("contentHeight must be >= 0, got " + pixels);
            this.contentHeight = pixels;
            return this;
        }

        /**
         * The consumer owns the scroll, in pixels from the top: {@code get} is read
         * every frame, {@code set} receives each change. Without it the scroll is
         * the container's own view state.
         */
        public Builder state(DoubleSupplier get, DoubleConsumer set) {
            this.scrollGet = Objects.requireNonNull(get, "get");
            this.scrollSet = Objects.requireNonNull(set, "set");
            return this;
        }

        @Override
        public ScrollContainer build() {
            require((height > 0 || fill) && (fitWidth || width > 0),
                    "size(w, h) is required (or fitWidth() and height(h), or fillHeight() with a width)");
            require(!(laneOutside && fitWidth), "laneOutside() takes a declared width: use size(w, h), not fitWidth()");
            return new ScrollContainer(this);
        }
    }
}
