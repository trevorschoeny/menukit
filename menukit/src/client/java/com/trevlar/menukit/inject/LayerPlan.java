package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.Panel;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * The one frame-composition plan every panel surface follows (§0065): FLOW, then
 * the modal dim, then OVERLAY.
 *
 * <ul>
 *   <li><b>FLOW</b>: every placed panel that is not an overlay, in host order (the
 *       host's sorted z-order, topmost last).</li>
 *   <li><b>Dim</b>: when any shown panel on the surface {@link Panel#dimsBehind()
 *       dims behind}, one translucent fill over the whole screen, so it covers
 *       vanilla content and every FLOW panel alike.</li>
 *   <li><b>OVERLAY</b>: every {@link Panel#isOverlayPositioned() overlay} (a
 *       {@code center()} panel, or any dim or modal panel), on top.</li>
 * </ul>
 *
 * <p>A surface with no slot pass composes the three in one go ({@link #compose}).
 * A container screen is the one surface that has to split them, because vanilla's
 * slot pass sits between FLOW and OVERLAY; {@link ContainerScreenLayers} is that
 * instance of this plan.
 *
 * <p>Claims follow the same order read top-down: an OVERLAY panel is above every
 * FLOW panel, and within a layer the host's later entries are above earlier ones.
 */
@ApiStatus.Internal
public final class LayerPlan {

    private LayerPlan() {}

    /** The two panel layers of a frame, bottom to top. */
    public enum Layer { FLOW, OVERLAY }

    /** ~75% black: the dim fill, tuned to vanilla's confirm-screen darkening. */
    public static final int DIM_COLOR = 0xC0000000;

    /** Which layer a panel draws (and claims) in. */
    public static Layer layerOf(Panel panel) {
        return panel.isOverlayPositioned() ? Layer.OVERLAY : Layer.FLOW;
    }

    /**
     * FLOW for every host, the dim if any host shows a dimming panel, then OVERLAY
     * for every host. {@code hosts} is bottom to top.
     */
    public static void compose(List<PanelHost> hosts, GuiGraphicsExtractor graphics,
                               int mouseX, int mouseY, int screenW, int screenH,
                               PanelHost.PointerPolicy pointer) {
        for (PanelHost host : hosts) host.render(graphics, mouseX, mouseY, Layer.FLOW, pointer);
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
        for (PanelHost host : hosts) host.render(graphics, mouseX, mouseY, Layer.OVERLAY, pointer);
    }
}
