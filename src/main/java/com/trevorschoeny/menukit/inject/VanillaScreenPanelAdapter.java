package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelDispatch;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.RegionAnchor;
import com.trevorschoeny.menukit.core.RenderContext;
import com.trevorschoeny.menukit.core.VanillaScreenRegion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase 18s — sibling to {@link ScreenPanelAdapter} that anchors MK
 * panels onto vanilla NON-container screens — the Options screen, Controls,
 * KeyBinds, world-select, server-list, anywhere {@link Screen} is the
 * superclass rather than {@code AbstractContainerScreen}.
 *
 * <h2>Why a sibling, not an extension</h2>
 *
 * {@link ScreenPanelAdapter} is built around the inventory-chrome model —
 * {@link MenuRegion} anchors against {@code leftPos}/{@code topPos}/
 * {@code imageWidth}/{@code imageHeight}; {@link MenuChrome} extends
 * those bounds by the screen's chrome extents; {@link ScreenOriginFn}
 * takes an {@code AbstractContainerScreen<?>}. None of that applies to
 * non-container screens (which have no inventory chrome — they fill the
 * whole screen). Trying to unify the two would bifurcate the API per
 * call site; sibling-with-its-own-vocabulary keeps each call site
 * type-correct.
 *
 * <h2>Same registry, separate dispatch path</h2>
 *
 * {@link ScreenPanelRegistry}'s {@code AFTER_INIT} listener already fires
 * for every opened screen — the container-screen filter happens at the
 * dispatch layer. Phase 18s extends that listener to branch on screen
 * type: container screens still go through {@link ScreenPanelRegistry}'s
 * existing logic; non-container screens route to
 * {@link VanillaScreenPanelRegistry} for the parallel dispatch path
 * (render via {@code addRenderableOnly}, input via Fabric's
 * {@code ScreenMouseEvents}).
 *
 * <h2>Lifecycle parallels {@link ScreenPanelAdapter}</h2>
 *
 * <ol>
 *   <li>Construct with a {@link Panel}, a {@link VanillaScreenRegion}, and
 *       a padding (and optionally a {@link RegionAnchor} for explicit
 *       priority). Constructor registers the panel with the
 *       {@link RegionRegistry} and tracks the adapter in
 *       {@link VanillaScreenPanelRegistry}'s pending set.</li>
 *   <li>Call {@code .on(Class...)} or {@code .onAny()} to declare
 *       targeting. Targeting moves the adapter from pending to registered.
 *       Targeting must be declared exactly once.</li>
 *   <li>When a matching screen opens, the registry dispatches render +
 *       input to the adapter.</li>
 * </ol>
 *
 * <h2>Input contract</h2>
 *
 * MK panels EAT clicks within their bounds — vanilla widgets behind the
 * panel are NOT dispatched to when the click lands inside the panel.
 * Mirrors the default {@code Panel.opaque(true)} contract of the
 * container-screen path. Clicks outside the panel bounds fall through
 * to vanilla's widget dispatch unchanged.
 *
 * <h2>v1 scope</h2>
 *
 * Region-based only (no lambda-origin escape hatch — consumers who need
 * bespoke positioning on a vanilla screen can either write their own
 * Screen mixin or use the {@link VanillaScreenRegion} anchors).
 * No modal / dim-behind / hover-suppression machinery (no consumer has
 * surfaced the need for modals on vanilla screens yet). Fold-on-evidence
 * for both.
 */
public final class VanillaScreenPanelAdapter {

    /** Default content padding — matches {@link ScreenPanelAdapter#DEFAULT_PADDING}. */
    public static final int DEFAULT_PADDING = 7;

    private final Panel panel;
    private final int padding;
    private final VanillaScreenOriginFn originFn;

    /** Declared targets when {@link #targetedAny} is false. Null until {@code .on()}. */
    private @Nullable List<Class<? extends Screen>> targets = null;

    /** True when {@link #onAny()} has been called. Mutually exclusive with {@link #targets}. */
    private boolean targetedAny = false;

    // ── Constructors ────────────────────────────────────────────────────

    /**
     * Region-aware constructor with explicit padding. Registers the panel
     * into the given {@link VanillaScreenRegion} via {@link RegionRegistry}
     * with the declared padding so stacking math and overflow checks both
     * account for it. Uses {@link RegionAnchor#DEFAULT_PRIORITY} for
     * sibling ordering.
     */
    public VanillaScreenPanelAdapter(Panel panel, VanillaScreenRegion region, int padding) {
        this(panel, region, padding, RegionAnchor.DEFAULT_PRIORITY);
    }

    /**
     * Region-aware constructor accepting a {@link RegionAnchor} — region
     * paired with an explicit stacking priority. Padding defers to
     * {@link Panel#interiorPadding()} (0 for NONE, 7 otherwise — same
     * style-conditional default as {@link ScreenPanelAdapter}).
     */
    public VanillaScreenPanelAdapter(Panel panel, RegionAnchor<VanillaScreenRegion> anchor) {
        this(panel, anchor.region(), panel.interiorPadding(), anchor.priority());
    }

    /** Region-aware constructor with both explicit padding and priority. */
    public VanillaScreenPanelAdapter(Panel panel, RegionAnchor<VanillaScreenRegion> anchor,
                                      int padding) {
        this(panel, anchor.region(), padding, anchor.priority());
    }

    /** Internal canonical constructor. */
    private VanillaScreenPanelAdapter(Panel panel, VanillaScreenRegion region,
                                       int padding, int priority) {
        this.panel = Objects.requireNonNull(panel, "panel must not be null");
        this.padding = padding;
        RegionRegistry.registerVanillaScreen(panel, region, padding, priority);
        this.originFn = RegionRegistry.vanillaScreenOriginFn(panel, region);
        VanillaScreenPanelRegistry.trackPending(this);
    }

    // ── Targeting API ───────────────────────────────────────────────────

    /**
     * Declares the screen classes this adapter applies to. Resolution is
     * class-ancestry — the adapter fires on any opened {@link Screen}
     * that is an instance of one or more of {@code screenClasses}.
     *
     * <p>Multi-target semantics are OR — any matching target fires the
     * adapter. Call exactly once. Duplicate declarations throw.
     */
    @SafeVarargs
    public final VanillaScreenPanelAdapter on(Class<? extends Screen>... screenClasses) {
        requireUndeclared();
        if (screenClasses.length == 0) {
            throw new IllegalArgumentException(
                    "VanillaScreenPanelAdapter for panel '" + panel.getId() +
                    "': .on() requires at least one screen class. Use .onAny() for " +
                    "every-screen targeting (rare; usually you want a specific class).");
        }
        this.targets = List.of(screenClasses);
        VanillaScreenPanelRegistry.markTargetingDeclared(this);
        return this;
    }

    /**
     * Declares this adapter fires on every opened non-container {@link Screen}.
     * Rare for vanilla screens (the panel would appear on every menu screen
     * the user navigates — title, options, world-select, etc.); typically
     * consumers want a specific class. {@code .on(Class...)} is preferred.
     */
    public VanillaScreenPanelAdapter onAny() {
        requireUndeclared();
        this.targetedAny = true;
        VanillaScreenPanelRegistry.markTargetingDeclared(this);
        return this;
    }

    private void requireUndeclared() {
        if (targets != null || targetedAny) {
            throw new IllegalStateException(
                    "VanillaScreenPanelAdapter for panel '" + panel.getId() +
                    "' already declared targeting. Call .on(...) or .onAny() exactly once.");
        }
    }

    // ── Teardown ────────────────────────────────────────────────────────

    /**
     * Removes this adapter from every internal registry collection. Idempotent.
     * After {@code unregister()} the adapter contributes nothing to layout,
     * dispatch, or rendering.
     */
    public void unregister() {
        RegionRegistry.unregisterVanillaScreen(panel);
        VanillaScreenPanelRegistry.untrack(this);
    }

    // ── Accessors ──────────────────────────────────────────────────────

    public Panel getPanel() { return panel; }
    public int getPadding() { return padding; }
    public @Nullable List<Class<? extends Screen>> getTargets() { return targets; }
    public boolean isTargetedAny() { return targetedAny; }
    public boolean isTargetingDeclared() { return targets != null || targetedAny; }

    /**
     * Returns true if this adapter's declared targets match the given
     * screen via class-ancestry (or if {@code .onAny()} was called).
     */
    public boolean matches(Screen screen) {
        if (targetedAny) return true;
        if (targets == null) return false;
        for (Class<? extends Screen> target : targets) {
            if (target.isInstance(screen)) return true;
        }
        return false;
    }

    // ── Render + input ─────────────────────────────────────────────────

    /**
     * Returns the panel's screen-space origin given the current screen
     * dimensions, or empty when the panel is hidden / out-of-region.
     * Used by {@link VanillaScreenPanelRegistry} for hit-test math.
     */
    public Optional<ScreenOrigin> getOriginForScreen(int sw, int sh, Screen screen) {
        if (!panel.isVisible()) return Optional.empty();
        ScreenOrigin origin = originFn.compute(sw, sh, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return Optional.empty();
        return Optional.of(origin);
    }

    /**
     * Renders the panel background (when style is not {@link PanelStyle#NONE})
     * and the panel's visible elements at the origin computed from the screen
     * dimensions.
     */
    public void render(GuiGraphics graphics, int sw, int sh,
                       int mouseX, int mouseY, Screen screen) {
        if (!panel.isVisible()) return;

        ScreenOrigin origin = originFn.compute(sw, sh, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return;

        int panelWidth = panel.getWidth() + 2 * padding;
        int panelHeight = panel.getHeight() + 2 * padding;

        if (panel.getStyle() != PanelStyle.NONE) {
            PanelRendering.renderPanel(graphics,
                    origin.x(), origin.y(),
                    panelWidth, panelHeight,
                    panel.getStyle());
        }

        RenderContext ctx = new RenderContext(
                graphics, origin.x() + padding, origin.y() + padding,
                mouseX, mouseY);
        PanelDispatch.renderElements(panel, ctx);

        panel.maybeQueueTooltip(graphics,
                origin.x(), origin.y(), panelWidth, panelHeight,
                mouseX, mouseY, ctx.hasMouseInput());
    }

    /**
     * Dispatches a mouse click to any visible element under the cursor.
     * Returns true if any element consumed the click — OR if the click
     * landed inside the panel's outer bounds but no element claimed it
     * (eats the click per the input-contract — panels are opaque to
     * vanilla-widget dispatch within their bounds).
     */
    public boolean mouseClicked(int sw, int sh, double mouseX, double mouseY,
                                 int button, Screen screen) {
        if (!panel.isVisible()) return false;

        ScreenOrigin origin = originFn.compute(sw, sh, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return false;

        int panelWidth = panel.getWidth() + 2 * padding;
        int panelHeight = panel.getHeight() + 2 * padding;

        // Bounds check — if click is outside the panel's outer rect, fall
        // through to vanilla without claiming.
        boolean inPanel = mouseX >= origin.x() && mouseX < origin.x() + panelWidth
                       && mouseY >= origin.y() && mouseY < origin.y() + panelHeight;
        if (!inPanel) return false;

        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;

        // Two-pass dispatch matching ScreenPanelAdapter (active-overlay first).
        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;
            int[] overlay = element.getActiveOverlayBounds();
            if (overlay != null
                    && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                    && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
                element.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;
            if (!element.hitTest(mouseX, mouseY, contentX, contentY)) continue;
            if (element.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // In-panel but no element claimed → still eat (opacity contract).
        return true;
    }

    /**
     * Dispatches a mouse-wheel scroll to any visible element under the cursor.
     * Returns true if any element consumed the scroll.
     */
    public boolean mouseScrolled(int sw, int sh, double mouseX, double mouseY,
                                  double scrollX, double scrollY, Screen screen) {
        if (!panel.isVisible()) return false;

        ScreenOrigin origin = originFn.compute(sw, sh, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return false;

        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;
            int[] overlay = element.getActiveOverlayBounds();
            if (overlay != null
                    && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                    && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
                element.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                return true;
            }
        }

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;
            if (!element.hitTest(mouseX, mouseY, contentX, contentY)) continue;
            if (element.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatches a mouse-release event to all visible elements. NOT
     * hit-tested (release fires for every visible element so drag-end
     * detection works when the cursor has moved off during drag).
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!panel.isVisible()) return false;
        boolean consumed = false;
        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;
            if (element.mouseReleased(mouseX, mouseY, button)) {
                consumed = true;
            }
        }
        return consumed;
    }
}
