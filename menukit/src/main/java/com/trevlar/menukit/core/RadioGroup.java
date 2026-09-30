package com.trevlar.menukit.core;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The lens a set of {@code Radio} buttons shares: which value is selected, read
 * from the consumer every frame and written back when the player picks another
 * (§0026, §0066). It stores nothing.
 *
 * <pre>{@code
 * RadioGroup<Mode> mode = RadioGroup.state(() -> config.mode, m -> config.mode = m);
 * Radio.builder(mode, Mode.FAST).label(Component.literal("Fast")).build();
 * Radio.builder(mode, Mode.SAFE).label(Component.literal("Safe")).at(0, 14).build();
 * }</pre>
 *
 * <p><b>Not an element.</b> The group holds no Radios, and Radios are not its
 * children: each lives in a panel like any other element and asks the group
 * whether its value is the selected one. The group is wiring only, which keeps
 * "a Panel is the ceiling of composition" true.
 *
 * <p>Values are compared with {@link Objects#equals}, so they should implement
 * {@code equals} (enums do). A {@code get} that returns {@code null} selects no
 * Radio.
 *
 * @param <T> the value type (typically an enum)
 */
public final class RadioGroup<T> {

    private final Supplier<T> get;
    private final Consumer<T> set;

    private RadioGroup(Supplier<T> get, Consumer<T> set) {
        this.get = get;
        this.set = set;
    }

    /**
     * The group's lens: {@code get} is read every frame by each Radio to draw
     * itself; {@code set} receives the value of a Radio the player picks, only
     * when it differs from the current selection.
     */
    public static <T> RadioGroup<T> state(Supplier<T> get, Consumer<T> set) {
        return new RadioGroup<>(Objects.requireNonNull(get, "get"), Objects.requireNonNull(set, "set"));
    }

    /** The currently selected value, read from the consumer. May be null. */
    public T selected() {
        return get.get();
    }

    /** Whether {@code value} is the selected one. */
    public boolean isSelected(T value) {
        return Objects.equals(get.get(), value);
    }

    /** Selects {@code value}: hands it to the consumer when it differs from the current selection. */
    public void select(T value) {
        if (!isSelected(value)) set.accept(value);
    }
}
