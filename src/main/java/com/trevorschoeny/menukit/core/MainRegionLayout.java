package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.inject.RegionRegistry;
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
 *       a dim/modal panel): floats centred on the screen window (the single overlay
 *       rule ①). NOTE: {@code MKCHandledScreen} renders its panels in declaration
 *       order — there is no separate on-top/dim pass for an overlay added directly to
 *       a custom screen's panel list (none exist today; injected modals use the
 *       vanilla region path, which DOES have the 3-pass). Wiring an on-top pass here
 *       is a follow-up if an in-panel custom-screen overlay is ever needed.</li>
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

    /** Title strip reserved at the top of the main frame — the screen title draws
     *  here and the MAIN panel's content sits below it. Matches the legacy BODY-
     *  stack's titleHeight reservation so the title never overprints the first row. */
    private static final int TITLE_STRIP = 14;

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
     * @param panels       the screen's panels (declaration order — siblings sharing a
     *                     region stack in this order)
     * @param sizeFn       per-context Panel → {outerWidth, outerHeight} (padding-inclusive)
     * @param screenW      GUI-scaled screen width
     * @param screenH      GUI-scaled screen height
     * @param reserveTitle reserve a title strip at the top of the frame (true for
     *                     container screens that draw the title at the frame top;
     *                     false for standalone screens that draw it at the screen top)
     * @param autoFitMain  feed the MAIN frame a vertical budget so it auto-scrolls
     *                     when taller than the screen ("shrink-to-fit is the default",
     *                     applied to the frame itself). Pass false for a slot-bearing
     *                     container main — its vanilla slots are pinned in absolute
     *                     coords and have no scroll hook, so it must keep its natural
     *                     height. Standalone (element-only) mains always pass true.
     */
    public static Result resolve(List<Panel> panels, Function<Panel, int[]> sizeFn,
                                 int screenW, int screenH, boolean reserveTitle,
                                 boolean autoFitMain) {
        Panel main = null;
        for (Panel p : panels) {
            if (p.getPosition().mode() == PanelPosition.Mode.MAIN) { main = p; break; }
        }
        Map<String, PanelBounds> bounds = new LinkedHashMap<>();
        if (main == null) {
            return new Result(screenW / 2, screenH / 2, 0, 0, bounds);
        }

        // The MAIN frame is centred on the screen window, so it gets the
        // symmetric centred-screen width budget (grow until SCREEN_EDGE_MARGIN
        // from both edges, then wrap) — fed BEFORE measuring, the SAME reactive-
        // sizing engine its region siblings use, just with the centred budget.
        feedCentered(main, screenW);
        // "Shrink-to-fit is the default" applied to the frame itself: feed the MAIN
        // its vertical budget too, so a main taller than the screen auto-scrolls (the
        // SAME effectiveContentHeight → ScrollContainer path region siblings already
        // take) instead of growing off-screen and forcing the consumer to pin a
        // height by hand. Gated by autoFitMain because a slot-bearing container main
        // must NOT scroll — its vanilla slots are pinned in absolute coords with no
        // scroll hook (the host passes false there). Fed BEFORE the measure, exactly
        // like the width budget, so the measure reflects any wrap/scroll.
        if (autoFitMain) feedMainHeight(main, screenH);
        int[] ms = sizeFn.apply(main);
        int mainW = ms[0], mainContentH = ms[1];
        // Reserve the title strip at the top of the frame (the vanilla-container
        // convention the legacy BODY-stack reserved via titleHeight): the screen
        // title draws in the strip, the MAIN panel's content sits BELOW it. Without
        // this the title overprinted the main panel's first row (③ blocker).
        int titleStrip = reserveTitle ? TITLE_STRIP : 0;
        int frameH = mainContentH + titleStrip;
        int leftPos = (screenW - mainW) / 2;
        int topPos = (screenH - frameH) / 2;
        // Standalone title-dock clamp — restores the legacy BODY-stack guard the
        // MAIN-path migration dropped. A standalone screen draws its title at the
        // SCREEN top (y = SCREEN_EDGE_MARGIN), OUTSIDE the frame (titleStrip = 0 when
        // !reserveTitle); centring a tall frame on the full screen height would creep
        // its top edge up into that title band, so clamp the frame top to sit just
        // below it. Container screens (reserveTitle) draw the title INSIDE the frame
        // and need no screen-top clamp. Inert for a short frame whose centred top
        // already sits well below the band.
        if (!reserveTitle) {
            topPos = Math.max(topPos, RegionConstants.SCREEN_EDGE_MARGIN + TITLE_STRIP);
        }
        // MAIN content below the title strip; bounds are leftPos/topPos-relative.
        bounds.put(main.getId(), new PanelBounds(0, titleStrip, mainW, mainContentH));

        // The frame every sibling resolves against — the FULL main frame (title
        // strip + content) in screen coords, so siblings anchor OUTSIDE the title.
        ScreenBounds frame = new ScreenBounds(leftPos, topPos, mainW, frameH);
        int margin = RegionConstants.SCREEN_EDGE_MARGIN;

        // Running axial stacking prefix per region (declaration order), mirroring
        // RegionRegistry.axialPrefix for the vanilla path.
        Map<MenuRegion, Integer> prefixByRegion = new EnumMap<>(MenuRegion.class);

        for (Panel p : panels) {
            if (p == main) continue;
            if (!com.trevorschoeny.menukit.window.ClientWindowVisibility.panelShown(p)) continue;

            int pad = p.interiorPadding();

            // Overlays float centred on the screen window, on top (the single
            // overlay rule ① — identical to MKScreen and the vanilla region path).
            // Centred budget fed BEFORE measuring (the SAME engine), so a wide
            // overlay wraps to the screen rather than overflowing.
            if (p.isOverlayPositioned()) {
                feedCentered(p, screenW);
                int[] s = sizeFn.apply(p);
                int pw = s[0], ph = s[1];
                int ox = (screenW - pw) / 2, oy = (screenH - ph) / 2;
                bounds.put(p.getId(), new PanelBounds(ox - leftPos, oy - topPos, pw, ph));
                continue;
            }

            PanelPosition pos = p.getPosition();
            switch (pos.mode()) {
                case REGION -> {
                    MenuRegion region = pos.menuRegion();
                    if (region == null) continue; // malformed — skip defensively
                    // SHARED ENGINE — feed the anchor-aware width+height budget
                    // against the MAIN frame, EXACTLY as the vanilla path feeds it
                    // against the menu frame (RegionRegistry.resolveAround). This is
                    // the fix for the resize-engine split: a sibling now shrinks /
                    // wraps / auto-scrolls into the room its anchor leaves toward the
                    // screen edge, instead of measuring at full natural width and
                    // overlapping the main frame.
                    RegionRegistry.feedRegionBudget(p, region, frame, pad, screenW, screenH);
                    int[] s = sizeFn.apply(p);
                    int pw = s[0], ph = s[1];
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
                    // Chrome anchored to a fixed SCREEN-edge spot (Back button,
                    // title). It floats free of the frame, so it gets the centred-
                    // screen width budget (wrap to the screen, not to an anchor's
                    // room) and resolves via the SAME screen-edge geometry MKScreen
                    // uses — one screen-anchor placement in either context.
                    feedCentered(p, screenW);
                    int[] s = sizeFn.apply(p);
                    int pw = s[0], ph = s[1];
                    ScreenRegion anchor = pos.screenAnchor();
                    if (anchor == null) anchor = ScreenRegion.TOP_LEFT;
                    ScreenOrigin so = RegionMath.resolveScreenRegion(
                            anchor, screenW, screenH, pw, ph, margin);
                    bounds.put(p.getId(), new PanelBounds(so.x() - leftPos, so.y() - topPos, pw, ph));
                }
                default -> {
                    // BODY (or any non-region) panel on a main screen has no anchor
                    // in this model — skip rather than guess a position.
                }
            }
        }
        return new Result(leftPos, topPos, mainW, frameH, bounds);
    }

    /**
     * Centred-panel width budget — fed BEFORE measuring a panel that floats or
     * centres on the screen window (the MAIN frame, an overlay, or screen-
     * anchored chrome): it may grow until {@link RegionConstants#SCREEN_EDGE_MARGIN}
     * from BOTH screen edges, then wrap. Height grows naturally; only region
     * siblings auto-scroll into an anchor's room (②). Mirrors the centred budget
     * {@code MKScreen}/{@code MKCHandledScreen} feed a BODY-stack panel, so the
     * main-path and legacy-path resize engines agree.
     */
    private static void feedCentered(Panel p, int screenW) {
        p.setAvailableContentWidth(
                screenW - 2 * RegionConstants.SCREEN_EDGE_MARGIN - 2 * p.interiorPadding());
    }

    /**
     * Centred-frame HEIGHT budget — the vertical twin of {@link #feedCentered},
     * fed to the MAIN frame so it auto-scrolls when its content is taller than the
     * screen (the "shrink-to-fit is the default" principle applied to the frame, not
     * just to region siblings). The budget is the content-height between the top
     * chrome and the bottom screen-edge margin: the full screen height, minus a
     * margin at each edge, minus one {@link #TITLE_STRIP} for the title band (drawn
     * at the screen top for a standalone screen, or inside the frame for a
     * container), minus the panel's own interior padding. When the content fits under
     * this ceiling the feed is inert (no scroll, no scrollbar reserve); only a
     * genuine overflow builds the ScrollContainer (Panel.ensureConfigured, gated by
     * MIN_SCROLL_VIEWPORT). Only the MAIN frame auto-fits height this way — an
     * overlay / dialog grows naturally (a too-tall overlay is a consumer bug, not
     * something to silently scroll) and screen-anchored chrome is small by design.
     */
    private static void feedMainHeight(Panel p, int screenH) {
        p.setAvailableContentHeight(
                screenH - 2 * RegionConstants.SCREEN_EDGE_MARGIN - TITLE_STRIP - 2 * p.interiorPadding());
    }
}
