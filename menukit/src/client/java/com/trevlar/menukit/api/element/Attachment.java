package com.trevlar.menukit.api.element;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The screen a container's children are attached to, so a child added after build
 * attaches the way a child present at the screen's init did (7.3.0). The one
 * attach and detach path: a container that changes its children while the screen is
 * open ({@link ListView} first) keeps one of these, and every child goes through the
 * same {@link PanelElement#onAttach} and {@link PanelElement#onDetach} the screen's
 * init and close use ({@link ChildDispatch#attach}, {@link ChildDispatch#detach}).
 *
 * <h3>What attaching does</h3>
 *
 * Whatever each element does in {@code onAttach}: a {@link TextField} builds its
 * {@code EditBox} and joins the screen's focus, every button, toggle, checkbox and
 * radio registers its focus and narration stand-in, a slot element joins its
 * library's registry. Detaching undoes it: focus held by the child is released, its
 * stand-ins leave the screen, an open popover closes.
 *
 * <pre>{@code
 * private final Attachment attachment = new Attachment();
 *
 * public void onAttach(Screen screen) { attachment.attach(screen, children); }
 * public void onDetach(Screen screen) { attachment.detach(screen, children); }
 *
 * void add(PanelElement child)    { children.add(child); attachment.added(child); }
 * void remove(PanelElement child) { children.remove(child); attachment.removed(child); }
 * }</pre>
 *
 * <p>Before the screen's init, and after its close, there is no screen: a child added
 * then is attached with the rest at the next init. Render thread only.
 */
public final class Attachment {

    private @Nullable Screen screen;

    /** The screen the children are attached to now, or {@code null} when none is. */
    public @Nullable Screen screen() {
        return screen;
    }

    /**
     * The container's own {@code onAttach}: remembers {@code screen} and attaches every
     * child. A second init of the same screen (a resize) attaches again, as the host
     * does; a different screen first detaches from the old one.
     */
    public void attach(Screen screen, List<? extends PanelElement> children) {
        if (this.screen != null && this.screen != screen) ChildDispatch.detach(children, this.screen);
        this.screen = screen;
        ChildDispatch.attach(children, screen);
    }

    /** The container's own {@code onDetach}: detaches every child and forgets the screen. */
    public void detach(Screen screen, List<? extends PanelElement> children) {
        ChildDispatch.detach(children, screen);
        if (this.screen == screen) this.screen = null;
    }

    /** A child added after build: attached now when the container is, else at the next init. */
    public void added(PanelElement child) {
        if (screen != null) child.onAttach(screen);
    }

    /** A child removed after build: detached now when the container is attached. */
    public void removed(PanelElement child) {
        if (screen != null) child.onDetach(screen);
    }
}
