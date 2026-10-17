package com.trevlar.menukit.api.host;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.inject.LayerPlan;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The panel host of a library's own screen: a screen class a library ships (a custom
 * container screen, say) that lays out its own panels the way {@code MKScreen} does,
 * with the main panel as the frame, and joins MenuKit's claims, modals and draw order
 * with them. Panels other mods inject onto the screen (adapters, anchors) are hosted by
 * MenuKit as on any screen; this host holds only the screen's own.
 *
 * <p>The screen drives it: {@link #layout} each frame (its main panel's rectangle
 * becomes the screen's image), {@link #renderFlow} before vanilla's content pass, the
 * input methods from its own handlers, and {@link #attach} and {@link #detach} from
 * {@code init} and {@code removed}. Popover and overlay panels draw above vanilla's
 * slots through MenuKit's container-screen layers, with no call from the screen.
 *
 * <pre>{@code
 * host = OwnHost.of(this, ScreenKind.PANEL, OwnHost.TitleBand.IN_FRAME)
 *         .slotOwnership(this::ownsSlotAt);
 * for (Panel p : panels) host.add(p, p.getPosition(), p.interiorPadding());
 * }</pre>
 */
public final class OwnHost {

    /** Where the screen's title sits, which decides the room reserved for it. */
    public enum TitleBand {
        /** Inside the frame, above the main content (a container screen's). */
        IN_FRAME,
        /** At the top of the screen, outside the frame. */
        SCREEN_TOP,
        /** Nowhere: no title and no room reserved. */
        NONE
    }

    /**
     * Where a panel landed this frame: its outer rectangle, padding included, in screen
     * coordinates.
     */
    public record Placement(Panel panel, int x, int y, int width, int height) {
        /** Whether the point is inside the rectangle. */
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /**
     * One frame's placements, bottom to top, and the main panel's rectangle (the
     * screen's {@code leftPos, topPos, imageWidth, imageHeight}), or {@code null} when
     * the screen has no shown main panel.
     */
    public record Layout(List<Placement> placed, @Nullable Reference main) {}

    /** Whether {@code panel} presents the live slot under the point (its claim then routes the point to that slot). */
    @FunctionalInterface
    public interface SlotOwnership {
        boolean ownsSlotAt(Panel panel, double mouseX, double mouseY);
    }

    /** Drawn after a panel's background and before its elements (slot frames, say). */
    @FunctionalInterface
    public interface Decoration {
        void draw(GuiGraphicsExtractor graphics, Placement placed);
    }

    private final Screen screen;
    private final PanelHost host;
    private int order = 0;

    private OwnHost(Screen screen, PanelHost host) {
        this.screen = screen;
        this.host = host;
    }

    /**
     * A host for {@code screen}'s own panels, laid out against the screen's size each
     * frame (so it may be made in the screen's constructor, before its size is set).
     *
     * @param kind      the look the screen's panels draw in
     * @param titleBand where the screen's title sits
     */
    public static OwnHost of(Screen screen, ScreenKind kind, TitleBand titleBand) {
        Objects.requireNonNull(screen, "screen");
        PanelHost.TitleBand band = switch (titleBand) {
            case IN_FRAME -> PanelHost.TitleBand.IN_FRAME;
            case SCREEN_TOP -> PanelHost.TitleBand.SCREEN_TOP;
            case NONE -> PanelHost.TitleBand.NONE;
        };
        return new OwnHost(screen, PanelHost.standalone(kind,
                () -> new PanelHost.Frame(screen.width, screen.height, null), band));
    }

    /**
     * A panel's content size, {@code {width, height}} without padding, for panels whose
     * own size does not cover everything they show (slots drawn in them, say). Default:
     * the panel's own.
     */
    public OwnHost contentSize(Function<Panel, int[]> size) {
        host.contentSize(size);
        return this;
    }

    /**
     * Whether the main panel gets a height budget and scrolls when taller than the
     * screen. Answer {@code false} for a main panel holding slots: they sit at fixed
     * positions and cannot scroll. Default: every main panel fits.
     */
    public OwnHost autoFitMain(Predicate<Panel> autoFit) {
        host.autoFitMain(autoFit);
        return this;
    }

    /** Which live slots each panel presents (see {@link SlotOwnership}). Default: none. */
    public OwnHost slotOwnership(SlotOwnership ownership) {
        host.slotOwnership(ownership::ownsSlotAt);
        return this;
    }

    /** Drawn between each panel's background and its elements. */
    public OwnHost decoration(Decoration decoration) {
        host.decoration((graphics, placed) -> decoration.draw(graphics, placement(placed)));
        return this;
    }

    /**
     * Adds a panel. Panels order by their priority, then by the order they were added.
     *
     * @param position the position to place it at (the panel's own, or a default the
     *                 screen picks for an unplaced one)
     * @param padding  content padding between the panel's edge and its elements
     */
    public void add(Panel panel, PanelPosition position, int padding) {
        host.add(panel, position, padding, "", order++);
    }

    /** This frame's placements and main rectangle. Cheap: call it every frame. */
    public Layout layout() {
        PanelHost.Layout layout = host.layout();
        List<Placement> placed = new ArrayList<>(layout.placed().size());
        for (PanelHost.Placed p : layout.placed()) placed.add(placement(p));
        return new Layout(List.copyOf(placed), layout.main());
    }

    /**
     * Joins MenuKit's claims and modals with this host and attaches its elements (a
     * text field registers its vanilla widget). Call from the screen's {@code init};
     * safe to call again after a resize.
     */
    public void attach() {
        ScreenPanelRegistry.attachOwnHost(screen, host);
        host.attach(screen);
    }

    /** Detaches the elements. Call from the screen's {@code removed}. */
    public void detach() {
        host.detach(screen);
    }

    /**
     * Draws the screen's ordinary (FLOW) panels, live where the pointer is theirs.
     * Call before vanilla's content pass, so the screen's slots draw on top of them.
     */
    public void renderFlow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        host.render(graphics, mouseX, mouseY, LayerPlan.Layer.FLOW,
                ScreenPanelRegistry.pointerPolicy(screen, mouseX, mouseY));
    }

    /** Whether one of this host's modal panels is shown. */
    public boolean hasModal() {
        return host.topmostModal() != null;
    }

    /** A press: the topmost panel under the point takes it. @return whether it was consumed */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return host.mouseClicked(mouseX, mouseY, button, hasModal());
    }

    /** A release, offered to every element so a drag ends wherever the cursor is. */
    public void mouseReleased(double mouseX, double mouseY, int button) {
        host.mouseReleased(mouseX, mouseY, button);
    }

    /** A scroll: the topmost panel under the point takes it. @return whether it was consumed */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return host.mouseScrolled(mouseX, mouseY, scrollX, scrollY, hasModal());
    }

    /** A key, offered to the panels' elements top down. @return whether it was consumed */
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return host.keyPressed(key, scanCode, modifiers, hasModal());
    }

    /** Closes any open popover the click at the point fell outside of. Call before {@link #mouseClicked}. */
    public void notifyOutsideClick(double mouseX, double mouseY) {
        host.notifyOutsideClick(mouseX, mouseY);
    }

    private static Placement placement(PanelHost.Placed p) {
        return new Placement(p.panel(), p.x(), p.y(), p.w(), p.h());
    }
}
