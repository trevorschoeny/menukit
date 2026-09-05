package com.trevorschoeny.menukit.core.layout;

import com.trevorschoeny.menukit.core.PanelElement;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Internal — generalizes "thing the layout can place" so {@link Row} and
 * {@link Column} builders can mix element-specs and nested-helper entries
 * uniformly during the {@code build()} pass.
 *
 * <p>Two construction paths:
 *
 * <ul>
 *   <li>{@link #fromSpec(ElementSpec)} — wraps an {@link ElementSpec};
 *       its {@code emitAt(x, y)} returns a single-element list via
 *       {@link ElementSpec#at(int, int)}.</li>
 *   <li>{@link #fromNested(int, int, BiFunction)} — wraps a nested Row /
 *       Column builder; its {@code emitAt(x, y)} delegates to the nested
 *       builder's translate-and-emit function, which produces the nested
 *       layout's elements with positions adjusted by the outer translation.</li>
 * </ul>
 *
 * <p>Package-private — not part of the public M8 surface. Consumers
 * compose layouts via {@link Row}/{@link Column} builders and never see
 * {@code LayoutEntry}.
 */
final class LayoutEntry {

    private final int width;
    private final int height;
    private final boolean leaf;
    private final BiFunction<Integer, Integer, List<PanelElement>> emitter;

    private LayoutEntry(int width, int height, boolean leaf,
                        BiFunction<Integer, Integer, List<PanelElement>> emitter) {
        this.width = width;
        this.height = height;
        this.leaf = leaf;
        this.emitter = emitter;
    }

    int width() { return width; }
    int height() { return height; }

    /**
     * True for a single-element {@link #fromSpec} entry; false for a nested
     * Row/Column. Pass-3 column-fill stretches only leaf entries — blanket-
     * stretching the leaves of a nested row would override that row's own
     * internal horizontal layout.
     */
    boolean isLeaf() { return leaf; }

    List<PanelElement> emitAt(int x, int y) {
        return emitter.apply(x, y);
    }

    /** Wraps an {@link ElementSpec} as a single-element entry. */
    static LayoutEntry fromSpec(ElementSpec spec) {
        return new LayoutEntry(spec.width(), spec.height(), true,
                (x, y) -> List.of(spec.at(x, y)));
    }

    /**
     * Wraps a nested helper's compute-bounds + translate-emit pair.
     * The nested helper's {@code emitter} should accept the outer-layout
     * origin and return the nested layout's elements with their
     * {@code childX}/{@code childY} computed against that origin.
     */
    static LayoutEntry fromNested(int width, int height,
            BiFunction<Integer, Integer, List<PanelElement>> emitter) {
        return new LayoutEntry(width, height, false, emitter);
    }
}
