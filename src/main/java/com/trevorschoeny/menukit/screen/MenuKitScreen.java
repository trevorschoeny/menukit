package com.trevorschoeny.menukit.screen;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelBounds;
import com.trevorschoeny.menukit.core.PanelDispatch;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.PanelTreeLayout;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for standalone screens built with MenuKit — full-screen,
 * client-local, interactive UIs that are not tied to a container menu.
 *
 * <p>Extends vanilla's {@link Screen} directly; a MenuKit standalone screen
 * <em>is</em> a vanilla Screen. Ecosystem mixins into {@code Screen} affect
 * MenuKit standalone screens identically (vanilla-screen substitutability).
 *
 * <p>Holds a list of {@link Panel}s (element-only; no slot groups — those
 * are inventory-menu machinery). Layout is resolved via
 * {@link PanelLayout} using the same {@link com.trevorschoeny.menukit.core.PanelPosition}
 * constraint system inventory-menu screens use. Panels are centered on the
 * screen; elements render on top of their panels; input is dispatched to
 * elements in reverse panel order (top-most first) with the first consumer
 * winning.
 *
 * <p>This is a minimal base class. Keyboard handling, focus management, and
 * drag modes are not implemented — they'll land in later phases as the
 * element palette surfaces need for them.
 *
 * @see MenuKitHandledScreen inventory-menu analogue (holds slots + sync)
 */
public class MenuKitScreen extends Screen {

    /**
     * Padding inside each styled panel (pixels from panel edge to content).
     * Phase 18r — actual padding applied is style-conditional via
     * {@link Panel#interiorPadding()}: {@code PANEL_PADDING} for styled
     * panels (RAISED / DARK / INSET), {@code 0} for {@link PanelStyle#NONE}.
     * The constant is retained for consumers who want the styled-panel value.
     */
    protected static final int PANEL_PADDING = 7;
    /** Vertical gap between body panels. */
    protected static final int BODY_GAP = 14;
    /** Gap between a relative panel and its anchor. */
    protected static final int RELATIVE_GAP = 4;
    /** Vertical space reserved above the first panel for the title. */
    protected static final int TITLE_HEIGHT = 14;

    private final List<Panel> panels;

    /** Panel ID → computed layout bounds (in layout-local space). */
    private Map<String, PanelBounds> panelBounds = new LinkedHashMap<>();

    /** Screen-space offset applied to layout-local coordinates; computed per init. */
    private int leftPos = 0;
    private int topPos = 0;

    protected MenuKitScreen(Component title, List<Panel> panels) {
        super(title);
        this.panels = List.copyOf(panels);
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        // Phase 14d-3 — fire onAttach lifecycle hook on each panel
        // element so widget-wrapping elements (TextField etc.) can
        // register vanilla widgets via addRenderableWidget.
        for (Panel panel : panels) {
            for (PanelElement element : panel.getElements()) {
                element.onAttach(this);
            }
        }
        // Phase 17 — register panel rendering as a vanilla Renderable so it
        // participates in Screen.render's renderables iteration. The
        // iteration fires BEFORE the end-of-frame tooltip flush, so widgets
        // calling GuiGraphics.setTooltipForNextFrame during render get their
        // tooltip drawn in the same frame. Pre-Phase-17 we rendered panels
        // AFTER super.render in this class's own render() override — that
        // still beat the tooltip flush in theory, but routing through the
        // standard renderables iteration is the architecturally clean path
        // and matches how vanilla buttons participate.
        //
        // Added AFTER super.init() so MK panels render AFTER any vanilla-added
        // renderables (their order in the renderables list = paint order).
        // Cleared by Screen.clearWidgets() on next init() — re-register on
        // every init() (including resize) keeps the registration fresh.
        this.addRenderableOnly(this::renderPanels);
    }

    /**
     * Renders all visible panels in two passes: backgrounds first, then
     * element layers. Called from the renderables iteration registered in
     * {@link #init()}. Pre-Phase-17 this body lived in {@code render()}
     * after {@code super.render(...)}; moved into a {@link Renderable} so
     * tooltip-flush ordering is correct.
     *
     * <p>Recomputes layout each frame so panels whose visibility is
     * supplier-driven (e.g., a modal panel gated by {@link Panel#showWhen})
     * get bounds entries when they become visible mid-screen. Matches
     * {@code MenuKitHandledScreen.renderBg}'s per-frame
     * {@code computeLayout()} for the same reason. Cheap — a few additions
     * per panel.
     */
    private void renderPanels(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeLayout();

        // ── Modal state survey ────────────────────────────────────────
        // anyDimBehind   → render a dim overlay between non-dim and dim panels
        // anyTracksModal → suppress hover/clicks on non-modal-tracking panels
        boolean anyDimBehind = false;
        boolean anyTracksModal = false;
        for (Panel p : panels) {
            if (!p.isVisible()) continue;
            if (p.dimsBehind()) anyDimBehind = true;
            if (p.tracksAsModal()) anyTracksModal = true;
        }

        // ── Pass 1: non-dim panels ────────────────────────────────────
        // Non-modal-tracking panels render with sentinel mouse coords
        // when a modal is up, so their widgets behave inert (no hover,
        // no tooltip, no element-level click hit-test). Mirrors how
        // ScreenPanelAdapter handles modal-tracking on vanilla screens.
        for (Panel p : panels) {
            if (!p.isVisible() || p.dimsBehind()) continue;
            boolean suppressMouse = anyTracksModal && !p.tracksAsModal();
            renderSinglePanel(p, graphics,
                    suppressMouse ? -1 : mouseX,
                    suppressMouse ? -1 : mouseY);
        }

        // ── Pass 2: dim overlay (between non-dim and dim panels) ──────
        // Covers the full screen (vanilla bg + non-dim panels) so dim
        // panels read as visually elevated. ~75% black — matches the
        // ScreenPanelRegistry value (kept consistent across render paths).
        if (anyDimBehind) {
            graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        }

        // ── Pass 3: dim panels on top of dim ──────────────────────────
        // Dim panels keep real mouse coords — they're the active surface.
        for (Panel p : panels) {
            if (!p.isVisible() || !p.dimsBehind()) continue;
            renderSinglePanel(p, graphics, mouseX, mouseY);
        }
    }

    /**
     * Renders a single panel's background + elements + panel-level tooltip.
     * Called per-panel from the modal-aware 3-pass loop in
     * {@link #renderPanels}. {@code mouseX}/{@code mouseY} may be sentinel
     * {@code -1} when modal-tracking has suppressed this panel's interactive
     * state — in that case the panel's elements receive the sentinel via
     * {@link RenderContext} and behave inert.
     */
    private void renderSinglePanel(Panel panel, GuiGraphics graphics, int mouseX, int mouseY) {
        int[] rect = effectivePanelScreenBounds(panel);
        if (rect == null) return;
        int x = rect[0], y = rect[1], w = rect[2], h = rect[3];

        // Background
        PanelRendering.renderPanel(graphics, x, y, w, h, panel.getStyle());

        // Elements — interior padding is style-conditional (0 for NONE,
        // PANEL_PADDING otherwise) per Panel.interiorPadding().
        int padding = panel.interiorPadding();
        int contentX = x + padding;
        int contentY = y + padding;
        RenderContext ctx = new RenderContext(graphics, contentX, contentY, mouseX, mouseY);
        PanelDispatch.renderElements(panel, ctx);

        // Panel-level tooltip — fires over the panel's outer bounds.
        // Queued AFTER element render so it wins last-call-wins
        // semantics for setTooltipForNextFrame.
        panel.maybeQueueTooltip(graphics, x, y, w, h,
                mouseX, mouseY, ctx.hasMouseInput());
    }

    @Override
    public void removed() {
        // Phase 14d-3 — fire onDetach so widget-wrapping elements can
        // unregister via screen.removeWidget. Mirror of init's onAttach.
        //
        // Phase 16h note: cursor capture used to live here as a custom
        // override branch. It's now handled by CursorContinuity (which
        // registers a per-screen ScreenEvents.remove listener when the
        // consumer opts in), so removed() goes back to its single
        // concern — element detach lifecycle.
        for (Panel panel : panels) {
            for (PanelElement element : panel.getElements()) {
                element.onDetach(this);
            }
        }
        super.removed();
    }

    // ── Layout ──────────────────────────────────────────────────────────

    /**
     * Returns the outer size (background extent, including padding) of a
     * panel for layout. Defers to {@link Panel#getWidth()} and
     * {@link Panel#getHeight()} for the content extent — those handle
     * pinned dims (M5) and Phase 16g auto-scroll wrapping authoritatively.
     * The screen's job is just to add its own {@link #PANEL_PADDING} to
     * produce the outer bounds.
     *
     * <p>Phase 16g bug fix: prior versions re-iterated panel elements and
     * computed extent locally, bypassing pinned dims and missing the
     * scroll-container-outer-width contribution when auto-scroll fired.
     * Using the panel's own size methods keeps the screen and the panel
     * agreeing on size in all configurations.
     */
    private int[] computePanelSize(Panel panel) {
        int padding = panel.interiorPadding();
        return new int[]{
                panel.getWidth() + 2 * padding,
                panel.getHeight() + 2 * padding
        };
    }

    /**
     * Returns the panel's screen-space bounds as
     * {@code [x, y, width, height]}. Used by render + input dispatch so
     * they agree on where the panel actually is.
     *
     * <p>Two layout regimes:
     * <ul>
     *   <li><b>Overlay panels</b> ({@link Panel#dimsBehind} true) — auto-
     *       centered on the screen. Their declared {@link PanelPosition}
     *       and the layout-computed bounds in {@link #panelBounds} are
     *       ignored. An overlay's defining property is "covers the screen
     *       above the dim layer"; BODY-stacking semantics don't fit.
     *       This is what makes {@code Panel.modal()} read as "modal
     *       overlay" on {@link MenuKitScreen} without consumers also
     *       configuring a position mode.</li>
     *   <li><b>Layout panels</b> (everything else) — use the bounds
     *       computed by {@link PanelTreeLayout}, translated by
     *       {@link #leftPos}/{@link #topPos}.</li>
     * </ul>
     */
    private int[] effectivePanelScreenBounds(Panel panel) {
        int[] size = computePanelSize(panel);
        int outerW = size[0], outerH = size[1];

        if (panel.dimsBehind()) {
            int x = (this.width - outerW) / 2;
            int y = (this.height - outerH) / 2;
            return new int[]{x, y, outerW, outerH};
        }

        PanelBounds bounds = panelBounds.get(panel.getId());
        if (bounds == null) return null;
        return new int[]{
                leftPos + bounds.x(),
                topPos + bounds.y(),
                bounds.width(),
                bounds.height()
        };
    }

    private void computeLayout() {
        // Phase 16j R1 — delegate to shared PanelTreeLayout primitive.
        // MK has no minimum image size (standalone screens are sized by
        // their content); pass 0 for both min dims.
        var layout = PanelTreeLayout.resolve(
                panels, this::computePanelSize,
                BODY_GAP, RELATIVE_GAP, TITLE_HEIGHT,
                /*minImageWidth=*/ 0, /*minImageHeight=*/ 0);
        panelBounds = layout.bounds();
        leftPos = (width  - layout.totalWidth())  / 2 - layout.layoutOriginX();
        topPos  = (height - layout.totalHeight()) / 2 - layout.layoutOriginY();
    }

    // ── Rendering ───────────────────────────────────────────────────────
    //
    // Panel rendering is registered as a vanilla Renderable in init() (see
    // renderPanels above). No explicit render() override needed — vanilla's
    // Screen.render iterates renderables, calls our renderPanels callback,
    // and the end-of-frame tooltip flush happens AFTER that. Widgets
    // calling GuiGraphics.setTooltipForNextFrame during render get their
    // tooltip drawn in the same frame.

    // ── Input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        if (dispatchElementClick(event.x(), event.y(), event.button())) {
            return true;
        }
        // Modal click-eat: when a tracksAsModal panel is visible and the
        // click landed OUTSIDE its bounds (so dispatchElementClick above
        // didn't route to one of its elements), eat the click so the
        // underlying screen doesn't receive it either. Mirrors
        // ScreenPanelRegistry.dispatchOpaqueClick's behavior for vanilla
        // container screens. Click-eat returns BEFORE super.mouseClicked
        // so the underlying Screen's machinery (e.g., creative-tab
        // selection) doesn't fire.
        if (anyVisibleModalTrackingPanel()) {
            return true;
        }
        return super.mouseClicked(event, flag);
    }

    /** Returns true if at least one visible panel has {@code tracksAsModal()} set. */
    private boolean anyVisibleModalTrackingPanel() {
        for (Panel p : panels) {
            if (p.isVisible() && p.tracksAsModal()) return true;
        }
        return false;
    }

    /**
     * Dispatches a click to panel elements in reverse panel order (the
     * last-declared panel's elements get first crack, matching visual
     * z-order). Returns true if any element consumed the click.
     *
     * <p>Phase 14d-5 — two-pass dispatch:
     * <ol>
     *   <li><b>Pass 1: active-overlay claims.</b> Any element with an
     *       {@link PanelElement#getActiveOverlayBounds active overlay}
     *       (e.g., Dropdown's popover when open) gets exclusive dispatch
     *       over its overlay region — the click is dropped or consumed
     *       by that element regardless of {@code mouseClicked}'s return,
     *       so behind elements stay innately inert (parallel to M9's
     *       panel-level modal click-eat, at element granularity).</li>
     *   <li><b>Pass 2: normal hit-test.</b> If no active overlay claims
     *       the click, fall through to standard {@link PanelElement#hitTest}-
     *       gated dispatch on each element's layout bounds.</li>
     * </ol>
     */
    private boolean dispatchElementClick(double mouseX, double mouseY, int button) {
        // Modal-aware dispatch: when a tracksAsModal panel is visible, only
        // its own elements are eligible to receive the click — clicks on
        // underlying panels' elements are inert. Mirrors how
        // ScreenPanelAdapter passes mouseX = -1 to non-modal-tracking
        // panels during render, but for click dispatch we filter the panel
        // list directly. Underlying panel elements never see the click.
        boolean modalUp = anyVisibleModalTrackingPanel();

        List<Panel> reversed = panels.reversed();

        // ── Pass 1: active-overlay exclusive claims ───────────────────
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
            if (modalUp && !panel.tracksAsModal()) continue; // modal-blocked
            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;
                int[] overlay = element.getActiveOverlayBounds();
                if (overlay != null
                        && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                        && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
                    element.mouseClicked(mouseX, mouseY, button);
                    return true;     // exclusive — no further dispatch
                }
            }
        }

        // ── Pass 2: normal hit-test dispatch ──────────────────────────
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
            if (modalUp && !panel.tracksAsModal()) continue; // modal-blocked
            int[] rect = effectivePanelScreenBounds(panel);
            if (rect == null) continue;

            int padding = panel.interiorPadding();
            int contentX = rect[0] + padding;
            int contentY = rect[1] + padding;

            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;

                // hit-test via PanelElement.hitTest (default = layout-bounds)
                if (element.hitTest(mouseX, mouseY, contentX, contentY)) {
                    if (element.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── Scroll + release dispatch (Phase 14d-2.6 primitive-gap fold-inline) ─
    //
    // ScreenPanelAdapter (the MenuContext path) dispatches scroll + release
    // to elements via Fabric's ScreenMouseEvents. MenuKitScreen
    // (StandaloneContext) didn't have parallel plumbing because no
    // consumer surfaced the need until the Test Hub (Phase 14d-2.6) wanted
    // a ScrollContainer inside a MenuKit-native standalone screen. Adding
    // here as a primitive-gap fold-inline per TESTING_CONVENTIONS.md
    // structural test sentence.
    //
    // Shape mirrors ScreenPanelAdapter's element dispatch:
    //   - mouseScrolled: hit-tested against element bounds (only the
    //     element under the cursor receives scroll)
    //   - mouseReleased: NOT hit-tested (every visible element receives
    //     release for drag-end detection — fires regardless of cursor
    //     position, per 14d-2 ScrollContainer plumbing)

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double scrollX, double scrollY) {
        if (dispatchElementScroll(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean dispatchElementScroll(double mouseX, double mouseY,
                                           double scrollX, double scrollY) {
        // Same two-pass dispatch as dispatchElementClick — see its
        // javadoc for the overlay-claim-then-hit-test rationale.
        // Same modal-aware filter: when modal-tracking is up, only
        // tracksAsModal panels are eligible.
        boolean modalUp = anyVisibleModalTrackingPanel();
        List<Panel> reversed = panels.reversed();

        // Pass 1: active-overlay exclusive claims
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
            if (modalUp && !panel.tracksAsModal()) continue;
            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;
                int[] overlay = element.getActiveOverlayBounds();
                if (overlay != null
                        && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                        && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
                    element.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                    return true;     // exclusive
                }
            }
        }

        // Pass 2: normal hit-test
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
            if (modalUp && !panel.tracksAsModal()) continue;
            int[] rect = effectivePanelScreenBounds(panel);
            if (rect == null) continue;

            int padding = panel.interiorPadding();
            int contentX = rect[0] + padding;
            int contentY = rect[1] + padding;

            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;

                if (element.hitTest(mouseX, mouseY, contentX, contentY)) {
                    if (element.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // Release fires for every visible element regardless of cursor
        // position — drag-end detection per 14d-2 ScrollContainer plumbing.
        // Release is NOT modal-filtered — an underlying widget that started
        // a drag (before the modal opened) needs its release to fire so it
        // can clean up drag state. Mirrors ScreenPanelRegistry's release
        // dispatch which fires for every adapter regardless of modal.
        for (Panel panel : panels) {
            if (!panel.isVisible()) continue;
            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;
                element.mouseReleased(event.x(), event.y(), event.button());
            }
        }
        return super.mouseReleased(event);
    }

    // ── Panel Access ────────────────────────────────────────────────────

    /** Returns the ordered list of panels (immutable). */
    public List<Panel> getPanels() { return panels; }
}
