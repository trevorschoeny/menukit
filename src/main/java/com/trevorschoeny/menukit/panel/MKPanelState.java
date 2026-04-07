package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-panel visibility state for individual elements (slots, buttons, text,
 * groups) identified by their element IDs.
 *
 * <p>Overrides take priority over the element's own {@code disabledWhen}
 * predicate. A {@code null} override means "use the def's disabledWhen."
 * A {@code true} override means "force visible." A {@code false} override
 * means "force hidden."
 *
 * <p>Part of the <b>MenuKit</b> conditional visibility system.
 */
public class MKPanelState {

    private final Map<String, Boolean> visibilityOverrides = new HashMap<>();

    // Scroll offset tracking: elementId -> [scrollX, scrollY]
    private final Map<String, float[]> scrollOffsets = new HashMap<>();

    // Active tab index tracking: elementId -> activeTabIndex
    private final Map<String, Integer> activeTabIndices = new HashMap<>();

    /**
     * Returns the visibility override for an element, or null if no override
     * exists (fall through to the def's disabledWhen predicate).
     */
    public @Nullable Boolean getVisible(String elementId) {
        return visibilityOverrides.get(elementId);
    }

    /** Sets a visibility override for an element. */
    public void setVisible(String elementId, boolean visible) {
        visibilityOverrides.put(elementId, visible);
    }

    /** Removes the visibility override for an element, reverting to disabledWhen. */
    public void clearOverride(String elementId) {
        visibilityOverrides.remove(elementId);
    }

    /** Clears all visibility overrides for this panel. */
    public void clearAll() {
        visibilityOverrides.clear();
    }

    // ── Scroll Offset Tracking ───────────────────────────────────────────────

    /**
     * Returns the scroll offset for a scroll container element, or [0, 0]
     * if no offset has been recorded yet.
     *
     * @param elementId the scroll container's ID
     * @return a float array [scrollX, scrollY] in pixels
     */
    public float[] getScrollOffset(String elementId) {
        return scrollOffsets.getOrDefault(elementId, new float[]{0f, 0f});
    }

    /**
     * Sets the scroll offset for a scroll container element.
     *
     * @param elementId the scroll container's ID
     * @param scrollX   horizontal scroll offset in pixels
     * @param scrollY   vertical scroll offset in pixels
     */
    public void setScrollOffset(String elementId, float scrollX, float scrollY) {
        scrollOffsets.put(elementId, new float[]{scrollX, scrollY});
    }

    // ── Active Tab Tracking ─────────────────────────────────────────────────

    /**
     * Returns the active tab index for a tabs element, or 0 if none has been set.
     *
     * @param elementId the tabs element's ID
     * @return the active tab index (0-based)
     */
    public int getActiveTab(String elementId) {
        return activeTabIndices.getOrDefault(elementId, 0);
    }

    /**
     * Sets the active tab index for a tabs element.
     *
     * @param elementId the tabs element's ID
     * @param index     the active tab index (0-based)
     */
    public void setActiveTab(String elementId, int index) {
        activeTabIndices.put(elementId, index);
    }
}
