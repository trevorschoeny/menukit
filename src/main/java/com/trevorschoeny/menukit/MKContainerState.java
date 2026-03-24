package com.trevorschoeny.menukit;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/**
 * External state attached to any {@link Container} via the mixin layer.
 * Stored in {@link MKContainerStateRegistry}, keyed by container identity.
 *
 * <p>This enables features on ALL containers globally — even ones from
 * unknown mods that MenuKit has never seen before.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
public class MKContainerState {

    // ── Tagging ──────────────────────────────────────────────────────────
    // Optional metadata that identifies this container to MenuKit.

    /** Human-readable name (e.g., "mk:hotbar", "equipment"). Null if untagged. */
    private String name;

    /** Persistence type. Null means "use vanilla default behavior." */
    private MKContainerDef.Persistence persistence;

    // ── Read-Only Enforcement ────────────────────────────────────────────

    /** When true, setItem() is blocked (OUTPUT persistence). */
    private boolean readOnly;

    // ── Change Listeners ─────────────────────────────────────────────────
    // Subscribers are notified whenever setItem/removeItem modifies contents.

    private List<Consumer<Container>> changeListeners;

    // ── Getters / Setters ────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public MKContainerDef.Persistence getPersistence() { return persistence; }
    public void setPersistence(MKContainerDef.Persistence persistence) {
        this.persistence = persistence;
        // OUTPUT implies read-only
        this.readOnly = (persistence == MKContainerDef.Persistence.OUTPUT);
    }

    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    // ── Change Listener API ──────────────────────────────────────────────

    /**
     * Subscribes to content changes on this container. The listener receives
     * the Container reference whenever its contents change.
     */
    public void addChangeListener(Consumer<Container> listener) {
        if (changeListeners == null) changeListeners = new ArrayList<>(2);
        changeListeners.add(listener);
    }

    /** Removes a previously registered change listener. */
    public void removeChangeListener(Consumer<Container> listener) {
        if (changeListeners != null) changeListeners.remove(listener);
    }

    /** Fires all change listeners. Called from the Container mixin. */
    public void fireChangeListeners(Container container) {
        if (changeListeners != null) {
            for (Consumer<Container> listener : changeListeners) {
                listener.accept(container);
            }
        }
    }

    /** Whether any change listeners are registered. */
    public boolean hasChangeListeners() {
        return changeListeners != null && !changeListeners.isEmpty();
    }
}
