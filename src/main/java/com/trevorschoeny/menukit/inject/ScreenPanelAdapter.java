package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.MenuRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Bundles the mechanical parts of rendering a {@link Panel} inside a vanilla
 * screen and dispatching input to it. Consumers hold this as a {@code @Unique}
 * field on their mixin and call its methods from inside the mixin's own render
 * and {@code mouseClicked} methods.
 *
 * <h3>Context-parity with other rendering contexts</h3>
 *
 * After the Phase 12.5 V4 pass, this adapter renders panels identically to
 * the other two MenuKit rendering contexts (standalone {@code MenuKitScreen},
 * HUD {@code MKHudPanel}):
 *
 * <ul>
 *   <li><b>{@link PanelStyle} auto-render.</b> When the wrapped panel's style
 *       is not {@link PanelStyle#NONE}, the adapter paints the panel
 *       background before rendering elements. Consumers previously had to
 *       render backgrounds themselves; they don't anymore.</li>
 *   <li><b>Content padding.</b> Elements render inside a padded content area.
 *       Padding defaults to {@link #DEFAULT_PADDING} (matches
 *       {@code MenuKitScreen.PANEL_PADDING}) — set explicitly via the
 *       {@code padding} constructor parameter, including {@code 0} for
 *       flush-edge behavior.</li>
 * </ul>
 *
 * <p>These defaults may shift the visual output of panels that were
 * constructed against the pre-12.5 adapter (no background, no padding).
 * Consumer mods migrate during Phase 13a by either declaring
 * {@link PanelStyle#NONE} explicitly + passing {@code padding=0}, or adjusting
 * their origin functions to account for the new padded content offset. See
 * {@code menukit/Design Docs/Phase 12/M5_REGION_SYSTEM.md} §12.5a addendum.
 *
 * <h3>Scope — what the adapter bundles, what it doesn't</h3>
 *
 * The adapter bundles the mechanical parts of injection:
 * <ul>
 *   <li><b>Coordinate translation.</b> The consumer supplies a
 *       {@link ScreenOriginFn} that computes the panel's screen-space origin
 *       from the vanilla screen's bounds. The adapter calls it per frame so
 *       resizes are handled automatically.</li>
 *   <li><b>Panel-background rendering.</b> When {@code panel.getStyle() != NONE},
 *       paints the styled background at the panel's origin with padding-inclusive
 *       dimensions.</li>
 *   <li><b>Render dispatch.</b> Constructs the {@link RenderContext} with the
 *       padded content origin and mouse coords, iterates visible elements,
 *       and calls {@code element.render(ctx)}.</li>
 *   <li><b>Input dispatch.</b> Translates mouse coordinates with padding
 *       applied, hit-tests each visible element against its bounds, and
 *       dispatches {@code mouseClicked} to elements under the cursor. Returns
 *       whether any element consumed the click.</li>
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
 *       the return value and decides whether to cancel vanilla's handling.</li>
 * </ul>
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

    /**
     * Default content padding — matches {@code MenuKitScreen.PANEL_PADDING}.
     * Consumers wanting flush-edge behavior pass {@code 0} explicitly via
     * the padding-accepting constructor overload.
     */
    public static final int DEFAULT_PADDING = 7;

    private final Panel panel;
    private final ScreenOriginFn originFn;
    private final int padding;

    // ── Targeting state (region-based adapters only) ────────────────────
    //
    // Region-based adapters must declare targeting via .on(Class...) or
    // .onAny() before first screen open. Construction registers with
    // ScreenPanelRegistry's pending set; .on/.onAny() removes.
    //
    // Lambda-based adapters (constructed with ScreenOriginFn) don't
    // participate — they're scoped by the consumer's own mixin. The
    // targeting methods throw if called on them.

    private final boolean regionBased;

    /** Declared targets when {@link #targetedAny} is false. Null until .on() is called. */
    private @Nullable List<Class<? extends AbstractContainerScreen<?>>> targets = null;

    /** True when {@link #onAny()} has been called. Mutually exclusive with {@link #targets}. */
    private boolean targetedAny = false;

    // ── Constructors ────────────────────────────────────────────────────

    /**
     * Constructs an adapter with a consumer-supplied origin function and
     * default content padding ({@link #DEFAULT_PADDING}).
     */
    public ScreenPanelAdapter(Panel panel, ScreenOriginFn originFn) {
        this(panel, originFn, DEFAULT_PADDING);
    }

    /**
     * Constructs an adapter with a consumer-supplied origin function and
     * explicit content padding.
     */
    public ScreenPanelAdapter(Panel panel, ScreenOriginFn originFn, int padding) {
        this.panel = panel;
        this.originFn = originFn;
        this.padding = padding;
        this.regionBased = false;
    }

    /**
     * Region-aware constructor using default content padding. See
     * {@link #ScreenPanelAdapter(Panel, MenuRegion, int)} for the
     * padding-accepting overload.
     */
    public ScreenPanelAdapter(Panel panel, MenuRegion region) {
        this(panel, region, DEFAULT_PADDING);
    }

    /**
     * Region-aware constructor with explicit padding. Registers the panel
     * into the given {@link MenuRegion} via {@link RegionRegistry} with
     * the declared padding so stacking math and overflow checks both account
     * for it.
     *
     * <p><b>Singleton contract.</b> Construct exactly one adapter per logical
     * panel, typically as a {@code static final} field at mod init. Dynamic
     * construction is unsupported — each call appends to the registry and
     * there is no {@code unregister()}. See M5 design doc §6.1.
     *
     * <p>Render and input dispatch short-circuit when the region resolver
     * returns {@link ScreenOrigin#OUT_OF_REGION} (panel overflows the
     * region's available space). The registry logs a one-shot warning the
     * first time a panel overflows a given region (see
     * {@link RegionRegistry} for the deduplication semantics).
     */
    public ScreenPanelAdapter(Panel panel, MenuRegion region, int padding) {
        this.panel = panel;
        this.padding = padding;
        this.regionBased = true;
        RegionRegistry.registerMenu(panel, region, padding);
        this.originFn = RegionRegistry.menuOriginFn(panel, region);
        ScreenPanelRegistry.trackPending(this);
    }

    // ── Targeting API (region-based adapters) ───────────────────────────

    /**
     * Declares the screen classes this adapter applies to. Resolution is
     * class-ancestry — the adapter fires on any opened
     * {@link AbstractContainerScreen} that is an instance of one or more
     * of {@code screenClasses}. A consumer targeting
     * {@code ChestScreen.class} thus covers modded subclasses
     * (e.g., {@code DoubleChestScreen extends ChestScreen}).
     *
     * <p>Multi-target semantics are OR — any matching target fires the
     * adapter. Example: {@code .on(InventoryScreen.class,
     * CreativeModeInventoryScreen.class)} covers both the survival and
     * creative player-inventory screens (and modded subclasses of either).
     *
     * <p>Call exactly once per region-based adapter. Duplicate declarations
     * throw {@link IllegalStateException}. Calling on a lambda-based adapter
     * throws — lambda adapters are scoped by the consumer's own mixin, not
     * by the library's registry. Use {@link #onAny()} for the "every
     * screen" shape; don't pass {@code AbstractContainerScreen.class} here.
     *
     * @param screenClasses one or more screen classes; must not be empty
     * @return this adapter, for chaining
     * @throws IllegalStateException if the adapter is lambda-based or if
     *         targeting was already declared
     * @throws IllegalArgumentException if {@code screenClasses} is empty
     */
    @SafeVarargs
    public final ScreenPanelAdapter on(
            Class<? extends AbstractContainerScreen<?>>... screenClasses) {
        requireRegionBased();
        requireUndeclared();
        if (screenClasses.length == 0) {
            throw new IllegalArgumentException(
                    "Adapter for panel '" + panel.getId() + "': .on() requires at " +
                    "least one screen class. Use .onAny() for every-screen targeting.");
        }
        this.targets = List.of(screenClasses);
        ScreenPanelRegistry.markTargetingDeclared(this);
        return this;
    }

    /**
     * Declares this adapter fires on every opened
     * {@link AbstractContainerScreen}, including
     * {@link net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen},
     * modded screens, and future screen classes. Explicit "every screen"
     * intent — different from
     * {@code .on(AbstractContainerScreen.class)} at the documentation level
     * (though functionally identical via class-ancestry). Use {@code .on(Class...)}
     * when the consumer wants a specific set of screens; {@code .onAny()}
     * when they genuinely mean "everywhere."
     *
     * @return this adapter, for chaining
     * @throws IllegalStateException if the adapter is lambda-based or if
     *         targeting was already declared
     */
    public ScreenPanelAdapter onAny() {
        requireRegionBased();
        requireUndeclared();
        this.targetedAny = true;
        ScreenPanelRegistry.markTargetingDeclared(this);
        return this;
    }

    private void requireRegionBased() {
        if (!regionBased) {
            throw new IllegalStateException(
                    "Adapter for panel '" + panel.getId() + "' is lambda-based; " +
                    ".on() / .onAny() apply to region-based adapters only. Lambda " +
                    "adapters (constructed with a ScreenOriginFn) are scoped by " +
                    "the consumer's own mixin — the library's ScreenPanelRegistry " +
                    "doesn't dispatch them.");
        }
    }

    private void requireUndeclared() {
        if (targets != null || targetedAny) {
            throw new IllegalStateException(
                    "Adapter for panel '" + panel.getId() + "' already declared " +
                    "targeting. Call .on(...) or .onAny() exactly once.");
        }
    }

    // ── Lambda-path opacity registration (M9) ──────────────────────────
    //
    // Lambda-based adapters render through their consumer's own mixin and
    // are exempt from the region-based ScreenPanelRegistry dispatch path.
    // Pre-M9, this meant lambda panels were also exempt from the modal
    // mechanism (click-eat, hover suppression, tooltip suppression).
    //
    // M9's click-through prohibition is a library-wide invariant, so
    // lambda panels participate via these two methods: consumer's mixin
    // calls .activeOn(this, () -> ScreenBounds(...)) in init() (TAIL),
    // and .deactivate(this) in removed() (TAIL). The library tracks the
    // (Screen, adapter, boundsSupplier) triple in its unified opacity
    // registry; the four input-handler mixins consult the registry for
    // opacity dispatch decisions. The escape-hatch property — consumer
    // owns rendering and dispatch in their own mixin — is preserved;
    // the library only learns about bounds for opacity purposes.
    //
    // See M9 §4.4 for the full design including failure-mode analysis.

    /**
     * Registers this lambda-based adapter as active on the given screen
     * with the supplied bounds-supplier for opacity dispatch (M9). Called
     * from the consumer's mixin during {@code init()} ({@code @At("TAIL")}).
     *
     * <p>The {@code boundsSupplier} returns the current
     * {@link ScreenBounds} for the screen — typically
     * {@code () -> new ScreenBounds(0, 0, this.width, this.height)} for
     * a vanilla standalone screen, or per-frame computed bounds for an
     * inventory-style screen. The supplier is evaluated each time the
     * library queries this adapter's opacity bounds (per click, per
     * hover, per tooltip). Cheap-supplier discipline applies — keep the
     * supplier free of allocations or expensive lookups.
     *
     * <p>Region-based adapters do NOT call this method — they participate
     * in opacity dispatch automatically via {@link ScreenPanelRegistry}'s
     * targeting + region resolution. Calling on a region-based adapter
     * throws.
     *
     * <p><b>Failure mode — consumer forgets {@code activeOn}.</b> The
     * panel renders correctly via the consumer's mixin (since rendering
     * is consumer-owned for lambda adapters) but the M9 opacity
     * invariant doesn't apply — clicks pass through to vanilla widgets
     * underneath, hover/tooltip leak from below. The library does NOT
     * enforce registration; lambda is the escape hatch and consumers
     * accept the explicit responsibility. See M9 §4.4 failure-mode
     * analysis.
     *
     * @param screen          the screen this adapter is active on
     * @param boundsSupplier  per-call screen-bounds computation
     * @return this adapter, for chaining
     * @throws IllegalStateException if the adapter is region-based
     */
    public ScreenPanelAdapter activeOn(Screen screen,
                                        Supplier<ScreenBounds> boundsSupplier) {
        if (regionBased) {
            throw new IllegalStateException(
                    "Adapter for panel '" + panel.getId() + "' is region-based; " +
                    ".activeOn() applies to lambda-based adapters only. " +
                    "Region-based adapters participate in opacity dispatch " +
                    "automatically via ScreenPanelRegistry's targeting (.on/.onAny).");
        }
        if (screen == null || boundsSupplier == null) {
            throw new IllegalArgumentException(
                    "Adapter for panel '" + panel.getId() + "': activeOn(...) " +
                    "requires non-null screen + boundsSupplier.");
        }
        ScreenPanelRegistry.registerLambdaActive(screen, this, boundsSupplier);
        return this;
    }

    /**
     * Unregisters this lambda-based adapter from the given screen for
     * opacity dispatch (M9). Called from the consumer's mixin during
     * {@code removed()} ({@code @At("TAIL")}).
     *
     * <p>Idempotent — calling on an unregistered (screen, adapter) pair
     * is a no-op. Calling on a region-based adapter throws.
     *
     * @param screen the screen to deactivate from
     * @return this adapter, for chaining
     * @throws IllegalStateException if the adapter is region-based
     */
    public ScreenPanelAdapter deactivate(Screen screen) {
        if (regionBased) {
            throw new IllegalStateException(
                    "Adapter for panel '" + panel.getId() + "' is region-based; " +
                    ".deactivate() applies to lambda-based adapters only.");
        }
        if (screen == null) return this;
        ScreenPanelRegistry.unregisterLambdaActive(screen, this);
        return this;
    }

    // ── Targeting queries (for ScreenPanelRegistry dispatch, step 3) ────

    /** True if the adapter was constructed with a region rather than a lambda. */
    public boolean isRegionBased() {
        return regionBased;
    }

    /** True if {@code .on(...)} or {@code .onAny()} has been called. */
    public boolean isTargetingDeclared() {
        return targets != null || targetedAny;
    }

    /**
     * Tests whether this adapter's declared targets match the given opened
     * screen class. Always false for lambda-based adapters and for
     * region-based adapters that haven't declared targeting yet — step 3's
     * registry dispatch uses this to filter matching adapters per screen
     * open.
     */
    public boolean matches(Class<? extends AbstractContainerScreen<?>> screenClass) {
        if (targetedAny) return true;
        if (targets == null) return false;
        for (Class<? extends AbstractContainerScreen<?>> target : targets) {
            if (target.isAssignableFrom(screenClass)) return true;
        }
        return false;
    }

    // ── Accessors ──────────────────────────────────────────────────────

    /** Returns the panel this adapter wraps. */
    public Panel getPanel() {
        return panel;
    }

    /** Returns the content padding applied inside the panel. */
    public int getPadding() {
        return padding;
    }

    /**
     * Returns the panel's screen-space origin (top-left corner of the
     * background rectangle) for the given screen bounds, or empty when the
     * panel is invisible or out-of-region.
     *
     * <p>Added in Phase 12.5 (V4 finding) so consumers rendering sibling
     * decorations alongside the adapter's panel (tooltips, hover overlays,
     * related info panels) don't re-derive origin math from {@code RegionMath}.
     * The content area begins at {@code origin + getPadding()}.
     *
     * <p>Takes the live screen instance so chrome-aware origin functions
     * (from region-aware constructors) can consult
     * {@link MenuChrome}. Non-region origin functions ignore the
     * screen parameter; pass {@code null} if the caller doesn't have one.
     */
    public Optional<ScreenOrigin> getOrigin(ScreenBounds screenBounds,
                                            AbstractContainerScreen<?> screen) {
        if (!panel.isVisible()) return Optional.empty();
        ScreenOrigin origin = originFn.compute(screenBounds, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return Optional.empty();
        return Optional.of(origin);
    }

    /**
     * M9 lambda-path origin query — accepts any {@link Screen} subclass.
     * For lambda-based adapters active on standalone vanilla screens
     * (PauseScreen, OptionsScreen, etc.) where the consumer's mixin
     * registers via {@link #activeOn} but the screen isn't an
     * {@link AbstractContainerScreen}.
     *
     * <p>Casts {@code screen} to {@code AbstractContainerScreen} when
     * possible (so chrome-aware region functions still work); passes
     * {@code null} otherwise (lambda origin functions ignore the
     * parameter per {@link ScreenOriginFn#compute} javadoc).
     */
    public Optional<ScreenOrigin> getOriginForScreen(ScreenBounds screenBounds,
                                                      @Nullable Screen screen) {
        if (!panel.isVisible()) return Optional.empty();
        AbstractContainerScreen<?> acs = (screen instanceof AbstractContainerScreen<?> a) ? a : null;
        ScreenOrigin origin = originFn.compute(screenBounds, acs);
        if (origin == ScreenOrigin.OUT_OF_REGION) return Optional.empty();
        return Optional.of(origin);
    }

    // ── Render + input ─────────────────────────────────────────────────

    /**
     * Renders the panel background (when style is not {@link PanelStyle#NONE})
     * and the panel's visible elements at the origin computed from the given
     * screen bounds. No-op when {@code !panel.isVisible()} or when the
     * region-aware origin resolver returns out-of-region.
     *
     * <p>{@code screen} is passed through to the origin function so
     * chrome-aware region resolution can consult {@link MenuChrome}.
     * Non-region origin functions ignore it; callers that genuinely lack a
     * screen reference pass {@code null} and accept that chrome-aware
     * regions will resolve without chrome adjustment.
     */
    public void render(GuiGraphics graphics, ScreenBounds screenBounds,
                       int mouseX, int mouseY,
                       AbstractContainerScreen<?> screen) {
        if (!panel.isVisible()) return;

        ScreenOrigin origin = originFn.compute(screenBounds, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return;

        // Padding-inclusive dimensions for the background rectangle.
        int panelWidth = panel.getWidth() + 2 * padding;
        int panelHeight = panel.getHeight() + 2 * padding;

        // Phase 14d-1 modal dim-behind: rendered by ScreenPanelRegistry
        // in a separate pass between non-modal and modal adapters, so
        // the dim covers BOTH vanilla content AND non-modal MK panels.
        // Per-adapter dim was order-fragile (only worked when modal
        // happened to iterate last); pass-based dim is architecturally
        // correct.

        // Auto-render the panel background when the declared style is
        // not NONE. Consumers who want flush-element rendering without a
        // background use PanelStyle.NONE on their Panel.
        if (panel.getStyle() != PanelStyle.NONE) {
            PanelRendering.renderPanel(graphics,
                    origin.x(), origin.y(),
                    panelWidth, panelHeight,
                    panel.getStyle());
        }

        // Phase 14d-1 / M9 modal-tracking hover suppression for
        // non-modal-tracking panels. Reuses RenderContext's existing "no
        // input dispatch" sentinel (mouseX = -1) — the same convention
        // HUDs use. Semantically this render pass has no input visible
        // to the elements, so hasMouseInput() returns false and
        // isHovered() short-circuits to false. All PanelElement kinds
        // (Button, Toggle, Checkbox, future widgets) inherit the inert
        // behavior automatically through the existing context API; no
        // per-element mixins.
        //
        // The modal-tracking panel itself keeps real coords so its OWN
        // buttons detect hover and dispatch clicks normally. Single check
        // at the RenderContext construction site — architectural fix at
        // the right level using the right existing primitive.
        //
        // Non-modal-tracking opaque panels (popovers, dropdowns) get
        // bounds-driven hover suppression via the unified registry's
        // findOpaquePanelAt query in the slot-hover + tooltip mixins —
        // not via this -1 sentinel. The -1 sentinel here specifically
        // covers "modal-tracking is up; non-modal-tracking MK panels are
        // behind the dim and should look inert."
        int effectiveMouseX = mouseX;
        int effectiveMouseY = mouseY;
        if (!panel.tracksAsModal()
                && ScreenPanelRegistry.hasVisibleModalTrackingOnScreen(screen)) {
            effectiveMouseX = -1;
            effectiveMouseY = -1;
        }

        // Content origin is the panel origin shifted inward by padding.
        // Elements' childX / childY are relative to this content origin,
        // matching MenuKitScreen and MKHudPanel semantics.
        RenderContext ctx = new RenderContext(
                graphics, origin.x() + padding, origin.y() + padding,
                effectiveMouseX, effectiveMouseY);

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;
            element.render(ctx);
        }
    }

    /**
     * Dispatches a mouse click to any visible element under the cursor.
     * No-op (returns false) when {@code !panel.isVisible()} or out-of-region.
     *
     * <p>Hit-testing uses padded content origin so element bounds line up with
     * where the elements actually rendered. {@code screen} is threaded to the
     * origin function for chrome-aware region resolution parity with
     * {@link #render}.
     *
     * @return {@code true} if an element consumed the click.
     */
    public boolean mouseClicked(ScreenBounds screenBounds,
                                double mouseX, double mouseY, int button,
                                AbstractContainerScreen<?> screen) {
        if (!panel.isVisible()) return false;

        ScreenOrigin origin = originFn.compute(screenBounds, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return false;

        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;

            int sx = contentX + element.getChildX();
            int sy = contentY + element.getChildY();
            if (mouseX < sx || mouseX >= sx + element.getWidth()) continue;
            if (mouseY < sy || mouseY >= sy + element.getHeight()) continue;

            if (element.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatches a mouse-wheel scroll to any visible element under the cursor.
     * Same hit-test logic as {@link #mouseClicked}; returns whether any
     * element consumed the scroll.
     *
     * <p>Added in Phase 14d-2 alongside {@code ScrollContainer} — the first
     * element kind that consumes scroll input. Existing elements default
     * {@link PanelElement#mouseScrolled false} and pass through.
     *
     * @return {@code true} if an element consumed the scroll.
     */
    public boolean mouseScrolled(ScreenBounds screenBounds,
                                 double mouseX, double mouseY,
                                 double scrollX, double scrollY,
                                 AbstractContainerScreen<?> screen) {
        if (!panel.isVisible()) return false;

        ScreenOrigin origin = originFn.compute(screenBounds, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return false;

        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;

        for (PanelElement element : panel.getElements()) {
            if (!element.isVisible()) continue;

            int sx = contentX + element.getChildX();
            int sy = contentY + element.getChildY();
            if (mouseX < sx || mouseX >= sx + element.getWidth()) continue;
            if (mouseY < sy || mouseY >= sy + element.getHeight()) continue;

            if (element.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatches a mouse-release event to all visible elements. Unlike
     * {@link #mouseClicked}, this is NOT hit-tested against element bounds
     * — release fires for every visible element so drag-end detection
     * works when the cursor has moved off the element during drag.
     *
     * <p>Added in Phase 14d-2 alongside {@code ScrollContainer} for
     * scrollbar-drag end detection. Existing elements default
     * {@link PanelElement#mouseReleased false}.
     */
    public boolean mouseReleased(ScreenBounds screenBounds,
                                 double mouseX, double mouseY, int button,
                                 AbstractContainerScreen<?> screen) {
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
