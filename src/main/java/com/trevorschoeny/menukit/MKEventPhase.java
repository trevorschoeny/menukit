package com.trevorschoeny.menukit;

/**
 * The phase at which an event listener fires during dispatch.
 *
 * <p>Events are dispatched in three phases:
 * <ol>
 *   <li><b>ALLOW</b> — Can cancel the event entirely. If any ALLOW listener
 *       returns {@link MKEventResult#CONSUMED}, the event stops and BEFORE/AFTER
 *       never fire.</li>
 *   <li><b>BEFORE</b> — Notification only. Fires after all ALLOW listeners have
 *       permitted the event. Cannot cancel. Useful for logging, state preparation.</li>
 *   <li><b>AFTER</b> — Notification only. Fires after the event has been fully
 *       processed. Cannot cancel. Useful for reacting to completed actions.</li>
 * </ol>
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public enum MKEventPhase {

    /**
     * First phase. Listeners can return CONSUMED to cancel the event entirely.
     * If any ALLOW listener cancels, BEFORE and AFTER phases never fire.
     */
    ALLOW,

    /**
     * Second phase. Fires after all ALLOW listeners permit the event.
     * This is the default phase for backward compatibility.
     *
     * <p>For backward compat, returning CONSUMED in BEFORE still stops the
     * BEFORE chain and signals the caller to cancel vanilla behavior.
     * AFTER listeners still fire regardless.
     */
    BEFORE,

    /**
     * Third phase. Notification only — fires after the event is fully processed.
     * Return value is ignored. Useful for post-processing and UI updates.
     */
    AFTER
}
