package com.trevlar.menukit.api.element;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A button that opens a menu of actions (7.3.0): a row's "more" button, with
 * Duplicate and Delete in it. The popover is a {@link Dropdown}'s, but its rows run
 * actions instead of choosing a value, and nothing is ever selected.
 *
 * <pre>{@code
 * MenuButton.builder().size(20, 20)
 *         .label(Component.literal("..."))
 *         .action(Component.literal("Duplicate"), () -> sandboxes.duplicate(id))
 *         .danger(Component.literal("Delete"), () -> sandboxes.delete(id))
 *         .build();
 * }</pre>
 *
 * <h3>The trigger</h3>
 *
 * Its label centred, with no arrow: on an options screen vanilla's button, on a
 * container screen MenuKit's raised one, pressed while the menu is open.
 *
 * <h3>The menu</h3>
 *
 * One row per action, in the order given, as wide as the widest label (at least the
 * trigger's width). A wider menu ends at the trigger's right edge, so a small button at
 * the end of a row opens back over the row; it opens below the trigger, or above when
 * there is no room. A click on a row runs its action and closes the menu, as do Up, Down
 * and Enter. A {@link Builder#danger danger} action's label is red: an action that
 * destroys something. It draws on the overlay pass, so it is on top of everything
 * beside it and is not cut off by a scroll area around it; a click outside it closes
 * it, as does Escape, without closing the screen. No submenus.
 *
 * <p>Like every element it takes {@code at}, {@code visibleWhen}, {@code disabledWhen}
 * (greyed, and the menu does not open), {@code tooltip}, {@code opaque} and
 * {@code declId}. An action that throws is the consumer's error and is not caught.
 */
public final class MenuButton extends PopoverControl<MenuButton.Action> {

    /** Red, for an action that destroys something: the colour a dialog's invalid text has. */
    static final int DANGER = TextField.TEXT_INVALID;

    /**
     * One row of the menu: its label, what it does, and whether it destroys something.
     *
     * @param label  the row's text
     * @param run    what picking it does
     * @param danger drawn red
     */
    public record Action(Component label, Runnable run, boolean danger) {
        public Action {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(run, "run");
        }
    }

    private final Component triggerLabel;

    private MenuButton(Builder b) {
        super(b);
        this.triggerLabel = b.triggerLabel;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override protected Component triggerText() { return triggerLabel; }

    /** Nothing in an action menu is selected. */
    @Override protected boolean isSelected(Action item) { return false; }

    /** Runs the action and closes the menu. */
    @Override
    protected void pick(Action item) {
        close();
        item.run().run();
    }

    @Override protected boolean chevron() { return false; }

    /** As wide as its widest label. */
    @Override protected int popoverWidth() { return widestRow(); }

    @Override
    protected int rowColor(Action item) {
        return item.danger() ? DANGER : super.rowColor(item);
    }

    /** {@link #size} and at least one action are required; {@link #label} is the trigger's text. */
    public static final class Builder extends PopoverControl.Builder<Action, MenuButton, Builder> {
        private Component triggerLabel = Component.literal("...");
        private final List<Action> actions = new ArrayList<>();

        private Builder() {
            label(Action::label);
        }

        @Override protected Builder self() { return this; }

        /** The trigger's text. Default {@code "..."}. */
        public Builder label(Component label) {
            this.triggerLabel = Objects.requireNonNull(label, "label");
            return this;
        }

        /** Adds a row that runs {@code action}. */
        public Builder action(Component label, Runnable action) {
            actions.add(new Action(label, action, false));
            return items(List.copyOf(actions));
        }

        /** Adds a row that runs {@code action}, its label red: an action that destroys something. */
        public Builder danger(Component label, Runnable action) {
            actions.add(new Action(label, action, true));
            return items(List.copyOf(actions));
        }

        @Override
        public MenuButton build() {
            requireCommon();
            return new MenuButton(this);
        }
    }
}
