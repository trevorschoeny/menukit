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
 * Something a {@link Row} or {@link Column} can place, so the builders can mix
 * elements and nested layouts in {@code build()}.
 *
 * <ul>
 *   <li>{@link #fromElement} wraps a built element; its {@code emitAt(x, y)}
 *       moves it there and returns it.</li>
 *   <li>{@link #fromNested} wraps a nested Row or Column builder; its
 *       {@code emitAt(x, y)} returns the nested layout's elements, offset by the
 *       outer position.</li>
 * </ul>
 *
 * <p>Package-private: consumers only see the Row and Column builders.
 */
final class LayoutEntry {

    // Sizes are read when the layout runs, not when the entry is added: a
    // text-bearing element measures with the game's font, which mod init lacks.
    private final IntSupplier width;
    private final IntSupplier height;
    private final boolean leaf;
    private final boolean spacer;
    private final BiFunction<Integer, Integer, List<PanelElement>> emitter;
    // The built elements this entry places, or null when it cannot move them (a bare
    // PanelElement); then the whole layout runs at build, and throws there.
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
     * True for a single-element entry, false for a nested Row or Column. Column
     * FILL stretches only leaf entries: stretching a nested row's leaves would
     * override that row's own layout.
     */
    boolean isLeaf() { return leaf; }

    /** True for {@link #spacer()}: a gap with no element, whose share of the
     *  row's leftover width the caller works out before emitting. */
    boolean isSpacer() { return spacer; }

    List<PanelElement> emitAt(int x, int y) {
        return emitter.apply(x, y);
    }

    /**
     * Wraps a built element, moved into place with {@code setChildPosition} when the
     * layout runs. Its height is its baseline: what it grew by (a wrapped label, a
     * vertical Flow's stack below its first child) is the panel's reflow to add, so
     * the layout counts it once, not twice.
     *
     * <p>A bare {@link PanelElement} (not an {@link AbstractPanelElement}) cannot be
     * moved, so it throws when the layout runs rather than sit at its built position
     * over its siblings.
     */
    static LayoutEntry fromElement(PanelElement element) {
        return new LayoutEntry(element::getWidth, () -> element.getHeight() - element.extraLayoutHeight(),
                true, false, (x, y) -> {
                    if (element instanceof AbstractPanelElement a) {
                        a.setChildPosition(x, y);
                        return List.of(element);
                    }
                    throw new IllegalStateException(
                            "MenuKit: " + element.getClass().getName()
                            + " is a bare PanelElement with no setChildPosition, so a Row or Column cannot "
                            + "position it. Extend AbstractPanelElement, or add the element to the panel directly.");
                },
                element instanceof AbstractPanelElement a ? List.of(a) : null);
    }

    /**
     * Wraps a nested layout's bounds and emitter. The emitter takes the origin in
     * the outer layout and returns the nested elements positioned from it.
     */
    static LayoutEntry fromNested(IntSupplier width, IntSupplier height,
            BiFunction<Integer, Integer, List<PanelElement>> emitter,
            @Nullable List<AbstractPanelElement> members) {
        return new LayoutEntry(width, height, false, false, emitter, members);
    }

    /**
     * A flexible gap: no width of its own, emits nothing, and takes whatever room
     * {@link Row.Builder#width(int)} leaves over. See {@link Row.Builder#addSpacer()}.
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
     * builds at mod init. A layout holding a bare {@link PanelElement} runs now, so
     * it throws at build.
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
