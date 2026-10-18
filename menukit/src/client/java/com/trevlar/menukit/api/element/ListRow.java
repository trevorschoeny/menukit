package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of an editing {@link ListView} (7.3.0): the consumer's row, and the list's
 * controls for it (Edit, the move arrows, remove) in a lane at its trailing
 * edge. Package-private: the list makes one per key only when it has a control to put
 * there, so a list with none keeps the consumer's row as it is.
 *
 * <h3>The lane</h3>
 *
 * The consumer's row gets the width less the lane, so its own trailing content
 * (a spacer and a control) ends where the lane starts. Each control keeps its slot
 * whether or not it shows (a pinned row has no arrows and no remove button), so every
 * row of the list is the same width and the lane's controls line up in columns.
 *
 * <p>The lane is centred on the row's first line, not its whole height: the row's
 * height less what it grew by ({@link PanelElement#extraLayoutHeight}), so a
 * {@link Section} row that opens keeps its controls beside its header. A row shorter
 * than the lane grows to hold it.
 */
final class ListRow extends AbstractPanelElement {

    /** Between the consumer's row and the lane: a Flow's gap. */
    static final int LANE_GAP = Flow.DEFAULT_GAP;
    /** Between two controls in the lane: the gap between any two MenuKit buttons side by side. */
    static final int CONTROL_GAP = DialogLayout.BUTTON_GAP;
    /** The lane's buttons, all this tall: Edit, and the square icon ones (up, down, remove). */
    static final int CONTROL = 16;

    private final PanelElement row;
    private final List<PanelElement> controls;
    private final List<PanelElement> all;

    /** The width the list gives the row; until the first pass, its natural width. */
    private int budget = -1;

    ListRow(PanelElement row, List<PanelElement> controls) {
        this.row = row;
        this.controls = List.copyOf(controls);
        List<PanelElement> all = new ArrayList<>(controls.size() + 1);
        all.add(row);
        all.addAll(controls);
        this.all = List.copyOf(all);
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

    /** The lane's top, from the row's: centred on the row's first line, never above the row. */
    private int laneTop() {
        int firstLine = row.getHeight() - Math.max(0, row.extraLayoutHeight());
        return Math.max(0, (firstLine - laneHeight()) / 2);
    }

    @Override
    public int naturalWidth() {
        return row.naturalWidth() + LANE_GAP + laneWidth();
    }

    @Override
    public int getWidth() {
        return budget > 0 ? budget : naturalWidth();
    }

    @Override
    public int getHeight() {
        return Math.max(row.getHeight(), laneTop() + laneHeight());
    }

    /** What the consumer's row grew by, so a row inside a row still reports it. */
    @Override
    public int extraLayoutHeight() {
        return row.extraLayoutHeight();
    }

    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
        row.layoutWithin(Math.max(1, this.budget - LANE_GAP - laneWidth()));
    }

    @Override
    public void fillWidth(int width) {
        layoutWithin(width);
    }

    /** Places the row at the left and the lane's controls at the trailing edge, each centred in the lane. */
    @Override
    public void setChildPosition(int x, int y) {
        super.setChildPosition(x, y);
        if (row instanceof AbstractPanelElement r) r.setChildPosition(x, y);
        int top = y + laneTop(), laneH = laneHeight();
        int cx = x + getWidth() - laneWidth();
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
        return ChildDispatch.hitTest(all, in);
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        return ChildDispatch.mouseClicked(all, in, button);
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        return ChildDispatch.mouseScrolled(all, in, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(all, in, button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(all, in, keyCode, scanCode, modifiers);
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return ChildDispatch.activeOverlay(all, in);
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
