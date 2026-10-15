package com.trevlar.menukit.core;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.layout.Column;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;

import org.jetbrains.annotations.ApiStatus;

/**
 * The one layout every MenuKit dialog uses ({@code ConfirmDialog},
 * {@code AlertDialog}, a {@code ColorField}'s dialog): rows top to bottom, a title,
 * the body, and the button row last, in a centred modal {@link PanelStyle#RAISED}
 * panel.
 *
 * <p>The rows start at the panel's content origin, so the panel's padding is the
 * only margin and it is the same on all four sides. That padding leaves
 * {@link #GAP} between the frame and the rows on either kind of screen (the host
 * widens it where the frame is thicker, {@link Panel#frameExtra}), and the rows are
 * {@link #GAP} apart, so the button row sits centred between the body above it and
 * the frame below it.
 *
 * <p>A row made with {@link #centred} is centred across the dialog at whatever width
 * it ends up (a {@link Flow} with a spacer at each end), so it stays centred when
 * another row's width changes at runtime (a Tabs on an options screen's page is
 * wider than on a container screen). Any other row (a Tabs) is laid out to the
 * dialog's full width.
 */
@ApiStatus.Internal
public final class DialogLayout {

    /**
     * Room between the frame and the rows, and between one row and the next. The
     * panel's padding less MenuKit's 3 px frame ({@link Panel#INTERIOR_PADDING} - 3).
     */
    public static final int GAP = Panel.INTERIOR_PADDING - 3;
    /** Between two buttons in the button row. */
    public static final int BUTTON_GAP = 4;
    /** A dialog button's size, vanilla's confirm-screen buttons. */
    public static final int BUTTON_W = 60;
    public static final int BUTTON_H = 20;

    private DialogLayout() {}

    /** The elements in one row, centred across the dialog's width. */
    public static PanelElement centred(PanelElement... elements) {
        Flow.Builder row = Flow.builder().gap(BUTTON_GAP).add(Flow.spacer());
        for (PanelElement e : elements) row.add(e);
        return row.add(Flow.spacer()).build();
    }

    /** The button row: the buttons side by side, centred. */
    public static PanelElement buttons(Button... buttons) {
        return centred(buttons);
    }

    /**
     * The dialog: {@code rows} stacked from the top, {@link #GAP} apart, in a centred
     * modal RAISED panel, hidden until shown, Escape running {@code onEscape}.
     */
    public static Panel panel(String id, Runnable onEscape, PanelElement... rows) {
        Column.Builder column = Column.at(0, 0).spacing(GAP);
        for (PanelElement row : rows) column.add(row);
        return Panel.builder(id)
                .elements(column.build())
                .visible(false)
                .style(PanelStyle.RAISED)
                .position(PanelPosition.center())
                .build()
                .modal()
                .onEscape(onEscape);
    }
}
