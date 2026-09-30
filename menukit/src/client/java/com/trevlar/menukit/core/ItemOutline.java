package com.trevlar.menukit.core;

import com.trevlar.menukit.api.element.SlotRendering;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The bookkeeping behind {@link SlotRendering#drawItemOutline}: which of this
 * frame's item draws are silhouettes, and in what colour.
 *
 * <h3>Why two steps</h3>
 *
 * In 26.2 an item is drawn in two phases. While a screen extracts its render
 * state, {@code GuiGraphicsExtractor.item(...)} only records a
 * {@link GuiItemRenderState}; later the {@code GuiRenderer} renders every
 * recorded item into its item atlas and blits each one from there. The item's
 * pixels exist only at that second step. So an outline is marked at the first
 * (the states recorded inside {@link #silhouettes}) and drawn at the second:
 * {@code MKItemOutlineMarkMixin} records the mark as the state is added, and
 * {@code MKItemOutlineDrawMixin} blits a marked state through
 * {@link MKRenderPipelines#GUI_SILHOUETTE} in its colour instead of normally.
 *
 * <p>Ordering needs nothing extra: vanilla's render state puts an element that
 * overlaps an earlier one above it, so the item drawn after its four silhouettes
 * lands on top of them.
 *
 * <p>Render thread only. The marks are weak, so states a frame discards are
 * collected with it.
 */
@ApiStatus.Internal
public final class ItemOutline {

    private ItemOutline() {}

    private static final Map<GuiItemRenderState, Integer> MARKED = Collections.synchronizedMap(new WeakHashMap<>());

    /** The colour items recorded right now are marked with; 0 when none. */
    private static int pending = 0;

    /** Runs {@code draws} with every item it records marked as a silhouette in {@code argb}. */
    public static void silhouettes(int argb, Runnable draws) {
        int previous = pending;
        pending = argb;
        try {
            draws.run();
        } finally {
            pending = previous;
        }
    }

    /** Called as an item state is recorded: marks it when a silhouette is being drawn. */
    public static void mark(GuiItemRenderState state) {
        if (pending != 0) MARKED.put(state, pending);
    }

    /** The silhouette colour of {@code state}, removing the mark; 0 for an ordinary item. */
    public static int take(GuiItemRenderState state) {
        Integer argb = MARKED.remove(state);
        return argb == null ? 0 : argb;
    }
}
