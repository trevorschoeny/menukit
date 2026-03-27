package com.trevorschoeny.menukit;

import java.util.function.Supplier;

/**
 * Defines a dynamic repeating section within a layout group.
 *
 * <p>At build time, the template element is expanded into {@code maxItems}
 * copies, each with a {@code disabledWhen} predicate that checks
 * {@code index >= activeCount.get()}. The expanded children live inside
 * {@code expandedGroup} — a regular {@link MKGroupDef} that the layout
 * engine processes like any other nested group.
 *
 * <p>At runtime, {@code activeCount} controls how many copies are visible.
 * Disabled copies are marked inactive and excluded from flow layout,
 * so the panel shrinks/grows dynamically without runtime slot injection.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public record MKDynamicGroupDef(
        int maxItems,
        MKGroupChild template,
        Supplier<Integer> activeCount,
        MKGroupDef.LayoutMode layoutMode,
        int gap,
        int cellSize,
        int maxRows,
        MKGroupDef expandedGroup    // pre-expanded group with maxItems children + disabledWhen predicates
) {}
