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

    // Supplier-driven visibility (Phase 10). When non-null, this takes precedence
    // over the imperative `visible` field — isVisible() reads the supplier, and
    // setVisible(...) silently no-ops. Clear with showWhen(null) to revert to
    // imperative control. Matches the Phase 8/9 state-ownership pattern
    // (Toggle.linked): consumer holds the state; library reads via supplier.
    private @Nullable Supplier<Boolean> visibilitySupplier;

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

    // ── Owner Reference ─────────────────────────────────────────────────

    /** Sets the owning handler. Called during handler construction. */
    public void setOwner(PanelOwner owner) { this.owner = owner; }

    /** Returns the owning handler, or null if not yet attached. */
    public @Nullable PanelOwner getOwner() { return owner; }
}
