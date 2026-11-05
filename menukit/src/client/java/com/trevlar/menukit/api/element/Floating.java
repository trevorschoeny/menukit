package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.api.panel.ScreenOrigin;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One element of a panel drawn at a screen point of its own, outside the panel
 * (7.6.0): a dialog's button beside the screen's hotbar, say.
 *
 * <pre>{@code
 * Floating.at(() -> new ScreenOrigin(screen.leftPos() + 198, hotbarY), saveButton)
 * }</pre>
 *
 * The point is read every frame, so it follows what it sits by. The element belongs
 * to its panel: it shows and hides with it, draws in its layer (a modal's, above the
 * dim), grays with it, and takes input as the panel's own, a modal's included. It
 * takes no room in the panel's layout, so the panel's size and background leave it
 * out, and the panel claims only the element's own box out there (its hit test), so
 * a click beside it reaches what is behind. Put it among the panel's top-level
 * elements: inside a scroll area it would scroll and clip with the content.
 */
public final class Floating extends AbstractPanelElement {

    private final Supplier<ScreenOrigin> origin;
    private final PanelElement element;
    private final List<PanelElement> one;

    private Floating(Supplier<ScreenOrigin> origin, PanelElement element) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.element = Objects.requireNonNull(element, "element");
        this.one = List.of(element);
    }

    /** {@code element} with its top-left at the screen point {@code origin} gives, read every frame. */
    public static Floating at(Supplier<ScreenOrigin> origin, PanelElement element) {
        return new Floating(origin, element);
    }

    // The element's own origin is the screen point; the panel's is left behind.

    private RenderContext placed(RenderContext ctx) {
        ScreenOrigin o = origin.get();
        return ctx.at(o.x(), o.y());
    }

    private InputContext placed(InputContext in) {
        ScreenOrigin o = origin.get();
        return in.at(o.x(), o.y());
    }

    /** No room in the panel: its size is the element's, out where it floats. */
    @Override public int getWidth() { return 0; }
    @Override public int getHeight() { return 0; }
    @Override public int naturalWidth() { return 0; }

    /** Shown while the element is. */
    @Override public boolean isVisible() { return element.isVisible(); }

    @Override public boolean isInteractive() { return element.isInteractive(); }

    /** The element at its own width, at its own origin's top-left. */
    @Override
    public void layoutWithin(int budget) {
        element.layoutWithin(element.naturalWidth());
        if (element instanceof AbstractPanelElement e) e.setChildPosition(0, 0);
    }

    @Override public void fillWidth(int width) { layoutWithin(width); }

    @Override public void render(RenderContext ctx) { ChildDispatch.render(one, placed(ctx)); }
    @Override public void renderCulled(RenderContext ctx) { ChildDispatch.renderCulled(one, placed(ctx)); }
    @Override public void renderOverlay(RenderContext ctx) { ChildDispatch.renderOverlay(one, placed(ctx)); }

    @Override public boolean hitTest(InputContext in) { return ChildDispatch.hitTest(one, placed(in)); }
    @Override public boolean mouseClicked(InputContext in, int button) { return ChildDispatch.mouseClicked(one, placed(in), button); }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        return ChildDispatch.mouseScrolled(one, placed(in), scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(one, placed(in), button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(one, placed(in), keyCode, scanCode, modifiers);
    }

    @Override public int @Nullable [] getActiveOverlayBounds(InputContext in) { return ChildDispatch.activeOverlay(one, placed(in)); }
    @Override public void notifyClickOutsideOverlay(InputContext in) { ChildDispatch.notifyClickOutside(one, placed(in)); }
    @Override public boolean presentsSlotAt(InputContext in) { return ChildDispatch.presentsSlotAt(one, placed(in)); }
    @Override public int @Nullable [] takeRevealRequest() { return null; }   // nothing to scroll to out here

    @Override public void screenKind(ScreenKind kind) { ChildDispatch.screenKind(one, kind); }
    @Override public void inDialog(boolean inDialog) { ChildDispatch.inDialog(one, inDialog); }
    @Override public void inBand(boolean inBand) { ChildDispatch.inBand(one, inBand); }
    @Override public void onAttach(Screen screen) { ChildDispatch.attach(one, screen); }
    @Override public void onDetach(Screen screen) { ChildDispatch.detach(one, screen); }
}
