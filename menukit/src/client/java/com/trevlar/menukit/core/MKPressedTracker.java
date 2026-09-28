package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

import java.util.WeakHashMap;

/**
 * <b>ACCEPTED aesthetic-only exception to §0019</b> (see
 * {@code MKVanillaButtonPressedMixin} class javadoc for the
 * full carve-out rationale).
 *
 * <p>Shared press-state tracker for the "vanilla-style pressed-visual
 * on every clickable" exploration. Used by:
 *
 * <ul>
 *   <li>{@code MKVanillaButtonPressedMixin} — tracks press on
 *       vanilla {@code AbstractButton} instances</li>
 *   <li>{@code MKYaclWidgetPressedMixin} — tracks press on
 *       YACL {@code AbstractWidget} instances (when YACL is loaded;
 *       silently skips otherwise via {@code @Pseudo})</li>
 *   <li>{@code MKYaclControllerOverlayMixin} — reads the press
 *       state at render time on YACL {@code ControllerWidget}
 *       instances</li>
 * </ul>
 *
 * <h3>Why a shared tracker</h3>
 *
 * Mixin's {@code @Unique} fields are per-mixin-target. The vanilla
 * button mixin can hold its field on AbstractButton. The YACL case
 * needs press tracking on YACL's AbstractWidget but render-overlay
 * on its ControllerWidget subclass — different target classes, can't
 * share a {@code @Unique} field. A WeakHashMap-based external
 * tracker sidesteps that with a single source of truth.
 *
 * <h3>WeakHashMap semantics</h3>
 *
 * Keyed by widget instance (Object reference). Weak keys mean closing
 * a screen and letting widgets be GC'd naturally evicts entries —
 * no per-screen cleanup needed. {@link #isPressedAndCheckRelease}
 * polls GLFW once per call and auto-clears the entire map when the
 * mouse button is released, so stale entries from edge cases
 * (release-off-widget, screen-swap-mid-press) drain on the next
 * query.
 */
public final class MKPressedTracker {

    private MKPressedTracker() {}

    private static final WeakHashMap<Object, Boolean> PRESSED = new WeakHashMap<>();

    /**
     * Records that a click has been received on the given widget.
     * Called from a mixin's TAIL on the widget's click-handling
     * method (typically when the method returns true / claims the
     * click).
     */
    public static void markPressed(Object widget) {
        PRESSED.put(widget, Boolean.TRUE);
    }

    /**
     * Removes a widget's pressed-state entry. Called from a mixin's
     * mouseReleased hook for explicit release tracking (the lazy
     * GLFW-poll cleanup in {@link #isPressedAndCheckRelease} also
     * handles this, but the explicit removal is cleaner when the
     * release event is available).
     */
    public static void clearPressed(Object widget) {
        PRESSED.remove(widget);
    }

    /**
     * Returns true if the widget is currently in the pressed-state
     * map AND the left mouse button is still held (per GLFW poll).
     * Side effect: if GLFW reports the button released, clears the
     * entire PRESSED map — drains stale entries from edge cases like
     * release-off-widget or screen-swap-mid-press.
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
