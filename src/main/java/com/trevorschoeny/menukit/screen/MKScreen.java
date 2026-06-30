package com.trevorschoeny.menukit.screen;

import com.trevorschoeny.menukit.core.MainRegionLayout;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelBounds;
import com.trevorschoeny.menukit.core.PanelDispatch;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.PanelTreeLayout;
import com.trevorschoeny.menukit.core.RegionConstants;
import com.trevorschoeny.menukit.core.RegionMath;
import com.trevorschoeny.menukit.core.RenderContext;
import com.trevorschoeny.menukit.core.ScreenRegion;
import com.trevorschoeny.menukit.window.ClientWindowVisibility;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

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
 * @see MKCHandledScreen inventory-menu analogue (holds slots + sync)
 */
public class MKScreen extends Screen {

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

    /** Movement ③ — true when the screen declared a MAIN panel and computeLayout
     *  used MainRegionLayout (panelBounds already hold every panel's resolved
     *  position); false for the legacy BODY-stack path. */
    private boolean mainLayout = false;

    /**
     * Optional "return" action, run on close (Escape) instead of the default
     * close-to-game. Set when this screen was opened OVER something the consumer
     * wants to restore on exit — most importantly a live server-synced container
     * (an {@code MKCHandledScreen}): opening a plain {@link net.minecraft.client.gui.screens.Screen}
     * over a container makes vanilla close that container server-side, so
     * returning to it means RE-OPENING it (re-issuing the open request), not a
     * client-only screen swap. The return action carries that re-open. Null =
     * default close behavior (back to game / parent). See {@link #setReturnAction}.
     */
    private @org.jspecify.annotations.Nullable Runnable returnAction;

    protected MKScreen(Component title, List<Panel> panels) {
        super(title);
        this.panels = List.copyOf(panels);
    }

    /**
     * Sets the action run when this screen closes via Escape (see
     * {@link #returnAction}). Subclasses also call {@link #runReturnAction()}
     * from their own "Back" chrome so Back and Escape agree. A null action (the
     * default) leaves vanilla close behavior untouched.
     */
    protected void setReturnAction(@org.jspecify.annotations.Nullable Runnable returnAction) {
        this.returnAction = returnAction;
    }

    /** Whether a return action is set (Back chrome can branch on this). */
    protected boolean hasReturnAction() {
        return returnAction != null;
    }

    /**
     * Runs the return action if one is set and returns true; otherwise returns
     * false so the caller can fall back to its default navigation. Idempotent
     * intent: the action is expected to {@code setScreen(...)} away from here.
     */
    protected boolean runReturnAction() {
        if (returnAction == null) return false;
        returnAction.run();
        return true;
    }

    @Override
    public void onClose() {
        // Escape: if a return action is set (e.g. re-open the container this
        // screen was launched over), run it instead of the default close. The
        // default closes to the game/parent — wrong when we floated over a live
        // container that vanilla closed server-side on the way in.
        if (runReturnAction()) return;
        super.onClose();
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
                // Detach-then-attach: on a RESIZE, vanilla runs clearWidgets()
                // + init() WITHOUT firing removed(), so a widget-wrapping element
                // (Slider/TextField) whose onAttach short-circuits on
                // attachedScreen==this would never re-register → dead after
                // resize. The onDetach first resets that latch so onAttach
                // re-adds the vanilla widget. First init: onDetach is a no-op.
                element.onDetach(this);
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
     * {@code MKCHandledScreen.renderBg}'s per-frame
     * {@code computeLayout()} for the same reason. Cheap — a few additions
     * per panel.
     */
    private void renderPanels(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeLayout();

        // Pass 3 — draw the screen title centered at the top edge. MKScreen
        // reserves TITLE_HEIGHT above the body for it, but never painted it, so
        // every standalone screen showed a blank title band. The corner Back
        // panel is top-LEFT, so a top-center title never collides with it.
        graphics.drawCenteredString(this.font, this.title,
                this.width / 2, RegionConstants.SCREEN_EDGE_MARGIN, 0xFFFFFFFF);

        // ── Modal state survey ────────────────────────────────────────
        // anyDimBehind   → render a dim overlay between non-dim and dim panels
        // anyTracksModal → suppress hover/clicks on non-modal-tracking panels
        boolean anyDimBehind = false;
        boolean anyTracksModal = false;
        for (Panel p : panels) {
            if (!ClientWindowVisibility.panelShown(p)) continue;
            if (p.dimsBehind()) anyDimBehind = true;
            if (p.tracksAsModal()) anyTracksModal = true;
        }

        // ── Pass 1: body panels (NOT overlay-positioned) ──────────────
        // The base screen content — body-stacked + screen-anchored chrome.
        // Non-modal-tracking panels render with sentinel mouse coords when a
        // modal is up, so their widgets behave inert (no hover, no tooltip, no
        // element-level click hit-test). Mirrors how ScreenPanelAdapter handles
        // modal-tracking on vanilla screens.
        for (Panel p : panels) {
            if (!ClientWindowVisibility.panelShown(p) || p.isOverlayPositioned()) continue;
            boolean suppressMouse = anyTracksModal && !p.tracksAsModal();
            renderSinglePanel(p, graphics,
                    suppressMouse ? -1 : mouseX,
                    suppressMouse ? -1 : mouseY);
        }

        // ── Pass 2: dim overlay (between body and overlay panels) ─────
        // Covers the full screen (vanilla bg + body panels) so overlay panels
        // read as visually elevated. Gated on dimsBehind() ALONE — the dim is a
        // pure visual, independent of overlay positioning (M9). ~75% black —
        // matches the ScreenPanelRegistry value (consistent across render paths).
        if (anyDimBehind) {
            graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        }

        // ── Pass 3: overlay-positioned panels, on top ─────────────────
        // Centered overlays (PanelPosition.center(), dimsBehind, or
        // tracksAsModal) draw last so they float above the body regardless of
        // declaration order. They keep real mouse coords UNLESS another modal is
        // up that they don't themselves track (same suppression as Pass 1) — so
        // the active modal stays live while any sibling overlay goes inert.
        for (Panel p : panels) {
            if (!ClientWindowVisibility.panelShown(p) || !p.isOverlayPositioned()) continue;
            boolean suppressMouse = anyTracksModal && !p.tracksAsModal();
            renderSinglePanel(p, graphics,
                    suppressMouse ? -1 : mouseX,
                    suppressMouse ? -1 : mouseY);
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
        // PURE MEASURE — the reactive-sizing budget is the layout DRIVER's job,
        // not this size function's, because the right budget depends on the
        // panel's ROLE (a centred BODY/MAIN panel wraps to the whole screen; a
        // region sibling wraps to the room its anchor leaves toward the screen
        // edge). The driver feeds setAvailableContentWidth/Height per role BEFORE
        // calling this — MainRegionLayout via the shared engine for the main path,
        // computePanelSizeCentered for the legacy BODY stack. Feeding a flat
        // budget here would clobber the driver's anchor-aware feed (last write
        // wins), which is exactly what made region siblings overlap.
        int padding = panel.interiorPadding();
        return new int[]{
                panel.getWidth() + 2 * padding,
                panel.getHeight() + 2 * padding
        };
    }

    /**
     * The legacy BODY-stack size function: feed the centred-screen width budget
     * (a body-stacked panel is centred, so it may grow until SCREEN_EDGE_MARGIN
     * from both edges, then wrap) BEFORE the pure {@link #computePanelSize}
     * measure. The main path ({@link MainRegionLayout}) feeds per role itself, so
     * it uses the pure measure directly; only the legacy path needs this wrapper.
     */
    private int[] computePanelSizeCentered(Panel panel) {
        panel.setAvailableContentWidth(
                this.width - 2 * RegionConstants.SCREEN_EDGE_MARGIN - 2 * panel.interiorPadding());
        return computePanelSize(panel);
    }

    /**
     * Returns the panel's screen-space bounds as
     * {@code [x, y, width, height]}. Used by render + input dispatch so
     * they agree on where the panel actually is.
     *
     * <p>Two layout regimes:
     * <ul>
     *   <li><b>Overlay panels</b> ({@link Panel#isOverlayPositioned()} true —
     *       i.e. {@link PanelPosition#center()}, or a {@code dimsBehind} /
     *       {@code tracksAsModal} panel) — auto-centered on the screen. Their
     *       BODY-relative layout bounds are ignored. An overlay's defining
     *       property is "floats over the screen"; body-stacking semantics don't
     *       fit. This is what makes {@code Panel.modal()} read as "modal overlay"
     *       without consumers also configuring a position mode — AND lets a
     *       non-dim, non-modal overlay opt in via {@code position(center())}.
     *       Note this is decoupled from the dim VISUAL: centering keys off
     *       {@code isOverlayPositioned()}, while the dim fill keys off
     *       {@code dimsBehind()} alone (M9 — the flags stay independent).</li>
     *   <li><b>Layout panels</b> (everything else) — use the bounds
     *       computed by {@link PanelTreeLayout}, translated by
     *       {@link #leftPos}/{@link #topPos}.</li>
     * </ul>
     */
    private int[] effectivePanelScreenBounds(Panel panel) {
        // Movement ③ main-path — MainRegionLayout already positioned EVERY panel
        // (main frame, region siblings, overlay-centred, screen-anchored chrome)
        // into leftPos/topPos-relative bounds, so just translate. The 3-pass in
        // renderPanels still orders overlays on top via isOverlayPositioned().
        if (mainLayout) {
            PanelBounds b = panelBounds.get(panel.getId());
            if (b == null) return null;
            return new int[]{leftPos + b.x(), topPos + b.y(), b.width(), b.height()};
        }

        // Legacy regime (no MAIN panel): overlay + screen-anchor panels are
        // excluded from the BODY stack, so feed their centred-screen budget here
        // (the driver-feeds-per-role rule — computePanelSize is now pure measure).
        int[] size = computePanelSizeCentered(panel);
        int outerW = size[0], outerH = size[1];

        if (panel.isOverlayPositioned()) {
            int x = (this.width - outerW) / 2;
            int y = (this.height - outerH) / 2;
            return new int[]{x, y, outerW, outerH};
        }

        // Pass 3 — screen-edge-anchored chrome (e.g. the "Back" button or a
        // title): positioned at a fixed ScreenRegion spot inset by
        // SCREEN_EDGE_MARGIN, independent of the centered body stack (which
        // excludes it from layout + extent). Same screen-edge geometry the
        // custom-container path (MainRegionLayout) uses — one rule both contexts.
        if (panel.getPosition().mode() == PanelPosition.Mode.SCREEN_ANCHOR) {
            int m = RegionConstants.SCREEN_EDGE_MARGIN;
            ScreenRegion anchor = panel.getPosition().screenAnchor();
            if (anchor == null) anchor = ScreenRegion.TOP_LEFT;
            var so = RegionMath.resolveScreenRegion(
                    anchor, this.width, this.height, outerW, outerH, m);
            return new int[]{so.x(), so.y(), outerW, outerH};
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
        if (MainRegionLayout.hasMain(panels)) {
            // Movement ③ — a standalone screen can also name a MAIN panel and
            // anchor siblings to it via MenuRegion (the unified placement model).
            // reserveTitle=false: MKScreen draws its title at the SCREEN top
            // (renderPanels), not at the frame top, so the frame needs no strip.
            // MainRegionLayout returns leftPos/topPos-relative bounds + the
            // centred frame's leftPos/topPos; mainLayout makes
            // effectivePanelScreenBounds read those bounds directly.
            var layout = MainRegionLayout.resolve(
                    panels, this::computePanelSize, this.width, this.height,
                    /*reserveTitle=*/ false);
            panelBounds = layout.bounds();
            leftPos = layout.leftPos();
            topPos  = layout.topPos();
            mainLayout = true;
            return;
        }
        mainLayout = false;
        // Legacy BODY-stack — delegate to the shared PanelTreeLayout primitive.
        // MK has no minimum image size (standalone screens are sized by their
        // content); pass 0 for both min dims.
        var layout = PanelTreeLayout.resolve(
                panels, this::computePanelSizeCentered,
                BODY_GAP, RELATIVE_GAP, TITLE_HEIGHT,
                /*minImageWidth=*/ 0, /*minImageHeight=*/ 0);
        panelBounds = layout.bounds();
        leftPos = (width  - layout.totalWidth())  / 2 - layout.layoutOriginX();
        topPos  = (height - layout.totalHeight()) / 2 - layout.layoutOriginY();
        // Pass 3 — when the panel tree is wider/taller than the screen (small
        // window / high GUI scale), centering pushes the left/top edge off-screen
        // and clips BOTH sides. Clamp so the layout's leftmost/topmost panel edge
        // stays at SCREEN_EDGE_MARGIN — the left/top content stays reachable and
        // only the far side clips. (The min panel edge sits at leftPos +
        // layoutOriginX / topPos + layoutOriginY.)
        int m = RegionConstants.SCREEN_EDGE_MARGIN;
        leftPos = Math.max(leftPos, m - layout.layoutOriginX());
        // Reserve the title band on the top clamp so a too-tall body docks BELOW
        // the centered title (drawn at y=m) instead of overprinting its first row.
        topPos  = Math.max(topPos,  m + TITLE_HEIGHT - layout.layoutOriginY());
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
        // Dismiss-on-outside-click janitor — notify every visible element whose
        // transient overlay/trigger the click fell OUTSIDE of, so open popovers
        // (Dropdown/DropdownMulti) close when you click away, even if another
        // element consumes the click. Each element self-guards (no-op unless the
        // click is genuinely outside its own bounds), so it's safe to run on
        // every click before dispatch. Element-level twin of MKFocus's
        // outside-click focus janitor; wired the same way in every dispatcher.
        notifyOutsideClickDismiss(event.x(), event.y());

        if (dispatchElementClick(event.x(), event.y(), event.button())) {
            return true;
        }
        // Modal click-eat: when a tracksAsModal panel is visible and the
        // click landed OUTSIDE its bounds (so dispatchElementClick above
        // didn't route to one of its elements), eat the click so the
        // underlying screen doesn't receive it either. Mirrors
        // ScreenPanelRegistry.dispatchCoveredClick's behavior for vanilla
        // container screens. Click-eat returns BEFORE super.mouseClicked
        // so the underlying Screen's machinery (e.g., creative-tab
        // selection) doesn't fire.
        if (anyVisibleModalTrackingPanel()) {
            return true;
        }
        // Opaque click-eat: Panel.opaque(true) promises "empty space within the
        // panel's bounds eats input" — but the modal eat-check above only covers
        // tracksAsModal panels, so a plain non-modal opaque panel would let clicks
        // fall through to panels behind it, breaking that promise. Close the gap:
        // after element dispatch (above) declined the click and no modal ate it,
        // if the click landed inside ANY visible opaque panel's resolved bounds,
        // eat it so panels/the screen behind don't receive it.
        //
        // Conservative + additive: this ONLY decides whether to EAT — it never
        // changes which elements receive clicks (element dispatch already ran and
        // returned false). Mirrors the spirit of
        // ScreenPanelRegistry.dispatchCoveredClick / findCoveringPanelAt on the
        // vanilla-container path, scoped here to MKScreen's own per-panel bounds.
        // Vanilla-widget routing (registered Slider/TextField via addWidget) MUST
        // run BEFORE the opaque click-eat. Those widgets are the panel's OWN
        // interactive content, living inside its opaque bounds — they need the
        // initiating click to start a drag / take focus. They have no
        // PanelElement.mouseClicked (so dispatchElementClick above returned
        // false for them); they rely on this super call. Pre-Pass-3 the
        // opaque-eat returned true first and ate the click → sliders/text fields
        // were dead inside every opaque MKScreen panel. The eat now runs AFTER
        // super, suppressing only true fall-through (empty opaque space).
        if (super.mouseClicked(event, flag)) {
            return true;
        }
        // Opaque click-eat: nothing (MK element OR vanilla widget) consumed the
        // click, so if it landed in empty space inside a visible opaque panel,
        // eat it so panels/the screen behind don't receive it.
        if (clickInsideAnyOpaquePanel(event.x(), event.y())) {
            return true;
        }
        return false;
    }

    /**
     * Notifies every visible element of an outside click so popover-like
     * elements (Dropdown/DropdownMulti) can dismiss. Each element self-guards
     * via {@link PanelElement#notifyClickOutsideOverlay} — it closes only if the
     * click fell outside its own overlay/trigger — so calling it unconditionally
     * on every element is safe and the dispatcher needs no per-element
     * knowledge. Runs on every click (before dispatch) so an open popover
     * dismisses even when the click is consumed by a different element.
     */
    private void notifyOutsideClickDismiss(double mouseX, double mouseY) {
        for (Panel panel : panels) {
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;
                element.notifyClickOutsideOverlay(mouseX, mouseY);
            }
        }
    }

    /**
     * True if (mouseX, mouseY) lands inside the resolved screen bounds of any
     * visible {@link Panel#isOpaque() opaque} panel. Reuses
     * {@link #effectivePanelScreenBounds} so the eat-test agrees exactly with
     * where the panel was drawn (same regime handling for overlay vs layout
     * panels). Visibility is gated by {@link ClientWindowVisibility#panelShown}
     * so a hidden opaque panel never eats. This is the click-eat half of
     * Panel.opaque()'s contract on standalone screens.
     */
    private boolean clickInsideAnyOpaquePanel(double mouseX, double mouseY) {
        for (Panel panel : panels) {
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            if (!panel.isOpaque()) continue;
            int[] rect = effectivePanelScreenBounds(panel);
            if (rect == null) continue;
            int x = rect[0], y = rect[1], w = rect[2], h = rect[3];
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Offer the key to panel elements first (Dropdown arrow/Enter/Escape
        // nav). If none consumes, fall through to vanilla's own handling
        // (focused widgets, Escape-to-close, etc.). Keyboard parallel to the
        // mouseClicked dispatch above.
        if (dispatchElementKeyPress(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        // B3 modal-Escape fix: when a tracksAsModal panel is visible and the
        // user presses Escape, dismiss the TOPMOST modal instead of letting
        // super.keyPressed close the whole screen out from under it. Fire the
        // modal's onEscape action (ConfirmDialog.onCancel / AlertDialog
        // .onAcknowledge wire this) so the consumer's self-dismiss runs, then
        // eat the key. Even with no escape action, we eat Escape so it can't
        // close the host screen while a modal is open. Runs BEFORE super.
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Panel modal = topmostVisibleModalTrackingPanel();
            if (modal != null) {
                Runnable escape = modal.getEscapeAction();
                if (escape != null) escape.run();
                return true; // eat — do not let vanilla close the host screen
            }
        }
        // Panel toggle keys: a Panel built with .toggleKey(GLFW_KEY) flips its own
        // visibility when that key is pressed. MKCHandledScreen dispatches these for
        // server-synced MKC menus; MKScreen is the standalone-screen twin, so honor
        // the same Panel property here — client-side, since an MKScreen panel carries
        // no server menu to sync. (A panel whose visibility is supplier-driven via
        // showWhen ignores setVisible by design, so toggleKey + showWhen don't mix —
        // a toggleKey panel owns its own visibility.)
        for (Panel p : panels) {
            if (p.getToggleKey() >= 0 && p.getToggleKey() == event.key()) {
                p.setVisible(!p.isVisible());
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /**
     * Returns the topmost (last-declared, highest z-order) visible panel with
     * {@code tracksAsModal()} set, or {@code null} if none. Matches the
     * reverse-panel-order z precedence used by {@link #dispatchElementClick}.
     */
    private @org.jspecify.annotations.Nullable Panel topmostVisibleModalTrackingPanel() {
        for (Panel p : panels.reversed()) {
            if (ClientWindowVisibility.panelShown(p) && p.tracksAsModal()) return p;
        }
        return null;
    }

    /**
     * Dispatches a key press to panel elements in reverse panel order (top-
     * most panel's elements first, matching z-order and the click dispatch).
     * NOT hit-tested — keyboard events aren't pointer-localized, so each
     * visible element is offered the key until one consumes it. Modal-aware:
     * when a {@code tracksAsModal} panel is visible, only its own elements are
     * eligible (mirrors {@link #dispatchElementClick}).
     */
    private boolean dispatchElementKeyPress(int keyCode, int scanCode, int modifiers) {
        boolean modalUp = anyVisibleModalTrackingPanel();
        for (Panel panel : panels.reversed()) {
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            if (modalUp && !panel.tracksAsModal()) continue; // modal-blocked
            for (PanelElement element : panel.getElements()) {
                if (!ClientWindowVisibility.elementShown(panel, element)) continue;
                if (element.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns true if at least one visible panel has {@code tracksAsModal()} set. */
    private boolean anyVisibleModalTrackingPanel() {
        for (Panel p : panels) {
            if (ClientWindowVisibility.panelShown(p) && p.tracksAsModal()) return true;
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
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            if (modalUp && !panel.tracksAsModal()) continue; // modal-blocked
            for (PanelElement element : panel.getElements()) {
                if (!ClientWindowVisibility.elementShown(panel, element)) continue;
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
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            if (modalUp && !panel.tracksAsModal()) continue; // modal-blocked
            int[] rect = effectivePanelScreenBounds(panel);
            if (rect == null) continue;

            int padding = panel.interiorPadding();
            int contentX = rect[0] + padding;
            int contentY = rect[1] + padding;

            for (PanelElement element : panel.getElements()) {
                if (!ClientWindowVisibility.elementShown(panel, element)) continue;

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
    // to elements via Fabric's ScreenMouseEvents. MKScreen
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
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            if (modalUp && !panel.tracksAsModal()) continue;
            for (PanelElement element : panel.getElements()) {
                if (!ClientWindowVisibility.elementShown(panel, element)) continue;
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
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            if (modalUp && !panel.tracksAsModal()) continue;
            int[] rect = effectivePanelScreenBounds(panel);
            if (rect == null) continue;

            int padding = panel.interiorPadding();
            int contentX = rect[0] + padding;
            int contentY = rect[1] + padding;

            for (PanelElement element : panel.getElements()) {
                if (!ClientWindowVisibility.elementShown(panel, element)) continue;

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
            if (!ClientWindowVisibility.panelShown(panel)) continue;
            for (PanelElement element : panel.getElements()) {
                if (!ClientWindowVisibility.elementShown(panel, element)) continue;
                element.mouseReleased(event.x(), event.y(), event.button());
            }
        }
        return super.mouseReleased(event);
    }

    // ── Panel Access ────────────────────────────────────────────────────

    /** Returns the ordered list of panels (immutable). */
    public List<Panel> getPanels() { return panels; }
}
