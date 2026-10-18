package com.trevlar.menukit.api.layout;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.PanelElement;

/**
 * An element whose position a layout helper ({@link Row}, {@link Column})
 * computes: its size now, the element itself when {@link #at} is called with the
 * computed position.
 *
 * <p>MenuKit's own elements need no spec: build them with their builder and hand
 * the element to {@code Row.add}/{@code Column.add}, which wraps it through
 * {@link #of} and moves it with {@code setChildPosition}. Implement this interface for a custom
 * element that can only be constructed once its position is known; a Row or
 * Column holding one is laid out in {@code build()}, so build it where every
 * element in it can be measured (a screen's {@code init}, not mod init).
 *
 * @see Row
 * @see Column
 */
public interface ElementSpec {

    /** Width of the element in pixels (panel-local). */
    int width();

    /** Height of the element in pixels (panel-local). */
    int height();

    /**
     * The element positioned at the given panel-local coordinates. Called once
     * per spec by the layout helper.
     */
    PanelElement at(int childX, int childY);

    /**
     * An already-built element as a spec: reports its live size and, at layout
     * time, moves it with {@code setChildPosition}. What {@code Row.add(element)}
     * and {@code Column.add(element)} use.
     *
     * @throws IllegalStateException at layout time for an element that cannot be
     *         moved (a bare {@link PanelElement}, not an {@link AbstractPanelElement}):
     *         placing it silently at its built position would overlap its siblings
     */
    static ElementSpec of(PanelElement element) {
        return new ElementSpec() {
            @Override public int width()  { return element.getWidth(); }
            // The element's baseline height: what it grew by (a wrapped label, a vertical
            // Flow's stack below its first child) is the panel's reflow to add, so the
            // layout counts it once, not twice (7.3.0: a vertical Flow in a dialog left a
            // gap of its own growth under it).
            @Override public int height() { return element.getHeight() - element.extraLayoutHeight(); }
            @Override public PanelElement at(int x, int y) {
                if (element instanceof AbstractPanelElement a) {
                    a.setChildPosition(x, y);
                    return element;
                }
                throw new IllegalStateException(
                        "ElementSpec.of(): " + element.getClass().getName()
                        + " is a bare PanelElement with no setChildPosition, so a Row or Column cannot "
                        + "position it. Extend AbstractPanelElement, implement ElementSpec, or add the "
                        + "element to the panel directly.");
            }
        };
    }
}
