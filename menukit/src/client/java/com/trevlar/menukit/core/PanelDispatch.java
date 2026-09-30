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
 * iteration uniform as well. Since 6.0.0 (§0065) the surrounding wrapping is
 * uniform too: every context renders through {@code PanelHost.render}, this
 * primitive's one caller.
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
}
