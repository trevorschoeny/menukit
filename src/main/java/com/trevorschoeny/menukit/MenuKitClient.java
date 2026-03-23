package com.trevorschoeny.menukit;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side entry point for MenuKit.
 * Registers the MKScreen factory for standalone panel screens.
 */
public class MenuKitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuKit.initClient();
    }
}
