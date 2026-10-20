package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which slots each panel shows this frame on a container screen, and the draw of
 * those slots right after their panel (7.3.0, the draw-order rule of
 * {@link ContainerScreenLayers}).
 *
 * <h3>The rule</h3>
 *
 * A slot a panel shows draws right after that panel's own drawing, and a panel above
 * covers the slots of the panels below it. Menu order no longer decides it. So:
 * <ol>
 *   <li><b>Recorded first.</b> At the head of the frame, before vanilla's slot pass,
 *       {@link ContainerScreenLayers} lays every panel out once without drawing it
 *       (the place pass). A slot element writes its slot's position and reports it
 *       ({@code SlotClip.shown}); {@link #record} files the slot under the panel
 *       being laid out ({@link #beginPanel}).</li>
 *   <li><b>Skipped by vanilla.</b> Vanilla's slot pass leaves out every slot
 *       {@link #isPresented} (its walk is wrapped, not replaced), so an unshown
 *       vanilla slot draws exactly as before.</li>
 *   <li><b>Drawn with the panel.</b> When the panel draws for real, its host calls
 *       {@link #drawAfter} between the panel's elements and their open popovers: the
 *       screen's own {@code extractSlot} for each of its slots (another mod's
 *       {@code extractSlot} mixin still marks it), the hovered one between vanilla's
 *       back and front highlights.</li>
 * </ol>
 *
 * <p>Only panels drawn after vanilla's slot pass take part: the screen's registry
 * hosts' FLOW panels and every host's POPOVER and OVERLAY panels. A library's own
 * screen draws its own FLOW panels before the slot pass, under every slot, so their
 * slots stay in vanilla's pass as they always were.
 *
 * <p><b>Background panels</b> (7.4.0, {@link ScreenBackground}) draw in the screen's
 * background pass, before anything else of the frame. The same rule holds there: a slot a
 * background panel shows is placed while the panel renders and drawn right after the
 * panel's elements, in the background pass ({@link #drawAfter}), so the screen's widgets,
 * every other mod's, and everything else draw over it, as over its panel. Those slots are
 * filed apart ({@link #placedBelow}, {@link #below}); vanilla's pass leaves them out, and
 * they are not the "unreported created slot" that splits layer 2 in
 * {@link ContainerScreenLayers}. Vanilla has not worked out the hovered slot yet at that
 * point, so the background draw asks vanilla's own lookup ({@code getHoveredSlot}) itself,
 * and puts the screen's hovered slot back after.
 *
 * <p>Render thread only, like the slot pass. Cleared at the start of every place pass and
 * at the end of every container screen's frame.
 */
@ApiStatus.Internal
public final class PresentedSlots {

    private PresentedSlots() {}

    /** Draws one presented slot; the default asks the screen. Swapped by MenuKit's checks. */
    @FunctionalInterface
    public interface Drawer {
        /**
         * @param hovered whether {@code slot} is the screen's hovered slot, so its
         *                highlights go around it
         */
        void draw(GuiGraphicsExtractor graphics, Slot slot, boolean hovered);
    }

    /** The slots each panel presented this frame, in the order it placed them. Identity-keyed. */
    private static final Map<Panel, List<Slot>> BY_PANEL = new IdentityHashMap<>();
    /** Every slot presented this frame, whichever panel: what vanilla's pass skips. */
    private static final Set<Slot> ALL = Collections.newSetFromMap(new IdentityHashMap<>());

    /** True during the place pass: reports are filed, and nothing is drawn. */
    private static boolean recording = false;

    /**
     * The slots each background panel placed this frame, and all of them (7.4.0). Filed
     * while {@link #background} is on; cleared at the start of the background layer and
     * at the end of the frame.
     */
    private static final Map<Panel, List<Slot>> BELOW = new IdentityHashMap<>();
    private static final Set<Slot> BELOW_ALL = Collections.newSetFromMap(new IdentityHashMap<>());
    /** True while the background layer draws. */
    private static boolean background = false;
    /** The panel being laid out or drawn now, or {@code null} between panels. */
    private static @Nullable Panel current;

    /** The screen this frame's slots belong to, and the mouse, for the default drawer. */
    private static @Nullable AbstractContainerScreen<?> screen;
    private static int mouseX, mouseY;
    /** The hovered slot by hand, for MenuKit's checks (no screen); the screen's otherwise. */
    private static @Nullable Slot hoveredForChecks;

    private static Drawer drawer = PresentedSlots::drawOnScreen;

    /**
     * The panel that last showed each slot, whatever the frame, and the panels drawn this
     * frame: so a slot library can tell a slot whose panel is hidden (hand a vanilla slot
     * back to its place) from one scrolled out of a shown panel (park it). Weak and
     * identity-keyed: a slot and a panel are objects, and a closed screen's go with it.
     */
    private static final Map<Slot, Panel> LAST_PANEL = new java.util.WeakHashMap<>();
    private static final Set<Panel> DRAWN = Collections.newSetFromMap(new IdentityHashMap<>());

    // The frame

    /** The start of a place pass on {@code screen}: last frame's record goes. */
    static void beginFrame(@Nullable AbstractContainerScreen<?> s, int mx, int my) {
        BY_PANEL.clear();
        ALL.clear();
        hoveredForChecks = null;
        screen = s;
        mouseX = mx;
        mouseY = my;
    }

    /** Whether reports are being filed (the place pass) rather than slots drawn. */
    static void recording(boolean on) {
        recording = on;
    }

    /** The host starts laying out or drawing {@code panel}'s elements. */
    public static void beginPanel(Panel panel) {
        current = panel;
        DRAWN.add(panel);
    }

    /**
     * The end of a container screen's frame, after its slot library has asked
     * {@link #panelShowing}: nothing of this frame outlives it, so a panel also shown on
     * a screen with no slot pass never draws a closed screen's slots.
     */
    public static void endFrame() {
        BELOW.clear();
        BELOW_ALL.clear();
        DRAWN.clear();
        BY_PANEL.clear();
        ALL.clear();
        screen = null;
    }

    /**
     * {@code SlotClip.panelShowing}: whether the panel that last showed {@code slot} was
     * drawn this frame. False when it was not (hidden, or not on this screen) and when no
     * panel ever showed the slot.
     */
    public static boolean panelShowing(Slot slot) {
        Panel panel = LAST_PANEL.get(slot);
        return panel != null && DRAWN.contains(panel);
    }

    /**
     * The panel that last showed {@code slot}, on any frame, or {@code null} when none
     * ever did: how the container region knows a slot is one of its own while it is
     * scrolled out of view (7.4.0).
     */
    public static @Nullable Panel lastPanel(Slot slot) {
        return LAST_PANEL.get(slot);
    }

    /** The host has drawn the panel's elements. */
    public static void endPanel() {
        current = null;
    }

    /**
     * {@code SlotClip.shown}: the panel being laid out presents {@code slot}. Filed only
     * during the place pass; a slot two panels report belongs to the first.
     */
    public static void record(Slot slot) {
        if (current != null) LAST_PANEL.put(slot, current);
        if (background && current != null) {
            if (BELOW_ALL.add(slot)) BELOW.computeIfAbsent(current, p -> new ArrayList<>()).add(slot);
            return;
        }
        if (!recording || current == null) return;
        if (ALL.add(slot)) BY_PANEL.computeIfAbsent(current, p -> new ArrayList<>()).add(slot);
    }

    /**
     * The background layer starts ({@code true}, last frame's background slots go) or ends.
     * {@code s} is the container screen whose background it is, and the mouse, for drawing
     * its panels' slots; {@code null} on any other screen (and in MenuKit's checks, which
     * set the hovered slot by hand).
     */
    static void background(boolean on, @Nullable AbstractContainerScreen<?> s, int mx, int my) {
        if (on) {
            BELOW.clear();
            BELOW_ALL.clear();
            screen = s;
            mouseX = mx;
            mouseY = my;
        }
        background = on;
    }

    /**
     * Whether a background panel placed {@code slot} this frame (7.4.0): it drew right
     * after its panel, in the background pass, so vanilla's pass leaves it out, and it is
     * a reported slot, not a layer boundary.
     */
    public static boolean placedBelow(@Nullable Slot slot) {
        return slot != null && BELOW_ALL.contains(slot);
    }

    /**
     * Whether {@code slot} is a background panel's (one that is showing) and one of the
     * screen's widgets is over it at the point (7.4.0). The slot draws with its panel,
     * under every widget, so the point is the widget's and the slot is not hovered there.
     * Asked between frames too (input), so it goes by the panel that last showed the slot,
     * not this frame's record. A slot vanilla draws (the inventory's) is never one.
     */
    public static boolean underWidget(AbstractContainerScreen<?> screen, @Nullable Slot slot, double mouseX, double mouseY) {
        Panel panel = slot == null ? null : LAST_PANEL.get(slot);
        return panel != null && panel.isBackground() && panel.isVisible()
                && SlotScreenDispatcher.widgetAt(screen, mouseX, mouseY);
    }

    /** The slots background panel {@code panel} placed this frame, in placing order (empty for none). */
    public static List<Slot> below(Panel panel) {
        return BELOW.getOrDefault(panel, List.of());
    }

    /** Whether a panel presents {@code slot} this frame: vanilla's slot pass leaves it out. */
    public static boolean isPresented(@Nullable Slot slot) {
        return slot != null && ALL.contains(slot);
    }

    /** The slots {@code panel} presents this frame, in placing order (empty for none). */
    public static List<Slot> of(Panel panel) {
        return BY_PANEL.getOrDefault(panel, List.of());
    }

    // Drawing

    /**
     * Right after {@code panel}'s elements draw (before their popovers): its slots,
     * through the drawer. Nothing during the place pass, and nothing for a panel that
     * shows no slot (every panel on a screen that is not a container screen).
     */
    public static void drawAfter(GuiGraphicsExtractor graphics, Panel panel) {
        if (recording) return;
        if (background) {
            drawBelow(graphics, panel);
            return;
        }
        List<Slot> slots = BY_PANEL.get(panel);
        if (slots == null) return;
        Slot hovered = screen == null ? hoveredForChecks : ((AbstractContainerScreenAccessor) screen).mk$getHoveredSlot();
        for (Slot slot : slots) {
            // Vanilla's pass draws only an active slot; so does this one.
            if (slot.isActive()) drawer.draw(graphics, slot, slot == hovered);
        }
    }

    /**
     * A background panel's slots, right after its elements placed them, in the background
     * pass (7.4.0): before every widget and renderable of the screen's, so a foreign button
     * over a slot covers its item, and over the panel, clipped as any presented slot.
     *
     * <p>Vanilla works out the hovered slot later in the frame, so this asks vanilla's own
     * lookup now, with this frame's positions for every background slot: the answer vanilla
     * gets a moment later, for its tooltip, unless a slot drawn after the slot pass moves
     * under the mouse in between (then the highlight is a frame late). The screen's
     * {@code hoveredSlot} is that slot only while these draw (the highlights read it), and
     * goes back after, so vanilla's own hover bookkeeping ({@code onStopHovering}) is as
     * before.
     */
    private static void drawBelow(GuiGraphicsExtractor graphics, Panel panel) {
        List<Slot> slots = BELOW.get(panel);
        if (slots == null) return;
        AbstractContainerScreen<?> s = screen;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) s;
        Slot hovered = acc == null ? hoveredForChecks : acc.mk$findHoveredSlot(mouseX, mouseY);
        Slot vanillaHovered = acc == null ? null : acc.mk$getHoveredSlot();
        if (acc != null) acc.mk$setHoveredSlot(hovered);
        try {
            for (Slot slot : slots) {
                if (slot.isActive()) drawer.draw(graphics, slot, slot == hovered);
            }
        } finally {
            if (acc != null) acc.mk$setHoveredSlot(vanillaHovered);
        }
    }

    /** For MenuKit's checks: the hovered slot a background panel's slots draw with, with no screen. */
    @VisibleForTesting
    public static void hoveredForChecks(@Nullable Slot hovered) {
        hoveredForChecks = hovered;
    }

    /**
     * The default drawer: the screen's own {@code extractSlot} under the frame's
     * {@code leftPos/topPos} translation, as vanilla's slot pass draws it, the hovered
     * slot between the back and front highlights. Every one of those calls carries
     * {@link ContainerScreenLayers}' clip hooks, so a slot across a scroll area's
     * edge is cut as before.
     */
    private static void drawOnScreen(GuiGraphicsExtractor graphics, Slot slot, boolean hovered) {
        AbstractContainerScreen<?> s = screen;
        if (s == null) return;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) s;
        graphics.pose().pushMatrix();
        graphics.pose().translate(acc.mk$getLeftPos(), acc.mk$getTopPos());
        try {
            if (hovered) acc.mk$extractSlotHighlightBack(graphics);
            acc.mk$extractSlot(graphics, slot, mouseX, mouseY);
            if (hovered) acc.mk$extractSlotHighlightFront(graphics);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** For MenuKit's checks: swaps the drawer, and back with {@code null}. */
    @VisibleForTesting
    public static void drawerForChecks(@Nullable Drawer d) {
        drawer = d == null ? PresentedSlots::drawOnScreen : d;
    }

    /** For MenuKit's checks: a place pass by hand, with no screen, {@code hovered} the hovered slot. */
    @VisibleForTesting
    public static void placePassForChecks(Runnable layOut, @Nullable Slot hovered) {
        beginFrame(null, -1, -1);
        hoveredForChecks = hovered;
        recording(true);
        try {
            layOut.run();
        } finally {
            recording(false);
            current = null;
        }
    }
}
