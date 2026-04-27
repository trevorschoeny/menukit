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
    END
}
