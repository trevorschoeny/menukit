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
 * One row of an editing {@link ListView} (7.3.0): the consumer's row, and the list's
 * controls for it (Edit, the move arrows, remove) on the row's first line, at its
 * trailing edge. Package-private: the list makes one per key only when it has a control
 * to put there, so a list with none keeps the consumer's row as it is. The list's
 * toolbar is one too: the search field, and the sort and add buttons as its controls.
 *
 * <h3>Inside the row</h3>
 *
 * The row is the consumer's, whatever it is, and the list draws nothing of its own
 * around it. The controls go on the row's first line ({@link AbstractPanelElement#reserveTrailing}):
 * a boxed {@link Flow} keeps them inside its card, its content that much narrower; a
 * {@link Section}, alone or in a boxed Flow, keeps them on its header line, so what
 * opens under it has the whole width. Any other row (a bare text field) is narrowed
 * by the controls and a gap, and they sit beside it.
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

    /** Between the consumer's row and the controls: a button's gap. */
    static final int LANE_GAP = DialogLayout.BUTTON_GAP;
    /** Between two controls: the gap between any two MenuKit buttons side by side. */
    static final int CONTROL_GAP = DialogLayout.BUTTON_GAP;
    /** The row's buttons, all this tall: Edit, and the square icon ones (up, down, remove). */
    static final int CONTROL = 16;

    // mockup: where the controls sit, for the round-3 comparison shots; remove with Lane
    // once one placement is picked. INSIDE is the shipped behaviour (inside the card,
    // after its content); DIVIDER the same with a line between content and controls;
    // BOXED outside the card in a card of their own, the list's gap apart; BARE outside
    // the card with no box (a row that keeps no room always gets that).
    enum Lane { INSIDE, DIVIDER, BOXED, BARE }

    /** mockup: the DIVIDER lane's space on each side of its line: a card's padding, so the controls sit in a compartment as padded as the card. */
    private static final int DIVIDER_PAD = Flow.BOX_PADDING;
    /** mockup: the DIVIDER lane's line column: the Default look's divider is two lines (light, dark), the Panel look's one. */
    private static final int DIVIDER_W = 2;

    private final Lane lane;
    /** mockup: the BOXED lane's space between the row's card and the controls' card: the list's gap between rows. */
    private final int boxGap;

    private final PanelElement row;
    private final List<PanelElement> controls;
    /** Drawn in this order, the controls over the row; input goes the other way, the controls first. */
    private final List<PanelElement> all;
    private final List<PanelElement> input;
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

    ListRow(PanelElement row, List<PanelElement> controls) {
        this(row, controls, null);
    }

    /** mockup: a row whose controls sit in {@code lane}; {@code boxGap} is the list's gap, for BOXED. */
    ListRow(PanelElement row, List<PanelElement> controls, Lane lane, int boxGap) {
        this(row, controls, null, lane, boxGap);
    }

    /** The toolbar: never wider than {@code rowsWidth}, the list's rows' width now. */
    ListRow(PanelElement row, List<PanelElement> controls, @Nullable IntSupplier rowsWidth) {
        this(row, controls, rowsWidth, Lane.INSIDE, 0);
    }

    private ListRow(PanelElement row, List<PanelElement> controls, @Nullable IntSupplier rowsWidth, Lane lane, int boxGap) {
        this.lane = lane;
        this.boxGap = boxGap;
        this.rowsWidth = rowsWidth;
        this.row = row;
        this.controls = List.copyOf(controls);
        List<PanelElement> all = new ArrayList<>(controls.size() + 1);
        all.add(row);
        all.addAll(controls);
        this.all = List.copyOf(all);
        this.input = List.copyOf(all.reversed());
    }

    private boolean inside() {
        if (inside == null) {
            // mockup: the outside lanes never ask the row for room; DIVIDER asks for its line and padding too.
            int room = lane == Lane.DIVIDER ? DIVIDER_PAD + DIVIDER_W + DIVIDER_PAD + laneWidth() : LANE_GAP + laneWidth();
            inside = (lane == Lane.INSIDE || lane == Lane.DIVIDER) && row instanceof AbstractPanelElement r && r.reserveTrailing(room);
        }
        return inside;
    }

    /** What the controls take beside a row that keeps no room for them: a gap and the lane, or (mockup: BOXED) the list's gap and a padded card. */
    private int besideWidth() {
        return lane == Lane.BOXED ? boxGap + boxWidth() : LANE_GAP + laneWidth();
    }

    /** mockup: the BOXED lane's card: the controls with a card's padding either side. */
    private int boxWidth() {
        return Flow.BOX_PADDING + laneWidth() + Flow.BOX_PADDING;
    }

    /** The consumer's row inside. */
    PanelElement row() {
        return row;
    }

    // Layout

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

    @Override
    public int naturalWidth() {
        return inside() ? row.naturalWidth() : row.naturalWidth() + besideWidth();
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
        row.layoutWithin(inside() ? w : Math.max(1, w - besideWidth()));
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
        int cx = x + getWidth() - line()[2] - laneWidth() - (lane == Lane.BOXED && !inside() ? Flow.BOX_PADDING : 0);
        for (PanelElement c : controls) {
            if (c instanceof AbstractPanelElement e) e.setChildPosition(cx, top + (laneH - c.getHeight()) / 2);
            cx += c.getWidth() + CONTROL_GAP;
        }
    }

    // Render and input: the row and its controls share the list's origin.

    @Override
    public void render(RenderContext ctx) {
        if (lane == Lane.INSIDE || lane == Lane.BARE) {
            ChildDispatch.render(all, ctx);
            return;
        }
        // mockup: the DIVIDER line and the BOXED card go over the row and under the controls.
        ChildDispatch.render(List.of(row), ctx);
        if (row.isVisible()) {
            if (lane == Lane.BOXED && !inside()) boxCard(ctx);
            if (lane == Lane.DIVIDER && inside()) dividerLine(ctx);
        }
        ChildDispatch.render(controls, ctx);
    }

    /** mockup: the controls' own card, beside the row's, as tall as it. */
    private void boxCard(RenderContext ctx) {
        int w = boxWidth();
        Flow.drawCard(ctx, getChildX() + getWidth() - w, row.getChildY(), w, row.getHeight());
    }

    /**
     * mockup: the line between the row's content and the controls, the card's inner
     * height (inside the Default card's 1px border, inside the Panel card's border and
     * bevel), in MenuKit's divider style for the look: vanilla's light and dark pair in
     * the Default look, the Panel look's one grey line beside the controls' padding.
     */
    private void dividerLine(RenderContext ctx) {
        var g = ctx.graphics();
        int cx = ctx.originX() + getChildX() + getWidth() - line()[2] - laneWidth();
        int x = cx - DIVIDER_PAD - DIVIDER_W;
        int top = ctx.originY() + row.getChildY(), bottom = top + row.getHeight();
        if (ctx.kind() == ScreenKind.DEFAULT) {
            g.fill(x, top + 1, x + 1, bottom - 1, VanillaLook.SEPARATOR_LIGHT);
            g.fill(x + 1, top + 1, x + 2, bottom - 1, VanillaLook.SEPARATOR_DARK);
        } else {
            g.fill(x + 1, top + 2, x + 2, bottom - 2, Divider.DEFAULT_COLOR);
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
