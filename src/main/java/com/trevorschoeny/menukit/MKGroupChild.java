package com.trevorschoeny.menukit;

/**
 * A child element within an {@link MKGroupDef} layout group.
 *
 * <p>Children are either leaf elements (slots, buttons, text) or nested
 * layout groups. The sealed interface enables exhaustive pattern matching
 * when walking the layout tree.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public sealed interface MKGroupChild
        permits MKGroupChild.Slot, MKGroupChild.Button, MKGroupChild.Text, MKGroupChild.Group {

    /** A slot element. */
    record Slot(MKSlotDef def) implements MKGroupChild {}

    /** A button element. */
    record Button(MKButtonDef def) implements MKGroupChild {}

    /** A text label element. */
    record Text(MKTextDef def) implements MKGroupChild {}

    /** A nested layout group. */
    record Group(MKGroupDef def) implements MKGroupChild {}

    /** Returns true if this child is disabled at runtime. */
    default boolean isDisabled() {
        return switch (this) {
            case Slot s -> s.def().disabledWhen() != null && s.def().disabledWhen().getAsBoolean();
            case Button b -> b.def().disabledWhen() != null && b.def().disabledWhen().getAsBoolean();
            case Text t -> t.def().disabledWhen() != null && t.def().disabledWhen().getAsBoolean();
            case Group g -> g.def().disabledWhen() != null && g.def().disabledWhen().getAsBoolean();
        };
    }
}
