package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.VanillaLook;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * An element and a few controls on its first line, at its trailing edge (7.3.0).
 * Package-private; {@link ListView} makes two kinds:
 *
 * <ul>
 *   <li><b>A row with its controls</b> ({@link ListView.Builder#controlsInRows()}): the
 *       consumer's row, then a vertical divider line, then up, down and remove. The
 *       list makes one per key only when the list has controls in its rows, so any
 *       other list keeps the consumer's row as it is.</li>
 *   <li><b>The toolbar</b>: the search field, then the sort, add, remove and arrow
 *       buttons as its controls, a button's gap after it, no divider.</li>
 * </ul>
 *
 * <h3>Inside the row</h3>
 *
 * The row is the consumer's, whatever it is. The controls go on the row's first line
 * ({@link AbstractPanelElement#reserveTrailing}): a boxed {@link Flow} keeps them inside
 * its card, its content that much narrower; a {@link Section}, alone or in a boxed Flow,
 * keeps them on its header line, so what opens under it has the whole width. Any other
 * row (a bare text field) is narrowed by the same room and they sit beside it. Either
 * way the divider is the card's padding from the content and from the controls, so the
 * controls sit in a compartment of their own.
 *
 * <p>Each control keeps its slot whether or not it shows (a pinned row has no arrows
 * and no remove button), so every row of the list leaves the same room and the
 * controls line up in columns. They are centred on the first line: a Section's
 * header, or a row's height less what it grew by ({@link PanelElement#extraLayoutHeight}).
 * A first line shorter than the controls moves the row down until they fit.
 *
 * <p>The toolbar is as wide as the list's rows, whatever room it is given
 * ({@link #ListRow(PanelElement, List, IntSupplier)}): placed above the list's scroll
 * area, its right end lines up with the rows' right end whether the scrollbar shows
 * or not.
 */
final class ListRow extends AbstractPanelElement {

    /** The toolbar's: between the search field and its buttons, a button's gap. */
    static final int LANE_GAP = DialogLayout.BUTTON_GAP;
    /** Between two controls: the gap between any two MenuKit buttons side by side. */
    static final int CONTROL_GAP = DialogLayout.BUTTON_GAP;
    /** A row's buttons, all this tall and square: up, down, remove. */
    static final int CONTROL = 16;
    /** A row's divider: a card's padding on each side of its line, so the controls' compartment is as padded as the card. */
    static final int DIVIDER_PAD = Flow.BOX_PADDING;
    /** The divider's column: the Default look draws two lines (light, dark), the Panel look one. */
    static final int DIVIDER_W = 2;

    private final PanelElement row;
    private final List<PanelElement> controls;
    /** Drawn in this order, the controls over the row; input goes the other way, the controls first. */
    private final List<PanelElement> all;
    private final List<PanelElement> input;
    /** A list row's controls sit behind a divider line; the toolbar's buttons a gap after the search field. */
    private final boolean divider;
    /**
     * Whether the row keeps the controls' room on its own first line (otherwise they sit
     * beside it): asked once, on first need, since a button measures its label with the
     * game's font and a list may be built before it loads.
     */
    private @Nullable Boolean inside;

    /** The width the list gives the row; until the first pass, its natural width. */
    private int budget = -1;
    /**
     * The toolbar's: the width of the list's rows ({@code <= 0} until the list is laid
     * out), which it never passes. Null for a row, which the list lays out itself.
     */
    private final @Nullable IntSupplier rowsWidth;

    /** A list row and its controls, behind a divider. */
    ListRow(PanelElement row, List<PanelElement> controls) {
        this(row, controls, null, true);
    }

    /** The toolbar: never wider than {@code rowsWidth}, the list's rows' width now. */
    ListRow(PanelElement row, List<PanelElement> controls, IntSupplier rowsWidth) {
        this(row, controls, rowsWidth, false);
    }

    private ListRow(PanelElement row, List<PanelElement> controls, @Nullable IntSupplier rowsWidth, boolean divider) {
        this.rowsWidth = rowsWidth;
        this.divider = divider;
        this.row = row;
        this.controls = List.copyOf(controls);
        List<PanelElement> all = new ArrayList<>(controls.size() + 1);
        all.add(row);
        all.addAll(controls);
        this.all = List.copyOf(all);
        this.input = List.copyOf(all.reversed());
    }

    /** The consumer's row inside. */
    PanelElement row() {
        return row;
    }

    /** The controls, left to right. */
    List<PanelElement> controls() {
        return controls;
    }

    // Layout

    private boolean inside() {
        if (inside == null) inside = row instanceof AbstractPanelElement r && r.reserveTrailing(room());
        return inside;
    }

    /** What the controls take from the row's line, or beside it: the divider and its padding, or the toolbar's gap, and the lane. */
    private int room() {
        return (divider ? DIVIDER_PAD + DIVIDER_W + DIVIDER_PAD : LANE_GAP) + laneWidth();
    }

    /** Every control's slot and the gaps between them, shown or not. */
    private int laneWidth() {
        int w = 0;
        for (PanelElement c : controls) w += c.getWidth();
        return w + Math.max(0, controls.size() - 1) * CONTROL_GAP;
    }

    private int laneHeight() {
        int h = 0;
        for (PanelElement c : controls) h = Math.max(h, c.getHeight());
        return h;
    }

    /** The row's first line, {@code {top, height, right}}: where it keeps the room, or its baseline height. */
    private int[] line() {
        if (inside()) return ((AbstractPanelElement) row).trailingLine();
        return new int[]{0, row.getHeight() - Math.max(0, row.extraLayoutHeight()), 0};
    }

    /** The controls' top, from the row's: centred on the first line (negative: above the row's top). */
    private int centred() {
        int[] line = line();
        return line[0] + (line[1] - laneHeight()) / 2;
    }

    /** How far the row sits below this element's top so the controls fit above its first line. */
    private int rowDrop() {
        return Math.max(0, -centred());
    }

    /** The left edge of the controls, from this element's left. */
    private int laneX() {
        return getWidth() - line()[2] - laneWidth();
    }

    @Override
    public int naturalWidth() {
        return inside() ? row.naturalWidth() : row.naturalWidth() + room();
    }

    /**
     * The width it was given; the toolbar no wider than the rows. Read live, so when the
     * list's scrollbar shows or hides, the toolbar's container sees the change and lays
     * it out again.
     */
    @Override
    public int getWidth() {
        return budget > 0 ? capped(budget) : naturalWidth();
    }

    private int capped(int width) {
        int rows = rowsWidth == null ? 0 : rowsWidth.getAsInt();
        return rows > 0 ? Math.min(width, rows) : width;
    }

    @Override
    public int getHeight() {
        int drop = rowDrop();
        return Math.max(drop + row.getHeight(), drop + centred() + laneHeight());
    }

    /** What the consumer's row grew by, so a row inside a row still reports it. */
    @Override
    public int extraLayoutHeight() {
        return row.extraLayoutHeight();
    }

    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
        int w = getWidth();
        row.layoutWithin(inside() ? w : Math.max(1, w - room()));
    }

    @Override
    public void fillWidth(int width) {
        layoutWithin(width);
    }

    /** Places the row at the left and the controls on its first line, ending at its trailing edge, each centred. */
    @Override
    public void setChildPosition(int x, int y) {
        super.setChildPosition(x, y);
        int drop = rowDrop();
        if (row instanceof AbstractPanelElement r) r.setChildPosition(x, y + drop);
        int top = y + drop + centred(), laneH = laneHeight();
        int cx = x + laneX();
        for (PanelElement c : controls) {
            if (c instanceof AbstractPanelElement e) e.setChildPosition(cx, top + (laneH - c.getHeight()) / 2);
            cx += c.getWidth() + CONTROL_GAP;
        }
    }

    // Render and input: the row and its controls share the list's origin.

    /** The row, then its divider over it, then the controls. */
    @Override
    public void render(RenderContext ctx) {
        ChildDispatch.render(List.of(row), ctx);
        if (divider && row.isVisible()) dividerLine(ctx);
        ChildDispatch.render(controls, ctx);
    }

    /**
     * The line between the row's content and the controls, in MenuKit's divider style
     * for the look: vanilla's light and dark pair in the Default look, the Panel look's
     * one grey line. It runs along the first line and the card's padding above and below
     * it, kept inside the card's border (the Default card's 1 px, the Panel card's border
     * and bevel): the card's whole inner height for a one-line card, and only the header
     * line of a card holding an open {@link Section}, whose content has the full width.
     */
    private void dividerLine(RenderContext ctx) {
        var g = ctx.graphics();
        boolean card = row instanceof Flow f && f.isBoxed();
        int pad = card ? Flow.BOX_PADDING : 0;
        int border = !card || !inside() ? 0 : ctx.kind() == ScreenKind.DEFAULT ? 1 : 2;
        int[] line = line();
        int h = row.getHeight();
        int top = Math.max(border, line[0] - pad), bottom = Math.min(h - border, line[0] + line[1] + pad);
        int x = ctx.originX() + getChildX() + laneX() - DIVIDER_PAD - DIVIDER_W;
        int y = ctx.originY() + row.getChildY();
        if (ctx.kind() == ScreenKind.DEFAULT) {
            g.fill(x, y + top, x + 1, y + bottom, VanillaLook.SEPARATOR_LIGHT);
            g.fill(x + 1, y + top, x + 2, y + bottom, VanillaLook.SEPARATOR_DARK);
        } else {
            g.fill(x + 1, y + top, x + 2, y + bottom, Divider.DEFAULT_COLOR);
        }
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(all, ctx);
    }

    @Override public boolean isInteractive() { return true; }

    @Override
    public boolean hitTest(InputContext in) {
        return ChildDispatch.hitTest(input, in);
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        return ChildDispatch.mouseClicked(input, in, button);
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        return ChildDispatch.mouseScrolled(input, in, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(all, in, button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(input, in, keyCode, scanCode, modifiers);
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return ChildDispatch.activeOverlay(input, in);
    }

    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(all, in);
    }

    @Override
    public boolean presentsSlotAt(InputContext in) {
        return ChildDispatch.presentsSlotAt(all, in);
    }

    @Override
    public int @Nullable [] takeRevealRequest() {
        return ChildDispatch.takeRevealRequest(all);
    }

    @Override public void screenKind(ScreenKind kind) { ChildDispatch.screenKind(all, kind); }
    @Override public void inDialog(boolean inDialog) { ChildDispatch.inDialog(all, inDialog); }
    @Override public void inBand(boolean inBand) { ChildDispatch.inBand(all, inBand); }
    @Override public void onAttach(Screen screen) { ChildDispatch.attach(all, screen); }
    @Override public void onDetach(Screen screen) { ChildDispatch.detach(all, screen); }
}
