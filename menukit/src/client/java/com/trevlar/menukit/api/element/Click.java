package com.trevlar.menukit.api.element;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * A mouse click as seen by a {@link PanelElement}: which button, plus the
 * modifier keys held at the moment the click was dispatched.
 *
 * <p>Handed to {@link Button#onSecondaryClick} / {@link Toggle#onSecondaryClick}
 * handlers so one handler can branch on right, middle, and shift+right without
 * three separate hooks. The modifier state is sampled from the keyboard when
 * the click reaches the element; {@link PanelElement#mouseClicked} carries only the
 * button index, and dispatch is synchronous inside the input callback, so this
 * is the state at the moment the player clicked. (26.2 dropped the static
 * {@code Screen.hasShiftDown()} helpers, hence the direct key poll.)
 *
 * @param button mouse button, as vanilla numbers it: {@link #LEFT}, {@link #RIGHT},
 *               {@link #MIDDLE}. Since 26.3 these are 1, 3 and 2, not 0, 1 and 2,
 *               so compare against the constants rather than literals
 * @param shift  shift held
 * @param ctrl   control (command on macOS) held
 * @param alt    alt/option held
 */
public record Click(int button, boolean shift, boolean ctrl, boolean alt) {

    public static final int LEFT   = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int RIGHT  = InputConstants.MOUSE_BUTTON_RIGHT;
    public static final int MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;

    /** Captures the click for {@code button} with the current modifier state. */
    public static Click of(int button) {
        return new Click(button,
                held(InputConstants.KEY_LSHIFT) || held(InputConstants.KEY_RSHIFT),
                // Control, or the command key on macOS, matching what vanilla's
                // old Screen.hasControlDown() treated as "control".
                held(InputConstants.KEY_LCONTROL) || held(InputConstants.KEY_RCONTROL)
                        || (MAC && (held(InputConstants.KEY_LGUI) || held(InputConstants.KEY_RGUI))),
                held(InputConstants.KEY_LALT) || held(InputConstants.KEY_RALT));
    }

    // Command counts as control on macOS; plain JDK check so this needs no
    // Minecraft platform class (those moved again in 26.2).
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    private static boolean held(int key) {
        return InputConstants.isKeyDown(key);
    }

    public boolean isLeft()   { return button == LEFT; }
    public boolean isRight()  { return button == RIGHT; }
    public boolean isMiddle() { return button == MIDDLE; }

    /** Right-click with shift held, for example a "pin this mode" gesture. */
    public boolean isShiftRight() { return isRight() && shift; }

    /** Any button other than left. What the secondary-click handlers receive. */
    public boolean isSecondary() { return button != LEFT; }
}
