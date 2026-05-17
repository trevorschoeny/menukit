package com.trevorschoeny.menukit.core;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Abstract base for library-provided {@link PanelElement} implementations.
 * Holds the cross-cutting state every concrete element needs (visibility —
 * imperative + supplier-driven) so individual elements don't each re-implement
 * the same pattern.
 *
 * <h2>Why an abstract base, not just defaults on the interface</h2>
 *
 * Java interfaces can't hold instance state (no fields), so a supplier-driven
 * visibility flag has to live somewhere concrete. Two options were considered:
 * (a) add the field + chainable {@code showWhen()} to every element class
 * (Button, Toggle, Checkbox, etc. — ~14 classes, ~5 lines each), or
 * (b) introduce this base and let library elements extend it. We took (b)
 * because every library element implements {@link PanelElement} directly
 * (no other supers to clash with) and the duplication-vs-base trade tips
 * toward base when new cross-cutting chainables ({@code .disabled()} etc.)
 * become free to add later in one place.
 *
 * <h2>Consumer custom elements</h2>
 *
 * The {@link PanelElement} interface stays as the consumer-facing extension
 * point — custom elements can {@code implement PanelElement} directly and
 * provide their own {@link PanelElement#isVisible} override if they need
 * conditional visibility. They lose the chainable {@code .showWhen()} sugar
 * but the dispatch sites only consult {@code isVisible()} so functionality
 * is identical.
 *
 * <h2>Visibility precedence</h2>
 *
 * Mirrors {@link Panel#showWhen}: when {@link #showWhen} is called with a
 * non-null supplier, the supplier drives {@link #isVisible}. {@link #setVisible}
 * silently no-ops while a supplier is active (consumer holds the state, library
 * reads via supplier). Clearing with {@code showWhen(null)} reverts to
 * imperative control and resets {@code visible = true}.
 */
public abstract class AbstractPanelElement implements PanelElement {

    /**
     * Imperative visibility state. Defaults true (matches
     * {@link PanelElement#isVisible}'s default). Read by {@link #isVisible}
     * only when {@link #visibilitySupplier} is null.
     */
    private boolean visible = true;

    /**
     * Supplier-driven visibility, mirroring {@link Panel#showWhen}. When
     * non-null, takes precedence over {@link #visible}. Cleared by
     * {@code showWhen(null)}.
     */
    private @Nullable Supplier<Boolean> visibilitySupplier;

    /**
     * Returns whether this element is currently visible.
     *
     * <p>If a visibility supplier is set (via {@link #showWhen(Supplier)}),
     * returns the supplier's current value. Otherwise returns the imperative
     * {@code visible} field controlled via {@link #setVisible(boolean)}.
     */
    @Override
    public boolean isVisible() {
        if (visibilitySupplier != null) {
            return visibilitySupplier.get();
        }
        return visible;
    }

    /**
     * Sets this element's imperative visibility.
     *
     * <p>Silently no-ops when a {@link #showWhen} supplier is active — the
     * supplier owns visibility in that mode. Call {@code showWhen(null)}
     * first to revert to imperative control.
     */
    public void setVisible(boolean visible) {
        if (visibilitySupplier != null) return; // silent no-op when supplier active
        this.visible = visible;
    }

    /**
     * Installs a supplier that drives this element's visibility. The supplier
     * is consulted on every {@link #isVisible} call (typically every frame
     * from {@code render} + every input dispatch), so keep it cheap.
     *
     * <p>Mirrors {@link Panel#showWhen}. The consumer-side mental model is
     * "consumer holds the boolean source-of-truth; library reads via
     * supplier"; same shape as {@link Toggle#linked}.
     *
     * <p>Use case: 3 buttons in one panel, 2 conditionally visible. Without
     * this you'd need two panels and a {@code STACK_GAP} between them.
     *
     * <p>Pass {@code null} to clear the supplier and revert to imperative
     * control. The prior {@link #setVisible} state is NOT restored — the
     * element resets to default-visible (matching {@link Panel#showWhen}).
     *
     * @return this element, for method chaining.
     */
    public AbstractPanelElement showWhen(@Nullable Supplier<Boolean> supplier) {
        this.visibilitySupplier = supplier;
        if (supplier == null) {
            // Reset to default-visible per the Panel.showWhen-locked semantics.
            this.visible = true;
        }
        return this;
    }

    // ── Tooltip (hover-triggered) ──────────────────────────────────────
    //
    // Hoisted in Phase 18r-2 from per-widget duplication (Button, Toggle,
    // Checkbox, Slider, Radio, Divider, Dropdown, Icon, ItemDisplay,
    // ProgressBar, TextLabel, TextField each held an identical
    // tooltipSupplier field + two chainable setters). The cross-cutting
    // FIELD + chainable lives here; per-widget render code still owns
    // the trigger logic (when to actually queue the tooltip) since trigger
    // varies — Button suppresses on press, Dropdown gates on trigger-vs-
    // popover, etc. Per-widget render reads via {@link #getTooltipSupplier()}.

    /**
     * Optional hover-triggered tooltip text supplier. Set via
     * {@link #tooltip(Component)} or {@link #tooltip(Supplier)} during the
     * element construction chain. Read by per-widget render code via
     * {@link #getTooltipSupplier()} to decide whether to queue a tooltip
     * for the current frame.
     */
    private @Nullable Supplier<Component> tooltipSupplier;

    /**
     * Attaches a hover-triggered tooltip with fixed text. Returns this
     * element for method chaining. Tooltip renders at the mouse position
     * using vanilla's tooltip styling.
     *
     * <p>Post-construction configuration setter — intended to be called
     * once during the construction chain. See
     * {@code Design Docs/Element Design Docs/TOOLTIP_DESIGN_DOC.md}.
     */
    public AbstractPanelElement tooltip(Component text) {
        return tooltip(() -> text);
    }

    /**
     * Attaches a hover-triggered tooltip with supplier-driven text. The
     * supplier is invoked each frame while hovered. Returns this element
     * for method chaining.
     */
    public AbstractPanelElement tooltip(@Nullable Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    /**
     * Returns the currently-attached tooltip supplier, or {@code null} if
     * none. Concrete widgets read this from their {@code render()} to
     * decide whether to call {@code setTooltipForNextFrame} for the
     * current frame (gated on their own hover + suppression logic).
     */
    protected @Nullable Supplier<Component> getTooltipSupplier() {
        return tooltipSupplier;
    }
}
