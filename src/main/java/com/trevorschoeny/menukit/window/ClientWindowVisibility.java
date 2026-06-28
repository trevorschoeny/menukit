package com.trevorschoeny.menukit.window;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;

/**
 * The one client-side gate that folds the engine {@code VISIBILITY} behavior into
 * the panel dispatch — the bridge between "set visibility through the window" and
 * "the renderer/input loop actually honors it."
 *
 * <h2>Why this and not {@code Panel.isVisible()}</h2>
 *
 * {@code Panel.isVisible()} (the panel's own field / {@code showWhen} supplier) is
 * called on BOTH sides — a created slot's server-side {@code MKCSlot.isInert} reads
 * it for sync. Engine VISIBILITY is CLIENT-tier and MUST be resolved on the client
 * only (the engine store is shared client+server in single-player, so resolving it
 * server-side would let a client hide stop server sync). So the engine resolution
 * lives here, called ONLY from the client render/input dispatch — never from
 * {@code Panel.isVisible()} or any server path.
 *
 * <h2>Additive</h2>
 *
 * Each method ANDs the existing visibility with the resolved {@link VisibilityRule}.
 * With no VISIBILITY declared the rule resolves to {@link VisibilityRule#VISIBLE},
 * so {@code panelShown == panel.isVisible()} — zero behavior change until a consumer
 * sets it. Panel-level VISIBILITY cascades to child elements via the engine's
 * owner-chain walk, so {@link #elementShown} also reflects a hidden parent panel.
 */
public final class ClientWindowVisibility {

    private ClientWindowVisibility() {}

    /** Client-side: should this panel display/interact this frame (own visibility AND engine VISIBILITY)? */
    public static boolean panelShown(Panel panel) {
        if (!panel.isVisible()) return false;
        return WindowEngine.resolve(PanelAddressing.of(panel), BehaviorKeys.VISIBILITY).visible();
    }

    /** Client-side: should this element of {@code panel} display/interact (own visibility AND engine VISIBILITY)? */
    public static boolean elementShown(Panel panel, PanelElement element) {
        if (!element.isVisible()) return false;
        return WindowEngine.resolve(PanelAddressing.of(panel, element), BehaviorKeys.VISIBILITY).visible();
    }
}
