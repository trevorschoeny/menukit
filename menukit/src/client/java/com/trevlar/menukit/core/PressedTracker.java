package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

import java.util.WeakHashMap;

/**
 * Part of MenuKit's one styling exception (see {@code MKVanillaButtonPressedMixin}).
 *
 * <p>Press state for the vanilla-style pressed visual, shared by
 * {@code MKVanillaButtonPressedMixin} (vanilla {@code AbstractButton}),
 * {@code MKYaclWidgetPressedMixin} (YACL widgets, when YACL is loaded) and
 * {@code MKYaclControllerOverlayMixin}, which reads it at render time.
 *
 * <p>A {@code @Unique} field belongs to one mixin target. YACL presses are tracked
 * on the element classes but drawn on {@code ControllerWidget}, so they can't share
 * a field; this map holds the state for all of them.
 *
 * <p>Keys are weak, so widgets from a closed screen drop out when collected.
 * {@link #isPressedAndCheckRelease} clears the whole map once GLFW reports the
 * mouse released, which also drains leftovers from a release off the widget or a
 * screen change mid-press.
 */
public final class PressedTracker {

    private PressedTracker() {}

    private static final WeakHashMap<Object, Boolean> PRESSED = new WeakHashMap<>();

    /** Records a press on the widget, from a mixin at the TAIL of its click handler. */
    public static void markPressed(Object widget) {
        PRESSED.put(widget, Boolean.TRUE);
    }

    /** Clears a widget's press when a release event is available; {@link #isPressedAndCheckRelease} also clears it lazily. */
    public static void clearPressed(Object widget) {
        PRESSED.remove(widget);
    }

    /**
     * Whether the widget is pressed and the left mouse button is still held.
     * If GLFW reports the button released, clears the whole map.
     */
    public static boolean isPressedAndCheckRelease(Object widget) {
        if (!PRESSED.containsKey(widget)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            PRESSED.clear();
            return false;
        }
        long handle = mc.getWindow().handle();
        if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT)
                != GLFW.GLFW_PRESS) {
            PRESSED.clear();
            return false;
        }
        return true;
    }
}
