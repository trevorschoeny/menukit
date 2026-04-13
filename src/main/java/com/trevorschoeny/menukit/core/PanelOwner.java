package com.trevorschoeny.menukit.core;

/**
 * Minimal interface for the owner of a {@link Panel} tree. Implemented by
 * {@link com.trevorschoeny.menukit.screen.MenuKitScreenHandler}.
 *
 * <p>Lives in {@code core} so that Panel can hold a typed reference without
 * depending on the {@code screen} package (which pulls in vanilla's
 * {@code AbstractContainerMenu}).
 *
 * <p>Exposes only what Panel actually needs from its owner.
 */
public interface PanelOwner {

    /**
     * Notifies the owner that a panel's visibility changed. The owner is
     * responsible for triggering a sync pass over the affected slot range.
     *
     * @param panel the panel whose visibility was toggled
     */
    void onPanelVisibilityChanged(Panel panel);
}
