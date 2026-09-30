package com.trevlar.menukit.core;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The one way a list of elements is rendered and receives input (§0066). A
 * panel's host dispatches to the panel's elements through it, and every element
 * that contains others ({@link ScrollContainer}, {@link Flow}, {@link Section},
 * {@link Tabs}) dispatches to its children through it, so the order below is the
 * same at every level.
 *
 * <h3>The order</h3>
 * <ol>
 *   <li><b>Render:</b> every shown child's {@code render} ({@link #render}),
 *       and in a second pass every shown child's {@code renderOverlay}
 *       ({@link #renderOverlay}), so an open popover is on top of every sibling
 *       whatever the declaration order. A host runs both passes; a container runs
 *       the base pass from its {@code render} and forwards the overlay pass from
 *       its own {@code renderOverlay}, so the host's second pass reaches its
 *       children after every base render.</li>
 *   <li><b>Click and wheel:</b> a child whose open overlay (a Dropdown popover)
 *       contains the point gets the event exclusively. Otherwise the first child
 *       whose hit test contains the point and that consumes it.</li>
 *   <li><b>Release:</b> every shown child, not hit-tested, so a drag ends
 *       wherever the cursor is.</li>
 *   <li><b>Key:</b> each shown child in order until one consumes it.</li>
 *   <li><b>Outside click:</b> every shown child, each deciding for itself.</li>
 * </ol>
 *
 * <h3>Disabled</h3>
 *
 * In a disabled context (the {@link InputContext}'s flag: a disabled panel, or a
 * container whose own {@code disabledWhen} holds) clicks, wheel and keys are not
 * delivered at all. Releases and outside clicks are, so a drag or an open popover
 * can still end.
 *
 * <p>Each child receives the context it was given: a container moves the origin
 * to where it positions its children ({@link InputContext#at}) before calling
 * these, and adds its own disabled state ({@link InputContext#disabledIf}).
 * "Shown" is the child's own {@link PanelElement#isVisible()}; a host passes only
 * the elements the window engine also shows.
 */
public final class ChildDispatch {

    private ChildDispatch() {}

    // ── Render ─────────────────────────────────────────────────────────

    /** The base pass: renders every shown child. */
    public static void render(List<? extends PanelElement> children, RenderContext ctx) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.render(ctx);
        }
    }

    /** The overlay pass: every shown child's {@code renderOverlay}, after every base render. */
    public static void renderOverlay(List<? extends PanelElement> children, RenderContext ctx) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.renderOverlay(ctx);
        }
    }

    // ── Queries ────────────────────────────────────────────────────────

    /** Whether any shown child wants an event at the context's point. */
    public static boolean hitTest(List<? extends PanelElement> children, InputContext in) {
        for (PanelElement c : children) {
            if (c.isVisible() && c.hitTest(in)) return true;
        }
        return false;
    }

    /** The first shown child's open overlay, whether or not it contains the point; {@code null} when none is open. */
    public static int @Nullable [] activeOverlay(List<? extends PanelElement> children, InputContext in) {
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            int[] o = c.getActiveOverlayBounds(in);
            if (o != null) return o;
        }
        return null;
    }

    /** The shown child whose open overlay contains the point, or {@code null}. */
    public static @Nullable PanelElement overlayOwner(List<? extends PanelElement> children, InputContext in) {
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            int[] o = c.getActiveOverlayBounds(in);
            if (o != null && in.isInside(o)) return c;
        }
        return null;
    }

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * Routes a click: an open overlay under the point takes it exclusively (it
     * counts as consumed whatever the owner answers, so nothing behind a popover
     * sees it), else the first hit-tested child that consumes it.
     */
    public static boolean mouseClicked(List<? extends PanelElement> children, InputContext in, int button) {
        if (in.disabled()) return false;
        PanelElement owner = overlayOwner(children, in);
        if (owner != null) {
            owner.mouseClicked(in, button);
            return true;
        }
        for (PanelElement c : children) {
            if (!c.isVisible() || !c.hitTest(in)) continue;
            if (c.mouseClicked(in, button)) return true;
        }
        return false;
    }

    /** Routes a wheel scroll, in the same order as {@link #mouseClicked}. */
    public static boolean mouseScrolled(List<? extends PanelElement> children, InputContext in,
                                        double scrollX, double scrollY) {
        if (in.disabled()) return false;
        PanelElement owner = overlayOwner(children, in);
        if (owner != null) {
            owner.mouseScrolled(in, scrollX, scrollY);
            return true;
        }
        for (PanelElement c : children) {
            if (!c.isVisible() || !c.hitTest(in)) continue;
            if (c.mouseScrolled(in, scrollX, scrollY)) return true;
        }
        return false;
    }

    /** Offers a release to every shown child, not hit-tested and not gated by disabled. */
    public static void mouseReleased(List<? extends PanelElement> children, InputContext in, int button) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.mouseReleased(in, button);
        }
    }

    /** Offers a key to each shown child until one consumes it. */
    public static boolean keyPressed(List<? extends PanelElement> children, InputContext in,
                                     int keyCode, int scanCode, int modifiers) {
        if (in.disabled()) return false;
        for (PanelElement c : children) {
            if (c.isVisible() && c.keyPressed(in, keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    /** Tells every shown child about a click, so an open popover it fell outside of closes. */
    public static void notifyClickOutside(List<? extends PanelElement> children, InputContext in) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.notifyClickOutsideOverlay(in);
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    /**
     * Attaches every child, shown or not, as a hidden panel's elements are: a
     * widget-wrapping child registers its vanilla widget once per screen.
     */
    public static void attach(List<? extends PanelElement> children, Screen screen) {
        for (PanelElement c : children) c.onAttach(screen);
    }

    /** Detaches every child. */
    public static void detach(List<? extends PanelElement> children, Screen screen) {
        for (PanelElement c : children) c.onDetach(screen);
    }
}
