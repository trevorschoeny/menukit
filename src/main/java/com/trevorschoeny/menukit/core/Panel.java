package com.trevorschoeny.menukit.core;

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

    private final String id;
    private final List<PanelElement> elements;
    private final PanelStyle style;
    private final PanelPosition position;
    private final int toggleKey; // GLFW key code that toggles visibility, or -1 for none
    private boolean visible;

    // ── Size pinning (M5 region stacking) ──────────────────────────────
    // When pinnedWidth >= 0, getWidth() returns pinnedWidth regardless of
    // element visibility. Same for pinnedHeight. Opt-in escape hatch for
    // panels whose dynamic element visibility would otherwise cause
    // getWidth/getHeight to collapse to zero, which would jitter the
    // stacking of subsequent panels in a region.
    private int pinnedWidth = -1;
    private int pinnedHeight = -1;

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

    /** Returns how this panel is positioned in the layout. */
    public PanelPosition getPosition() { return position; }

    /** Returns the GLFW key code that toggles this panel's visibility, or -1 for none. */
    public int getToggleKey() { return toggleKey; }

    // ── Elements ────────────────────────────────────────────────────────

    /** Returns the panel's elements — buttons, text labels, etc. (immutable). */
    public List<PanelElement> getElements() { return elements; }

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
        int extent = 0;
        for (PanelElement e : elements) {
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
        int extent = 0;
        for (PanelElement e : elements) {
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

    // ── Owner Reference ─────────────────────────────────────────────────

    /** Sets the owning handler. Called during handler construction. */
    public void setOwner(PanelOwner owner) { this.owner = owner; }

    /** Returns the owning handler, or null if not yet attached. */
    public @Nullable PanelOwner getOwner() { return owner; }
}
