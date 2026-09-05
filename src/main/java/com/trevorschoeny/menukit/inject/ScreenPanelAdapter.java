package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.MenuRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.RegionAnchor;
import com.trevorschoeny.menukit.core.RenderContext;
import com.trevorschoeny.menukit.window.ClientWindowVisibility;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Renders a {@link Panel} inside a vanilla container screen and dispatches
 * input to it. Constructing an adapter registers it; the library renders the
 * panel and routes clicks on every targeted screen with no consumer mixin.
 *
 * <pre>{@code
 * Panel panel = Panel.builder("mymod:controls")
 *         .add(new Button(0, 0, 90, 16, Component.literal("Press"), b -> {}))
 *         .build();
 * new ScreenPanelAdapter(panel, MenuRegion.RIGHT_ALIGN_TOP.priority(10))
 *         .on(InventoryScreen.class);
 * }</pre>
 *
 * <p>Targeting: with no {@link #on(Class...)} or {@link #onAny()} call the
 * panel renders on every container screen. {@link #unregister()} removes it.
 * A consumer-owned mixin may instead call {@link #render} and
 * {@link #mouseClicked} directly for a pixel-positioned panel; that path does
 * not require targeting.
 *
 * <h3>Context-parity with other rendering contexts</h3>
 *
 * This adapter renders panels identically to
 * the other two MenuKit rendering contexts (standalone {@code MKScreen},
 * HUD {@code MKHudPanel}):
 *
 * <ul>
 *   <li><b>{@link PanelStyle} auto-render.</b> When the wrapped panel's style
 *       is not {@link PanelStyle#NONE}, the adapter paints the panel
 *       background before rendering elements. Consumers previously had to
 *       render backgrounds themselves; they don't anymore.</li>
 *   <li><b>Content padding.</b> Elements render inside a padded content area.
 *       Padding defaults to {@link #DEFAULT_PADDING} (matches
 *       {@code MKScreen.PANEL_PADDING}) — set explicitly via the
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
 *   <li><b>Coordinate translation.</b> The panel's screen-space origin is
 *       resolved from its declared {@link MenuRegion} and the vanilla screen's
 *       bounds via {@link RegionRegistry#resolveMenuOrigin}. The adapter
 *       resolves it per frame so resizes are handled automatically.</li>
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
 */
public final class ScreenPanelAdapter {

    /**
     * Default content padding — matches {@code MKScreen.PANEL_PADDING}.
     * Consumers wanting flush-edge behavior pass {@code 0} explicitly via
     * the padding-accepting constructor overload.
     */
    public static final int DEFAULT_PADDING = 7;

    private final Panel panel;
    /** The declared region anchor — {@code null} for a pixel-positioned panel
     *  (the panel's {@link com.trevorschoeny.menukit.core.PanelPosition.Mode#PIXEL}
     *  supplier is the origin authority; {@code resolveMenuOrigin} branches on the
     *  position mode BEFORE touching the region, so the null never flows into
     *  region math). */
    private final @Nullable MenuRegion region;
    private final int padding;

    // ── Targeting state ─────────────────────────────────────────────────
    //
    // Adapters must declare targeting via .on(Class...) or .onAny() before
    // first screen open. Construction registers with ScreenPanelRegistry's
    // pending set; .on/.onAny() removes.

    /** Declared targets when {@link #targetedAny} is false. Null until .on() is called. */
    private @Nullable List<Class<? extends AbstractContainerScreen<?>>> targets = null;

    /** True when {@link #onAny()} has been called. Mutually exclusive with {@link #targets}. */
    private boolean targetedAny = false;

    /**
     * Declared {@link ScreenMatcher} when {@link #onMatching} was called. Null
     * otherwise. Mutually exclusive with {@link #targets} / {@link #targetedAny}.
     *
     * <p>This is the parity-shaped targeting mode: instead of an explicit class
     * list ({@code .on}) or unconditional ({@code .onAny}), the adapter delegates
     * its {@link #matches} decision to a {@link ScreenMatcher} — the same
     * default-on/opt-out-per-screen primitive registered-slot parity uses. It is
     * what lets the container-parity registration ({@code MKCContainerPanel})
     * wire a panel's chrome with {@code ScreenMatcher.all()} as the default and a
     * per-screen exclusion as the opt-out, in one place, without a second
     * targeting vocabulary.
     */
    private @Nullable ScreenMatcher matcher = null;

    // ── Constructors ────────────────────────────────────────────────────
    //
    // Phase 16j H3 — constructor sprawl consolidated. Three public surfaces
    // remain, organized by orthogonal axes:
    //
    //   Placement     | Padding
    //   ──────────────┼──────────
    //   MenuRegion    | explicit
    //   RegionAnchor  | default (DEFAULT_PADDING)
    //   RegionAnchor  | explicit
    //
    // The "MenuRegion + default padding" convenience overload was dropped —
    // consumers pass padding explicitly (typically DEFAULT_PADDING or 0).
    //
    // The "anchor + default padding" overload stays because it's the
    // 16i ergonomic happy path (priority specified inline via
    // MenuRegion.X.priority(N)).

    /**
     * Region-aware constructor with explicit padding. Registers the panel
     * into the given {@link MenuRegion} via {@link RegionRegistry} with
     * the declared padding so stacking math and overflow checks both
     * account for it. Uses {@link RegionAnchor#DEFAULT_PRIORITY} for
     * sibling ordering; pair with the {@link RegionAnchor} constructor
     * below for explicit priority.
     *
     * <p>Per-instance lifecycle: construct adapters typically as a
     * {@code static final} field at mod init. For test/verification or
     * runtime UI swaps, call {@link #unregister()} when done. See M5
     * design doc §6.1.
     */
    public ScreenPanelAdapter(Panel panel, MenuRegion region, int padding) {
        this(panel, region, padding, RegionAnchor.DEFAULT_PRIORITY);
    }

    /**
     * Region-aware constructor accepting a {@link RegionAnchor} — region
     * paired with an explicit stacking priority. Use when sibling panels
     * in the same region need deterministic ordering relative to each
     * other (e.g., {@code MenuRegion.RIGHT_ALIGN_TOP.priority(50)}).
     *
     * <p>Padding defers to {@link Panel#interiorPadding()} — {@code 0} for
     * {@link com.trevorschoeny.menukit.core.PanelStyle#NONE} (element edge
     * = panel edge), {@link #DEFAULT_PADDING} for styled panels. Consumers
     * who want a different value pass it via the explicit-padding
     * constructor overload.
     */
    public ScreenPanelAdapter(Panel panel, RegionAnchor<MenuRegion> anchor) {
        this(panel, anchor.region(), panel.interiorPadding(), anchor.priority());
    }

    /** Region-aware constructor with both explicit padding and priority. */
    public ScreenPanelAdapter(Panel panel, RegionAnchor<MenuRegion> anchor, int padding) {
        this(panel, anchor.region(), padding, anchor.priority());
    }

    /** Internal canonical constructor — public region-based overloads
     *  delegate here. */
    private ScreenPanelAdapter(Panel panel, MenuRegion region, int padding, int priority) {
        this.panel = panel;
        this.padding = padding;
        this.region = region;
        RegionRegistry.registerMenu(panel, region, padding, priority);
        ScreenPanelRegistry.trackPending(this);
    }

    /**
     * Pixel-positioned constructor (§0057 Revision — the precision escape). The
     * panel must declare {@link com.trevorschoeny.menukit.core.PanelPosition#pixel}:
     * its per-frame supplier is the origin authority, so there is no region — the
     * panel never registers into region stacking (it doesn't stack; it sits at
     * exactly the supplied coordinates) and the reactive wrap/scroll budgets are
     * never fed (pixel placement = the consumer owns the exact geometry). Render,
     * input, hover-suppression, and opacity all ride the same machinery as a
     * region panel — only the origin source differs.
     *
     * @throws IllegalArgumentException if the panel's position is not
     *         {@code PanelPosition.pixel(...)} — a loud fail beats a silently
     *         unresolvable placement (the A4 rule)
     */
    public ScreenPanelAdapter(Panel panel, int padding) {
        if (panel.getPosition().mode()
                != com.trevorschoeny.menukit.core.PanelPosition.Mode.PIXEL) {
            throw new IllegalArgumentException(
                    "ScreenPanelAdapter(panel, padding) is the PIXEL-position "
                    + "constructor — panel '" + panel.getId() + "' must declare "
                    + ".position(PanelPosition.pixel(originSupplier)). For region "
                    + "placement use ScreenPanelAdapter(panel, region, padding).");
        }
        this.panel = panel;
        this.padding = padding;
        this.region = null;
        ScreenPanelRegistry.trackPending(this);
    }

    // ── Teardown ────────────────────────────────────────────────────────

    /**
     * Phase 16j R5 — removes this adapter from every internal registry
     * collection: the per-region MENU list, the per-screen match cache,
     * and the PENDING/REGISTERED tracking sets. After {@code unregister()}
     * the adapter contributes nothing to layout, dispatch, or rendering.
     *
     * <p>Intended for code paths that construct adapters outside mod-init
     * (test/verification, runtime UI swaps, hot-reload-style workflows).
     * Most consumers don't need this — they construct adapters as
     * {@code static final} fields and never unregister.
     *
     * <p>Idempotent: calling {@code unregister()} twice is safe. The
     * adapter object itself is not reusable after unregister — to
     * resume a registration, construct a new adapter.
     *
     * <p>Note: the underlying {@link Panel} can be reused with a new
     * adapter after this; the panel itself isn't owned by the adapter.
     * Per-Panel metadata (priority, modId, padding) was cleared by
     * {@link RegionRegistry#unregisterMenu}, so re-registering picks up
     * fresh values from the new constructor call.
     */
    public void unregister() {
        RegionRegistry.unregisterMenu(panel);
        ScreenPanelRegistry.untrack(this);
    }

    // ── Targeting API ───────────────────────────────────────────────────

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
     * <p><b>Player inventory (§0051):</b> {@code InventoryScreen} (survival) and
     * {@code CreativeModeInventoryScreen} (creative) are <em>sibling</em> classes
     * — neither extends the other — so {@code .on(InventoryScreen.class)} alone is
     * <b>survival-only</b> and silently misses creative. Use
     * {@link #onPlayerInventory()} to target the player inventory in both modes.
     *
     * <p>Call exactly once per adapter. Duplicate declarations throw
     * {@link IllegalStateException}. Use {@link #onAny()} for the "every
     * screen" shape; don't pass {@code AbstractContainerScreen.class} here.
     *
     * @param screenClasses one or more screen classes; must not be empty
     * @return this adapter, for chaining
     * @throws IllegalStateException if targeting was already declared
     * @throws IllegalArgumentException if {@code screenClasses} is empty
     */
    @SafeVarargs
    public final ScreenPanelAdapter on(
            Class<? extends AbstractContainerScreen<?>>... screenClasses) {
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
     * <p>This is also the <b>implicit default</b>: a region-based adapter that
     * declares no targeting at all is promoted to {@code onAny()} at the first-
     * screen checkpoint ({@link ScreenPanelRegistry#applyEverywhereDefault}).
     * Calling {@code onAny()} only makes that intent explicit; to render on
     * fewer screens, narrow with {@code .on(...)} / {@code .onPlayerInventory()}
     * / {@code .onMatching(ScreenMatcher.allExcept(...))} instead.
     *
     * @return this adapter, for chaining
     * @throws IllegalStateException if targeting was already declared
     */
    public ScreenPanelAdapter onAny() {
        requireUndeclared();
        this.targetedAny = true;
        ScreenPanelRegistry.markTargetingDeclared(this);
        return this;
    }

    /**
     * Declares this adapter fires on the player inventory in <b>both</b> game
     * modes — the survival {@link InventoryScreen} and the creative
     * {@link CreativeModeInventoryScreen}. The two are sibling classes (neither
     * extends the other), so {@code .on(InventoryScreen.class)} alone is
     * survival-only and silently misses creative (§0051). This is the turnkey
     * both-modes target, so a consumer decorating the player inventory cannot
     * accidentally ship a survival-only panel.
     *
     * <p>An opt-out narrowing from the everywhere default (see {@link #onAny()}):
     * call this when a panel belongs only on the player inventory, not on every
     * container.
     *
     * <p>Equivalent to
     * {@code .on(InventoryScreen.class, CreativeModeInventoryScreen.class)}.
     *
     * @return this adapter, for chaining
     * @throws IllegalStateException if targeting was already declared
     */
    public ScreenPanelAdapter onPlayerInventory() {
        return on(InventoryScreen.class, CreativeModeInventoryScreen.class);
    }

    /**
     * Declares this adapter fires on every screen a {@link ScreenMatcher}
     * accepts — the parity-shaped targeting mode. Where {@link #on} is an
     * explicit class list and {@link #onAny} is unconditional, this delegates
     * the per-screen decision to the matcher, so {@link ScreenMatcher#all()}
     * (default-on everywhere) with a {@link ScreenMatcher#allExcept} opt-out is
     * expressible as a single targeting declaration.
     *
     * <p>This is the seam the container-parity registration
     * ({@code MKCContainerPanel}) uses to wire a registered panel's chrome:
     * one parity scope drives both the slot projection (MKC side) and the
     * chrome targeting (here), so the two cannot drift.
     *
     * @param matcher the screen scope; must be non-null
     * @return this adapter, for chaining
     * @throws IllegalStateException if targeting was already declared
     */
    public ScreenPanelAdapter onMatching(ScreenMatcher matcher) {
        requireUndeclared();
        if (matcher == null) {
            throw new IllegalArgumentException(
                    "Adapter for panel '" + panel.getId() + "': onMatching(...) "
                    + "requires a non-null ScreenMatcher. Use ScreenMatcher.all() "
                    + "for the everywhere default.");
        }
        this.matcher = matcher;
        ScreenPanelRegistry.markTargetingDeclared(this);
        return this;
    }

    private void requireUndeclared() {
        if (targets != null || targetedAny || matcher != null) {
            throw new IllegalStateException(
                    "Adapter for panel '" + panel.getId() + "' already declared " +
                    "targeting. Call .on(...) / .onAny() / .onMatching(...) exactly once.");
        }
    }

    // ── Targeting queries (for ScreenPanelRegistry dispatch, step 3) ────

    /** True if {@code .on(...)}, {@code .onAny()}, or {@code .onMatching(...)} has been called. */
    public boolean isTargetingDeclared() {
        return targets != null || targetedAny || matcher != null;
    }

    /**
     * Tests whether this adapter's declared targets match the given opened
     * screen class. An adapter that hasn't declared targeting returns false
     * only transiently —
     * the first-screen checkpoint ({@link ScreenPanelRegistry#applyEverywhereDefault})
     * promotes it to {@code onAny()} (every container) before dispatch, so by the
     * time this is consulted an undeclared adapter matches all. Step 3's registry
     * dispatch uses this to filter matching adapters per screen open.
     */
    public boolean matches(Class<? extends AbstractContainerScreen<?>> screenClass) {
        if (targetedAny) return true;
        if (matcher != null) return matcher.matches(screenClass);
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
     * <p>Takes the live screen instance so chrome-aware region resolution
     * can consult {@link MenuChrome}.
     */
    public Optional<ScreenOrigin> getOrigin(ScreenBounds screenBounds,
                                            AbstractContainerScreen<?> screen) {
        if (!ClientWindowVisibility.panelShown(panel)) return Optional.empty();
        ScreenOrigin origin = RegionRegistry.resolveMenuOrigin(panel, region, screenBounds, screen);
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
     * <p>{@code screen} is passed through to the region resolver so
     * chrome-aware region resolution can consult {@link MenuChrome}.
     */
    public void render(GuiGraphicsExtractor graphics, ScreenBounds screenBounds,
                       int mouseX, int mouseY,
                       AbstractContainerScreen<?> screen) {
        if (!ClientWindowVisibility.panelShown(panel)) return;

        ScreenOrigin origin = RegionRegistry.resolveMenuOrigin(panel, region, screenBounds, screen);
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
        // findCoveringPanelAt query in the slot-hover + tooltip mixins —
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
        // matching MKScreen and MKHudPanel semantics.
        RenderContext ctx = new RenderContext(
                graphics, origin.x() + padding, origin.y() + padding,
                effectiveMouseX, effectiveMouseY);
        com.trevorschoeny.menukit.core.PanelDispatch.renderElements(panel, ctx);

        // Panel-level tooltip — fires over the panel's outer bounds
        // (background-inclusive). Uses effectiveMouseX/Y so a modal-
        // suppressed adapter doesn't queue its tooltip (matches the
        // hover suppression applied to children).
        panel.maybeQueueTooltip(graphics,
                origin.x(), origin.y(), panelWidth, panelHeight,
                effectiveMouseX, effectiveMouseY, ctx.hasMouseInput());
    }

    /**
     * Dispatches a mouse click to any visible element under the cursor.
     * No-op (returns false) when {@code !panel.isVisible()} or out-of-region.
     *
     * <p>Hit-testing uses padded content origin so element bounds line up with
     * where the elements actually rendered. {@code screen} is threaded to the
     * region resolver for chrome-aware region resolution parity with
     * {@link #render}.
     *
     * @return {@code true} if an element consumed the click.
     */
    public boolean mouseClicked(ScreenBounds screenBounds,
                                double mouseX, double mouseY, int button,
                                AbstractContainerScreen<?> screen) {
        if (!ClientWindowVisibility.panelShown(panel)) return false;

        ScreenOrigin origin = RegionRegistry.resolveMenuOrigin(panel, region, screenBounds, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return false;

        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;

        // Phase 14d-5 — two-pass dispatch:
        //   Pass 1: active-overlay claims (Dropdown popover when open).
        //   Pass 2: normal hit-test dispatch.
        for (PanelElement element : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, element)) continue;
            int[] overlay = element.getActiveOverlayBounds();
            if (overlay != null
                    && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                    && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
                element.mouseClicked(mouseX, mouseY, button);
                return true;     // exclusive
            }
        }

        for (PanelElement element : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, element)) continue;

            if (!element.hitTest(mouseX, mouseY, contentX, contentY)) continue;

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
        if (!ClientWindowVisibility.panelShown(panel)) return false;

        ScreenOrigin origin = RegionRegistry.resolveMenuOrigin(panel, region, screenBounds, screen);
        if (origin == ScreenOrigin.OUT_OF_REGION) return false;

        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;

        // Phase 14d-5 — two-pass dispatch matching mouseClicked.
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

        for (PanelElement element : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, element)) continue;

            if (!element.hitTest(mouseX, mouseY, contentX, contentY)) continue;

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
        if (!ClientWindowVisibility.panelShown(panel)) return false;
        boolean consumed = false;
        for (PanelElement element : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, element)) continue;
            if (element.mouseReleased(mouseX, mouseY, button)) {
                consumed = true;
            }
        }
        return consumed;
    }

    /**
     * Dispatches a key press to this adapter's visible elements (keyboard-nav
     * completion — the keyboard parallel to {@link #mouseClicked}). NOT
     * hit-tested: keyboard events aren't localized to a cursor position, so
     * every visible element is offered the key until one consumes it. Returns
     * true once an element consumes, so the caller eats the key from vanilla.
     * Canonical consumer: an open {@link com.trevorschoeny.menukit.core.Dropdown}.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!ClientWindowVisibility.panelShown(panel)) return false;
        for (PanelElement element : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, element)) continue;
            if (element.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
