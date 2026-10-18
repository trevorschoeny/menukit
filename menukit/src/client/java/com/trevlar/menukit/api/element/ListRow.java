package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
 */
final class ListRow extends AbstractPanelElement {

    /** Between the consumer's row and the controls: a button's gap. */
    static final int LANE_GAP = DialogLayout.BUTTON_GAP;
    /** Between two controls: the gap between any two MenuKit buttons side by side. */
    static final int CONTROL_GAP = DialogLayout.BUTTON_GAP;
    /** The row's buttons, all this tall: Edit, and the square icon ones (up, down, remove). */
    static final int CONTROL = 16;

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

    ListRow(PanelElement row, List<PanelElement> controls) {
        this.row = row;
        this.controls = List.copyOf(controls);
        List<PanelElement> all = new ArrayList<>(controls.size() + 1);
        all.add(row);
        all.addAll(controls);
        this.all = List.copyOf(all);
        this.input = List.copyOf(all.reversed());
    }

    private boolean inside() {
        if (inside == null) inside = row instanceof AbstractPanelElement r && r.reserveTrailing(LANE_GAP + laneWidth());
        return inside;
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
        return inside() ? row.naturalWidth() : row.naturalWidth() + LANE_GAP + laneWidth();
    }

    @Override
    public int getWidth() {
        return budget > 0 ? budget : naturalWidth();
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
        row.layoutWithin(inside() ? this.budget : Math.max(1, this.budget - LANE_GAP - laneWidth()));
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
        int cx = x + getWidth() - line()[2] - laneWidth();
        for (PanelElement c : controls) {
            if (c instanceof AbstractPanelElement e) e.setChildPosition(cx, top + (laneH - c.getHeight()) / 2);
            cx += c.getWidth() + CONTROL_GAP;
        }
    }

    // Render and input: the row and its controls share the list's origin.

    @Override
    public void render(RenderContext ctx) {
        ChildDispatch.render(all, ctx);
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
