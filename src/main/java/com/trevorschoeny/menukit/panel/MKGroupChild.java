package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.widget.MKButtonDef;
import com.trevorschoeny.menukit.widget.MKSlotDef;
import com.trevorschoeny.menukit.widget.MKTextDef;

import org.jspecify.annotations.Nullable;

/**
 * A child element within an {@link MKGroupDef} layout group.
 *
 * <p>Children are either leaf elements (slots, buttons, text) or nested
 * layout groups. The sealed interface enables exhaustive pattern matching
 * when walking the layout tree.
 *
 * <p>Each child may optionally carry an {@code id} for element-level
 * visibility overrides via {@link MKPanelStateRegistry}.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public sealed interface MKGroupChild
        permits MKGroupChild.Slot, MKGroupChild.Button, MKGroupChild.Text, MKGroupChild.Group,
                MKGroupChild.SlotGroup, MKGroupChild.Spanning, MKGroupChild.Dynamic,
                MKGroupChild.Scroll, MKGroupChild.Tabs {

    /** A slot element. */
    record Slot(MKSlotDef def, @Nullable String id) implements MKGroupChild {}

    /** A button element. */
    record Button(MKButtonDef def, @Nullable String id) implements MKGroupChild {}

    /** A text label element. */
    record Text(MKTextDef def, @Nullable String id) implements MKGroupChild {}

    /** A nested layout group. */
    record Group(MKGroupDef def, @Nullable String id) implements MKGroupChild {}

    /**
     * A slot group -- wraps a {@link MKGroupDef} with container-type metadata.
     * Used to represent a group of slots belonging to a specific container type
     * (e.g., chest slots, hotbar slots). Laid out just like {@link Group} but
     * carries additional type information for the conditional element system.
     *
     * <p>For virtual SlotGroups (vanilla panels with no actual slot defs in
     * the tree), the inner group is empty and contributes zero size to layout.
     */
    record SlotGroup(
            String id,                      // unique identifier (required, not nullable)
            MKContainerType containerType,  // what type of container this provides for
            MKGroupDef group                // the actual group containing the slots
    ) implements MKGroupChild {}

    /** Wraps a child element with column/row span for grid layout. */
    record Spanning(MKGroupChild inner, int colSpan, int rowSpan) implements MKGroupChild {}

    /**
     * A dynamic repeating section. Wraps an {@link MKDynamicGroupDef} that
     * contains a pre-expanded group of template copies. At runtime,
     * {@code activeCount} controls how many copies are visible.
     */
    record Dynamic(MKDynamicGroupDef def, @Nullable String id) implements MKGroupChild {}

    /**
     * A scrollable container with a fixed viewport and scrollable content group.
     * The id is required for scroll offset tracking in {@link MKPanelState}.
     */
    record Scroll(MKScrollDef def, @Nullable String id) implements MKGroupChild {}

    /** A tabbed container with switchable content groups. */
    record Tabs(MKTabsDef def, @Nullable String id) implements MKGroupChild {}

    /**
     * Returns true if this child is disabled at runtime, using only
     * the def's disabledWhen predicate (no panel state override).
     */
    default boolean isDisabled() {
        return switch (this) {
            case Slot s -> s.def().disabledWhen() != null && s.def().disabledWhen().getAsBoolean();
            case Button b -> b.def().disabledWhen() != null && b.def().disabledWhen().getAsBoolean();
            case Text t -> t.def().disabledWhen() != null && t.def().disabledWhen().getAsBoolean();
            case Group g -> g.def().disabledWhen() != null && g.def().disabledWhen().getAsBoolean();
            case SlotGroup sg -> sg.group().disabledWhen() != null && sg.group().disabledWhen().getAsBoolean();
            case Spanning s -> s.inner().isDisabled(); // delegate to wrapped child
            // Dynamic is "disabled" when activeCount <= 0 (all children hidden)
            case Dynamic d -> d.def().activeCount().get() <= 0;
            // Scroll containers are never disabled by themselves
            case Scroll sc -> false;
            // Tabs containers are never disabled by themselves
            case Tabs tb -> false;
        };
    }

    /**
     * Returns true if this child is disabled at runtime, checking the
     * panel's element visibility state first, then falling through to
     * the def's disabledWhen predicate.
     *
     * <p>A visibility override of {@code true} (visible) means NOT disabled.
     * A visibility override of {@code false} (hidden) means disabled.
     * No override means fall through to the def's disabledWhen.
     *
     * @param panelName the panel this child belongs to, or null
     */
    default boolean isDisabled(@Nullable String panelName) {
        // 1. Get element ID from this child
        String elemId = switch (this) {
            case Slot s -> s.id();
            case Button b -> b.id();
            case Text t -> t.id();
            case Group g -> g.id();
            case SlotGroup sg -> sg.id();
            case Spanning s -> null; // spanning has no ID — delegate visibility to inner
            case Dynamic d -> d.id();
            case Scroll sc -> sc.id();
            case Tabs tb -> tb.id();
        };

        // 2. If ID exists and panelName exists, check MKPanelStateRegistry
        if (elemId != null && panelName != null) {
            MKPanelState state = MKPanelStateRegistry.get(panelName);
            if (state != null) {
                Boolean override = state.getVisible(elemId);
                if (override != null) {
                    // visible=true means NOT disabled; visible=false means disabled
                    return !override;
                }
            }
        }

        // 3. Fall through to def's disabledWhen
        return isDisabled();
    }
}
