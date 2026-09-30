package com.trevlar.menukit.api.element;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single-selection dropdown: a trigger showing the current choice, and a
 * popover list to pick another from. A lens onto the consumer's value (§0026,
 * §0066).
 *
 * <pre>{@code
 * Dropdown.<GameMode>builder().size(120, 20)
 *         .items(List.of(GameMode.values()))
 *         .label(m -> Component.literal(m.displayName()))
 *         .state(() -> mode, m -> mode = m)
 *         .build();
 *
 * // the integer and enum lenses
 * Dropdown.ofInts(1, 10).size(60, 16).state(config::rows, config::setRows).build();
 * Dropdown.ofEnum(Speed.class).size(90, 16).state(config::speed, config::setSpeed).build();
 * }</pre>
 *
 * <h3>The lens</h3>
 *
 * {@code state(get, set)} is required: the trigger shows the label of what
 * {@code get} returns (empty for {@code null}) every frame; picking another item
 * hands it to {@code set} and closes the popover. Picking the current item
 * writes nothing.
 *
 * <h3>The popover</h3>
 *
 * Opens below the trigger, or above when there is no room below, kept on screen
 * horizontally; scrolls past {@code maxVisibleItems}. It draws on the overlay
 * pass, so it is on top of every sibling, and claims its area exclusively while
 * open, so nothing behind it reacts. A click outside it closes it. While open,
 * Up and Down highlight a row, Enter picks it, Escape closes the popover. Its
 * open, scroll and highlight state are the element's own view state. See
 * {@link PopoverControl} for what both dropdowns share.
 *
 * @param <T> the value type; identity by {@code equals}
 */
public final class Dropdown<T> extends PopoverControl<T> {

    private final Supplier<@Nullable T> stateGet;
    private final Consumer<T> stateSet;

    private Dropdown(Builder<T> b) {
        super(b);
        this.stateGet = b.stateGet;
        this.stateSet = b.stateSet;
    }

    /** A dropdown over items you give it. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** The integer lens: the whole numbers {@code min} to {@code max} inclusive, labelled with themselves. */
    public static Builder<Integer> ofInts(int min, int max) {
        if (max < min) throw new IllegalArgumentException("Dropdown.ofInts: max below min, " + min + ".." + max);
        List<Integer> values = new ArrayList<>(max - min + 1);
        for (int i = min; i <= max; i++) values.add(i);
        return new Builder<Integer>().items(values);
    }

    /** The enum lens: every constant of {@code type}, in declaration order. */
    public static <E extends Enum<E>> Builder<E> ofEnum(Class<E> type) {
        return new Builder<E>().items(List.of(type.getEnumConstants()));
    }

    @Override
    protected Component triggerText() {
        T current = stateGet.get();
        return current != null ? label.apply(current) : Component.empty();
    }

    @Override
    protected boolean isSelected(T item) {
        return Objects.equals(stateGet.get(), item);
    }

    /** Hands the pick to the consumer when it differs, and closes. */
    @Override
    protected void pick(T item) {
        if (!isSelected(item)) stateSet.accept(item);
        close();
    }

    public static final class Builder<T> extends PopoverControl.Builder<T, Dropdown<T>, Builder<T>> {
        private @Nullable Supplier<@Nullable T> stateGet;
        private @Nullable Consumer<T> stateSet;

        private Builder() {}

        @Override protected Builder<T> self() { return this; }

        /**
         * Required: the lens. {@code get} is read every frame (a {@code null} shows
         * an empty trigger); {@code set} receives a picked item that differs from it.
         */
        public Builder<T> state(Supplier<@Nullable T> get, Consumer<T> set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        @Override
        public Dropdown<T> build() {
            requireCommon();
            require(stateGet != null, "state(get, set) is required; a Dropdown shows the consumer's value");
            return new Dropdown<>(this);
        }
    }
}
