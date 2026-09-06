package com.trevlar.menukit.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * A mouse click as seen by a {@link PanelElement}: which button, plus the
 * modifier keys held at the moment the click was dispatched.
 *
 * <p>Handed to {@link Button#onSecondaryClick} / {@link Toggle#onSecondaryClick}
 * handlers so one handler can branch on right, middle, and shift+right without
 * three separate hooks. The modifier state is sampled from GLFW when the click
 * reaches the element; {@link PanelElement#mouseClicked} carries only the
 * button index, and dispatch is synchronous inside the input callback, so this
 * is the state at the moment the player clicked. (26.2 dropped the static
 * {@code Screen.hasShiftDown()} helpers, hence the direct key poll.)
 *
 * @param button GLFW mouse button index (0 left, 1 right, 2 middle, ...)
 * @param shift  shift held
 * @param ctrl   control (command on macOS) held
 * @param alt    alt/option held
 */
public record Click(int button, boolean shift, boolean ctrl, boolean alt) {

    public static final int LEFT   = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    public static final int RIGHT  = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    public static final int MIDDLE = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;

    /** Captures the click for {@code button} with the current modifier state. */
    public static Click of(int button) {
        return new Click(button,
                held(GLFW.GLFW_KEY_LEFT_SHIFT) || held(GLFW.GLFW_KEY_RIGHT_SHIFT),
                // Control, or the command key on macOS, matching what vanilla's
                // old Screen.hasControlDown() treated as "control".
                held(GLFW.GLFW_KEY_LEFT_CONTROL) || held(GLFW.GLFW_KEY_RIGHT_CONTROL)
                        || (MAC && (held(GLFW.GLFW_KEY_LEFT_SUPER) || held(GLFW.GLFW_KEY_RIGHT_SUPER))),
                held(GLFW.GLFW_KEY_LEFT_ALT) || held(GLFW.GLFW_KEY_RIGHT_ALT));
    }

    // Command counts as control on macOS; plain JDK check so this needs no
    // Minecraft platform class (those moved again in 26.2).
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    private static boolean held(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public boolean isLeft()   { return button == LEFT; }
    public boolean isRight()  { return button == RIGHT; }
    public boolean isMiddle() { return button == MIDDLE; }

    /** Right-click with shift held — the "pin this mode" gesture IP asked for. */
    public boolean isShiftRight() { return isRight() && shift; }

    /** Any button other than left. What the secondary-click handlers receive. */
    public boolean isSecondary() { return button != LEFT; }
}
