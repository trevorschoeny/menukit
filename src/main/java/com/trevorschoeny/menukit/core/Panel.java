package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.List;
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
 * panel by id through the owning {@code MenuKitScreenHandler}'s group map.
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
 *   <li><b>Supplier-driven</b> via {@link #showWhen(Supplier)}. Consumer holds
 *       the state; Panel reads via the supplier on each {@code isVisible()}
 *       call. Canonical for Phase 10 injected panels, HUDs, and standalone
 *       screens, following the Phase 8/9 state-ownership pattern
 *       ({@code Toggle.linked}).</li>
 * </ul>
 * The two modes are mutually exclusive — see {@link #showWhen(Supplier)} for
 * precedence semantics.
 */
public class Panel {

    // ── Interior padding (Phase 16g; per-style in Phase 18r) ───────────
    // The consumer-side screen (MenuKitScreen, MenuKitHandledScreen,
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

    // Supplier-driven visibility (Phase 10). When non-null, this takes precedence
    // over the imperative `visible` field — isVisible() reads the supplier, and
    // setVisible(...) silently no-ops. Clear with showWhen(null) to revert to
    // imperative control. Matches the Phase 8/9 state-ownership pattern
    // (Toggle.linked): consumer holds the state; library reads via supplier.
    private @Nullable Supplier<Boolean> visibilitySupplier;

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

    // Set during handler construction — typed via PanelOwner interface
    // so Panel doesn't depend on the screen package.
    private @Nullable PanelOwner owner;

    /**
     * Full constructor with all metadata.
     *
     * @param id        unique identifier within the screen
     * @param elements  panel elements — buttons, text labels, etc. (immutable after construction)
     * @param visible   initial visibility state
     * @param style     visual style for panel background rendering
     * @param position  how this panel is positioned in the layout
     * @param toggleKey GLFW key code that toggles this panel's visibility, or -1 for none
     */
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

    /** Creates a panel with default style (RAISED), position (BODY), no toggle key. */
    public Panel(String id, List<PanelElement> elements, boolean visible) {
        this(id, elements, visible, PanelStyle.RAISED, PanelPosition.BODY, -1);
    }

    /** Creates a visible panel with default style and position. */
    public Panel(String id, List<PanelElement> elements) {
        this(id, elements, true);
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
     * from element extent (or vice versa): {@code MenuKitScreen},
     * {@code MenuKitHandledScreen}, and the default-padding overloads of
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
     * <p><b>Lifecycle note:</b> {@code onAttach} / {@code onDetach}
     * propagation to children stops at the ScrollContainer in auto-scroll
     * mode (ScrollContainer doesn't currently propagate those lifecycle
     * hooks to its children). For wrap-only demos this is fine; for
     * interactive elements requiring widget registration (TextField etc.)
     * inside a scrolling panel, the lack of propagation is a known
     * pre-existing limitation of ScrollContainer, not introduced here.
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
        if (!configurationDirty) return;
        configurationDirty = false;

        // ── Step 1: wrap-width propagation ─────────────────────────────
        if (pinnedWidth >= 0) {
            // pinnedWidth IS the content extent (per M5 contract). The
            // wrap budget equals pinnedWidth directly — no padding
            // subtraction. When pinnedHeight is also set, the scrollbar
            // reserve (track + gutter) reduces the budget further; we
            // deduct it unconditionally because post-wrap overflow can't
            // be known until AFTER wrap is computed (circular dependency).
            // Always-reserving simplifies the math at the cost of a few
            // pixels of unused space on pinnedHeight-set panels that
            // don't actually overflow.
            int wrapBudget = pinnedWidth;
            if (pinnedHeight >= 0) {
                wrapBudget -= (ScrollContainer.TRACK_WIDTH
                        + ScrollContainer.SCROLLER_GUTTER);
            }
            if (wrapBudget < 1) wrapBudget = 1; // never zero/negative

            for (PanelElement e : elements) {
                if (e instanceof TextLabel label) {
                    label.setWrapWidth(wrapBudget);
                }
            }
        } else {
            // pinnedWidth cleared — clear any wrap state from a prior pass.
            for (PanelElement e : elements) {
                if (e instanceof TextLabel label) {
                    label.setWrapWidth(0);
                }
            }
        }

        // ── Step 2: auto-scroll wrap ───────────────────────────────────
        cachedScrollContainer = null;
        if (pinnedHeight >= 0) {
            // Aggregate content height after wrap propagation. Now
            // TextLabels report their wrapped (multi-line) heights, so
            // this measurement reflects the real visual extent.
            int contentHeight = aggregateRawContentHeight();
            int viewportHeight = pinnedHeight;
            if (viewportHeight > 0 && contentHeight > viewportHeight) {
                // Overflow → build a ScrollContainer over the original
                // elements. Outer width comes from pinnedWidth if set
                // (Panel's content area), otherwise the aggregate child
                // width plus scrollbar reserve so children get their
                // natural width and the scrollbar has room.
                int outerWidth;
                if (pinnedWidth >= 0) {
                    outerWidth = pinnedWidth;
                } else {
                    outerWidth = aggregateRawContentWidth()
                            + ScrollContainer.TRACK_WIDTH
                            + ScrollContainer.SCROLLER_GUTTER;
                }
                // ScrollContainer.Builder.size() rejects widths <= track +
                // gutter + 1 (= 17). Skip scroll silently if budget too
                // tight — content overflows but no crash.
                int minScrollWidth = ScrollContainer.TRACK_WIDTH
                        + ScrollContainer.SCROLLER_GUTTER + 1;
                if (outerWidth > minScrollWidth) {
                    cachedScrollContainer = ScrollContainer.builder()
                            .at(0, 0)
                            .size(outerWidth, viewportHeight)
                            .content(elements)
                            .scrollOffset(() -> scrollOffset, v -> scrollOffset = v)
                            .build();
                }
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
     * auto-sized" — e.g., MKC's {@code MenuKitHandledScreen.computePanelSize}
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
     * <p>If a visibility supplier is set (via {@link #showWhen(Supplier)}),
     * returns the supplier's current value. Otherwise returns the imperative
     * {@code visible} field, which is controlled via {@link #setVisible(boolean)}.
     */
    public boolean isVisible() {
        if (visibilitySupplier != null) {
            return visibilitySupplier.get();
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
     * {@link #showWhen(Supplier)} has been called with a non-null supplier,
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
    public Panel showWhen(@Nullable Supplier<Boolean> supplier) {
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
     * <p><b>Dispatcher coverage:</b> region-based {@code ScreenPanelAdapter}
     * panels participate automatically via the unified registry. Lambda-path
     * adapters must call {@code .activeOn(Screen, boundsSupplier)} from
     * their consumer mixin's {@code init()} to register their bounds for
     * opacity dispatch. See M9 §4.4 + {@code ScreenPanelAdapter.activeOn}.
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
     * (MenuKitScreen, MenuKitHandledScreen, ScreenPanelAdapter,
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
        graphics.setTooltipForNextFrame(
                Minecraft.getInstance().font, text, mouseX, mouseY);
    }

    // ── Owner Reference ─────────────────────────────────────────────────

    /** Sets the owning handler. Called during handler construction. */
    public void setOwner(PanelOwner owner) { this.owner = owner; }

    /** Returns the owning handler, or null if not yet attached. */
    public @Nullable PanelOwner getOwner() { return owner; }
}
