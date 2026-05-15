package com.trevorschoeny.menukit.screen;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelBounds;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelLayout;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;
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
 * @see MenuKitHandledScreen inventory-menu analogue (holds slots + sync)
 */
public class MenuKitScreen extends Screen {

    /** Padding inside each panel (pixels from panel edge to content). */
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

    // ── Phase 16h cursor preservation ──────────────────────────────────
    //
    // Library-not-platform discipline (§0019): opt-in per-screen. Default
    // false; consumers chain {@link #preserveCursorContinuity(boolean)}
    // after super() in their subclass constructor to enable.
    //
    // Mechanism (M9-cursor): on {@link #removed} the OS cursor position is
    // sampled via {@code GLFW.glfwGetCursorPos} and stashed on the static
    // {@link #stashedCursorPos} field. The next screen's init() — any
    // screen, vanilla or MK — restores via {@code GLFW.glfwSetCursorPos}
    // through the {@link #restoreStashedCursorIfAny} hook wired from
    // {@code MenuKitClient.onInitializeClient} (registers on Fabric's
    // {@code ScreenEvents.AFTER_INIT}). The stash is a one-shot — cleared
    // on restore so it doesn't apply to unrelated screen opens later.
    private boolean preserveCursorContinuity = false;

    /**
     * Static stash for cursor pos across screen transitions. Null = no
     * stash pending. Updated by removed(), consumed by
     * {@link #restoreStashedCursorIfAny()}.
     */
    private static double @Nullable [] stashedCursorPos = null;

    protected MenuKitScreen(Component title, List<Panel> panels) {
        super(title);
        this.panels = List.copyOf(panels);
    }

    /**
     * Phase 16h — opt this screen into cursor-position preservation across
     * transitions. When enabled, the cursor position is captured on
     * {@link #removed} (via {@code GLFW.glfwGetCursorPos}) and restored on
     * the next screen's init (via the
     * {@code MenuKitClient}-registered {@code ScreenEvents.AFTER_INIT}
     * hook calling {@link #restoreStashedCursorIfAny}).
     *
     * <p><b>Default false</b> per library-not-platform discipline (§0019).
     * Consumers chain this on the screen subclass to opt in:
     * <pre>{@code
     * public MyScreen() {
     *     super(title, panels);
     *     this.preserveCursorContinuity(true);
     * }
     * }</pre>
     *
     * <p>Only the screen being LEFT needs the flag set — the restore side
     * is universal (any screen's init reads the stash). For a clean
     * back-and-forth flow, set the flag on both endpoints (the hub AND
     * the sub-screen) so transitions in both directions preserve.
     *
     * @param preserve {@code true} to enable cursor preservation on this
     *                 screen's exit.
     * @return this screen, for chaining.
     */
    protected MenuKitScreen preserveCursorContinuity(boolean preserve) {
        this.preserveCursorContinuity = preserve;
        return this;
    }

    /**
     * Returns whether this screen will capture the cursor position on
     * exit. See {@link #preserveCursorContinuity(boolean)}.
     */
    public boolean isPreservingCursorContinuity() {
        return preserveCursorContinuity;
    }

    /**
     * Restores the stashed cursor position if one is pending, then clears
     * the stash. Wired from {@code MenuKitClient.onInitializeClient} as a
     * {@code ScreenEvents.AFTER_INIT} listener so it fires after ANY
     * screen's init — including vanilla screens — letting MK→vanilla
     * transitions preserve cursor too. One-shot semantics: after a restore
     * fires, the stash is cleared so unrelated subsequent screen opens
     * don't get the same cursor pose.
     */
    public static void restoreStashedCursorIfAny() {
        if (stashedCursorPos == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            stashedCursorPos = null; // avoid stale state if window vanished
            return;
        }
        GLFW.glfwSetCursorPos(mc.getWindow().handle(),
                stashedCursorPos[0], stashedCursorPos[1]);
        stashedCursorPos = null;
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
    }

    @Override
    public void removed() {
        // Phase 16h — capture cursor position before tearing down if the
        // consumer opted into cursor preservation. The next screen's init
        // (any screen, vanilla or MK) consumes the stash via the
        // ScreenEvents.AFTER_INIT hook in MenuKitClient. Captured before
        // onDetach + super.removed so the cursor sample reflects the
        // user's last-known position on this screen (subsequent teardown
        // can't move the cursor, but capturing first is the cleanest
        // ordering).
        if (preserveCursorContinuity) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null) {
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                GLFW.glfwGetCursorPos(mc.getWindow().handle(), xpos, ypos);
                stashedCursorPos = new double[]{xpos[0], ypos[0]};
            }
        }

        // Phase 14d-3 — fire onDetach so widget-wrapping elements can
        // unregister via screen.removeWidget. Mirror of init's onAttach.
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
        return new int[]{
                panel.getWidth() + 2 * PANEL_PADDING,
                panel.getHeight() + 2 * PANEL_PADDING
        };
    }

    private void computeLayout() {
        Map<String, int[]> sizes = new LinkedHashMap<>();
        for (Panel panel : panels) {
            sizes.put(panel.getId(), computePanelSize(panel));
        }

        panelBounds = PanelLayout.resolve(panels, sizes, BODY_GAP, RELATIVE_GAP, TITLE_HEIGHT);

        // Center the resolved layout on the screen. Total bounds are the max
        // extent across all panels including relative ones (which may be
        // outside the body column).
        int minX = 0, minY = 0, maxX = 0, maxY = 0;
        for (PanelBounds b : panelBounds.values()) {
            minX = Math.min(minX, b.x());
            minY = Math.min(minY, b.y());
            maxX = Math.max(maxX, b.x() + b.width());
            maxY = Math.max(maxY, b.y() + b.height());
        }
        int layoutWidth = maxX - minX;
        int layoutHeight = maxY - minY;
        leftPos = (width - layoutWidth) / 2 - minX;
        topPos = (height - layoutHeight) / 2 - minY;
    }

    // ── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Vanilla Screen.render() calls renderBackground() internally (which
        // applies the blur effect). Calling renderBackground() explicitly
        // here AND then super.render() triggers blur twice and fails the
        // "Can only blur once per frame" check in 1.21.x. Let super handle
        // the background; panels render afterward so they layer on top.
        super.render(graphics, mouseX, mouseY, delta);

        // Panel backgrounds
        for (Panel panel : panels) {
            if (!panel.isVisible()) continue;
            PanelBounds bounds = panelBounds.get(panel.getId());
            if (bounds == null) continue;

            PanelRendering.renderPanel(graphics,
                    leftPos + bounds.x(), topPos + bounds.y(),
                    bounds.width(), bounds.height(),
                    panel.getStyle());
        }

        // Panel elements
        for (Panel panel : panels) {
            if (!panel.isVisible()) continue;
            PanelBounds bounds = panelBounds.get(panel.getId());
            if (bounds == null) continue;

            int contentX = leftPos + bounds.x() + PANEL_PADDING;
            int contentY = topPos + bounds.y() + PANEL_PADDING;
            RenderContext ctx = new RenderContext(graphics, contentX, contentY, mouseX, mouseY);

            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;
                element.render(ctx);
            }
        }
    }

    // ── Input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        if (dispatchElementClick(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, flag);
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
        List<Panel> reversed = panels.reversed();

        // ── Pass 1: active-overlay exclusive claims ───────────────────
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
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
            PanelBounds bounds = panelBounds.get(panel.getId());
            if (bounds == null) continue;

            int contentX = leftPos + bounds.x() + PANEL_PADDING;
            int contentY = topPos + bounds.y() + PANEL_PADDING;

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
        List<Panel> reversed = panels.reversed();

        // Pass 1: active-overlay exclusive claims
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
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
            PanelBounds bounds = panelBounds.get(panel.getId());
            if (bounds == null) continue;

            int contentX = leftPos + bounds.x() + PANEL_PADDING;
            int contentY = topPos + bounds.y() + PANEL_PADDING;

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
