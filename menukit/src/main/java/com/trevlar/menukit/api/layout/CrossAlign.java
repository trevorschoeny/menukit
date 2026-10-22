package com.trevlar.menukit.api.layout;

/**
 * Cross-axis alignment for {@link Row} and {@link Column}.
 *
 * <p>In a {@link Row} the cross axis is vertical: {@link #START} aligns children
 * to the top, {@link #CENTER} centers them in the row's height, {@link #END}
 * aligns them to the bottom.
 *
 * <p>In a {@link Column} the cross axis is horizontal: {@code START} is left,
 * {@code CENTER} the horizontal center, {@code END} right.
 *
 * <p>Both default to {@code START}, which places children at the row or column
 * origin with no cross-axis adjustment. A row of mixed heights or a column of
 * mixed widths opts into {@code CENTER}.
 */
public enum CrossAlign {

    /** Align children to the low edge of the cross axis. */
    START,

    /** Center children on the cross axis. */
    CENTER,

    /** Align children to the high edge of the cross axis. */
    END,

    /**
     * Stretches every child that can fill to the column's width (the width of its
     * widest child). Children with a natural width keep it and align left, so a
     * mixed column reads as full-width controls with left-aligned switches and
     * checkboxes, not everything at one width. The children that fill (Button, a
     * labeled Toggle, Slider, Dropdown, DropdownMulti, TextField, ProgressBar,
     * ScrollContainer, a horizontal Divider) grow through
     * {@link com.trevlar.menukit.api.element.PanelElement#fillWidth(int)}. Those
     * sized by their content (TextLabel, Checkbox, Radio, ItemDisplay, Icon, and a
     * bare sprite Toggle) keep their width, left-aligned; their {@code fillWidth}
     * does nothing.
     *
     * <p>{@link Column} only. {@code Row} treats FILL as START: filling height is
     * not as cheap (a TextLabel can't take a forced height), so it is left out.
     */
    FILL
}
