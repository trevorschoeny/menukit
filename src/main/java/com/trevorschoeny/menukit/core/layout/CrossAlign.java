package com.trevorschoeny.menukit.core.layout;

/**
 * Cross-axis alignment policy for {@link Row} and {@link Column} layout
 * helpers.
 *
 * <p>For a {@link Row} (horizontal main axis), the cross axis is vertical:
 * {@link #START} aligns children to the top edge, {@link #CENTER}
 * vertically centers them within the row's bounding height, {@link #END}
 * aligns to the bottom edge.
 *
 * <p>For a {@link Column} (vertical main axis), the cross axis is
 * horizontal: {@code START} = left, {@code CENTER} = horizontal center,
 * {@code END} = right.
 *
 * <p>Default for both Row and Column is {@code START}, matching the
 * pre-M8 manual-layout pattern (children placed at row-origin / column-
 * origin without cross-axis adjustment). Mixed-height (Row) or
 * mixed-width (Column) consumer cases opt into {@code CENTER} explicitly.
 */
public enum CrossAlign {

    /** Align children to the low edge of the cross axis. */
    START,

    /** Center children on the cross axis. */
    CENTER,

    /** Align children to the high edge of the cross axis. */
    END,

    /**
     * Stretch every FILL-CAPABLE child to the column's cross-axis extent (the
     * width of its single widest child); intrinsic-width children keep their
     * width and align to the column's START (left) edge — so a mixed column
     * reads as full-width controls with left-aligned switches/checkboxes, not
     * "everything the same width" (Pass 3 column-intrinsic-fill). Each
     * fill-capable child (Button, LABELED Toggle, Slider, Dropdown, DropdownMulti, TextField,
     * ProgressBar, ScrollContainer, horizontal Divider) is grown to that width
     * via {@link com.trevorschoeny.menukit.core.PanelElement#fillWidth(int)};
     * auto-content-sized children (TextLabel, Checkbox, Radio, ItemDisplay,
     * Icon, and a BARE/sprite Toggle switch) keep their intrinsic width,
     * left-aligned (their {@code fillWidth} is a no-op by design).
     *
     * <p><b>{@link Column} only.</b> {@code Row} treats FILL as START on its
     * (vertical) cross axis — cross-axis height-fill is not symmetric-cheap
     * (TextLabel can't honor a forced height) and is deliberately deferred.
     */
    FILL
}
