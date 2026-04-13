package com.trevorschoeny.menukit.core;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
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
    private boolean visible;

    // Set during handler construction — the panel knows its parent
    private @Nullable Object handler; // Phase 3: typed as MenuKitScreenHandler

    /**
     * @param id      unique identifier within the screen
     * @param groups  ordered list of slot groups (immutable after construction)
     * @param visible initial visibility state
     */
    public Panel(String id, List<SlotGroup> groups, boolean visible) {
        this.id = id;
        this.groups = List.copyOf(groups);
        this.visible = visible;

        // Wire up group → panel references
        for (SlotGroup group : this.groups) {
            group.setPanel(this);
        }
    }

    /** Creates a visible panel. */
    public Panel(String id, List<SlotGroup> groups) {
        this(id, groups, true);
    }

    // ── Identity ────────────────────────────────────────────────────────

    /** Returns this panel's unique identifier within the screen. */
    public String getId() { return id; }

    // ── Groups ──────────────────────────────────────────────────────────

    /** Returns the ordered list of slot groups (immutable). */
    public List<SlotGroup> getGroups() { return groups; }

    // ── Visibility ──────────────────────────────────────────────────────

    /** Returns whether this panel is currently visible. */
    public boolean isVisible() { return visible; }

    /**
     * Sets this panel's visibility. When a panel becomes hidden, all its
     * slots become inert (getItem returns EMPTY, canInsert returns false,
     * quick-move skips them). When it becomes visible again, slots resume
     * normal behavior and a sync pass pushes real stacks to the client.
     *
     * <p>The handler is responsible for triggering the sync pass — this
     * method just flips the flag. Call
     * {@code MenuKitScreenHandler.setPanelVisible()} instead of calling
     * this directly.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    // ── Handler Reference ───────────────────────────────────────────────

    /** Sets the owning handler. Called during handler construction. */
    void setHandler(Object handler) { this.handler = handler; }

    /** Returns the owning handler, or null if not yet attached. */
    public @Nullable Object getHandler() { return handler; }
}
