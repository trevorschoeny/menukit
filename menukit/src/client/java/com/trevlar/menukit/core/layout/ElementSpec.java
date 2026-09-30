package com.trevlar.menukit.core.layout;

import com.trevlar.menukit.core.AbstractPanelElement;
import com.trevlar.menukit.core.PanelElement;

/**
 * An element whose position a layout helper ({@link Row}, {@link Column})
 * computes: its size now, the element itself when {@link #at} is called with the
 * computed position.
 *
 * <p>MenuKit's own elements need no spec: build them with their builder and hand
 * the element to {@code Row.add}/{@code Column.add}, which wraps it through
 * {@link #of} and moves it with {@code setChildPosition} (§0066: the {@code spec()}
 * factories folded into the builders). Implement this interface for a custom
 * element that can only be constructed once its position is known.
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
            @Override public int height() { return element.getHeight(); }
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
