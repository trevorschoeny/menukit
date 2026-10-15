package com.trevlar.menukit.core;

import com.mojang.blaze3d.platform.InputConstants;

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
 * {@link #isPressedAndCheckRelease} clears the whole map once the left button is
 * released, which also drains leftovers from a release off the widget or a
 * screen change mid-press.
 */
public final class PressedTracker {

    private PressedTracker() {}

    private static final WeakHashMap<Object, Boolean> PRESSED = new WeakHashMap<>();

    // Whether the left mouse button is held. Since 26.3 a mouse button can't be
    // polled, and MouseHandler.isLeftPressed() only follows clicks made with no
    // screen open, so this follows every button event instead
    // (MKModalMouseHandlerMixin feeds it from the head of MouseHandler.onButton).
    private static boolean leftMouseDown;

    // The modifier keys held at the last mouse button event, in vanilla's bit form
    // (InputWithModifiers.modifiers()). An element's click carries only the button, so
    // a hosted vanilla widget (WidgetHost) gets the rest of its MouseButtonEvent from
    // here: shift-click extends an EditBox's selection, say.
    private static int lastModifiers;

    /** Records a mouse button event, before vanilla or MenuKit act on it. */
    public static void onMouseButton(int button, int action, int modifiers) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) leftMouseDown = action == InputConstants.PRESS;
        lastModifiers = modifiers;
    }

    /** The modifier bits of the last mouse button event (0 before any). */
    public static int lastModifiers() {
        return lastModifiers;
    }

    /** Whether the left mouse button is held right now. */
    public static boolean isLeftMouseDown() {
        return leftMouseDown;
    }

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
     * If the button has been released, clears the whole map.
     */
    public static boolean isPressedAndCheckRelease(Object widget) {
        if (!PRESSED.containsKey(widget)) return false;
        if (!leftMouseDown) {
            PRESSED.clear();
            return false;
        }
        return true;
    }
}
