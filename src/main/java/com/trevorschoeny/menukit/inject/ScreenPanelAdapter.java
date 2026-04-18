package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.InventoryRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Bundles the mechanical parts of rendering a {@link Panel} inside a vanilla
 * screen and dispatching input to it. Consumers hold this as a {@code @Unique}
 * field on their mixin and call its methods from inside the mixin's own render
 * and {@code mouseClicked} methods.
 *
 * <h3>Scope — what the adapter bundles, what it doesn't</h3>
 *
 * The adapter bundles the mechanical parts of injection:
 * <ul>
 *   <li><b>Coordinate translation.</b> The consumer supplies a
 *       {@link ScreenOriginFn} that computes the panel's screen-space origin
 *       from the vanilla screen's bounds. The adapter calls it per frame so
 *       resizes are handled automatically.</li>
 *   <li><b>Render dispatch.</b> Constructs the {@link RenderContext} with the
 *       computed origin and mouse coords, iterates visible elements, and
 *       calls {@code element.render(ctx)}.</li>
 *   <li><b>Input dispatch.</b> Translates mouse coordinates, hit-tests each
 *       visible element against its bounds, and dispatches {@code mouseClicked}
 *       to elements under the cursor. Returns whether any element consumed
 *       the click.</li>
 * </ul>
 *
 * The adapter explicitly does <b>not</b> bundle policy decisions:
 * <ul>
 *   <li><b>Visibility composition.</b> The consumer decides whether to call
 *       {@code adapter.render(...)} and {@code adapter.mouseClicked(...)} at
 *       all. Visibility is either owned by {@code Panel}'s own supplier
 *       (set via {@link Panel#showWhen}) or by the consumer's own predicates
 *       layered in the mixin. The adapter short-circuits when
 *       {@code !panel.isVisible()} but does not manage visibility itself.</li>
 *   <li><b>Click cancellation.</b> {@link #mouseClicked} returns whether the
 *       click landed on an interactive element; the consumer's mixin inspects
 *       the return value and decides whether to cancel vanilla's handling
 *       (e.g., {@code cir.setReturnValue(true)}). Some consumers swallow
 *       clicks; some want vanilla to also see them.</li>
 * </ul>
 *
 * <h3>Panel-background rendering</h3>
 *
 * The adapter renders elements only. If the consumer's panel uses a non-NONE
 * {@link com.trevorschoeny.menukit.core.PanelStyle} (RAISED, DARK, INSET),
 * the consumer is responsible for rendering the background themselves — call
 * {@code PanelRendering.renderPanel(...)} from their mixin before
 * {@code adapter.render(...)}, or use elements that render their own
 * backgrounds (like {@code Button} with its built-in panel-styled background).
 *
 * <p>For the majority of Phase 10 audit cases, panels use {@code PanelStyle.NONE}
 * because the elements inside (buttons, icons) render their own styling.
 * Background support may be extended into the adapter if Phase 11 consumer
 * refactors surface demand.
 *
 * <h3>Lifecycle with the mixin</h3>
 *
 * The adapter is held as a {@code @Unique} final field on the consumer's
 * mixin. A single adapter instance lives for the lifetime of the vanilla
 * screen's class. The Panel it wraps is typically a static field on the
 * consumer's mod class — one Panel per visual group, constructed once at
 * mod init.
 *
 * @see Panel                   The visual unit being injected
 * @see ScreenBounds            Vanilla-screen layout snapshot passed per call
 * @see ScreenOriginFn          Pure function from bounds to panel origin
 * @see ScreenOriginFns         Common origin-function constructors
 */
public final class ScreenPanelAdapter {

    private final Panel panel;
    private final ScreenOriginFn originFn;

    /**
     * @param panel    the panel to render and dispatch input to
     * @param originFn computes the panel's screen-space origin from the
     *                 vanilla screen's bounds; called per frame
     */
    public ScreenPanelAdapter(Panel panel, ScreenOriginFn originFn) {
        this.panel = panel;
        this.originFn = originFn;
    }

    /**
     * Region-aware overload. Registers the panel into the given
     * {@link InventoryRegion} via {@link RegionRegistry} and wires an origin
     * function that consults the registry each frame to resolve the panel's
     * stacked position within the region.
     *
     * <p><b>Singleton contract.</b> Construct exactly one adapter per logical
     * panel, typically as a {@code static final} field at mod init. Dynamic
     * construction is unsupported — each call appends to the registry and
     * there is no {@code unregister()}. See M5 design doc §6.1.
     *
     * <p>Render and input dispatch short-circuit when the region resolver
     * returns {@link ScreenOrigin#OUT_OF_REGION} (panel overflows the region's
     * available space).
     */
    public ScreenPanelAdapter(Panel panel, InventoryRegion region) {
        this.panel = panel;
        this.originFn = RegionRegistry.inventoryOriginFn(panel, region);
        RegionRegistry.registerInventory(panel, region);
    }

    /** Returns the panel this adapter wraps. */
    public Panel getPanel() {
        return panel;
    }

    /**
     * Renders the panel's visible elements at the origin computed from the
     * given screen bounds. No-op when {@code !panel.isVisible()}.
     *
     * <p>Invisible elements (those whose {@link PanelElement#isVisible} returns
     * false) are skipped individually. The panel's overall visibility is
     * checked once at entry for efficiency.
     *
     * @param graphics     the graphics handle from the vanilla render call
     * @param screenBounds the vanilla screen's layout bounds this frame
     * @param mouseX       screen-space mouse X, for hover detection
     * @param mouseY       screen-space mouse Y, for hover detection
     */
    public void render(GuiGraphics graphics, ScreenBounds screenBounds,
                       int mouseX, int mouseY) {
        if (!panel.isVisible()) return;

        ScreenOrigin origin = originFn.compute(screenBounds);
        // Region-aware originFns return OUT_OF_REGION when the panel would
        // overflow its region. Short-circuit both render and input here so
        // the panel behaves as hidden when out of space.
        if (origin == ScreenOrigin.OUT_OF_REGION) return;

        RenderContext ctx = new RenderContext(
                graphics, origin.x(), origin.y(), mouseX, mouseY);

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;
            element.render(ctx);
        }
    }

    /**
     * Dispatches a mouse click to any visible element under the cursor.
     * No-op (returns false) when {@code !panel.isVisible()}.
     *
     * <p>Hit-testing is screen-space: the adapter computes each element's
     * screen-space bounds (origin + childX/childY + width/height) and
     * dispatches {@code mouseClicked} to the first element whose bounds
     * contain the cursor and whose {@code mouseClicked} returns true.
     *
     * @param screenBounds the vanilla screen's layout bounds this frame
     * @param mouseX       screen-space mouse X of the click
     * @param mouseY       screen-space mouse Y of the click
     * @param button       mouse button (0=left, 1=right, 2=middle)
     * @return {@code true} if an element consumed the click. The consumer's
     *         mixin inspects this to decide whether to cancel vanilla's
     *         handling.
     */
    public boolean mouseClicked(ScreenBounds screenBounds,
                                double mouseX, double mouseY, int button) {
        if (!panel.isVisible()) return false;

        ScreenOrigin origin = originFn.compute(screenBounds);
        // Out-of-region panels consume no clicks — they aren't rendered.
        if (origin == ScreenOrigin.OUT_OF_REGION) return false;

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;

            // Screen-space hit test against the element's bounds.
            int sx = origin.x() + element.getChildX();
            int sy = origin.y() + element.getChildY();
            if (mouseX < sx || mouseX >= sx + element.getWidth()) continue;
            if (mouseY < sy || mouseY >= sy + element.getHeight()) continue;

            // Click is within this element's bounds. Dispatch; the element
            // either consumes or falls through. Per PanelElement's contract,
            // the coords passed are screen-space.
            if (element.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }
}
