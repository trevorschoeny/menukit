package com.trevorschoeny.menukit;

/**
 * Accumulates fractional scroll deltas into integer ticks for item-transfer
 * operations. Handles both discrete mice (+1.0 per notch) and trackpads
 * (fractional deltas like 0.2, 0.3).
 *
 * <p>Each consumer should create its own instance — multiple scroll behaviors
 * can coexist without interfering with each other's accumulation state.
 *
 * <p>Direction changes (scrolling up after scrolling down) reset the
 * accumulator, matching user intent: "go the other way now."
 *
 * <p>Part of the <b>MenuKit</b> gesture-to-action framework.
 */
public final class MKScrollAccumulator {

    private double accumulated = 0.0;

    /**
     * Feeds a raw scroll delta and returns the number of integer ticks
     * to consume. Positive = scroll up, negative = scroll down.
     *
     * <p>Direction changes reset the accumulator before adding the new delta.
     * Fractional remainders are preserved across calls in the same direction.
     *
     * @param delta the raw scroll delta from the mouse/trackpad event
     * @return integer ticks to act on (0 if accumulated delta hasn't
     *         reached a full tick yet)
     */
    public int feed(double delta) {
        // Direction reversal resets — user changed intent
        if (accumulated != 0.0 && Math.signum(delta) != Math.signum(accumulated)) {
            accumulated = 0.0;
        }

        accumulated += delta;

        // Extract integer portion, keep fractional remainder
        int ticks = (int) accumulated;
        accumulated -= ticks;

        return ticks;
    }

    /** Resets the accumulator to zero. Call on menu close or context change. */
    public void reset() {
        accumulated = 0.0;
    }
}
