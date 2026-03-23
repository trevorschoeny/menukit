package com.trevorschoeny.menukit;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A radio group for {@link MKButton}s in toggle mode.
 *
 * <p>When a button in the group is pressed, all other buttons in the group
 * are automatically unpressed — only one can be active at a time.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * MKButtonGroup tabs = new MKButtonGroup();
 * MKButton tab1 = MKButton.builder().icon(ICON_A).toggle().group(tabs).build();
 * MKButton tab2 = MKButton.builder().icon(ICON_B).toggle().group(tabs).build();
 * // Clicking tab2 automatically unpresses tab1
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public class MKButtonGroup {

    private final List<MKButton> buttons = new ArrayList<>();
    private @Nullable MKButton selected;

    /** Registers a button with this group. Called by {@link MKButton.Builder#group}. */
    void register(MKButton button) {
        if (!buttons.contains(button)) {
            buttons.add(button);
        }
    }

    /** Removes a button from this group. */
    void unregister(MKButton button) {
        buttons.remove(button);
        if (selected == button) selected = null;
    }

    /**
     * Selects the given button, unpressing all others in the group.
     * Called internally by {@link MKButton#onPress} when a toggle button is pressed.
     */
    void select(MKButton button) {
        for (MKButton other : buttons) {
            if (other != button && other.isPressed()) {
                other.setPressed(false);
            }
        }
        selected = button;
    }

    /** Returns the currently selected (pressed) button, or null if none. */
    public @Nullable MKButton getSelected() {
        return selected;
    }

    /** Returns all buttons in this group. */
    public List<MKButton> getButtons() {
        return List.copyOf(buttons);
    }
}
