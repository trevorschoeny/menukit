package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The one way a list of elements is rendered and receives input. A
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
 *       children after every base render. A shown child skipped because it lies
 *       wholly outside the clip gets {@code renderCulled} in place of
 *       {@code render} ({@link #renderCulled}), which a container forwards.</li>
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
 * "Shown" is the child's own {@link PanelElement#isVisible()}.
 */
public final class ChildDispatch {

    private ChildDispatch() {}

    /**
     * The base pass: renders every shown child, except one wholly outside the clip in
     * play (7.4.0). A {@link ScrollContainer} already skipped its own children out of
     * view; this carries the rule down to every level, so a row inside a {@code Flow}
     * inside a scroll area is skipped too. That matters for slots: a slot element that
     * renders places its slot, and a slot placed out of view would sit under whatever
     * is past the clip's edge (the player's inventory, below a container region),
     * where its library would otherwise have parked it. A child with an open popover is
     * rendered anyway, as a {@code ScrollContainer} does, since its popover hangs from it.
     * A shown child skipped for the clip gets {@link PanelElement#renderCulled} instead,
     * so a slot element under it knows it is held out of view, not hidden.
     */
    public static void render(List<? extends PanelElement> children, RenderContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        ScreenRectangle clip = g == null || g.scissorStack == null ? null : g.scissorStack.peek();
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            if (clip != null && outside(c, ctx, clip) && c.getActiveOverlayBounds(ctx.input()) == null) {
                c.renderCulled(ctx);
                continue;
            }
            c.render(ctx);
        }
    }

    /**
     * The culled pass (7.4.0): every shown child's {@link PanelElement#renderCulled}. A
     * container whose own box is culled forwards it to its children through this, with
     * the context it would have rendered them with, so the call reaches every shown
     * element under it, at any depth. A hidden child, and so everything under it, gets
     * nothing.
     */
    public static void renderCulled(List<? extends PanelElement> children, RenderContext ctx) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.renderCulled(ctx);
        }
    }

    /**
     * Whether {@code c}, at its place from the context's origin, lies wholly outside
     * {@code clip} (screen space: the child's box goes through the pose as the scissor
     * did). A child with no height counts as one pixel tall, so a line drawn at its
     * place inside the clip is not skipped.
     */
    private static boolean outside(PanelElement c, RenderContext ctx, ScreenRectangle clip) {
        ScreenRectangle box = new ScreenRectangle(ctx.originX() + c.getChildX(), ctx.originY() + c.getChildY(),
                Math.max(1, c.getWidth()), Math.max(1, c.getHeight()));
        Matrix3x2fc pose = ctx.graphics().pose();
        if (pose != null) box = box.transformMaxBounds(pose);
        return box.right() <= clip.left() || box.left() >= clip.right()
                || box.bottom() <= clip.top() || box.top() >= clip.bottom();
    }

    /** The overlay pass: every shown child's {@code renderOverlay}, after every base render. */
    public static void renderOverlay(List<? extends PanelElement> children, RenderContext ctx) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.renderOverlay(ctx);
        }
    }

    /** Whether any shown child wants an event at the context's point. */
    public static boolean hitTest(List<? extends PanelElement> children, InputContext in) {
        for (PanelElement c : children) {
            if (c.isVisible() && c.hitTest(in)) return true;
        }
        return false;
    }

    /**
     * Whether a shown child presents a live menu slot under the point
     * ({@link PanelElement#presentsSlotAt}), so an opaque panel's claim lets the point
     * through to a slot nested in a container. A container that clips its children
     * asks only for points inside its clip.
     */
    public static boolean presentsSlotAt(List<? extends PanelElement> children, InputContext in) {
        for (PanelElement c : children) {
            if (c.isVisible() && c.presentsSlotAt(in)) return true;
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

    /**
     * Attaches every child, shown or not, as a hidden panel's elements are: a
     * widget-wrapping child registers its vanilla widget once per screen.
     */
    public static void attach(List<? extends PanelElement> children, Screen screen) {
        for (PanelElement c : children) c.onAttach(screen);
    }

    /** Hands the kind of screen to every child, shown or not ({@link PanelElement#screenKind}). */
    public static void screenKind(List<? extends PanelElement> children, ScreenKind kind) {
        for (PanelElement c : children) c.screenKind(kind);
    }

    /** Tells every child, shown or not, whether it is in a dialog ({@link PanelElement#inDialog}). */
    public static void inDialog(List<? extends PanelElement> children, boolean inDialog) {
        for (PanelElement c : children) c.inDialog(inDialog);
    }

    /** The first child's request to be scrolled into view ({@link PanelElement#takeRevealRequest}), or {@code null}. */
    public static int @Nullable [] takeRevealRequest(List<? extends PanelElement> children) {
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            int[] r = c.takeRevealRequest();
            if (r != null) return r;
        }
        return null;
    }

    /** Tells every child, shown or not, whether it sits on a band another element draws ({@link PanelElement#inBand}). */
    public static void inBand(List<? extends PanelElement> children, boolean inBand) {
        for (PanelElement c : children) c.inBand(inBand);
    }

    /** Whether any shown child fills the height it is given ({@link PanelElement#fillsHeight}). */
    public static boolean anyFillsHeight(List<? extends PanelElement> children) {
        for (PanelElement c : children) {
            if (c.isVisible() && c.fillsHeight()) return true;
        }
        return false;
    }

    /** Detaches every child. */
    public static void detach(List<? extends PanelElement> children, Screen screen) {
        for (PanelElement c : children) c.onDetach(screen);
    }
}
