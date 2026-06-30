package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.inject.ScreenBounds;
import com.trevorschoeny.menukit.inject.ScreenOrigin;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Movement ③ — the custom-screen layout resolver. A custom screen names ONE
 * {@link PanelPosition.Mode#MAIN main} panel = its frame (centred on the screen
 * window, exactly like a vanilla container's menu frame); every other panel
 * anchors to that frame with a {@link MenuRegion} via {@link RegionMath#resolveMenu}
 * — the SAME math vanilla-injected panels take against the menu frame. So the
 * relative verbs (rightOf / above / below) and their edge-unaware Tree layout are
 * retired for custom screens, and siblings get region anchoring + screen-edge
 * clamping (no panel renders off-screen) + the single overlay rule (①) for free.
 *
 * <h3>Coordinate convention</h3>
 *
 * The result's {@code bounds} are <b>leftPos-relative</b> (the main panel sits at
 * {@code (0,0)}), matching what the screen's render + slot-positioning code adds
 * {@code leftPos}/{@code topPos} to. The main frame's screen origin is the returned
 * {@code leftPos}/{@code topPos}, so a vanilla container's {@code leftPos + slot.x}
 * math keeps working unchanged.
 *
 * <h3>What each non-main panel becomes</h3>
 * <ul>
 *   <li><b>Overlay</b> ({@link Panel#isOverlayPositioned()} — {@code center()}, or
 *       a dim/modal panel): floats centred on the screen window, drawn on top.</li>
 *   <li><b>{@link PanelPosition.Mode#REGION}</b>: anchored to the main frame via
 *       its {@link MenuRegion}, clamped into the screen safe area. Siblings sharing
 *       a region stack with {@link RegionConstants#MENU_STACK_GAP}.</li>
 *   <li><b>{@link PanelPosition.Mode#SCREEN_ANCHOR}</b>: pinned to a screen corner
 *       (chrome like a Back button), independent of the frame.</li>
 * </ul>
 *
 * <p><b>Note (②).</b> Size comes from the screen's {@code sizeFn} (which factors
 * in custom-screen slot groups, not just panel elements), and the origin from
 * {@link RegionMath#resolveMenu}, whose screen-edge clamp keeps siblings on-screen.
 * Per-sibling auto-SCROLL (the full ② treatment) is a slot-group ↔ scroll
 * integration not yet wired on this path — siblings that exceed their region are
 * clamped over the frame rather than scrolled. Inert for fitting siblings.
 */
public final class MainRegionLayout {

    private MainRegionLayout() {}

    /**
     * @param leftPos main frame screen-X (and the origin for leftPos-relative bounds)
     * @param topPos  main frame screen-Y
     * @param mainW   main panel outer width
     * @param mainH   main panel outer height
     * @param bounds  leftPos-relative bounds per visible panel id (main at 0,0)
     */
    public record Result(int leftPos, int topPos, int mainW, int mainH,
                         Map<String, PanelBounds> bounds) {}

    /** Whether {@code panels} contains a {@link PanelPosition.Mode#MAIN} panel —
     *  the gate for using this resolver vs the legacy BODY-stack layout. */
    public static boolean hasMain(List<Panel> panels) {
        for (Panel p : panels) {
            if (p.getPosition().mode() == PanelPosition.Mode.MAIN) return true;
        }
        return false;
    }

    /**
     * Resolves the main-panel frame + region-anchored siblings.
     *
     * @param panels  the screen's panels (declaration order — siblings sharing a
     *                region stack in this order)
     * @param sizeFn  per-context Panel → {outerWidth, outerHeight} (padding-inclusive)
     * @param screenW GUI-scaled screen width
     * @param screenH GUI-scaled screen height
     */
    public static Result resolve(List<Panel> panels, Function<Panel, int[]> sizeFn,
                                 int screenW, int screenH) {
        Panel main = null;
        for (Panel p : panels) {
            if (p.getPosition().mode() == PanelPosition.Mode.MAIN) { main = p; break; }
        }
        Map<String, PanelBounds> bounds = new LinkedHashMap<>();
        if (main == null) {
            return new Result(screenW / 2, screenH / 2, 0, 0, bounds);
        }

        int[] ms = sizeFn.apply(main);
        int mainW = ms[0], mainH = ms[1];
        int leftPos = (screenW - mainW) / 2;
        int topPos = (screenH - mainH) / 2;
        bounds.put(main.getId(), new PanelBounds(0, 0, mainW, mainH));

        // The frame every sibling resolves against — the main panel in screen coords.
        ScreenBounds frame = new ScreenBounds(leftPos, topPos, mainW, mainH);
        int margin = RegionConstants.SCREEN_EDGE_MARGIN;

        // Running axial stacking prefix per region (declaration order), mirroring
        // RegionRegistry.axialPrefix for the vanilla path.
        Map<MenuRegion, Integer> prefixByRegion = new EnumMap<>(MenuRegion.class);

        for (Panel p : panels) {
            if (p == main) continue;
            if (!com.trevorschoeny.menukit.window.ClientWindowVisibility.panelShown(p)) continue;

            int[] s = sizeFn.apply(p);
            int pw = s[0], ph = s[1];

            // Overlays float centred on the screen window, on top (the single
            // overlay rule ① — identical to MKScreen and the vanilla region path).
            if (p.isOverlayPositioned()) {
                int ox = (screenW - pw) / 2, oy = (screenH - ph) / 2;
                bounds.put(p.getId(), new PanelBounds(ox - leftPos, oy - topPos, pw, ph));
                continue;
            }

            PanelPosition pos = p.getPosition();
            switch (pos.mode()) {
                case REGION -> {
                    MenuRegion region = pos.menuRegion();
                    if (region == null) continue; // malformed — skip defensively
                    int prefix = prefixByRegion.getOrDefault(region, 0);
                    Optional<ScreenOrigin> o = RegionMath.resolveMenu(
                            region, frame, pw, ph, prefix, screenW, screenH);
                    if (o.isEmpty()) continue; // larger than the safe area — skip
                    ScreenOrigin so = o.get();
                    bounds.put(p.getId(), new PanelBounds(so.x() - leftPos, so.y() - topPos, pw, ph));
                    int axial = region.isHorizontalFlow() ? pw : ph;
                    prefixByRegion.put(region, prefix + axial + RegionConstants.MENU_STACK_GAP);
                }
                case SCREEN_ANCHOR -> {
                    ScreenCorner corner = pos.screenCorner();
                    if (corner == null) corner = ScreenCorner.TOP_LEFT;
                    int sx = switch (corner) {
                        case TOP_LEFT, BOTTOM_LEFT -> margin;
                        case TOP_RIGHT, BOTTOM_RIGHT -> screenW - pw - margin;
                    };
                    int sy = switch (corner) {
                        case TOP_LEFT, TOP_RIGHT -> margin;
                        case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - ph - margin;
                    };
                    bounds.put(p.getId(), new PanelBounds(sx - leftPos, sy - topPos, pw, ph));
                }
                default -> {
                    // BODY (or any non-region) panel on a main screen has no anchor
                    // in this model — skip rather than guess a position.
                }
            }
        }
        return new Result(leftPos, topPos, mainW, mainH, bounds);
    }
}
