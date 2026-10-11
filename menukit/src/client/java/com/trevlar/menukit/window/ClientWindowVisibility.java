package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.VisibilityRule;
import com.trevlar.menukit.api.window.WindowEngine;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;

/**
 * The one client-side place where the window engine's {@code VISIBILITY} behaviour
 * is applied to panel rendering and input, so visibility set through the window
 * actually takes effect.
 *
 * <h2>Why not in {@code Panel.isVisible()}</h2>
 *
 * {@code Panel.isVisible()} (the panel's own field or {@code visibleWhen} supplier)
 * runs on both sides: a created slot's server-side {@code CreatedSlot.isInert} reads
 * it for sync. Engine VISIBILITY is client-tier and must only be resolved on the
 * client. In single-player the engine store is shared by client and server, so
 * resolving it on the server would let a client-side hide stop server sync. So it is
 * resolved here, called only from client rendering and input, never from
 * {@code Panel.isVisible()} or any server path.
 *
 * <h2>Additive</h2>
 *
 * Each method ANDs the existing visibility with the resolved {@link VisibilityRule}.
 * With no VISIBILITY declared the rule is {@link VisibilityRule#VISIBLE}, so
 * {@code panelShown == panel.isVisible()} and nothing changes until a consumer sets
 * it. Panel-level VISIBILITY cascades to child elements through the engine's owner
 * chain, so {@link #elementShown} also reflects a hidden parent panel.
 */
public final class ClientWindowVisibility {

    private ClientWindowVisibility() {}

    /** Whether the panel shows and takes input this frame: its own visibility and engine VISIBILITY. */
    public static boolean panelShown(Panel panel) {
        if (!panel.isVisible()) return false;
        return WindowEngine.resolve(addressOf(panel), BehaviorKeys.VISIBILITY).visible();
    }

    /** Whether the element shows and takes input: its own visibility and engine VISIBILITY. */
    public static boolean elementShown(Panel panel, PanelElement element) {
        if (!element.isVisible()) return false;
        return WindowEngine.resolve(addressOf(panel, element), BehaviorKeys.VISIBILITY).visible();
    }

    /**
     * Whether the panel eats clicks over its bounds: its own {@code isOpaque()} and
     * the engine {@code OPACITY} key. OPACITY defaults to true, so this equals
     * {@code isOpaque()} until set through the window.
     */
    public static boolean panelOpaque(Panel panel) {
        if (!panel.isOpaque()) return false;
        return WindowEngine.resolve(addressOf(panel), BehaviorKeys.OPACITY).asBoolean();
    }

    // A live panel's and element's addresses (moved from PanelAddressing, which is
    // common since 6.0.0 and cannot name the client Panel type).

    private static Address addressOf(Panel panel) {
        return PanelAddressing.ofPanel(panel.id());
    }

    private static Address addressOf(Panel panel, PanelElement element) {
        return PanelAddressing.ofElement(panel.id(), elementDeclId(panel, element));
    }

    // An element's durable id: its explicit declId when given, else its position in
    // the panel's element list (stable across reopen while the order is). Never a
    // runtime counter.
    private static String elementDeclId(Panel panel, PanelElement element) {
        String declId = element.declId();
        if (declId != null) return declId;
        return "idx:" + panel.getRawElements().indexOf(element);
    }
}
