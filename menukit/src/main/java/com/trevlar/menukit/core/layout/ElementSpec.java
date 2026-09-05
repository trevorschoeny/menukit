package com.trevlar.menukit.core.layout;

import com.trevlar.menukit.core.AbstractPanelElement;
import com.trevlar.menukit.core.PanelElement;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * A factory for a {@link PanelElement} whose final position is computed
 * by an M8 layout helper ({@link Row}, {@link Column}).
 *
 * <p>Decouples element <i>declaration</i> (intrinsic dimensions, content,
 * behavior) from element <i>position</i> (childX, childY). The consumer
 * declares an ElementSpec at construction time; the layout helper computes
 * the position from spacing + alignment policy + sibling dimensions; the
 * helper invokes {@link #at(int, int)} to instantiate the element at its
 * final position.
 *
 * <p>Why deferred construction: {@link PanelElement} declares
 * {@code childX}/{@code childY} as "Fixed at construction; never mutated"
 * (THESIS Principle 4). If layout helpers received pre-constructed
 * elements, they could only either mutate child coordinates (violation)
 * or copy-construct (forces every element type to expose a copy-with-
 * position constructor). ElementSpec is the third path: positions flow
 * through the helper into the element constructor at first instantiation.
 *
 * <p>Library-shipped element types provide static {@code spec(...)}
 * factories returning {@code ElementSpec}. Consumers building custom
 * elements can implement this interface directly or wrap an existing
 * element type via an anonymous instance.
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
     * Construct the element positioned at the given panel-local coordinates.
     * Called once per spec by the layout helper; the returned element's
     * {@code childX}/{@code childY} match the supplied {@code (x, y)}.
     */
    PanelElement at(int childX, int childY);

    /**
     * Wraps an ALREADY-CONSTRUCTED element as an {@link ElementSpec} so it can
     * be dropped into a {@link Row}/{@link Column} (Pass 3). Bridges the common
     * fresh-consumer case — you hold pre-built widgets (constructor-built, or
     * returned from a factory) and want to lay them out, including with
     * {@link CrossAlign#FILL}. The wrapper reports the element's live
     * {@code getWidth()}/{@code getHeight()} and, at layout time, repositions it
     * via {@code setChildPosition} (for the library's {@link AbstractPanelElement}
     * widgets); a bare custom {@link PanelElement} that doesn't extend the base
     * keeps whatever position it was built with.
     */
    static ElementSpec of(PanelElement element) {
        return new ElementSpec() {
            @Override public int width()  { return element.getWidth(); }
            @Override public int height() { return element.getHeight(); }
            @Override public PanelElement at(int x, int y) {
                if (element instanceof AbstractPanelElement<?> a) {
                    a.setChildPosition(x, y);
                    return element;
                }
                // Fail loud, not silent: a bare PanelElement has no reposition
                // hook, so a layout helper cannot place it — rendering it at its
                // built coords would silently overlap siblings.
                throw new IllegalStateException(
                        "ElementSpec.of(): " + element.getClass().getName()
                        + " is a bare PanelElement with no setChildPosition — a Row/"
                        + "Column cannot position it. Extend AbstractPanelElement, or add "
                        + "the element to the panel directly instead of via a layout helper.");
            }
        };
    }

    /**
     * Returns an {@link ElementSpec} identical to this one that ALSO attaches a
     * hover tooltip to the element when the layout helper materializes it. This
     * closes the gap where the {@code .spec()} factories and the {@link Row}/
     * {@link Column} layout path could not carry a tooltip — the chainable
     * {@code .tooltip(...)} setter lives on the concrete element, but on this
     * path the element doesn't exist until {@link #at(int,int)} runs. An
     * immutable decorator: it defers to the underlying spec for size + element
     * construction, then applies the tooltip if the built element supports one
     * (a library {@link AbstractPanelElement}); a bare custom element that
     * doesn't extend the base keeps no tooltip.
     */
    default ElementSpec tooltip(Component text) {
        return tooltip(() -> text);
    }

    /** Supplier-driven variant of {@link #tooltip(Component)}. */
    default ElementSpec tooltip(Supplier<Component> supplier) {
        ElementSpec base = this;
        return new ElementSpec() {
            @Override public int width()  { return base.width(); }
            @Override public int height() { return base.height(); }
            @Override public PanelElement at(int x, int y) {
                PanelElement el = base.at(x, y);
                if (el instanceof AbstractPanelElement<?> a) {
                    a.tooltip(supplier);
                }
                return el;
            }
        };
    }
}
