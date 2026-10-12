package com.trevlar.menukit;

import com.trevlar.menukit.api.Declarations;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MenuKit's common initializer, run on both sides. MenuKit is universal because the
 * Kits built on it (SlotKit, ConfigKit) run server code that uses its placement types
 * and its declarations freeze; the client half is {@link MenuKitClient}.
 */
public final class MenuKit implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    @Override
    public void onInitialize() {
        // Declarations freeze at the first server start, after every entrypoint (on a
        // client, MenuKitClient freezes at client start, which comes first).
        ServerLifecycleEvents.SERVER_STARTING.register(server -> Declarations.freeze("server starting"));
        LOGGER.info("[MenuKit] Initialized");
    }
}
