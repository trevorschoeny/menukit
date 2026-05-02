package com.trevorschoeny.menukit.screen;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelBounds;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelLayout;
import com.trevorschoeny.menukit.core.PanelRendering;
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

    protected MenuKitScreen(Component title, List<Panel> panels) {
        super(title);
        this.panels = List.copyOf(panels);
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
    }

    // ── Layout ──────────────────────────────────────────────────────────

    /**
     * Returns the size of a panel based on its elements' bounds plus padding.
     * Standalone-screen panels have no slot groups — size comes from elements.
     */
    private int[] computePanelSize(Panel panel) {
        int maxRight = 0;
        int maxBottom = 0;
        for (PanelElement el : panel.getElements()) {
            maxRight = Math.max(maxRight, el.getChildX() + el.getWidth());
            maxBottom = Math.max(maxBottom, el.getChildY() + el.getHeight());
        }
        return new int[]{
                maxRight + 2 * PANEL_PADDING,
                maxBottom + 2 * PANEL_PADDING
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
     */
    private boolean dispatchElementClick(double mouseX, double mouseY, int button) {
        List<Panel> reversed = panels.reversed();
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
            PanelBounds bounds = panelBounds.get(panel.getId());
            if (bounds == null) continue;

            int contentX = leftPos + bounds.x() + PANEL_PADDING;
            int contentY = topPos + bounds.y() + PANEL_PADDING;

            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;

                double relX = mouseX - contentX - element.getChildX();
                double relY = mouseY - contentY - element.getChildY();
                if (relX >= 0 && relX < element.getWidth()
                        && relY >= 0 && relY < element.getHeight()) {
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
        List<Panel> reversed = panels.reversed();
        for (Panel panel : reversed) {
            if (!panel.isVisible()) continue;
            PanelBounds bounds = panelBounds.get(panel.getId());
            if (bounds == null) continue;

            int contentX = leftPos + bounds.x() + PANEL_PADDING;
            int contentY = topPos + bounds.y() + PANEL_PADDING;

            for (PanelElement element : panel.getElements()) {
                if (!element.isVisible()) continue;

                double relX = mouseX - contentX - element.getChildX();
                double relY = mouseY - contentY - element.getChildY();
                if (relX >= 0 && relX < element.getWidth()
                        && relY >= 0 && relY < element.getHeight()) {
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
