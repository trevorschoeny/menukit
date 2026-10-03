package com.trevlar.menukit.api.layout;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.PanelElement;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Internal, generalizes "thing the layout can place" so {@link Row} and
 * {@link Column} builders can mix element-specs and nested-helper entries
 * uniformly during the {@code build()} pass.
 *
 * <p>Two construction paths:
 *
 * <ul>
 *   <li>{@link #fromSpec(ElementSpec)}, wraps an {@link ElementSpec};
 *       its {@code emitAt(x, y)} returns a single-element list via
 *       {@link ElementSpec#at(int, int)}.</li>
 *   <li>{@link #fromNested(int, int, BiFunction)}, wraps a nested Row /
 *       Column builder; its {@code emitAt(x, y)} delegates to the nested
 *       builder's translate-and-emit function, which produces the nested
 *       layout's elements with positions adjusted by the outer translation.</li>
 * </ul>
 *
 * <p>Package-private, not part of the public M8 surface. Consumers
 * compose layouts via {@link Row}/{@link Column} builders and never see
 * {@code LayoutEntry}.
 */
final class LayoutEntry {

    // Sizes are read when the layout runs, not when the entry is added: a
    // text-bearing element measures with the game's font, which mod init lacks.
    private final IntSupplier width;
    private final IntSupplier height;
    private final boolean leaf;
    private final boolean spacer;
    private final BiFunction<Integer, Integer, List<PanelElement>> emitter;
    /** The built elements this entry places, or null when it constructs them at
     *  layout (a custom {@link ElementSpec}) or cannot move them (a bare
     *  {@link PanelElement}): then the whole layout runs at build. */
    private final @Nullable List<AbstractPanelElement> members;

    private LayoutEntry(IntSupplier width, IntSupplier height, boolean leaf, boolean spacer,
                        BiFunction<Integer, Integer, List<PanelElement>> emitter,
                        @Nullable List<AbstractPanelElement> members) {
        this.width = width;
        this.height = height;
        this.leaf = leaf;
        this.spacer = spacer;
        this.emitter = emitter;
        this.members = members;
    }

    int width() { return width.getAsInt(); }
    int height() { return height.getAsInt(); }

    /**
     * True for a single-element {@link #fromSpec} entry; false for a nested
     * Row/Column. Pass-3 column-fill stretches only leaf entries, blanket-
     * stretching the leaves of a nested row would override that row's own
     * internal horizontal layout.
     */
    boolean isLeaf() { return leaf; }

    /** True for {@link #spacer()}, a gap with no element, whose share of the
     *  row's leftover width is resolved by the caller before emitting. */
    boolean isSpacer() { return spacer; }

    List<PanelElement> emitAt(int x, int y) {
        return emitter.apply(x, y);
    }

    /** Wraps a custom {@link ElementSpec}, which constructs its element at layout. */
    static LayoutEntry fromSpec(ElementSpec spec) {
        return new LayoutEntry(spec::width, spec::height, true, false,
                (x, y) -> List.of(spec.at(x, y)), null);
    }

    /** Wraps an already-built element, moved into place when the layout runs. */
    static LayoutEntry fromElement(PanelElement element) {
        ElementSpec spec = ElementSpec.of(element);
        return new LayoutEntry(spec::width, spec::height, true, false,
                (x, y) -> List.of(spec.at(x, y)),
                element instanceof AbstractPanelElement a ? List.of(a) : null);
    }

    /**
     * Wraps a nested helper's bounds and translate-emit pair. The nested helper's
     * {@code emitter} accepts the outer-layout origin and returns the nested
     * layout's elements with their {@code childX}/{@code childY} computed against
     * that origin.
     */
    static LayoutEntry fromNested(IntSupplier width, IntSupplier height,
            BiFunction<Integer, Integer, List<PanelElement>> emitter,
            @Nullable List<AbstractPanelElement> members) {
        return new LayoutEntry(width, height, false, false, emitter, members);
    }

    /**
     * A flexible gap: zero intrinsic width, emits nothing, and stands for
     * "whatever room {@link Row.Builder#width(int)} leaves over", see
     * {@link Row.Builder#addSpacer()}.
     */
    static LayoutEntry spacer() {
        return new LayoutEntry(() -> 0, () -> 0, false, true, (x, y) -> List.of(), List.of());
    }

    /** Every built element the entries place, in order, or null if any entry has none to give. */
    static @Nullable List<AbstractPanelElement> members(List<LayoutEntry> entries) {
        List<AbstractPanelElement> all = new ArrayList<>();
        for (LayoutEntry e : entries) {
            if (e.members == null) return null;
            all.addAll(e.members);
        }
        return all;
    }

    /**
     * What {@code Row.build()} and {@code Column.build()} return: the placed
     * elements, placed on the first read of any one's position (the panel's first
     * layout pass, when the font exists), so a Row or Column of labels and buttons
     * builds at mod init. A layout with a custom {@link ElementSpec}, whose element
     * exists only once placed, runs now, measuring now.
     */
    static List<PanelElement> placeOnFirstRead(List<LayoutEntry> entries, Supplier<List<PanelElement>> layout) {
        List<AbstractPanelElement> members = members(entries);
        if (members == null) return layout.get();
        Runnable once = new Runnable() {
            private boolean ran;
            @Override public void run() {
                if (ran) return;   // one layout for the whole row, however many members are read
                ran = true;
                layout.get();
            }
        };
        for (AbstractPanelElement m : members) m.placeOnFirstRead(once);
        return new ArrayList<>(members);
    }
}
