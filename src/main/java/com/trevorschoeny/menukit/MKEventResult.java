package com.trevorschoeny.menukit;

/**
 * Return value from an event handler, controlling dispatch flow and vanilla behavior.
 *
 * <p>When the {@link MKEventBus} fires an event, each matching handler returns one
 * of these values. The bus uses the result to decide whether to continue dispatching
 * to lower-priority handlers or stop immediately.
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public enum MKEventResult {

    /**
     * Cancel vanilla behavior and stop the handler chain.
     * No further handlers will be called for this event.
     * The vanilla click/interaction that triggered the event is suppressed.
     */
    CONSUMED,

    /**
     * Continue to the next handler in the chain.
     * If all handlers return PASS, vanilla behavior proceeds normally.
     */
    PASS
}
