package com.trevorschoeny.menukit;

import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * UI-level event for button clicks, panel visibility changes, and element
 * visibility overrides. Dispatched through the {@link MKEventBus} alongside
 * slot events.
 *
 * <p>Constructed via static factory methods -- the constructor is private to
 * enforce correct field combinations for each event type.
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public final class MKUIEvent implements MKEvent {

    private final MKEvent.Type type;
    private final @Nullable String panelName;
    private final @Nullable String elementId;
    private final @Nullable MKButton button;
    private final boolean toggleState;
    private final @Nullable MKContext context;
    private final Player player;

    // Tab-specific fields -- only populated for TAB_CHANGED events
    private final int previousTabIndex;
    private final int newTabIndex;

    // ── Private Constructor ──────────────────────────────────────────────────

    private MKUIEvent(MKEvent.Type type,
                      @Nullable String panelName,
                      @Nullable String elementId,
                      @Nullable MKButton button,
                      boolean toggleState,
                      @Nullable MKContext context,
                      Player player,
                      int previousTabIndex,
                      int newTabIndex) {
        this.type = type;
        this.panelName = panelName;
        this.elementId = elementId;
        this.button = button;
        this.toggleState = toggleState;
        this.context = context;
        this.player = player;
        this.previousTabIndex = previousTabIndex;
        this.newTabIndex = newTabIndex;
    }

    /** Backwards-compatible constructor for non-tab events. */
    private MKUIEvent(MKEvent.Type type,
                      @Nullable String panelName,
                      @Nullable String elementId,
                      @Nullable MKButton button,
                      boolean toggleState,
                      @Nullable MKContext context,
                      Player player) {
        this(type, panelName, elementId, button, toggleState, context, player, -1, -1);
    }

    // ── Static Factories ────────────────────────────────────────────────────

    /** Creates a BUTTON_CLICK event. */
    public static MKUIEvent buttonClick(MKButton button,
                                         @Nullable String panelName,
                                         @Nullable MKContext context,
                                         Player player) {
        return new MKUIEvent(Type.BUTTON_CLICK, panelName, null, button, false, context, player);
    }

    /** Creates a BUTTON_TOGGLE event. */
    public static MKUIEvent buttonToggle(MKButton button,
                                          boolean pressed,
                                          @Nullable String panelName,
                                          @Nullable MKContext context,
                                          Player player) {
        return new MKUIEvent(Type.BUTTON_TOGGLE, panelName, null, button, pressed, context, player);
    }

    /** Creates a PANEL_SHOW event. */
    public static MKUIEvent panelShow(String panelName,
                                       @Nullable MKContext context,
                                       Player player) {
        return new MKUIEvent(Type.PANEL_SHOW, panelName, null, null, false, context, player);
    }

    /** Creates a PANEL_HIDE event. */
    public static MKUIEvent panelHide(String panelName,
                                       @Nullable MKContext context,
                                       Player player) {
        return new MKUIEvent(Type.PANEL_HIDE, panelName, null, null, false, context, player);
    }

    /** Creates an ELEMENT_SHOW event. */
    public static MKUIEvent elementShow(String panelName,
                                         String elementId,
                                         @Nullable MKContext context,
                                         Player player) {
        return new MKUIEvent(Type.ELEMENT_SHOW, panelName, elementId, null, false, context, player);
    }

    /** Creates an ELEMENT_HIDE event. */
    public static MKUIEvent elementHide(String panelName,
                                         String elementId,
                                         @Nullable MKContext context,
                                         Player player) {
        return new MKUIEvent(Type.ELEMENT_HIDE, panelName, elementId, null, false, context, player);
    }

    // ── MKEvent Interface ───────────────────────────────────────────────────

    @Override
    public MKEvent.Type getType() { return type; }

    @Override
    public @Nullable MKContext getContext() { return context; }

    @Override
    public Player getPlayer() { return player; }

    // ── UI-Specific Getters ─────────────────────────────────────────────────

    /** The panel this event relates to, or null for non-panel events. */
    public @Nullable String getPanelName() { return panelName; }

    /** The element ID for ELEMENT_SHOW/ELEMENT_HIDE events, or null. */
    public @Nullable String getElementId() { return elementId; }

    /** The button for BUTTON_CLICK/BUTTON_TOGGLE events, or null. */
    public @Nullable MKButton getButton() { return button; }

    /** The toggle state for BUTTON_TOGGLE events. */
    public boolean getToggleState() { return toggleState; }

    /** The previous tab index for TAB_CHANGED events. -1 for other event types. */
    public int getPreviousTabIndex() { return previousTabIndex; }

    /** The new tab index for TAB_CHANGED events. -1 for other event types. */
    public int getNewTabIndex() { return newTabIndex; }

    // ── Tab Event Factory ────────────────────────────────────────────────────

    /**
     * Creates a TAB_CHANGED event.
     *
     * @param panelName   the panel containing the tabs element
     * @param elementId   the tabs element's ID
     * @param previousTab the previously active tab index
     * @param newTab      the newly active tab index
     * @param context     the screen context, or null
     * @param player      the player who changed the tab
     */
    public static MKUIEvent tabChanged(String panelName,
                                        String elementId,
                                        int previousTab,
                                        int newTab,
                                        @Nullable MKContext context,
                                        Player player) {
        return new MKUIEvent(Type.TAB_CHANGED, panelName, elementId, null, false,
                context, player, previousTab, newTab);
    }
}
