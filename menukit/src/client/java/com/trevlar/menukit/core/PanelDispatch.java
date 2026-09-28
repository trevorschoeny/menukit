package com.trevlar.menukit.core;

/**
 * Phase 16j R2 — shared element-iteration primitive across the four
 * rendering contexts (MK standalone screens, MKC container screens,
 * HUD overlays, vanilla-screen injection adapters). The per-element
 * render contract is {@link PanelElement#render(RenderContext)} and
 * was always uniform; this primitive makes the surrounding
 * <em>iteration</em> uniform too.
 *
 * <h3>Why a separate utility</h3>
 *
 * Each context wraps the iteration with context-specific positioning,
 * backgrounds, and bookkeeping (HUD: visibility gates + region resolution;
 * standalone: layout-relative content origin; container: post-recenter
 * leftPos/topPos; injection: origin-fn-resolved screen origin). The
 * wrapping legitimately varies — but the per-frame "build ctx, iterate
 * elements, skip-if-hidden, render" inner loop is identical in all four.
 * Pre-16j it was duplicated four times; bug fixes to the iteration shape
 * (e.g., element-visibility semantics, future per-element pre/post hooks)
 * had to be applied four ways.
 *
 * <p>Per §0027: the rendering contract is uniform. R2 makes the
 * iteration uniform as well.
 */
public final class PanelDispatch {

    private PanelDispatch() {}

    /**
     * Iterates {@code panel}'s elements in declaration order, skipping any
     * whose {@link PanelElement#isVisible()} returns {@code false}, and
     * dispatches each visible element's {@code render(ctx)}.
     *
     * <p>Doesn't draw the panel background — that's a per-context concern
     * (HUD optionally uses {@code panel.getStyle()}; standalone always
     * does; MKC does as part of {@code renderBg}; injection adapter has
     * its own variant). Callers are expected to draw the background
     * <em>before</em> calling this dispatch so elements layer on top.
     *
     * @param panel the panel whose elements to render
     * @param ctx   the render context, already positioned at the content
     *              origin (panel origin + padding) by the caller
     */
    public static void renderElements(Panel panel, RenderContext ctx) {
        // Panel-aware path: each element's visibility folds in its engine VISIBILITY
        // (and, via the owner-chain cascade, a hidden parent panel) — resolved
        // CLIENT-side here. Two passes (base then overlay), like the List overload.
        java.util.List<PanelElement> elements = panel.getElements();
        for (PanelElement element : elements) {
            if (!com.trevlar.menukit.window.ClientWindowVisibility.elementShown(panel, element)) continue;
            element.render(ctx);
        }
        for (PanelElement element : elements) {
            if (!com.trevlar.menukit.window.ClientWindowVisibility.elementShown(panel, element)) continue;
            element.renderOverlay(ctx);
        }
    }

    /**
     * Iterates {@code elements} in declaration order, skipping any whose
     * {@link PanelElement#isVisible()} returns {@code false}, and
     * dispatches each visible element's {@code render(ctx)}. Used by the
     * HUD render path, which holds elements on {@code MKHudPanelDef}
     * (a record) rather than {@code Panel}.
     */
    public static void renderElements(java.util.List<PanelElement> elements, RenderContext ctx) {
        // Phase 18s follow-up — two-pass render so elements with
        // transient overlays (Dropdown popovers, etc.) always draw on
        // top regardless of declaration order.
        //
        // Pass 1: every visible element's base render. Layout-bounds
        // content paints in declaration order (later elements draw over
        // earlier ones, as before).
        //
        // Pass 2: every visible element's renderOverlay (default no-op).
        // The element with an active overlay paints its overlay AFTER
        // every sibling's base render — so an open Dropdown popover
        // visually obscures any sibling element underneath it, no
        // matter where in the elements list the Dropdown was declared.
        //
        // Sibling to the input-side fix: getActiveOverlayBounds + the
        // panel-adapter active-overlay dispatch Pass 1 (see
        // VanillaScreenPanelAdapter.mouseClicked) make clicks under an
        // overlay route exclusively to the overlay's owner. Together
        // they make overlays inert-on-top in both render AND input
        // dimensions.
        for (PanelElement element : elements) {
            if (!element.isVisible()) continue;
            element.render(ctx);
        }
        for (PanelElement element : elements) {
            if (!element.isVisible()) continue;
            element.renderOverlay(ctx);
        }
    }
}
