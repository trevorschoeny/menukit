package com.trevorschoeny.menukit.core;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The fundamental unit of composition. Every element in MenuKit lives
 * inside a Panel, because that's the scope at which visibility toggles —
 * and visibility is the load-bearing concept for the whole dynamic-menu story.
 *
 * <p>A Panel holds an ordered list of {@link SlotGroup}s and a visibility
 * flag. Visibility is mutable (the one mutable thing); the group list
 * is fixed after construction.
 *
 * <p>Part of the canonical MenuKit hierarchy:
 * Screen → Panel → SlotGroup → MenuKitSlot
 */
public class Panel {

    private final String id;
    private final List<SlotGroup> groups;
    private final PanelStyle style;
    private final PanelPosition position;
    private final int toggleKey; // GLFW key code that toggles visibility, or -1 for none
    private boolean visible;

    // Set during handler construction — typed via PanelOwner interface
    // so Panel doesn't depend on the screen package.
    private @Nullable PanelOwner owner;

    /**
     * Full constructor with all metadata.
     *
     * @param id        unique identifier within the screen
     * @param groups    ordered list of slot groups (immutable after construction)
     * @param visible   initial visibility state
     * @param style     visual style for panel background rendering
     * @param position  how this panel is positioned in the layout
     * @param toggleKey GLFW key code that toggles this panel's visibility, or -1 for none
     */
    public Panel(String id, List<SlotGroup> groups, boolean visible,
                 PanelStyle style, PanelPosition position, int toggleKey) {
        this.id = id;
        this.groups = List.copyOf(groups);
        this.visible = visible;
        this.style = style;
        this.position = position;
        this.toggleKey = toggleKey;

        // Wire up group → panel references
        for (SlotGroup group : this.groups) {
            group.setPanel(this);
        }
    }

    /** Creates a panel with default style (RAISED), position (BODY), no toggle key. */
    public Panel(String id, List<SlotGroup> groups, boolean visible) {
        this(id, groups, visible, PanelStyle.RAISED, PanelPosition.BODY, -1);
    }

    /** Creates a visible panel with default style and position. */
    public Panel(String id, List<SlotGroup> groups) {
        this(id, groups, true);
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

    // ── Groups ──────────────────────────────────────────────────────────

    /** Returns the ordered list of slot groups (immutable). */
    public List<SlotGroup> getGroups() { return groups; }

    // ── Visibility ──────────────────────────────────────────────────────

    /** Returns whether this panel is currently visible. */
    public boolean isVisible() { return visible; }

    /**
     * Sets this panel's visibility and notifies the owner to trigger
     * a sync pass over the affected slots.
     *
     * <p>When hidden, all slots become inert (getItem returns EMPTY,
     * canInsert returns false, quick-move skips them). When visible
     * again, slots resume normal behavior and the sync pass pushes
     * real stacks to the client.
     */
    public void setVisible(boolean visible) {
        if (this.visible == visible) return; // no-op if unchanged
        this.visible = visible;
        if (owner != null) {
            owner.onPanelVisibilityChanged(this);
        }
    }

    // ── Owner Reference ─────────────────────────────────────────────────

    /** Sets the owning handler. Called during handler construction. */
    public void setOwner(PanelOwner owner) { this.owner = owner; }

    /** Returns the owning handler, or null if not yet attached. */
    public @Nullable PanelOwner getOwner() { return owner; }
}
