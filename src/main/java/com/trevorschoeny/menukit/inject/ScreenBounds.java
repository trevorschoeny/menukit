package com.trevorschoeny.menukit.inject;

/**
 * Snapshot of a vanilla {@code AbstractContainerScreen}'s layout bounds for
 * one render or input-dispatch frame. Constructed by the consumer's mixin
 * from the screen's own {@code leftPos}, {@code topPos}, {@code imageWidth},
 * and {@code imageHeight}, then passed to {@link ScreenPanelAdapter} each
 * time it is called.
 *
 * <p>The adapter does not hold these values; it receives them per-call so
 * the adapter stays decoupled from {@code Screen} state. Per-frame resize
 * handling falls out naturally — the mixin reads the current values each
 * time, so no resize listener is needed.
 *
 * @param leftPos     screen-space X of the vanilla screen's container frame
 * @param topPos      screen-space Y of the vanilla screen's container frame
 * @param imageWidth  width of the vanilla screen's container frame
 * @param imageHeight height of the vanilla screen's container frame
 */
public record ScreenBounds(int leftPos, int topPos, int imageWidth, int imageHeight) {
}
