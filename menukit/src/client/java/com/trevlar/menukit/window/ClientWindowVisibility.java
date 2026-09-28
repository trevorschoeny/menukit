package com.trevlar.menukit.window;

import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelElement;

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
        return WindowEngine.resolve(addressOf(panel), BehaviorKeys.VISIBILITY).visible();
    }

    /** Client-side: should this element of {@code panel} display/interact (own visibility AND engine VISIBILITY)? */
    public static boolean elementShown(Panel panel, PanelElement element) {
        if (!element.isVisible()) return false;
        return WindowEngine.resolve(addressOf(panel, element), BehaviorKeys.VISIBILITY).visible();
    }

    /**
     * Client-side: is this panel interaction-opaque (eats clicks over its bounds)?
     * The panel's own {@code isOpaque()} AND the engine {@code OPACITY} key. Additive
     * (OPACITY defaults TRUE, so this equals {@code isOpaque()} until set through the
     * window). Resolved by panel address, client-side.
     */
    public static boolean panelOpaque(Panel panel) {
        if (!panel.isOpaque()) return false;
        return WindowEngine.resolve(addressOf(panel), BehaviorKeys.OPACITY).asBoolean();
    }

    // A live panel's and element's addresses (moved from PanelAddressing, which is
    // common since 6.0.0 and cannot name the client Panel type).

    /** The {@link Address} of {@code panel} itself (its own visibility/opacity/inertness). */
    private static Address addressOf(Panel panel) {
        return PanelAddressing.ofPanel(panel.getId());
    }

    /** The {@link Address} of {@code element} within {@code panel}. */
    private static Address addressOf(Panel panel, PanelElement element) {
        return PanelAddressing.ofElement(panel.getId(), elementDeclId(panel, element));
    }

    /**
     * An element's durable declaration id — its explicit {@code elementDeclId} when
     * given (Phase 0), else its registration-order position in the panel (stable
     * across reopen as long as the element list order is). Never a runtime counter.
     */
    private static String elementDeclId(Panel panel, PanelElement element) {
        String declId = element.getElementDeclId();
        if (declId != null) return declId;
        return "idx:" + panel.getRawElements().indexOf(element);
    }
}
