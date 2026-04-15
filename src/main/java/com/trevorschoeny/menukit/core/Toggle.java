package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A two-state on/off control. The general primitive for boolean setting
 * interactions. Clicking flips the state and fires a callback with the new
 * boolean value.
 *
 * <p>Renders using MenuKit's existing {@link PanelStyle} vocabulary rather
 * than a custom toggle sprite:
 * <ul>
 *   <li><b>Off:</b> {@link PanelStyle#RAISED} background</li>
 *   <li><b>On:</b> {@link PanelStyle#INSET} background (visually "pressed in")</li>
 *   <li><b>Hover (either state):</b> translucent white highlight overlay</li>
 *   <li><b>Disabled:</b> {@link PanelStyle#DARK} background; no hover highlight;
 *   clicks are ignored</li>
 * </ul>
 *
 * <p>Toggle ships no built-in label or icon. For a labeled toggle, compose
 * a {@link TextLabel} alongside or use {@code Checkbox} (settings-ready
 * variant with a built-in label and conventional check-mark visual).
 *
 * <h3>Mutable-state exception to the declared-structure discipline</h3>
 *
 * MenuKit's declared-structure discipline says structure is frozen at
 * construction and visibility is the only mutable dimension. Toggle is one
 * of a narrow set of elements (alongside {@code Checkbox} and {@code Radio})
 * that owns mutable boolean state as a second mutable dimension.
 *
 * <p>The exception is legitimate because state changes do not affect
 * structural shape — flipping a toggle does not add or remove elements,
 * does not alter layout, and does not mutate the panel's element list.
 * The only things that change are the internal boolean and the subsequent
 * render pass. No downstream structural consequence.
 *
 * <p>Scope of the exception: Toggle, Checkbox, Radio. Does not extend to
 * other elements. For state that lives outside the element (config files,
 * block entities, player attachments), use the Phase 9 state-linked variant
 * (see {@code Toggle.linked(...)}) which reads from a consumer-owned
 * {@link BooleanSupplier} instead of owning state internally.
 *
 * @see PanelElement The interface this implements
 * @see Button       Non-toggling interactive primitive
 */
public class Toggle implements PanelElement {

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final Consumer<Boolean> onToggle;
    private final @Nullable BooleanSupplier disabledWhen;

    // Mutable state — the one narrow exception to the declared-structure
    // discipline, documented in the class javadoc above.
    private boolean state;

    // Optional hover-triggered tooltip (post-construction configuration).
    private @Nullable Supplier<Component> tooltipSupplier;

    // Render-frame state — hover updated each render, read by mouseClicked.
    private boolean hovered = false;

    /**
     * Creates an always-enabled Toggle.
     *
     * @param childX       X position within panel content area
     * @param childY       Y position within panel content area
     * @param width        width in pixels
     * @param height       height in pixels
     * @param initialState starting boolean state
     * @param onToggle     fired on each state change with the new state
     */
    public Toggle(int childX, int childY, int width, int height,
                  boolean initialState,
                  Consumer<Boolean> onToggle) {
        this(childX, childY, width, height, initialState, onToggle, null);
    }

    /**
     * Creates a Toggle with a disabled-state predicate. When the predicate
     * returns true, the toggle renders with a dark background and ignores
     * clicks.
     *
     * @param childX       X position within panel content area
     * @param childY       Y position within panel content area
     * @param width        width in pixels
     * @param height       height in pixels
     * @param initialState starting boolean state
     * @param onToggle     fired on each state change with the new state
     * @param disabledWhen returns true when the toggle should be disabled,
     *                     or null for always enabled
     */
    public Toggle(int childX, int childY, int width, int height,
                  boolean initialState,
                  Consumer<Boolean> onToggle,
                  @Nullable BooleanSupplier disabledWhen) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.state = initialState;
        this.onToggle = onToggle;
        this.disabledWhen = disabledWhen;
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    // ── State ──────────────────────────────────────────────────────────

    /** Returns the current toggle state. */
    public boolean isOn() { return currentState(); }

    /**
     * Sets the toggle state programmatically. Fires {@code onToggle} with
     * the new state if it differs from the current state; no-op otherwise.
     * This lets chat commands, keybinds, or other non-click paths flip the
     * toggle while keeping observed callback behavior consistent.
     */
    public void setOn(boolean newState) {
        toggleTo(newState);
    }

    /** Returns whether the toggle is currently disabled. */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Returns whether the mouse is currently over this toggle (updated each frame). */
    public boolean isHovered() { return hovered; }

    // ── State extension points (factored for subclasses) ──────────────

    /**
     * Returns the Toggle's current boolean state.
     *
     * <p><b>Stable extension point for consumer Toggle subclasses.</b>
     * Override to read state from external storage (supplier, block entity,
     * config file, etc.). The default implementation returns the
     * element-owned internal state.
     *
     * <p>Base Toggle's render and click handling call {@code currentState()}
     * exactly once per frame. Subclasses overriding {@code currentState()}
     * may rely on this: their supplier is invoked once per frame for base
     * Toggle's rendering purposes. If the supplier returns different values
     * across rapid successive calls, only the first call per frame affects
     * the rendered output.
     */
    protected boolean currentState() {
        return state;
    }

    /**
     * Commits a state transition. Subclasses define what "commit" means for
     * their state-ownership model:
     *
     * <ul>
     *   <li>Base Toggle (element-owned state): writes the new state to
     *       internal storage and fires the {@code onToggle} callback with
     *       the new state.</li>
     *   <li>{@link #linked(int, int, int, int, java.util.function.BooleanSupplier, Runnable) Toggle.linked}
     *       (consumer-owned state): fires the consumer's callback; consumer
     *       is responsible for updating their own state. No internal storage
     *       commit happens.</li>
     * </ul>
     *
     * <p>Called from the {@code toggleTo} orchestration helper after the
     * short-circuit no-op check passes. Implementations should be atomic —
     * the state transition and the callback notification are conceptually
     * a single event.
     *
     * <p><b>Stable extension point.</b> Signature and semantic contract
     * maintained across MenuKit versions.
     */
    protected void applyState(boolean newState) {
        this.state = newState;
        onToggle.accept(newState);
    }

    /**
     * Orchestration: short-circuit on same-state, then applyState commits
     * and fires the callback atomically. Used by mouseClicked and setOn.
     */
    private void toggleTo(boolean newState) {
        if (currentState() == newState) return;
        applyState(newState);
    }

    // ── Tooltip (optional post-construction configuration) ─────────────

    /**
     * Attaches a hover-triggered tooltip with fixed text. Returns this
     * Toggle for method chaining.
     */
    public Toggle tooltip(Component text) {
        return tooltip(() -> text);
    }

    /**
     * Attaches a hover-triggered tooltip with supplier-driven text.
     * Returns this Toggle for method chaining.
     */
    public Toggle tooltip(Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;

        // Update hover state — false on HUDs (no input dispatch).
        hovered = isHovered(ctx);

        // Read current state exactly once per frame so the render pass is
        // internally consistent even when currentState() is backed by a
        // consumer-supplied BooleanSupplier (e.g., Toggle.linked).
        boolean disabled = isDisabled();
        boolean on = currentState();
        PanelStyle bg = disabled ? PanelStyle.DARK
                      : on       ? PanelStyle.INSET
                                 : PanelStyle.RAISED;

        PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, bg);

        // Hover highlight — same pattern as Button
        if (!disabled && hovered) {
            ctx.graphics().fill(sx + 1, sy + 1, sx + width - 1, sy + height - 1,
                    0x30FFFFFF);
        }

        // Hover-triggered tooltip — deferred to end-of-frame by vanilla.
        if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                ctx.graphics().setTooltipForNextFrame(
                        Minecraft.getInstance().font, ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Click Handling ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isDisabled()) return false;
        if (!hovered) return false;

        toggleTo(!currentState());
        return true;
    }
}
