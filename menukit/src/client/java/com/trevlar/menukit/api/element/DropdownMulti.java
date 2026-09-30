package com.trevlar.menukit.api.element;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A multi-selection dropdown: a trigger summarising the chosen set, and a
 * popover list where each row toggles its item. A lens onto the consumer's set
 * (§0026, §0066).
 *
 * <pre>{@code
 * DropdownMulti.<String>builder().size(160, 20)
 *         .items(List.of("Apple", "Banana", "Cherry"))
 *         .triggerLabel(set -> Component.literal(set.isEmpty() ? "None" : set.size() + " selected"))
 *         .state(() -> chosen, next -> chosen = next)
 *         .selectAllRow(Component.literal("Select all"))
 *         .clearAllRow(Component.literal("Clear all"))
 *         .build();
 * }</pre>
 *
 * <h3>The lens</h3>
 *
 * {@code state(get, set)} is required and speaks whole sets: a click on a row
 * hands {@code set} a new set with that item toggled; the pinned "Select all"
 * and "Clear all" rows hand it every item, or none. The consumer's set is never
 * mutated. Rows show a check mark and a highlight for the items {@code get}
 * contains, every frame.
 *
 * <p>The popover stays open across picks, so several items can be toggled in one
 * go; the trigger, Escape or a click outside closes it. Enter or Space toggles
 * the arrow-highlighted row. Everything else is as {@link Dropdown}; see
 * {@link PopoverControl}.
 *
 * @param <T> the item type; identity by {@code equals}
 */
public final class DropdownMulti<T> extends PopoverControl<T> {

    private static final int CHECKMARK_W = 9;
    private static final int CHECKMARK_H = 8;
    /** The check column: the mark and 3px before the text. */
    private static final int CHECK_COLUMN = CHECKMARK_W + 3;

    private final Supplier<Set<T>> stateGet;
    private final Consumer<Set<T>> stateSet;
    private final Function<Set<T>, Component> triggerLabel;
    private final @Nullable Component selectAllLabel;
    private final @Nullable Component clearAllLabel;

    private DropdownMulti(Builder<T> b) {
        super(b);
        this.stateGet = b.stateGet;
        this.stateSet = b.stateSet;
        this.triggerLabel = b.triggerLabel;
        this.selectAllLabel = b.selectAllLabel;
        this.clearAllLabel = b.clearAllLabel;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    protected Component triggerText() {
        return triggerLabel.apply(Set.copyOf(stateGet.get()));
    }

    @Override
    protected boolean isSelected(T item) {
        return stateGet.get().contains(item);
    }

    /** Hands the consumer a new set with {@code item} toggled; the popover stays open. */
    @Override
    protected void pick(T item) {
        Set<T> next = new LinkedHashSet<>(stateGet.get());
        if (!next.remove(item)) next.add(item);
        stateSet.accept(Set.copyOf(next));
    }

    // ── Pinned rows: Select all, then Clear all, when configured ───────

    @Override
    protected int pinnedRowCount() {
        return (selectAllLabel != null ? 1 : 0) + (clearAllLabel != null ? 1 : 0);
    }

    @Override
    protected Component pinnedLabel(int index) {
        return index == 0 && selectAllLabel != null ? selectAllLabel : Objects.requireNonNull(clearAllLabel);
    }

    @Override
    protected void pickPinned(int index) {
        boolean selectAll = index == 0 && selectAllLabel != null;
        stateSet.accept(selectAll ? Set.copyOf(items) : Set.of());
    }

    // ── Check column ───────────────────────────────────────────────────

    @Override
    protected int checkColumnWidth() {
        return CHECK_COLUMN;
    }

    @Override
    protected void drawCheck(GuiGraphicsExtractor g, int x, int rowY) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Checkbox.CHECKMARK_SPRITE,
                x, rowY + (ROW_HEIGHT - CHECKMARK_H) / 2, CHECKMARK_W, CHECKMARK_H);
    }

    public static final class Builder<T> extends PopoverControl.Builder<T, DropdownMulti<T>, Builder<T>> {
        private @Nullable Supplier<Set<T>> stateGet;
        private @Nullable Consumer<Set<T>> stateSet;
        private @Nullable Function<Set<T>, Component> triggerLabel;
        private @Nullable Component selectAllLabel;
        private @Nullable Component clearAllLabel;

        private Builder() {}

        @Override protected Builder<T> self() { return this; }

        /**
         * Required: the lens over the chosen set. {@code get} is read every frame;
         * {@code set} receives the whole new set after each toggle. Neither set is
         * mutated.
         */
        public Builder<T> state(Supplier<Set<T>> get, Consumer<Set<T>> set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        /** Required: the trigger's summary of the chosen set ("3 selected", a joined list). */
        public Builder<T> triggerLabel(Function<Set<T>, Component> summary) {
            this.triggerLabel = Objects.requireNonNull(summary, "summary");
            return this;
        }

        /** A pinned row that chooses every item. */
        public Builder<T> selectAllRow(Component label) {
            this.selectAllLabel = Objects.requireNonNull(label, "label");
            return this;
        }

        /** A pinned row that chooses none. */
        public Builder<T> clearAllRow(Component label) {
            this.clearAllLabel = Objects.requireNonNull(label, "label");
            return this;
        }

        @Override
        public DropdownMulti<T> build() {
            requireCommon();
            require(triggerLabel != null, "triggerLabel(...) is required: the consumer decides the summary");
            require(stateGet != null, "state(get, set) is required; a DropdownMulti shows the consumer's set");
            return new DropdownMulti<>(this);
        }
    }
}
