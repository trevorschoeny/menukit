package com.trevorschoeny.menukit;

/**
 * Why a panel was hidden. Carried by {@link MKUIEvent} for
 * {@link MKEvent.Type#PANEL_HIDE} events.
 *
 * <p>Listeners can use the reason to decide what cleanup is needed.
 * For example, a panel hiding due to {@link #LAYOUT} will reappear
 * momentarily, so cleanup should be skipped.
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public enum MKDismountReason {

    /** The entire screen session ended (screen was closed). */
    SCREEN_CLOSED,

    /** The panel's visibility predicate returned false. */
    CONDITION_CHANGED,

    /** {@code MenuKit.togglePanelVisibility()} was called explicitly. */
    MANUAL,

    /** Panel is being rebuilt during layout recalculation.
     *  It will reappear momentarily — skip heavy cleanup. */
    LAYOUT
}
