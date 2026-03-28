package com.trevorschoeny.menukit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared, callback-driven capture engine for multi-key keybind recording.
 * Both the YACL settings widget ({@link MKKeybindController.KeybindWidget})
 * and the vanilla Controls screen ({@code MKKeyBindsScreenMixin}) delegate
 * to a single instance of this class during capture sessions.
 *
 * <h3>High Water Mark pattern</h3>
 * <p>Two sets track the capture session:
 * <ul>
 *   <li>{@code heldKeys} -- keys physically held RIGHT NOW</li>
 *   <li>{@code highWaterMark} -- the largest set of simultaneously held keys
 *       seen during this capture session</li>
 * </ul>
 *
 * <p>As keys are pressed, they accumulate in {@code heldKeys}. Whenever
 * {@code heldKeys} grows beyond the previous {@code highWaterMark}, the mark
 * is updated (capped at {@link MKKeybind#MAX_COMBO}). When all keys are
 * released ({@code heldKeys} empties) and the high water mark is non-empty,
 * the binding is finalized via the {@code onFinalize} callback.
 *
 * <h3>Escape and Delete/Backspace</h3>
 * <ul>
 *   <li>Escape ALWAYS cancels capture, even mid-combo (via {@code onCancel})</li>
 *   <li>Delete/Backspace clears the binding to UNBOUND (via {@code onClear})</li>
 * </ul>
 *
 * <h3>GLFW fallback</h3>
 * <p>If no key/mouse events arrive for 2+ seconds while capturing, call
 * {@link #checkGLFWFallback(long)} from the render loop to poll GLFW directly
 * and finalize if all keys are actually released (handles alt-tab, focus loss).
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKKeybindCapture {

    // ── Constants ────────────────────────────────────────────────────────────

    /** How long (ms) without events before GLFW polling kicks in as a fallback. */
    private static final long GLFW_FALLBACK_TIMEOUT_MS = 2000;

    // ── Active Capture Tracking ──────────────────────────────────────────────
    //
    // Static references so the MKKeyMappingMixin can check whether a given
    // KeyMapping is currently being captured (for live preview in
    // getTranslatedKeyMessage). These are set/cleared by the vanilla Controls
    // screen mixin when capture starts/ends.

    /** The KeyMapping currently being captured, or null if no capture is active. */
    public static KeyMapping activeMapping;

    /** The active capture engine, or null if no capture is active. */
    public static MKKeybindCapture activeCapture;

    // ── Capture State ────────────────────────────────────────────────────────

    /** Keys currently physically held down during capture. */
    private final Set<InputConstants.Key> heldKeys = new LinkedHashSet<>();

    /** The largest set of simultaneously held keys seen during this capture
     *  session. This is what gets finalized into the binding. */
    private final Set<InputConstants.Key> highWaterMark = new LinkedHashSet<>();

    /** Whether we're actively capturing key input. */
    private boolean capturing = false;

    /** Timestamp of the last key/mouse event (press or release). Used by the
     *  GLFW polling fallback to detect stale state from alt-tab or focus loss. */
    private long lastEventTime = 0;

    // ── Callbacks ────────────────────────────────────────────────────────────

    /** Called when capture finalizes successfully with a new binding. */
    private final Consumer<MKKeybind> onFinalize;

    /** Called when capture is cancelled (Escape). */
    private final Runnable onCancel;

    /** Called when the high water mark changes (for live preview refresh). */
    private final Runnable onUpdate;

    /** Called when Delete/Backspace clears to UNBOUND. If null, treated as cancel. */
    private final Runnable onClear;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a capture engine with all callbacks.
     *
     * @param onFinalize called with the new MKKeybind when all keys are released
     * @param onCancel   called when Escape cancels capture
     * @param onUpdate   called when the high water mark changes (for live preview)
     * @param onClear    called when Delete/Backspace clears to UNBOUND (may be null)
     */
    public MKKeybindCapture(Consumer<MKKeybind> onFinalize, Runnable onCancel,
                            Runnable onUpdate, Runnable onClear) {
        this.onFinalize = onFinalize;
        this.onCancel = onCancel;
        this.onUpdate = onUpdate;
        this.onClear = onClear;
    }

    /**
     * Creates a capture engine without a separate clear callback.
     * Delete/Backspace will behave as cancel.
     */
    public MKKeybindCapture(Consumer<MKKeybind> onFinalize, Runnable onCancel, Runnable onUpdate) {
        this(onFinalize, onCancel, onUpdate, null);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts a new capture session. Clears all accumulated state.
     */
    public void start() {
        capturing = true;
        heldKeys.clear();
        highWaterMark.clear();
        lastEventTime = System.currentTimeMillis();
    }

    /** Returns true if this engine is currently capturing input. */
    public boolean isCapturing() {
        return capturing;
    }

    /**
     * Returns an unmodifiable snapshot of the current high water mark.
     * Used for live preview rendering.
     */
    public Set<InputConstants.Key> getHighWaterMark() {
        return Set.copyOf(highWaterMark);
    }

    /**
     * Returns a formatted display component for the current capture state.
     * Shows "> ... <" when no keys have been pressed yet, or "> Ctrl+K <"
     * showing the high water mark during/after key presses.
     */
    public Component getPreviewText() {
        if (highWaterMark.isEmpty()) {
            return Component.literal("> ... <")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
        }

        // Build a live preview from the high water mark
        MKKeybind partial = new MKKeybind(highWaterMark);
        String display = partial.getDisplayName().getString();
        return Component.literal("> " + display + " <")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
    }

    // ── Input Handlers (return true if consumed) ─────────────────────────────

    /**
     * Called when a keyboard key is pressed. Adds the key to {@code heldKeys}
     * and updates the high water mark if the held set grew.
     *
     * <p>Escape ALWAYS cancels. Delete/Backspace clears to UNBOUND.
     *
     * @param key the InputConstants.Key that was pressed
     * @return true if the event was consumed
     */
    public boolean onKeyPressed(InputConstants.Key key) {
        if (!capturing) return false;

        int keyCode = key.getValue();

        // Escape ALWAYS cancels, even mid-combo
        if (keyCode == InputConstants.KEY_ESCAPE) {
            cancelCapture();
            return true;
        }

        // Delete/Backspace clears to UNBOUND
        if (keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
            clearCapture();
            return true;
        }

        // Add the key to heldKeys and update high water mark
        heldKeys.add(key);
        lastEventTime = System.currentTimeMillis();
        updateHighWaterMark();

        return true;
    }

    /**
     * Called when a keyboard key is released. Removes the key from
     * {@code heldKeys}. When all keys are released, finalizes the binding
     * from {@code highWaterMark}.
     *
     * @param key the InputConstants.Key that was released
     * @return true if the event was consumed
     */
    public boolean onKeyReleased(InputConstants.Key key) {
        if (!capturing) return false;

        heldKeys.remove(key);
        lastEventTime = System.currentTimeMillis();

        // All keys released -- finalize from high water mark
        if (heldKeys.isEmpty() && !highWaterMark.isEmpty()) {
            finalizeCapture();
        }

        return true;
    }

    /**
     * Called when a mouse button is pressed during capture.
     *
     * @param mouseKey the InputConstants.Key for the mouse button
     * @return true if the event was consumed
     */
    public boolean onMousePressed(InputConstants.Key mouseKey) {
        if (!capturing) return false;

        heldKeys.add(mouseKey);
        lastEventTime = System.currentTimeMillis();
        updateHighWaterMark();

        return true;
    }

    /**
     * Called when a mouse button is released during capture.
     *
     * @param mouseKey the InputConstants.Key for the mouse button
     * @return true if the event was consumed
     */
    public boolean onMouseReleased(InputConstants.Key mouseKey) {
        if (!capturing) return false;

        heldKeys.remove(mouseKey);
        lastEventTime = System.currentTimeMillis();

        if (heldKeys.isEmpty() && !highWaterMark.isEmpty()) {
            finalizeCapture();
        }

        return true;
    }

    // ── GLFW Safety Fallback ─────────────────────────────────────────────────

    /**
     * Safety fallback: if no key/mouse events have arrived for 2+ seconds
     * while capturing, polls GLFW directly to check whether all keys in
     * the high water mark are actually released. This catches alt-tab and
     * focus-loss scenarios where release events are never delivered.
     *
     * <p>Call this from the render loop (every frame) while capturing.
     *
     * @param windowHandle the GLFW window handle
     */
    public void checkGLFWFallback(long windowHandle) {
        if (!capturing || highWaterMark.isEmpty()) return;

        long now = System.currentTimeMillis();
        if ((now - lastEventTime) < GLFW_FALLBACK_TIMEOUT_MS) return;

        // Check all keys from the high water mark
        boolean anyHeld = false;
        for (InputConstants.Key key : highWaterMark) {
            if (isKeyHeld(windowHandle, key)) {
                anyHeld = true;
                break;
            }
        }

        if (!anyHeld) {
            // All keys released but events were lost -- finalize
            heldKeys.clear();
            finalizeCapture();
        }
    }

    /**
     * Active GLFW polling for release detection — call EVERY FRAME from
     * the render loop. Unlike {@link #checkGLFWFallback(long)} which waits
     * 2 seconds, this polls immediately with no timeout.
     *
     * <p>Use this in contexts where {@code keyReleased} events are unreliable
     * (e.g., vanilla KeyBindsScreen where concrete mixin methods may not
     * receive release events). This method:
     * <ol>
     *   <li>Polls GLFW for each key in {@code heldKeys}</li>
     *   <li>Removes any that are no longer physically held</li>
     *   <li>Finalizes if all keys have been released and high water mark is non-empty</li>
     * </ol>
     *
     * @param windowHandle the GLFW window handle
     */
    public void pollReleases(long windowHandle) {
        if (!capturing || heldKeys.isEmpty()) return;

        // Poll each held key and remove released ones
        heldKeys.removeIf(key -> !isKeyHeld(windowHandle, key));

        // All keys released -- finalize from high water mark
        if (heldKeys.isEmpty() && !highWaterMark.isEmpty()) {
            finalizeCapture();
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Updates the high water mark if the current held set is larger than
     * the previous mark. Capped at {@link MKKeybind#MAX_COMBO} keys.
     */
    private void updateHighWaterMark() {
        int effectiveSize = Math.min(heldKeys.size(), MKKeybind.MAX_COMBO);
        if (effectiveSize > highWaterMark.size()) {
            highWaterMark.clear();
            int count = 0;
            for (InputConstants.Key key : heldKeys) {
                if (count >= MKKeybind.MAX_COMBO) break;
                highWaterMark.add(key);
                count++;
            }
            // Notify listener that the preview changed
            onUpdate.run();
        }
    }

    /**
     * Finalizes capture: creates an MKKeybind from the high water mark
     * and invokes the onFinalize callback.
     */
    private void finalizeCapture() {
        MKKeybind newBind = new MKKeybind(highWaterMark);
        resetState();
        onFinalize.accept(newBind);
    }

    /**
     * Cancels capture without changing the binding. Invokes onCancel callback.
     */
    private void cancelCapture() {
        resetState();
        onCancel.run();
    }

    /**
     * Clears to UNBOUND. Invokes onClear callback (or onCancel if no
     * onClear was provided).
     */
    private void clearCapture() {
        resetState();
        if (onClear != null) {
            onClear.run();
        } else {
            onCancel.run();
        }
    }

    /** Resets all internal state to non-capturing. Also clears the static
     *  active capture refs so the mixin stops showing live preview. */
    private void resetState() {
        capturing = false;
        heldKeys.clear();
        highWaterMark.clear();
        lastEventTime = 0;

        // Clear static tracking if this instance was the active capture
        if (activeCapture == this) {
            activeMapping = null;
            activeCapture = null;
        }
    }

    // ── GLFW Polling Helper ──────────────────────────────────────────────────

    /**
     * Returns true if the given key is currently physically held, using GLFW
     * polling. Handles both keyboard keys and mouse buttons.
     */
    private static boolean isKeyHeld(long windowHandle, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(windowHandle, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(windowHandle, key.getValue()) == GLFW.GLFW_PRESS;
    }
}
