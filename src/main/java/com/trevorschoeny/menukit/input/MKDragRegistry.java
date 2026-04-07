package com.trevorschoeny.menukit.input;

import com.trevorschoeny.menukit.MenuKit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of custom drag modes. Consulted by the drag mixin to determine
 * if a click-drag should activate a custom behavior instead of (or in
 * addition to) vanilla drag logic.
 *
 * <p>Part of the <b>MenuKit</b> drag mode API.
 */
public final class MKDragRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit");
    private static final List<MKDragMode> MODES = new ArrayList<>();

    private MKDragRegistry() {}

    /** Registers a drag mode. Called by MKDragMode.Builder.register(). */
    static void register(MKDragMode mode) {
        // Check for duplicate IDs — replace the old one if found
        for (MKDragMode existing : MODES) {
            if (existing.id().equals(mode.id())) {
                LOGGER.warn("[MenuKit] Drag mode '{}' is already registered — replacing", mode.id());
                MODES.remove(existing);
                break;
            }
        }
        MODES.add(mode);
        LOGGER.info("[MenuKit] Registered drag mode '{}'", mode.id());
    }

    /**
     * Finds the first drag mode whose activation test passes for the given context.
     * Returns null if no custom drag mode should activate.
     */
    public static @Nullable MKDragMode findActive(MKDragContext context) {
        for (MKDragMode mode : MODES) {
            if (mode.activationTest().test(context)) {
                return mode;
            }
        }
        return null;
    }

    /** Clears all registrations (for testing/reload). */
    public static void clear() {
        MODES.clear();
    }
}
