package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.Panel;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * The one frame-composition plan every panel surface follows: FLOW, then
 * POPOVER, then the modal dim, then OVERLAY.
 *
 * <ul>
 *   <li><b>FLOW</b>: every placed panel that is neither a popover nor an overlay, in
 *       host order (the host's sorted z-order, topmost last), host by host, bottom
 *       host first.</li>
 *   <li><b>POPOVER</b> (6.1.0): every {@link Panel#isPopover() popover} panel, of
 *       every host on the surface, in the same host-then-entry order. A popover is
 *       above every FLOW panel on the screen, whichever host holds either and
 *       whichever registered first: Inventory Max's pocket row, on the container
 *       screen's host, over Inventory Plus's switches on the player inventory's
 *       slot-group host.</li>
 *   <li><b>Dim</b>: when any shown panel on the surface {@link Panel#dimsBehind()
 *       dims behind}, one translucent fill over the whole screen, so it covers
 *       vanilla content and every FLOW and POPOVER panel alike.</li>
 *   <li><b>OVERLAY</b>: every {@link Panel#isOverlayPositioned() overlay} (a
 *       {@code center()} panel, or any dim or modal panel), on top. A modal stays
 *       above a popover.</li>
 * </ul>
 *
 * <h2>One order for drawing and claiming</h2>
 *
 * {@link #drawOrder} is the order, and both halves read it: the renderers draw it
 * bottom to top, and {@link ScreenPanelRegistry#claimAt} and the pointer dispatch
 * ({@link PanelHost#mouseClicked(List, double, double, int, boolean)}) walk it top
 * to bottom. So what is drawn on top is what takes the point, by construction: a
 * click or hover on a popover never reaches a panel drawn under it.
 *
 * <p>Z-order is still sorted order within a layer, and the layers stack the sorted
 * runs: FLOW below POPOVER below OVERLAY, as OVERLAY already did before popovers.
 *
 * <p>A surface with no slot pass composes the plan in one go ({@link #compose}).
 * A container screen is the one surface that has to split it, because vanilla's
 * slot pass sits inside it; {@link ContainerScreenLayers} is that instance of this
 * plan.
 */
@ApiStatus.Internal
public final class LayerPlan {

    private LayerPlan() {}

    /** The panel layers of a frame, bottom to top. */
    public enum Layer { FLOW, POPOVER, OVERLAY }

    /** The layers top to bottom: the order claims and pointer input test them in. */
    public static final List<Layer> TOP_DOWN = List.of(Layer.OVERLAY, Layer.POPOVER, Layer.FLOW);

    /** About 75% black: the dim fill, tuned to vanilla's confirm-screen darkening. */
    public static final int DIM_COLOR = 0xC0000000;

    /**
     * Which layer a panel draws (and claims) in. An overlay wins over a popover: a
     * popover that is also a modal (or centred) is a modal, and draws with the
     * modals above the dim.
     */
    public static Layer layerOf(Panel panel) {
        if (panel.isOverlayPositioned()) return Layer.OVERLAY;
        return panel.isPopover() ? Layer.POPOVER : Layer.FLOW;
    }

    /** One placed panel and the host it sits in: a step of the draw order. */
    public record Drawn(PanelHost host, PanelHost.Placed placed) {}

    /**
     * The placed panels of {@code layer} on {@code hosts} (bottom to top), in draw
     * order: host by host, and within a host in its sorted order. The one order the
     * renderers draw forward and the claims walk backward.
     */
    public static List<Drawn> drawOrder(List<PanelHost> hosts, Layer layer) {
        List<Drawn> out = new ArrayList<>();
        for (PanelHost host : hosts) {
            for (PanelHost.Placed p : host.placed()) {
                if (layerOf(p.panel()) == layer) out.add(new Drawn(host, p));
            }
        }
        return out;
    }

    /**
     * Every placed panel on {@code hosts}, top to bottom: OVERLAY, then POPOVER, then
     * FLOW, each layer's draw order reversed. What claims and pointer input walk.
     */
    public static List<Drawn> topDown(List<PanelHost> hosts) {
        List<Drawn> out = new ArrayList<>();
        for (Layer layer : TOP_DOWN) {
            List<Drawn> run = drawOrder(hosts, layer);
            for (int i = run.size() - 1; i >= 0; i--) out.add(run.get(i));
        }
        return out;
    }

    /** Draws {@code layer} of every host, in draw order. */
    public static void render(List<PanelHost> hosts, Layer layer, GuiGraphicsExtractor graphics,
                              int mouseX, int mouseY, PanelHost.PointerPolicy pointer) {
        for (Drawn d : drawOrder(hosts, layer)) d.host().render(graphics, mouseX, mouseY, d.placed(), pointer);
    }

    /**
     * FLOW for every host, POPOVER for every host, the dim if any host shows a
     * dimming panel, then OVERLAY for every host. {@code hosts} is bottom to top.
     */
    public static void compose(List<PanelHost> hosts, GuiGraphicsExtractor graphics,
                               int mouseX, int mouseY, int screenW, int screenH,
                               PanelHost.PointerPolicy pointer) {
        render(hosts, Layer.FLOW, graphics, mouseX, mouseY, pointer);
        render(hosts, Layer.POPOVER, graphics, mouseX, mouseY, pointer);
        dimAndOverlay(hosts, graphics, mouseX, mouseY, screenW, screenH, pointer);
    }

    /** The upper half of the plan: the dim (when any host needs it), then OVERLAY. */
    public static void dimAndOverlay(List<PanelHost> hosts, GuiGraphicsExtractor graphics,
                                     int mouseX, int mouseY, int screenW, int screenH,
                                     PanelHost.PointerPolicy pointer) {
        for (PanelHost host : hosts) {
            if (host.anyShown(Panel::dimsBehind)) {
                graphics.fill(0, 0, screenW, screenH, DIM_COLOR);
                break;
            }
        }
        render(hosts, Layer.OVERLAY, graphics, mouseX, mouseY, pointer);
    }
}
