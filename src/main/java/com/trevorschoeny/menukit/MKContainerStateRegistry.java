package com.trevorschoeny.menukit;

import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;

/**
 * Static registry mapping {@link Container} instances to their
 * {@link MKContainerState}. Uses identity-based lookup (not equals/hashCode)
 * so each Container object has its own independent state.
 *
 * <p>State is created lazily on first access. Cleanup should be called
 * when a Container is no longer needed (e.g., menu removal) to prevent
 * memory leaks.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
public class MKContainerStateRegistry {

    private static final IdentityHashMap<Container, MKContainerState> states = new IdentityHashMap<>();

    /** Gets or creates state for a container. */
    public static MKContainerState getOrCreate(Container container) {
        return states.computeIfAbsent(container, k -> new MKContainerState());
    }

    /** Gets state for a container, or null if none exists. */
    public static @Nullable MKContainerState get(Container container) {
        return states.get(container);
    }

    /** Removes state for a container. */
    public static void remove(Container container) {
        states.remove(container);
    }

    /** Whether any state exists for this container. */
    public static boolean has(Container container) {
        return states.containsKey(container);
    }
}
