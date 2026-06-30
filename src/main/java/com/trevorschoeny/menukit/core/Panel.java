package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The fundamental unit of composition. Every element in MenuKit lives
 * inside a Panel, because that's the scope at which visibility toggles —
 * and visibility is the load-bearing concept for the whole dynamic-menu story.
 *
 * <p>A Panel holds an ordered list of {@link PanelElement}s (buttons, text
 * labels, and anything else implementing the interface), along with a visual
 * style, a layout position, a toggle key, and a visibility flag. Visibility
 * is mutable (the one mutable thing); the element list is fixed after
 * construction.
 *
 * <p>Panel is context-neutral. The same Panel type is used across inventory
 * menus, HUDs, and standalone screens. Context-specific machinery — inventory
 * slot groups, HUD anchoring, standalone-screen lifecycle — lives on the
 * context-specific container holding the panel, not on the panel itself.
 *
 * <p>For inventory menus specifically, slot groups are associated with a
 * panel by id through the owning {@code MKCScreenHandler}'s group map.
 * The panel itself does not hold them.
 *
 * <h3>Visibility: imperative or supplier-driven</h3>
 *
 * Panel visibility can be controlled two ways:
 * <ul>
 *   <li><b>Imperative</b> via {@link #setVisible(boolean)}. The Panel holds
 *       the boolean; the owner (if any) is notified on changes to trigger a
 *       sync pass over affected slots. Canonical for MenuKit-native inventory
 *       menus where visibility must propagate server→client.</li>
 *   <li><b>Supplier-driven</b> via {@link #showWhen(java.util.function.BooleanSupplier)}. Consumer holds
 *       the state; Panel reads via the supplier on each {@code isVisible()}
 *       call. Canonical for Phase 10 injected panels, HUDs, and standalone
 *       screens, following the Phase 8/9 state-ownership pattern
 *       ({@code Toggle.linked}).</li>
 * </ul>
 * The two modes are mutually exclusive — see {@link #showWhen(java.util.function.BooleanSupplier)} for
 * precedence semantics.
 */
public class Panel {

    // ── Interior padding (Phase 16g; per-style in Phase 18r) ───────────
    // The consumer-side screen (MKScreen, MKCHandledScreen,
    // ScreenPanelAdapter) reserves padding pixels between the panel
    // background and where elements actually render. Panel-side mirror
    // of the canonical screen padding.
    //
    // INTERIOR_PADDING is the value for STYLED panels (RAISED / DARK /
    // INSET) — those need breathing room between a visible frame and the
    // elements inside. For PanelStyle.NONE there's no visible frame to
    // space FROM, so the per-style query {@link #interiorPadding()}
    // returns 0 — element edge = panel edge. Render sites that care about
    // per-style behavior call {@link #interiorPadding()}; the constant
    // remains for consumers who explicitly want the styled value.
    public static final int INTERIOR_PADDING = 7;

    private final String id;
    private final List<PanelElement> elements;
    private final PanelStyle style;
    private final PanelPosition position;
    private final int toggleKey; // GLFW key code that toggles visibility, or -1 for none
    private boolean visible;

    /**
     * Optional panel-level hover-triggered tooltip. Fires when the cursor is
     * over the panel's outer bounds regardless of which child element (if
     * any) is also hovered. Consumers who want tooltips that fire only on
     * specific children should put tooltips on those children — child
     * tooltips can overlap with this one and both will queue
     * (last-call-wins per vanilla's {@code setTooltipForNextFrame} semantics,
     * so child-render order determines which wins; the element pass runs
     * before this panel-level tooltip pass, so the panel tooltip wins by
     * default — see {@link #maybeQueueTooltip}).
     */
    private @Nullable Supplier<Component> tooltipSupplier;

    // ── Size pinning (M5 region stacking) ──────────────────────────────
    // When pinnedWidth >= 0, getWidth() returns pinnedWidth regardless of
    // element visibility. Same for pinnedHeight. Opt-in escape hatch for
    // panels whose dynamic element visibility would otherwise cause
    // getWidth/getHeight to collapse to zero, which would jitter the
    // stacking of subsequent panels in a region.
    //
    // Phase 16g — these pinned dims double as triggers for auto-wrap +
    // auto-scroll:
    //   - pinnedWidth set → every child TextLabel wraps to fit the panel's
    //     content width (pinnedWidth − 2 × INTERIOR_PADDING, minus the
    //     scrollbar reserve when pinnedHeight is also set).
    //   - pinnedHeight set + aggregate content height > pinnedHeight →
    //     getElements() returns a single internal ScrollContainer wrapping
    //     the original elements; mouse-wheel scrolls; scrollbar appears.
    // Both are always-on for bounded panels (no opt-in flag) per Trev's
    // 16g architectural call.
    private int pinnedWidth = -1;
    private int pinnedHeight = -1;

    // ── Pass 3 adaptive screen-edge wrap ───────────────────────────────
    // effectiveContentWidth is the screen-edge-derived content-width ceiling
    // set per-frame by the placement layer (RegionRegistry.resolveMenuOrigin,
    // MKScreen/MKCHandledScreen.computePanelSize, the HUD + vanilla-screen +
    // slot-group origin paths) via setAvailableContentWidth. -1 = unset (no
    // ceiling; panel grows to content as before). Distinct from pinnedWidth on
    // purpose: a library-computed fit ceiling must NOT masquerade as a
    // consumer-declared pin (getPinnedWidth() stays honest, so MKC's
    // pinned-vs-auto layout logic is untouched).
    //
    // The ceiling only bites when the panel is UNPINNED and its natural content
    // width exceeds the ceiling — then TextLabels wrap to it (the existing 16g
    // wrap machinery, now screen-driven instead of pin-driven). lastFitAvail
    // guards the configuration pass: setAvailableContentWidth flips
    // configurationDirty ONLY when the available width actually changes, so the
    // per-frame placement calls are free and the auto-scroll ScrollContainer
    // isn't rebuilt every frame (which would reset its drag state).
    private int effectiveContentWidth = -1;
    private int lastFitAvail = Integer.MIN_VALUE;

    // ── Movement ②: adaptive screen-edge auto-scroll (vertical twin) ────
    // effectiveContentHeight is the screen-edge-derived content-HEIGHT ceiling
    // set per-frame by the placement layer (RegionRegistry.resolveMenuOrigin)
    // via setAvailableContentHeight. -1 = unset. The height analog of
    // effectiveContentWidth: when the panel is UNPINNED (no pinnedHeight) and its
    // natural content height exceeds this ceiling, the panel auto-scrolls into the
    // ceiling (viewport = ceiling) instead of running off the top/bottom screen
    // edge — reusing the same ScrollContainer machinery pinnedHeight drives. When
    // content fits under the ceiling, nothing changes (no scroll, no reserve).
    // lastFitAvailHeight guards the configuration pass exactly like lastFitAvail.
    private int effectiveContentHeight = -1;
    private int lastFitAvailHeight = Integer.MIN_VALUE;

    // ── Phase 16g Auto-Scroll state ────────────────────────────────────
    // Scroll offset (0.0 - 1.0) for the auto-scroll wrap. Panel owns the
    // state directly here rather than delegating to a consumer-side field
    // because auto-scroll is an internal Panel concern — the consumer
    // never sees the inner ScrollContainer. Mutable; updated by
    // ScrollContainer's callback when the user scrolls.
    private double scrollOffset = 0.0;

    // Cached internal ScrollContainer for auto-scroll mode. Constructed
    // lazily on first getElements() call after pinnedHeight triggers
    // overflow, then reused across frames so its drag state + cached
    // render origin stay stable. Rebuilt only when pinnedHeight /
    // pinnedWidth change (the only mutable inputs to its construction).
    private @Nullable ScrollContainer cachedScrollContainer;

    // Tracks whether the configuration pass (wrap-width propagation +
    // scroll-container construction) has run since the last pinned-dim
    // change. The configuration pass is idempotent and cheap, but skipping
    // when nothing changed saves a per-frame walk over elements.
    private boolean configurationDirty = true;

    // Movement ④ — signature of the raw elements' live dimensions at the last
    // configuration pass. When an element changes its own size without a ceiling
    // change (e.g. a SlotFlowElement growing rows on a slot reveal), this differs
    // from the freshly-computed signature and forces a reconfigure. Sentinel
    // (MIN_VALUE) until the first pass so the first ensureConfigured always runs.
    private int lastLayoutSig = Integer.MIN_VALUE;

    // Supplier-driven visibility (Phase 10). When non-null, this takes precedence
    // over the imperative `visible` field — isVisible() reads the supplier, and
    // setVisible(...) silently no-ops. Clear with showWhen(null) to revert to
    // imperative control. Matches the Phase 8/9 state-ownership pattern
    // (Toggle.linked): consumer holds the state; library reads via supplier.
    // Uses BooleanSupplier (no boxing), unified with the element-level
    // showWhen / disabledWhen / revealWhen predicate type.
    private @Nullable BooleanSupplier visibilitySupplier;

    // Opacity / dim / modal-tracking flags (Phase 14d-2.5 M9 mechanism).
    //
    // Three independent flags compose the "modal" semantic. The 14d-1 single-
    // flag `cancelsUnhandledClicks` bundled all three concerns; M9 factors
    // them so future primitives (popovers, dropdowns) can opt into pieces
    // independently. See Design Docs/Mechanisms/M9_PANEL_OPACITY.md.
    //
    // - `opaque` (default TRUE): interaction opacity. When the panel is
    //   visible, input arriving at coords within the panel's bounds is
    //   handled by the panel; vanilla underneath never sees it. Default-true
    //   delivers Trevor's click-through prohibition principle: visible
    //   panels are interaction-opaque over their bounds. Consumers wanting
    //   transparent overlays opt out via `opaque(false)`.
    //
    // - `dimsBehind` (default FALSE): visual dim layer. When this panel is
    //   visible, ScreenPanelRegistry's render path inserts a translucent-
    //   black quad over the underlying screen before drawing this panel.
    //   Real modals set this true; non-modal opaque panels (decoration,
    //   popups) leave it false.
    //
    // - `tracksAsModal` (default FALSE): global modal-tracking. When this
    //   panel is visible, the library locks the OS cursor (no clickable-
    //   feedback over vanilla widgets) and eats keystrokes other than
    //   Escape. Real modals set this true; non-modal opaque panels leave
    //   it false (cursor + keyboard work normally outside the panel's
    //   bounds).
    //
    // The Panel.modal() sugar sets all three to true — canonical real-modal
    // pattern. Independent flag setters are exposed for non-canonical
    // compositions (popovers, click-blockers, etc.).
    private boolean opaque = true;
    private boolean dimsBehind = false;
    private boolean tracksAsModal = false;

    // Optional Escape action (B3 modal-Escape fix). When this panel is a
    // visible tracksAsModal panel and the user presses Escape, the host
    // (MKScreen / the container-screen ScreenPanelRegistry key path) invokes
    // this action to dismiss the topmost modal — instead of letting Escape
    // close the whole host screen out from under an open dialog. The dialog
    // builders (ConfirmDialog / AlertDialog) register their onCancel /
    // onAcknowledge here so the consumer's existing self-dismiss callback
    // fires on Escape. Null = no escape action declared (the host still EATS
    // Escape while a modal is up so it can't close the host screen).
    private @Nullable Runnable escapeAction;

    // Set during handler construction — typed via PanelOwner interface
    // so Panel doesn't depend on the screen package.
    private @Nullable PanelOwner owner;

    /**
     * Full constructor with all metadata.
     *
     * <p><b>Internal construction path.</b> Consumers build panels via
     * {@link #builder(String)} — the fluent builder names only the fields the
     * consumer cares about and hides the {@code -1} no-toggle-key sentinel
     * behind {@link Builder#toggleKey(int)}. This positional constructor is the
     * builder's terminal target and the library's own internal construction
     * site; it is kept public only so the builder (a nested static class) and
     * cross-package library code can reach it. Marked {@link ApiStatus.Internal}
     * to steer fresh consumers to {@code Panel.builder(...)} instead of the
     * magic-{@code -1} positional form.
     *
     * @param id        unique identifier within the screen
     * @param elements  panel elements — buttons, text labels, etc. (immutable after construction)
     * @param visible   initial visibility state
     * @param style     visual style for panel background rendering
     * @param position  how this panel is positioned in the layout
     * @param toggleKey GLFW key code that toggles this panel's visibility, or -1 for none
     */
    @ApiStatus.Internal
    public Panel(String id, List<PanelElement> elements,
                 boolean visible, PanelStyle style, PanelPosition position,
                 int toggleKey) {
        this.id = id;
        this.elements = List.copyOf(elements);
        this.visible = visible;
        this.style = style;
        this.position = position;
        this.toggleKey = toggleKey;
    }

    /**
     * Convenience constructor — default style (RAISED), position (BODY), no
     * toggle key. Use {@link #builder(String)} in consumer code; this
     * positional form is {@link ApiStatus.Internal} so fresh consumers are
     * steered to the builder.
     */
    @ApiStatus.Internal
    public Panel(String id, List<PanelElement> elements, boolean visible) {
        this(id, elements, visible, PanelStyle.RAISED, PanelPosition.BODY, -1);
    }

    /**
     * Convenience constructor — a visible panel with default style and
     * position. Use {@link #builder(String)} in consumer code; this positional
     * form is {@link ApiStatus.Internal} so fresh consumers are steered to the
     * builder.
     */
    @ApiStatus.Internal
    public Panel(String id, List<PanelElement> elements) {
        this(id, elements, true);
    }

    // ── Builder ─────────────────────────────────────────────────────────

    /**
     * Sentinel toggle-key value meaning "no toggle key." Hidden behind the
     * {@link Builder} so consumers never type a magic {@code -1} — they either
     * leave {@link Builder#toggleKey(int)} unset (no key) or pass a real GLFW
     * key code.
     */
    static final int NO_TOGGLE_KEY = -1;

    /**
     * Starts a fluent builder for a Panel with the given id. Additive
     * alternative to the positional constructors — lets a consumer name only
     * the fields they care about and skip the magic {@code -1} toggle-key
     * sentinel entirely.
     *
     * <pre>{@code
     * Panel p = Panel.builder("settings")
     *     .add(new Button(...))
     *     .add(new Toggle(...))
     *     .style(PanelStyle.RAISED)
     *     .position(PanelPosition.BODY)
     *     .build();
     * }</pre>
     *
     * @param id unique identifier within the screen
     * @return a new {@link Builder}
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Fluent builder for {@link Panel}. Mirrors the full positional
     * constructor's parameters as named, optional setters with sensible
     * defaults (visible, {@link PanelStyle#RAISED}, {@link PanelPosition#BODY},
     * no toggle key). The {@code -1} no-toggle-key sentinel is hidden behind
     * {@link #toggleKey(int)} — leaving it unset means "no key."
     */
    public static final class Builder {
        private final String id;
        private final List<PanelElement> elements = new java.util.ArrayList<>();
        private boolean visible = true;
        private PanelStyle style = PanelStyle.RAISED;
        private PanelPosition position = PanelPosition.BODY;
        private int toggleKey = NO_TOGGLE_KEY;

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Replaces the accumulated element list with the given one. Additive
         * with {@link #add(PanelElement)} — call order is: this resets the
         * list, subsequent {@code add(...)} append to it.
         */
        public Builder elements(List<PanelElement> elements) {
            this.elements.clear();
            this.elements.addAll(elements);
            return this;
        }

        /** Appends a single element to the panel's element list. */
        public Builder add(PanelElement element) {
            this.elements.add(element);
            return this;
        }

        /**
         * Appends an element declared as an
         * {@link com.trevorschoeny.menukit.core.layout.ElementSpec} — the same
         * fluent shape the dialog and {@code Row}/{@code Column} builders
         * consume. The spec is instantiated immediately at its declared origin
         * ({@code 0, 0}), so a consumer can write
         * {@code Panel.builder(id).add(Button.spec(...))} without dropping to a
         * raw constructor.
         *
         * <p>The instantiated element keeps its declared {@code (0, 0)} origin
         * unless the consumer repositions it. To place several specs at fixed
         * offsets, instantiate them through {@code Button.spec(...).at(x, y)}
         * (the spec's own {@code at(...)} returns the positioned element) and
         * feed those to {@link #add(PanelElement)}, or compose them with a
         * {@code Row}/{@code Column} and pass the resulting list to
         * {@link #elements(List)} — both layout helpers compute positions for
         * you. This overload simply completes builder symmetry with the layout
         * + dialog builders, which already accept {@code ElementSpec}.
         *
         * @param spec the element specification (dims + deferred construction)
         * @return this builder, for chaining
         */
        public Builder add(com.trevorschoeny.menukit.core.layout.ElementSpec spec) {
            this.elements.add(spec.at(0, 0));
            return this;
        }

        /** Sets the initial visibility state. Default {@code true}. */
        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        /** Sets the panel's visual background style. Default {@link PanelStyle#RAISED}. */
        public Builder style(PanelStyle style) {
            this.style = style;
            return this;
        }

        /** Sets how the panel is positioned in the layout. Default {@link PanelPosition#BODY}. */
        public Builder position(PanelPosition position) {
            this.position = position;
            return this;
        }

        /**
         * Sets the GLFW key code that toggles this panel's visibility. Leave
         * unset for no toggle key — there is no need to pass the {@code -1}
         * sentinel by hand.
         */
        public Builder toggleKey(int glfwKeyCode) {
            this.toggleKey = glfwKeyCode;
            return this;
        }

        /** Builds the configured Panel. */
        public Panel build() {
            return new Panel(id, elements, visible, style, position, toggleKey);
        }
    }

    // ── Identity ────────────────────────────────────────────────────────

    /** Returns this panel's unique identifier within the screen. */
    public String getId() { return id; }

    // ── Style & Position ──────────────────────────────────────────────

    /** Returns the visual style for this panel's background. */
    public PanelStyle getStyle() { return style; }

    /**
     * Returns the content padding the panel needs between its outer
     * (background) bounds and the element-render origin, in pixels.
     *
     * <p>Style-conditional:
     * <ul>
     *   <li>{@link PanelStyle#NONE} → {@code 0}. The panel has no visible
     *       frame, so there's nothing for elements to space FROM — the
     *       element edge IS the panel edge. Hover/click/tooltip bounds
     *       collapse onto the element extent.</li>
     *   <li>All other styles ({@link PanelStyle#RAISED},
     *       {@link PanelStyle#DARK}, {@link PanelStyle#INSET}) →
     *       {@link #INTERIOR_PADDING} ({@code 7}). Breathing room
     *       between the visible frame and the elements inside.</li>
     * </ul>
     *
     * <p>Consumed by every render context that computes outer bounds
     * from element extent (or vice versa): {@code MKScreen},
     * {@code MKCHandledScreen}, and the default-padding overloads of
     * {@link com.trevorschoeny.menukit.inject.ScreenPanelAdapter} +
     * {@link com.trevorschoeny.menukit.inject.SlotGroupPanelAdapter}.
     * Explicit-padding adapter overloads bypass this — the consumer is
     * in control.
     */
    public int interiorPadding() {
        return style == PanelStyle.NONE ? 0 : INTERIOR_PADDING;
    }

    /** Returns how this panel is positioned in the layout. */
    public PanelPosition getPosition() { return position; }

    /** Returns the GLFW key code that toggles this panel's visibility, or -1 for none. */
    public int getToggleKey() { return toggleKey; }

    // ── Elements ────────────────────────────────────────────────────────

    /**
     * Returns the effective element list for layout / render / input
     * dispatch.
     *
     * <p>Normal mode (no pinnedHeight, or content fits within pinnedHeight):
     * returns the panel's original declared elements.
     *
     * <p>Auto-scroll mode (pinnedHeight set + aggregate content height
     * exceeds pinnedHeight): returns a single internal
     * {@link ScrollContainer} wrapping the original elements. The screen
     * iterates this list opaquely — the ScrollContainer dispatches render,
     * click, scroll, and release to its children internally. From the
     * screen's POV, the swap is transparent: it's "an element," and
     * elements know how to render and route input.
     *
     * <p><b>Lifecycle note (Pass 3):</b> {@link ScrollContainer} now propagates
     * {@code onAttach} / {@code onDetach} / {@code keyPressed} to its children,
     * so widget-wrapping elements (Slider, TextField) inside an auto-scroll panel
     * register their vanilla widgets and route keyboard correctly — there is no
     * longer a propagation gap at the auto-scroll boundary.
     */
    public List<PanelElement> getElements() {
        ensureConfigured();
        if (cachedScrollContainer != null) {
            return List.of(cachedScrollContainer);
        }
        return elements;
    }

    /**
     * Returns the raw original elements declared at construction,
     * regardless of auto-scroll wrapping. Used internally by the
     * configuration pass and any consumer that needs to bypass the
     * effective-list swap (rare).
     */
    public List<PanelElement> getRawElements() {
        return elements;
    }

    // ── Phase 16g Configuration Pass ───────────────────────────────────

    /**
     * Runs the wrap + scroll configuration pass if pinned dims have
     * changed since the last pass. Idempotent and cheap; safe to call
     * from any size/element accessor.
     *
     * <p><b>Semantic note on pinned dims:</b> {@code pinnedWidth} and
     * {@code pinnedHeight} represent the panel's <i>content extent</i>
     * (matching the existing M5 contract — what {@link #getWidth()} /
     * {@link #getHeight()} return). The consumer-side screen adds its own
     * {@code PANEL_PADDING} on top to produce the panel's outer (background)
     * extent. So a panel with {@code pinnedWidth=80} renders 80px of
     * content + 2 × 7px of background padding = 94px outer.
     *
     * <p>Two responsibilities:
     * <ol>
     *   <li><b>Auto-wrap propagation</b> — when {@code pinnedWidth} is set,
     *       walks {@link #elements} and calls {@code setWrapWidth} on every
     *       {@link TextLabel} child. Budget = {@code pinnedWidth} (the
     *       content extent), minus the scrollbar reserve when
     *       {@code pinnedHeight} is also set (always-reserve, since
     *       post-wrap overflow can't be known until wrap is computed —
     *       see comment in body).</li>
     *   <li><b>Auto-scroll wrap</b> — when {@code pinnedHeight} is set AND
     *       aggregate content height (after wrap propagation, so wrapped
     *       heights are accurate) exceeds {@code pinnedHeight}, builds an
     *       internal {@link ScrollContainer} wrapping all original
     *       elements. {@link #getElements()} then returns this wrapper.
     *       ScrollContainer outer width = {@code pinnedWidth} if set,
     *       otherwise aggregate child width + scrollbar reserve.</li>
     * </ol>
     */
    private void ensureConfigured() {
        // Movement ④ — element-DRIVEN layout changes re-trigger the gated pass.
        // The pinned/available ceilings flip configurationDirty, but an element
        // that changes its OWN live size without a ceiling change would not —
        // e.g. a SlotFlowElement whose slot visibility toggled, growing its row
        // count. That left the auto-scroll overflow check (Step 4, gated here)
        // stale, so a reveal that overflows the height ceiling wouldn't build the
        // ScrollContainer. Detect a change in the raw elements' live dimensions
        // and force a reconfigure so wrap + scroll stay correct on reveal. Cheap
        // (no allocation), stable between reveals (same dims → same signature →
        // early-return), inert for panels whose elements don't self-resize.
        int sig = layoutSignature();
        if (sig != lastLayoutSig) configurationDirty = true;
        if (!configurationDirty) return;
        configurationDirty = false;

        // ── Step 1: resolve ONE content width, top-down ────────────────
        // The panel's content width is the single number every element reacts
        // to (Verification-4: element lives in panel; width flows DOWN). It is
        // the panel's natural hug-width — the widest element's UNCONSTRAINED
        // extent — clamped to the available ceiling so the panel never bleeds
        // past the screen-edge margin.
        //
        //   pinnedWidth set → contentWidth = pinnedWidth (consumer declared it).
        //   else            → contentWidth = min(naturalWidth, ceiling), where
        //                     ceiling = effectiveContentWidth (screen-edge room
        //                     set per-frame by the placement layer) or unbounded
        //                     when no placement ceiling applies. When the natural
        //                     width already fits under the ceiling, the panel
        //                     keeps hugging its content exactly as before — the
        //                     ceiling only bites a genuine overflow.
        int naturalW = 0;
        for (PanelElement e : elements) {
            if (!e.isVisible()) continue;
            int right = e.getChildX() + e.naturalWidth();
            if (right > naturalW) naturalW = right;
        }
        int contentWidth;
        if (pinnedWidth >= 0) {
            contentWidth = pinnedWidth;
        } else {
            int ceiling = (effectiveContentWidth >= 0)
                    ? effectiveContentWidth : Integer.MAX_VALUE;
            contentWidth = Math.min(naturalW, ceiling);
        }

        // ── Step 2+3+4: budget → measure → (only-on-overflow) reserve + scroll
        //
        // The viewport that bounds the panel vertically is the explicit
        // pinnedHeight if declared, else the screen-edge height ceiling
        // (Movement ② — effectiveContentHeight). Either way the panel scrolls
        // ONLY when its natural content overflows that viewport.
        // pinnedHeight (consumer-declared) always drives a scroll viewport. The
        // screen-edge height ceiling (②) only does so when it leaves a USEFUL
        // viewport: a region sibling squeezed to a sliver against a tall frame would
        // otherwise build a uselessly-tiny scrollbar — below the threshold we let it
        // render at natural height and rely on resolveMenu's on-screen clamp (the
        // honest height fallback, mirroring how an over-wide panel clamps).
        int viewportHeight;
        if (pinnedHeight >= 0) {
            viewportHeight = pinnedHeight;
        } else if (effectiveContentHeight >= MIN_SCROLL_VIEWPORT) {
            viewportHeight = effectiveContentHeight;
        } else {
            viewportHeight = -1;
        }

        // First lay out every element against the FULL content width (no
        // scrollbar reserve) and reflow, then measure the natural content
        // height. The reserve is deducted ONLY if the panel actually overflows
        // its viewport — otherwise a fitting panel would needlessly narrow its
        // content (wrapping text early) to clear a scrollbar that never appears.
        // (Pre-Movement-② the reserve was deducted unconditionally on any
        // pinnedHeight panel; gating it on real overflow is strictly tighter and
        // is what lets the placement layer impose a height ceiling on EVERY
        // region panel without shrinking the ones that fit.)
        layoutElementsWithin(contentWidth);
        reflowForWrap();
        int naturalContentHeight = aggregateRawContentHeight();

        cachedScrollContainer = null;
        boolean overflow = viewportHeight > 0 && naturalContentHeight > viewportHeight;
        if (overflow) {
            // Re-lay out with the scrollbar reserve so wrapped content clears the
            // track, reflow again, then wrap everything in a ScrollContainer sized
            // to the viewport. Outer width = the panel's content area: pinnedWidth
            // when declared; the ceiling-clamped contentWidth when a screen-edge
            // width ceiling bit; otherwise the natural child extent plus reserve.
            int reserve = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
            layoutElementsWithin(contentWidth - reserve);
            reflowForWrap();
            int outerWidth;
            if (pinnedWidth >= 0) {
                outerWidth = pinnedWidth;
            } else if (effectiveContentWidth >= 0) {
                outerWidth = contentWidth;
            } else {
                outerWidth = aggregateRawContentWidth() + reserve;
            }
            // ScrollContainer.Builder.size() rejects widths <= track + gutter + 1
            // (= 17). Skip scroll silently if the budget is too tight — content
            // overflows but no crash.
            int minScrollWidth = reserve + 1;
            if (outerWidth > minScrollWidth) {
                cachedScrollContainer = ScrollContainer.builder()
                        .at(0, 0)
                        .size(outerWidth, viewportHeight)
                        .content(elements)
                        .scrollOffset(() -> scrollOffset, v -> scrollOffset = v)
                        .build();
            }
        }

        // Store the POST-reflow signature so a SETTLED layout doesn't re-trigger an
        // extra reconfigure next frame: reflow just mutated each element's live
        // childY, and layoutSignature() reads childY — computing it HERE (after
        // reflow) means the signature the next frame derives from the settled
        // positions matches, so the gate skips. A real change (visibility / size / a
        // runtime move) still shifts the signature and re-triggers correctly.
        lastLayoutSig = layoutSignature();
    }

    /**
     * Step 2 of {@link #ensureConfigured}: hands each element its horizontal
     * budget = {@code contentWidth} minus the element's own left offset (room
     * from the element's left edge to the panel's content right edge). Every
     * element resolves its width — and, if it wraps a label, its height —
     * REVERSIBLY from this budget, so a later wider budget restores natural size.
     * Extracted so the configuration pass can lay out twice when overflow forces
     * a scrollbar reserve (first at full width to detect overflow, then narrower).
     */
    private void layoutElementsWithin(int contentWidth) {
        for (PanelElement e : elements) {
            int budget = contentWidth - e.getChildX();
            if (budget < MIN_ELEMENT_WIDTH) budget = MIN_ELEMENT_WIDTH;
            e.layoutWithin(budget);
        }
    }

    /**
     * A cheap, allocation-free hash of the raw elements' current visibility +
     * live position/size — the gate for the element-driven reconfigure
     * (Movement ④, see {@link #ensureConfigured}). Reads each element's
     * getChildX/Y/Width/Height (the flow's are live; a fixed element's are
     * constant), so the value is stable frame-to-frame unless an element actually
     * resizes/moves, at which point it changes and re-triggers configuration.
     */
    private int layoutSignature() {
        int sig = 1;
        for (PanelElement e : elements) {
            boolean vis = e.isVisible();
            sig = sig * 31 + (vis ? 1 : 0);
            if (vis) {
                sig = sig * 31 + e.getChildX() + e.getWidth();
                sig = sig * 31 + e.getChildY() + e.getHeight();
            }
        }
        return sig;
    }

    /** Minimum horizontal budget handed to any element (a tight gutter can't
     *  squeeze content to nothing). See {@link #ensureConfigured}. */
    private static final int MIN_ELEMENT_WIDTH = 8;

    /** Smallest screen-edge height ceiling (②) worth auto-scrolling INTO. When a
     *  region sibling is squeezed against a tall main frame its available height can
     *  floor to a sliver; building a scrollbar into fewer than this many pixels is
     *  worse than rendering at natural height and relying on the on-screen clamp.
     *  Gates ONLY the library-imposed screen-edge ceiling — a consumer's explicit
     *  {@code pinnedHeight} is always honored. See {@link #ensureConfigured}. */
    private static final int MIN_SCROLL_VIEWPORT = 24;

    /**
     * Re-stacks elements' live childY from their BASELINES so an auto-wrapped
     * (taller) element pushes everything below it down by exactly the extra
     * height — keeping fixed-childY layouts overlap-free under wrap.
     *
     * <p>The baseline is each element's authored / explicitly-moved Y, held on
     * the element itself ({@link AbstractPanelElement#reflowBaselineY()}, captured
     * lazily and updated by {@code at}/{@code setChildPosition}). Reflow sets the
     * live childY to {@code baseline + cumulativePush} via
     * {@link AbstractPanelElement#applyReflowedY} — which leaves the baseline
     * intact, so the push is reversible (a wider frame that un-wraps restores
     * {@code baseline + 0}) AND a runtime move re-bases the stack instead of being
     * clobbered. (Earlier this read a Panel-level snapshot captured once on the
     * first pass; that snapshot never saw a runtime move, so reflow re-asserted
     * the stale Y and explicit moves silently no-op'd.)
     *
     * <p>No-op when nothing wrapped (every extra is 0 → live = baseline). Render
     * and input read position via {@code getChildY()} each frame (§0047), so the
     * reflow takes effect uniformly with no dispatcher changes.
     *
     * <p>Only library elements ({@link AbstractPanelElement}) can be repositioned;
     * a bare custom {@link PanelElement} keeps its position (it doesn't auto-wrap,
     * so it never drives a reflow — only ever receives a push, which it can't take
     * here). Elements sharing a baseline childY form one row: the row's downward
     * push is the tallest wrap within it, so side-by-side cells (e.g. a slot grid
     * row) don't double-count.
     */
    private void reflowForWrap() {
        if (elements.isEmpty()) return;
        int n = elements.size();

        // Each element's BASELINE Y (authored / explicitly-moved). A bare custom
        // element can't be repositioned, so its baseline = its current childY.
        int[] baseline = new int[n];
        for (int i = 0; i < n; i++) {
            PanelElement e = elements.get(i);
            baseline[i] = (e instanceof AbstractPanelElement<?> ape)
                    ? ape.reflowBaselineY() : e.getChildY();
        }

        // Visit in baseline-Y (top-to-bottom) order so a wrapped row pushes only
        // the rows below it.
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order,
                java.util.Comparator.comparingInt(i -> baseline[i]));

        int cumulative = 0;            // extra pushed down by wrapped rows above
        int rowExtra = 0;              // tallest wrap in the current row
        int rowY = Integer.MIN_VALUE;  // current row's baseline Y
        for (int idx : order) {
            int dY = baseline[idx];
            if (dY != rowY) {
                cumulative += rowExtra; // bank the previous row's push
                rowExtra = 0;
                rowY = dY;
            }
            PanelElement e = elements.get(idx);
            if (e instanceof AbstractPanelElement<?> ape) {
                // Live Y = baseline + push; baseline untouched (reversible).
                ape.applyReflowedY(dY + cumulative);
            }
            if (e.isVisible()) {
                // Generic growth: any element that grew under layoutWithin — a
                // wrapped TextLabel OR a multi-line Button — pushes its row's
                // siblings down by its extra height.
                rowExtra = Math.max(rowExtra, e.extraLayoutHeight());
            }
        }
    }

    /**
     * Aggregate content height from the raw elements, used by the
     * configuration pass to detect scroll overflow. Walks the raw element
     * list (NOT {@link #getElements()}, to avoid recursion through the
     * configuration pass) and returns the max {@code childY + height}.
     */
    private int aggregateRawContentHeight() {
        int max = 0;
        for (PanelElement e : elements) {
            if (!e.isVisible()) continue;
            int bottom = e.getChildY() + e.getHeight();
            if (bottom > max) max = bottom;
        }
        return max;
    }

    /**
     * Aggregate content width from the raw elements. Used by the
     * configuration pass when auto-scroll fires without an explicit
     * {@code pinnedWidth} — gives the ScrollContainer a natural outer
     * width based on the widest child.
     */
    private int aggregateRawContentWidth() {
        int max = 0;
        for (PanelElement e : elements) {
            if (!e.isVisible()) continue;
            int right = e.getChildX() + e.getWidth();
            if (right > max) max = right;
        }
        return max;
    }

    // ── Size (for M5 region stacking) ──────────────────────────────────

    /**
     * Pins this panel's width and height for the region-stacking calculation.
     * Overrides the auto-sized bounding-box computation in {@link #getWidth()}
     * and {@link #getHeight()}. Use when the panel's content is
     * supplier-driven and its auto-size would fluctuate between frames —
     * pinning stabilizes the stacking math so subsequent panels in the same
     * region don't jitter.
     *
     * @param w pinned width in pixels (must be non-negative)
     * @param h pinned height in pixels (must be non-negative)
     * @return this panel, for method chaining
     */
    public Panel size(int w, int h) {
        this.pinnedWidth = w;
        this.pinnedHeight = h;
        // Pinned dims feed wrap-width + scroll-viewport calculations, so
        // re-run the configuration pass on next access.
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
        return this;
    }

    /**
     * Sets only the pinned width (height stays auto-sized). Trigger for
     * auto-wrap without auto-scroll — text wraps to the pinned width but
     * vertical extent grows naturally to fit the wrapped content.
     * Chainable.
     */
    public Panel pinnedWidth(int w) {
        this.pinnedWidth = w;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
        return this;
    }

    /**
     * Returns the pinned width set via {@link #size(int,int)} or
     * {@link #pinnedWidth(int)}, or {@code -1} if no pinned width is
     * declared. Exposed for consumer-screen layout code that needs to
     * distinguish "consumer declared a fixed width" from "panel
     * auto-sized" — e.g., MKC's {@code MKCHandledScreen.computePanelSize}
     * which uses pinned-when-set and slot+element max otherwise. The
     * panel's auto-sized {@link #getWidth()} aggregates from elements
     * only, so callers needing the slot-aware width can't infer
     * "pinned vs auto" from {@code getWidth()} alone.
     */
    public int getPinnedWidth() {
        return pinnedWidth;
    }

    /**
     * Returns the pinned height set via {@link #size(int,int)} or
     * {@link #pinnedHeight(int)}, or {@code -1} if no pinned height is
     * declared. See {@link #getPinnedWidth()} for the symmetric
     * "pinned vs auto" rationale.
     */
    public int getPinnedHeight() {
        return pinnedHeight;
    }

    /**
     * Sets only the pinned height (width stays auto-sized). Trigger for
     * auto-scroll without auto-wrap — content scrolls vertically when it
     * exceeds the pinned height; text doesn't wrap (long lines clip
     * horizontally inside the scissor). Chainable.
     */
    public Panel pinnedHeight(int h) {
        this.pinnedHeight = h;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
        return this;
    }

    /**
     * Sets the screen-edge-derived available content width (Pass 3 adaptive
     * wrap). Called by the placement layer EVERY frame, before reading
     * {@link #getWidth()}, with the content-width room remaining before the
     * physical screen-edge margin at this panel's anchor. When the panel is
     * unpinned and its natural content is wider than {@code avail}, its
     * TextLabels wrap to {@code avail} (and an auto-scroll viewport is sized to
     * it) instead of overflowing the screen — making screen-edge-aware
     * adaptive wrapping the default for every panel, with no consumer opt-in.
     *
     * <p><b>Idempotency / staleness guard.</b> Re-flips
     * {@code configurationDirty} (and drops the cached scroll container) ONLY
     * when {@code avail} actually changes from the last call. The placement
     * layer calls this per frame; without the guard, a stable frame would
     * either keep a stale wrap (if it never re-ran) or rebuild the scroll
     * container every frame (resetting its drag state). The guard delivers
     * both: re-fits on a real change (window resize), free otherwise.
     *
     * <p>Library-internal — consumers never call this; it carries the screen
     * geometry the panel itself is deliberately blind to. Negative values are
     * floored to a small minimum so a degenerate frame can't wrap to zero.
     *
     * @param avail content-width room before the screen-edge margin, in pixels
     */
    @ApiStatus.Internal
    public void setAvailableContentWidth(int avail) {
        int clamped = Math.max(MIN_FIT_WIDTH, avail);
        if (clamped == lastFitAvail) return; // no change — keep the cached pass
        lastFitAvail = clamped;
        this.effectiveContentWidth = clamped;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
    }

    /** Floor for {@link #setAvailableContentWidth} so a degenerate frame
     *  (tiny/negative avail) can't wrap content to nothing. */
    private static final int MIN_FIT_WIDTH = 16;

    /**
     * Imposes the per-frame screen-edge content-HEIGHT ceiling (Movement ②) —
     * the vertical twin of {@link #setAvailableContentWidth}. The placement layer
     * computes how much room the panel's anchor leaves toward the screen edge
     * ({@link RegionMath#availableMenuHeight}) and passes it here; when the panel's
     * natural content height exceeds the ceiling, the panel auto-scrolls into it
     * (reusing the {@code pinnedHeight} ScrollContainer path) instead of running
     * off-screen. When content fits under the ceiling, this is inert — no scroll,
     * no scrollbar reserve.
     *
     * <p>Idempotency guard identical to {@link #setAvailableContentWidth}: re-flips
     * {@code configurationDirty} (and drops the cached scroll container) ONLY when
     * {@code avail} changes, so the per-frame placement calls are free and the
     * scroll container isn't rebuilt every frame (which would reset its drag state).
     *
     * <p>Library-internal — consumers never call this; it carries the screen
     * geometry the panel is deliberately blind to.
     *
     * @param avail content-height room before the screen-edge margin, in pixels
     */
    @ApiStatus.Internal
    public void setAvailableContentHeight(int avail) {
        int clamped = Math.max(MIN_FIT_HEIGHT, avail);
        if (clamped == lastFitAvailHeight) return; // no change — keep cached pass
        lastFitAvailHeight = clamped;
        this.effectiveContentHeight = clamped;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
    }

    /** Floor for {@link #setAvailableContentHeight} so a degenerate frame
     *  can't collapse the scroll viewport to nothing. */
    private static final int MIN_FIT_HEIGHT = 16;

    /**
     * Returns the panel's width for region-stacking math.
     *
     * <p>If a pinned size was declared via {@link #size(int, int)}, returns
     * the pinned width. Otherwise, returns the bounding-box extent computed
     * from visible elements (max {@code childX + width}), plus any
     * background-padding contribution when the panel has a non-NONE style.
     *
     * <p>Consumers whose panels have only supplier-gated elements should pin
     * the size explicitly — auto-size collapses to zero when all elements
     * report invisible, causing subsequent panels to shift inward for a
     * frame until the elements reappear.
     */
    public int getWidth() {
        if (pinnedWidth >= 0) return pinnedWidth;
        // Iterate the EFFECTIVE element list (via getElements) so that
        // when auto-scroll fires without an explicit pinnedWidth, the
        // ScrollContainer's outer width (which includes scrollbar reserve)
        // contributes to the panel's reported size. Falls through to raw
        // elements when no scroll wrapper is active.
        int extent = 0;
        for (PanelElement e : getElements()) {
            if (!e.isVisible()) continue;
            int right = e.getChildX() + e.getWidth();
            if (right > extent) extent = right;
        }
        return extent + backgroundPadding();
    }

    /**
     * Returns the panel's height for region-stacking math.
     *
     * <p>See {@link #getWidth()} — same auto-size/pin semantics along the Y axis.
     */
    public int getHeight() {
        if (pinnedHeight >= 0) return pinnedHeight;
        // Iterate effective elements — see getWidth() comment for the
        // auto-scroll rationale (scroll container's outer extent must
        // factor in even when only pinnedHeight is set).
        int extent = 0;
        for (PanelElement e : getElements()) {
            if (!e.isVisible()) continue;
            int bottom = e.getChildY() + e.getHeight();
            if (bottom > extent) extent = bottom;
        }
        return extent + backgroundPadding();
    }

    /**
     * Additional pixels the panel background contributes beyond the element
     * extent. Zero for {@link PanelStyle#NONE}; reserved as a style-specific
     * hook for frame insets when styled-background panels need it. Kept as a
     * single value (rather than per-edge insets) because all current styles
     * are visually symmetric. Currently returns 0 for all styles — refine
     * when visual verification shows frame clipping.
     */
    private int backgroundPadding() {
        return 0;
    }

    // ── Visibility ──────────────────────────────────────────────────────

    /**
     * Returns whether this panel is currently visible.
     *
     * <p>If a visibility supplier is set (via {@link #showWhen(java.util.function.BooleanSupplier)}),
     * returns the supplier's current value. Otherwise returns the imperative
     * {@code visible} field, which is controlled via {@link #setVisible(boolean)}.
     */
    public boolean isVisible() {
        if (visibilitySupplier != null) {
            return visibilitySupplier.getAsBoolean();
        }
        return visible;
    }

    /**
     * Sets this panel's visibility and notifies the owner to trigger
     * a sync pass over the affected slots.
     *
     * <p>When hidden, all slots become inert (getItem returns EMPTY,
     * canInsert returns false, quick-move skips them). When visible
     * again, slots resume normal behavior and the sync pass pushes
     * real stacks to the client.
     *
     * <p><b>No-op when a visibility supplier is active.</b> If
     * {@link #showWhen(java.util.function.BooleanSupplier)} has been called with a non-null supplier,
     * calls to {@code setVisible} are silently ignored — the supplier is the
     * single source of truth. Consumers who have committed to supplier-driven
     * visibility should not get spurious partial overrides from unrelated code
     * paths. Call {@code showWhen(null)} first to revert to imperative control.
     */
    public void setVisible(boolean visible) {
        if (visibilitySupplier != null) return; // silent no-op when supplier is active
        if (this.visible == visible) return;    // no-op if unchanged
        this.visible = visible;
        if (owner != null) {
            owner.onPanelVisibilityChanged(this);
        }
    }

    /**
     * Installs a supplier that drives this panel's visibility. Once set, the
     * supplier is the single source of truth — {@link #isVisible()} evaluates
     * it on each call, and {@link #setVisible(boolean)} becomes a silent no-op.
     *
     * <p>This matches the Phase 8/9 state-ownership pattern established by
     * {@code Toggle.linked}: the consumer holds the state, the library reads
     * it via the supplier, and there is no parallel library-owned field that
     * could desync from consumer state.
     *
     * <h4>Precedence semantics</h4>
     * <ul>
     *   <li>Calling {@code showWhen(supplier)} replaces any prior
     *       {@link #setVisible(boolean)} state. The imperative {@code visible}
     *       field is ignored while the supplier is active.</li>
     *   <li>To revert to imperative-only visibility, call
     *       {@code showWhen(null)}. The prior {@code setVisible} state is not
     *       restored; visibility resets to the default ({@code true}) until
     *       the consumer calls {@code setVisible} again.</li>
     * </ul>
     *
     * <h4>Sync-safety caveat</h4>
     *
     * Intended for panels whose visibility is a client-side rendering decision —
     * Phase 10 injected panels, HUD panels, standalone-screen panels. For
     * MenuKit-native inventory-menu panels with slot groups (where visibility
     * must drive slot-inertness and server→client sync), continue to use
     * {@link #setVisible(boolean)} — it notifies the owner to trigger the sync
     * pass. {@code showWhen} does not.
     *
     * @param supplier the visibility predicate, or {@code null} to revert to
     *                 imperative control.
     * @return this panel, for method chaining.
     */
    public Panel showWhen(@Nullable BooleanSupplier supplier) {
        this.visibilitySupplier = supplier;
        if (supplier == null) {
            // Reset to default-visible per the design-doc-locked semantics.
            // The prior setVisible state is not restored.
            this.visible = true;
        }
        return this;
    }

    // ── Opacity / dim / modal-tracking (M9) ────────────────────────────

    /**
     * Sets whether this panel is interaction-opaque over its bounds. Chainable.
     *
     * <p>When {@code true} and the panel is visible, input arriving at coords
     * within the panel's bounding box (clicks, hover, tooltip queueing) is
     * handled by the panel and does not pass through to vanilla widgets
     * underneath. Empty space within the panel's bounds eats input;
     * tooltips for items behind the panel are suppressed; slot hover
     * returns null.
     *
     * <p>This is Trevor's click-through prohibition principle (Phase 14d-2):
     * visible panels are interaction-opaque over their bounds. Default-true
     * makes opacity the path-of-least-friction; consumers wanting
     * transparent overlays opt out explicitly.
     *
     * <p>The interaction footprint is the panel's bounding box, regardless
     * of {@link PanelStyle}. {@code PanelStyle.NONE + opaque(true)} is the
     * "click blocker" pattern (invisible but blocks input). {@code
     * PanelStyle.NONE + opaque(false)} is the rare transparent-overlay
     * escape hatch.
     *
     * <p><b>Dispatcher coverage:</b> {@code ScreenPanelAdapter} panels
     * participate automatically via the unified registry. See M9 §4.4.
     *
     * <p>Default: {@code true} (M9 default-flip from the 14d-1
     * {@code cancelsUnhandledClicks} default of {@code false}).
     *
     * @param isOpaque {@code true} to make this panel interaction-opaque
     * @return this panel, for chaining
     */
    public Panel opaque(boolean isOpaque) {
        this.opaque = isOpaque;
        return this;
    }

    /** Returns whether this panel is interaction-opaque. See {@link #opaque(boolean)}. */
    public boolean isOpaque() {
        return opaque;
    }

    /**
     * Sets whether the screen dims visually behind this panel when visible.
     * Chainable.
     *
     * <p>When {@code true} and the panel is visible, the dispatcher renders
     * a translucent-black quad over the underlying screen before drawing
     * this panel. Used by real modal dialogs to visually distinguish them
     * from regular decoration.
     *
     * <p>Independent of {@link #opaque(boolean)} — a panel can be opaque
     * without dimming (popovers, dropdowns) and could in principle be
     * transparent-with-dim (unusual; not a current use case). Independent
     * of {@link #tracksAsModal(boolean)} — dim is purely visual; modal
     * tracking governs cursor + keyboard.
     *
     * <p>Default: {@code false}.
     *
     * @param dims {@code true} to dim the screen behind this panel
     * @return this panel, for chaining
     */
    public Panel dimsBehind(boolean dims) {
        this.dimsBehind = dims;
        return this;
    }

    /** Returns whether this panel dims the screen behind it. See {@link #dimsBehind(boolean)}. */
    public boolean dimsBehind() {
        return dimsBehind;
    }

    /**
     * Sets whether this panel participates in global modal tracking.
     * Chainable.
     *
     * <p>When {@code true} and the panel is visible, the library applies
     * window-state suppressions:
     * <ul>
     *   <li><b>Cursor lock</b> — {@code Window.setAllowCursorChanges(false)}
     *       per-tick; the OS cursor stays as DEFAULT regardless of vanilla
     *       widgets requesting clickable-feedback (creative tabs, etc.).</li>
     *   <li><b>Keyboard suppression</b> — keystrokes other than Escape are
     *       eaten before reaching the underlying screen. Escape closes the
     *       screen as normal v1 behavior.</li>
     *   <li><b>Outside-bounds click eating</b> — clicks outside any visible
     *       opaque panel are eaten while a tracksAsModal panel is up
     *       (preserves modal-blocking semantic).</li>
     * </ul>
     *
     * <p>Pointer-driven suppressions (slot hover, tooltip queueing) are
     * governed by {@link #opaque(boolean)} bounds-locally — they do NOT
     * require {@code tracksAsModal}. The asymmetry is principled: pointer
     * position localizes naturally; cursor + keyboard are window-state
     * concerns appropriately scoped to modal-tracking. See M9 §4.7.
     *
     * <p>Default: {@code false}.
     *
     * @param tracks {@code true} to participate in modal tracking
     * @return this panel, for chaining
     */
    public Panel tracksAsModal(boolean tracks) {
        this.tracksAsModal = tracks;
        return this;
    }

    /** Returns whether this panel participates in modal tracking. See {@link #tracksAsModal(boolean)}. */
    public boolean tracksAsModal() {
        return tracksAsModal;
    }

    /**
     * Whether this panel is positioned as a screen-centered overlay rather than
     * flowing in the body stack. True when the panel either declares
     * {@link PanelPosition#center()} <em>or</em> carries an overlay/modal visual
     * semantic ({@link #dimsBehind()} or {@link #tracksAsModal()}).
     *
     * <p>This is the single predicate the standalone-screen
     * ({@link com.trevorschoeny.menukit.screen.MKScreen}) layer uses to decide
     * three things together: (1) auto-center the panel on the screen window,
     * (2) exclude it from the body-stack layout + extent, and (3) draw it in the
     * on-top overlay pass. Folding {@code dimsBehind}/{@code tracksAsModal} in
     * (not just {@code center()}) means existing dialogs — which set those via
     * {@code modal()} but never call {@code center()} — keep auto-centering with
     * no consumer change, while a non-dim, non-modal overlay can opt in
     * explicitly with {@code position(PanelPosition.center())}.
     *
     * <p>Note this is POSITION, decoupled from the dim VISUAL: the dim fill is
     * still gated on {@link #dimsBehind()} alone (M9 doctrine — the three flags
     * stay independent). An overlay panel may center-and-draw-on-top without
     * dimming.
     */
    public boolean isOverlayPositioned() {
        return getPosition().mode() == PanelPosition.Mode.CENTER
                || dimsBehind()
                || tracksAsModal();
    }

    /**
     * Builder convenience setting all three modal flags to {@code true}.
     * Equivalent to {@code opaque(true).dimsBehind(true).tracksAsModal(true)}.
     *
     * <p>Canonical "real modal" pattern — sets the whole bundle in one call.
     * Consumers building non-canonical compositions (popovers, dropdowns,
     * click-blockers) reach for the independent flag setters.
     *
     * <p><b>Undefined combination warning:</b> {@code opaque(false) +
     * tracksAsModal(true)} is logically nonsensical (clicks pass through
     * but Escape closes + cursor locks). Consumers constructing this
     * combination almost certainly have a bug; v1 doesn't reject the
     * combination but documents it as undefined. Future phases may
     * fold-on-evidence to reject at builder time. See M9 §4.3.
     *
     * @return this panel, for chaining
     */
    public Panel modal() {
        return opaque(true).dimsBehind(true).tracksAsModal(true);
    }

    /**
     * Registers an action invoked when the user presses Escape while this
     * panel is a visible {@link #tracksAsModal() modal} panel. Chainable.
     *
     * <p>Without this, Escape over an open modal flows past the modal to the
     * host screen's own Escape handler, which closes the entire screen out
     * from under the dialog. With an escape action set, the host
     * ({@code MKScreen}, or the container-screen key-dispatch path) invokes
     * this action FIRST and eats the key — so Escape dismisses the topmost
     * modal, the universal modal-cancel gesture.
     *
     * <p>The dialog builders wire this automatically:
     * {@link com.trevorschoeny.menukit.core.dialog.ConfirmDialog} registers
     * its {@code onCancel} and
     * {@link com.trevorschoeny.menukit.core.dialog.AlertDialog} registers its
     * {@code onAcknowledge}, so the consumer's existing self-dismiss callback
     * fires on Escape exactly as it does on the button click. Consumers
     * composing modal panels by hand set their own dismiss callback here.
     *
     * <p>Even when no escape action is set, a visible {@code tracksAsModal}
     * panel still causes the host to EAT Escape (so it cannot close the host
     * screen); the action only adds the consumer-driven dismissal on top.
     *
     * @param action the dismiss callback, or {@code null} to clear it
     * @return this panel, for chaining
     */
    public Panel onEscape(@Nullable Runnable action) {
        this.escapeAction = action;
        return this;
    }

    /**
     * Returns the Escape-dismiss action registered via {@link #onEscape(Runnable)},
     * or {@code null} if none. Invoked by the host's modal-Escape path when this
     * panel is the topmost visible modal. See {@link #onEscape(Runnable)}.
     */
    public @Nullable Runnable getEscapeAction() {
        return escapeAction;
    }

    // ── Tooltip (panel-level hover-triggered configuration) ───────────

    /**
     * Attaches a hover-triggered tooltip that fires whenever the cursor is
     * over the panel's outer bounds. Returns this panel for chaining.
     *
     * <p>Useful for "what is this panel for" disclosure on collapsible /
     * configurable panels. Consumers who want tooltips that fire only on
     * specific children should put tooltips on those children — child
     * tooltips are queued during the element-render pass and the
     * panel-level tooltip is queued after, so by vanilla's
     * last-call-wins semantics the panel tooltip takes precedence when
     * both are configured AND the cursor is over a child.
     */
    public Panel tooltip(Component text) {
        return tooltip(() -> text);
    }

    /**
     * Attaches a hover-triggered tooltip with supplier-driven text.
     * Supplier invoked each frame while the panel is hovered. Returns this
     * panel for chaining.
     */
    public Panel tooltip(Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    /** Returns the configured tooltip supplier (nullable), for callers that need to inspect. */
    public @Nullable Supplier<Component> getTooltipSupplier() {
        return tooltipSupplier;
    }

    /**
     * Queues the panel-level tooltip (if configured) when the cursor is
     * over the given outer rect. Called by each panel-rendering site
     * (MKScreen, MKCHandledScreen, ScreenPanelAdapter,
     * SlotGroupPanelAdapter) AFTER element rendering. No-op when the
     * tooltip supplier is unset or the cursor is out of bounds.
     *
     * <p>Skips when {@code hasMouseInput} is {@code false} — HUD contexts
     * use sentinel {@code mouseX = -1} per RenderContext conventions, so
     * the hit test would happen to miss, but the explicit gate makes the
     * intent clear and saves the supplier call.
     */
    public void maybeQueueTooltip(GuiGraphics graphics,
                                  int panelX, int panelY,
                                  int panelWidth, int panelHeight,
                                  int mouseX, int mouseY,
                                  boolean hasMouseInput) {
        if (tooltipSupplier == null || !hasMouseInput) return;
        if (mouseX < panelX || mouseX >= panelX + panelWidth) return;
        if (mouseY < panelY || mouseY >= panelY + panelHeight) return;
        Component text = tooltipSupplier.get();
        if (text == null) return;
        MKTooltip.queue(graphics, text, mouseX, mouseY);
    }

    // ── Owner Reference ─────────────────────────────────────────────────

    /** Sets the owning handler. Called during handler construction. */
    public void setOwner(PanelOwner owner) { this.owner = owner; }

    /** Returns the owning handler, or null if not yet attached. */
    public @Nullable PanelOwner getOwner() { return owner; }
}
