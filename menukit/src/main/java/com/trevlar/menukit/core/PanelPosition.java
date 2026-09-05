package com.trevlar.menukit.core;

import com.trevlar.menukit.inject.ScreenOrigin;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Describes how a panel is positioned within a screen's layout.
 *
 * <p><b>Movement ③ — the main-panel + region model.</b> A custom screen names
 * ONE panel as its {@link Mode#MAIN main} = its frame (centred on the screen,
 * exactly like a vanilla container's menu frame). Every other panel anchors to
 * that frame with a {@link Mode#REGION region} — the SAME {@link MenuRegion}
 * vocabulary and the SAME {@link RegionMath} resolver vanilla-injected panels
 * use against the menu frame — so siblings inherit overlay-centring (①) and
 * vertical edge-awareness + auto-scroll (②) for free. This retired the old
 * relative verbs (rightOf / leftOf / above / below), which were a second,
 * edge-unaware placement system bolted onto this enum.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link Mode#BODY} — stacks vertically in a single centred column
 *       (default; the legacy regime for simple standalone screens with no
 *       designated main panel).</li>
 *   <li>{@link Mode#MAIN} — the screen's frame: one per screen, centred, the
 *       anchor every {@code REGION} sibling resolves against.</li>
 *   <li>{@link Mode#REGION} — anchored to the main panel via a {@link MenuRegion}
 *       (RIGHT_ALIGN_TOP, BOTTOM_CENTER, …), resolved by {@link RegionMath}
 *       against the main panel's bounds.</li>
 *   <li>{@link Mode#SCREEN_ANCHOR} — pinned to a fixed screen corner (chrome).</li>
 *   <li>{@link Mode#CENTER} — a screen-centred overlay, drawn on top.</li>
 *   <li>{@link Mode#PIXEL} — pixel-precision override: the outer origin comes
 *       from a per-frame consumer supplier, in absolute screen pixels. The
 *       precision escape for positions regions can't express (§0057 Revision);
 *       see {@link #pixel}.</li>
 * </ul>
 *
 * <p>This is declarative metadata. The screen reads it during layout
 * computation to determine where each panel goes.
 */
public record PanelPosition(Mode mode,
                            @Nullable String anchorPanelId,
                            @Nullable ScreenRegion screenAnchor,
                            @Nullable MenuRegion menuRegion,
                            @Nullable Supplier<ScreenOrigin> pixelOrigin) {

    /** How a panel is positioned. */
    public enum Mode {
        /** Stacks vertically in a single centred column. Default. */
        BODY,
        /**
         * The screen's frame (Movement ③) — centred on the screen window, the
         * anchor every {@link #REGION} sibling resolves against. Exactly one
         * panel per screen should be {@code MAIN}; it is the custom-screen
         * analogue of a vanilla container's menu frame.
         */
        MAIN,
        /**
         * Anchored to the {@link #MAIN} panel via a {@link MenuRegion} (Movement
         * ③). Resolved by {@link RegionMath} against the main panel's bounds —
         * the same path vanilla-injected panels take against the menu frame, so
         * the panel is edge-aware on both axes and auto-scrolls on overflow.
         */
        REGION,
        /**
         * Pinned to a fixed {@link ScreenRegion screen-edge spot} (Pass 3), inset
         * by {@link RegionConstants#SCREEN_EDGE_MARGIN}. Excluded from the layout's
         * extent — chrome like a "Back" button (TOP_LEFT) or a title (TOP_CENTER)
         * stays put regardless of content size. See {@link #screenAnchor}.
         */
        SCREEN_ANCHOR,
        /**
         * Centred on the screen as an overlay — excluded from the body stack's
         * layout + extent, auto-centred on the screen window, and drawn on top
         * of the body in the overlay pass. The placement for dialogs, popovers,
         * and any panel that floats <em>over</em> the screen rather than flowing
         * in its column. This is purely a POSITION; it is independent of the
         * {@code dimsBehind}/{@code opaque}/{@code tracksAsModal} visual+input
         * flags (M9 doctrine — those compose freely with this). See
         * {@link #center}.
         */
        CENTER,
        /**
         * Pixel-precision override (§0057 Revision, Trev's call) — the panel's
         * outer origin comes from a consumer supplier, re-evaluated <b>every
         * frame</b>, in absolute screen pixels. The precision escape for the
         * positions regions cannot express: a point <em>inside</em> the frame
         * (e.g. directly above the offhand slot) or an origin that moves per
         * frame (e.g. a row centred over the hovered hotbar column). Declarative
         * regions remain the default; reach for this only when a region can't
         * say it. See {@link #pixel}.
         */
        PIXEL
    }

    /** Default position: body panel, stacks vertically. */
    public static final PanelPosition BODY =
            new PanelPosition(Mode.BODY, null, null, null, null);

    /**
     * The screen's main panel = its frame (Movement ③). Centred on the screen
     * window; every {@link #region} sibling anchors to its bounds. Exactly one
     * panel per screen should be {@code main()}.
     */
    public static PanelPosition main() {
        return new PanelPosition(Mode.MAIN, null, null, null, null);
    }

    /**
     * Anchors the panel to the main panel via {@code region} (Movement ③) — the
     * same {@link MenuRegion} vocabulary vanilla-injected panels use against the
     * menu frame. RIGHT_ALIGN_TOP sits it to the right of the main panel, top-
     * aligned; BOTTOM_CENTER below it, centred; and so on. Resolved by
     * {@link RegionMath} against the main panel's bounds, so it is edge-aware on
     * both axes and auto-scrolls when it would overflow the screen.
     */
    public static PanelPosition region(MenuRegion region) {
        return new PanelPosition(Mode.REGION, null, null, region, null);
    }

    /**
     * Pins the panel to a fixed {@link ScreenRegion screen-edge spot} (Pass 3) —
     * inset by {@link RegionConstants#SCREEN_EDGE_MARGIN} from the edges that spot
     * touches. The canonical screen-chrome placement: a "&lt; Back" button at
     * {@code TOP_LEFT}, a title at {@code TOP_CENTER}, a status line at
     * {@code BOTTOM_CENTER}. Positioned independently of the layout (contributes
     * nothing to its extent), so it stays put no matter how content sizes,
     * wraps, or scrolls.
     *
     * <p>Honored by both {@link com.trevlar.menukit.screen.MKScreen} (the
     * standalone screen) and {@link MainRegionLayout} (a custom container screen),
     * via {@link RegionMath#resolveScreenRegion} — the SAME screen-edge placement
     * in either context.
     */
    public static PanelPosition screenAnchor(ScreenRegion region) {
        return new PanelPosition(Mode.SCREEN_ANCHOR, null, region, null, null);
    }

    /**
     * Centres the panel on the screen as an overlay — excluded from the layout,
     * auto-centred on the screen window, and drawn on top. The canonical
     * placement for dialogs, popovers, and any panel that floats <em>over</em>
     * the screen instead of flowing in its column.
     *
     * <p>Position only: it composes freely with the {@code dimsBehind} (visual
     * dim), {@code opaque} (click-eat), and {@code tracksAsModal} (input-block)
     * flags per the M9 doctrine — a panel can be a centred overlay with any,
     * all, or none of them. Honored by
     * {@link com.trevlar.menukit.screen.MKScreen}.
     */
    public static PanelPosition center() {
        return new PanelPosition(Mode.CENTER, null, null, null, null);
    }

    /**
     * Pixel-precision position override (§0057 Revision — Trev's call,
     * 2026-07-01): places the panel's <b>outer</b> top-left (the background
     * origin; elements render inside at origin + padding) at exactly the
     * coordinates {@code origin} supplies, in <b>absolute screen pixels</b>.
     *
     * <p><b>Re-evaluated every frame.</b> The supplier runs on each layout/render
     * pass, so an origin computed from live state — a resolved vanilla slot rect
     * ({@link com.trevlar.menukit.inject.VanillaSlotResolver#resolve}), a
     * hovered hotbar column, a user-mutable count — tracks that state with no
     * consumer re-registration. Returning {@code null} skips the panel this frame
     * (not rendered, not hit-testable) — the natural "this screen doesn't surface
     * my anchor" escape, e.g. when a slot resolver comes back empty.
     *
     * <p><b>The precision escape, not the default.</b> Declarative regions remain
     * the placement model (§0057); {@code pixel(...)} exists for the positions
     * regions cannot express — a point <em>inside</em> the content frame, or an
     * origin that is genuinely dynamic per frame. Unlike the deleted lambda-anchor
     * escape hatch (a parallel placement system at the adapter layer), this is a
     * position KIND inside the one model: resolved by the same drivers, riding the
     * same render/input/opacity machinery as every other panel.
     *
     * <p><b>No reactive budgets.</b> A pixel panel measures at its natural size —
     * the engine feeds it no screen-edge wrap/scroll ceiling, because pixel
     * placement means the consumer owns the exact geometry (§0057's reactive
     * default would fight the precision). Keeping it on-screen is the supplier's
     * contract.
     *
     * <p>Honored by the vanilla-screen injection path
     * ({@link com.trevlar.menukit.inject.ScreenPanelAdapter} — including
     * {@code MKCContainerPanel} parity panels), {@link MainRegionLayout} (custom
     * screens with a {@code main()} frame), and
     * {@link com.trevlar.menukit.screen.MKScreen}'s legacy path. (The legacy
     * BODY-stack path on a custom <em>container</em> screen predates every
     * non-BODY mode and does not resolve them — use {@code main()} there.)
     *
     * @param origin per-frame supplier of the panel's outer top-left in absolute
     *               screen pixels; {@code null} return = skip this frame
     */
    public static PanelPosition pixel(Supplier<ScreenOrigin> origin) {
        return new PanelPosition(Mode.PIXEL, null, null, null, origin);
    }
}
