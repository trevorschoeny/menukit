package com.trevorschoeny.menukit.core;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Coordinator for a set of {@link Radio} elements that share single-selection
 * state. Holds the currently selected value and fires an {@code onSelect}
 * callback when the selection changes.
 *
 * <p><b>Not a {@link PanelElement}.</b> RadioGroup is a plain state-holding
 * object — consumers construct it once, then pass it to each Radio in the
 * group. Radios render their own checked state by comparing their value
 * against the group's current selection.
 *
 * <p>This coordinator-as-plain-object pattern preserves the library's
 * "Panel is the ceiling of composition" principle — RadioGroup does not
 * contain its Radios, and Radios are not children of the group. They live
 * in panels like any other element; the group is pure wiring.
 *
 * <h3>Mutable state</h3>
 *
 * RadioGroup holds a mutable selection value, a narrow exception to the
 * declared-structure discipline. See {@link Toggle} for the architectural
 * justification; the same rationale applies at the group level here —
 * selection changes do not affect structural shape, only which Radio
 * renders as checked.
 *
 * <h3>Value equality</h3>
 *
 * RadioGroup compares selections via {@link Objects#equals(Object, Object)}.
 * Values should implement {@code equals}/{@code hashCode} (enums do this by
 * default). Null is supported as a valid selection — a group constructed
 * with {@code null} as its initial selection renders no Radio as checked
 * until one is clicked.
 *
 * @param <T> the value type used to identify selections (typically an enum)
 * @see Radio
 */
public class RadioGroup<T> {

    private final Consumer<T> onSelect;

    // Mutable state — the exception documented above.
    private T selected;

    /**
     * @param initialSelection initial selected value (may be null for
     *                         "nothing selected initially")
     * @param onSelect         fired when the selection changes, with the
     *                         new selected value
     */
    public RadioGroup(T initialSelection, Consumer<T> onSelect) {
        this.selected = initialSelection;
        this.onSelect = onSelect;
    }

    /** Returns the currently selected value. May be null. */
    public T getSelected() {
        return selected;
    }

    /**
     * Sets the selection programmatically. Fires {@code onSelect} with the
     * new value if it differs from the current selection (per
     * {@link Objects#equals(Object, Object)}); no-op otherwise.
     */
    public void setSelected(T value) {
        if (Objects.equals(this.selected, value)) return;
        this.selected = value;
        onSelect.accept(value);
    }
}
