package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Focus for widgets MenuKit places on a screen, and the "is this point covered by
 * MenuKit" checks.
 *
 * <p>Panels eat clicks inside their bounds, and vanilla only moves focus when a
 * widget in {@code Screen.children()} claims a click. So on a click in empty space,
 * inside a panel or outside, nothing claims it, vanilla never calls
 * {@code Screen.setFocused(...)}, and a focused widget stays focused and keeps
 * swallowing keystrokes. Two paths fix that:
 * <ol>
 *   <li><b>Clicks MenuKit takes</b>: {@link com.trevlar.menukit.inject.ScreenPanelRegistry}
 *       calls {@link #blurOnOutsideBounds} after routing a click to a panel.</li>
 *   <li><b>Clicks vanilla handles</b>: a Fabric
 *       {@code ScreenMouseEvents.afterMouseClick} listener, installed by
 *       {@link #init}, does the same after vanilla's dispatch.</li>
 * </ol>
 * Both only touch widgets registered through {@link #addWidget}; other widgets on
 * the screen keep vanilla's focus behaviour.
 *
 * <p>The second path is a Fabric event rather than a mixin because in 26.2
 * {@code Screen} doesn't override {@code mouseClicked}; it inherits the default
 * method from {@code ContainerEventHandler}, so there is no code in
 * {@code Screen.class} to inject into.
 *
 * <h3>For mods</h3>
 *
 * Register focusable widgets (TextField, Keybindery's SearchBox and the like)
 * with {@link #addWidget} rather than {@code ScreenAccessor.mk$addWidget}: it adds
 * the widget to the screen and opts it into this focus handling. Pair it with
 * {@link #removeWidget}.
 *
 * <p>{@link #clear} blurs whatever is focused, for starting an input mode from a
 * clean state (chord capture, a modal confirm, handing navigation over).
 *
 * <p>Tracking is keyed weakly by {@link Screen}, so a collected screen drops out.
 * The widgets in each set are held strongly, but the screen's own children list
 * already keeps them alive as long.
 */
public final class Focus {

    // Widgets registered through addWidget, per screen. Weak keys so closed screens
    // don't leak. Render thread only, so no synchronisation.
    private static final WeakHashMap<Screen, Set<GuiEventListener>> MANAGED =
            new WeakHashMap<>();

    private Focus() {}

    /**
     * Registers the {@code ScreenEvents.AFTER_INIT} listener that adds the
     * {@code afterMouseClick} focus check to every screen. Called once from
     * {@code MenuKitClient.onInitializeClient}. The per-screen listeners go with
     * their screen.
     */
    public static void init() {
        ScreenEvents.AFTER_INIT.register(Focus::onScreenInit);
    }

    private static void onScreenInit(Minecraft client, Screen screen,
                                      int scaledWidth, int scaledHeight) {
        ScreenMouseEvents.afterMouseClick(screen).register(Focus::onAfterMouseClick);
    }

    // For clicks vanilla handled; same rule as the panel click paths.
    private static boolean onAfterMouseClick(Screen screen, MouseButtonEvent event,
                                              boolean consumed) {
        blurOnOutsideBounds(screen, event.x(), event.y());
        return false;  // after-events can't consume; the value is unused
    }

    /**
     * Clears the screen's focus when the focused widget was registered through
     * {@link #addWidget} and the click landed outside it. A click inside leaves
     * focus alone: either vanilla moves focus to whatever claimed the click, or
     * the widget keeps it (clicking a focused TextField again, say). Widgets not
     * registered here keep vanilla's behaviour.
     *
     * <p>Called after vanilla's click dispatch (through the
     * {@code afterMouseClick} listener) and after MenuKit routes a click to a
     * panel. So clicking any MenuKit button or dropdown that doesn't take focus
     * itself blurs a focused MenuKit text field, as vanilla does when another
     * widget claims a click.
     */
    public static void blurOnOutsideBounds(Screen screen, double mouseX, double mouseY) {
        GuiEventListener focused = screen.getFocused();
        if (focused == null) return;
        if (!isManaged(screen, focused)) return;
        if (focused.isMouseOver(mouseX, mouseY)) return;
        screen.setFocused(null);
    }

    /**
     * Adds a widget to the screen for input and opts it into MenuKit's focus
     * handling. Through {@link ScreenAccessor#mk$addWidget} the widget joins the
     * screen's {@code children} and {@code narratables} lists, so vanilla sends it
     * input, and it is recorded here, so a click outside it clears its focus.
     *
     * <p>Returns the widget, as vanilla's {@code addWidget} does.
     */
    public static <T extends GuiEventListener & NarratableEntry> T addWidget(
            Screen screen, T widget) {
        T result = ((ScreenAccessor) screen).mk$addWidget(widget);
        MANAGED.computeIfAbsent(screen, k -> new HashSet<>()).add(result);
        return result;
    }

    /**
     * Undoes {@link #addWidget}: removes the widget from the screen's input and
     * from focus tracking. Does nothing for a widget that was never registered.
     */
    public static void removeWidget(Screen screen, GuiEventListener widget) {
        ((ScreenAccessor) screen).mk$removeWidget(widget);
        Set<GuiEventListener> set = MANAGED.get(screen);
        if (set != null) {
            set.remove(widget);
            if (set.isEmpty()) MANAGED.remove(screen);
        }
    }

    /**
     * Clears focus on the screen, the same as {@code screen.setFocused(null)}. For
     * a mod that wants a clean focus state, say before starting chord capture.
     * MenuKit's click handling already clears focus in the cases above.
     */
    public static void clear(Screen screen) {
        screen.setFocused(null);
    }

    /**
     * Whether the widget was registered on the screen through {@link #addWidget}.
     * The focus check and the widget hover suppressor use it; the suppressor skips
     * registered widgets, so a {@code TextField}'s wrapped EditBox keeps its hover
     * and cursor feedback inside the panel it belongs to.
     */
    public static boolean isManaged(Screen screen, GuiEventListener widget) {
        Set<GuiEventListener> set = MANAGED.get(screen);
        return set != null && set.contains(widget);
    }

    /**
     * Whether vanilla content at the point is covered by MenuKit, by
     * {@link ScreenPanelRegistry#claimAt}: a shown modal claims everything; an
     * opaque panel claims its whole rectangle; a transparent panel its solid
     * elements; an open popover its area. Across every host on the screen (container
     * and vanilla-screen adapters, slot groups, a standalone screen's own panels).
     *
     * <p>One exception, for content that belongs to the screen itself (its widgets,
     * list rows, tabs, tooltips): a standalone screen's own panels do not make that
     * screen's own widgets inert (see {@link ScreenPanelRegistry#screenContentInertAt}).
     */
    public static boolean isCursorCovered(double mouseX, double mouseY) {
        return ScreenPanelRegistry.screenContentInertAt(Minecraft.getInstance().gui.screen(), mouseX, mouseY);
    }

    /** {@link #isCursorCovered} at the live cursor (GUI-scaled from MouseHandler). */
    public static boolean isCursorCoveredAtCursor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        var window = mc.getWindow();
        var mouseHandler = mc.mouseHandler;
        if (window == null || mouseHandler == null) return false;
        double scaledX = mouseHandler.xpos() * window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double scaledY = mouseHandler.ypos() * window.getGuiScaledHeight() / (double) window.getScreenHeight();
        return isCursorCovered(scaledX, scaledY);
    }

    /**
     * The inertness check every suppressor of screen content uses (widget hover,
     * list hover, creative tabs, tooltips), so they all agree on whether a point is
     * covered. Slot hover asks the slot form,
     * {@link ScreenPanelRegistry#slotInertAt}, which differs only in that a panel's
     * own slot stays live under its own claim. The modal case is part of the claim.
     */
    public static boolean isInertUnderPanel(double mouseX, double mouseY) {
        return isCursorCovered(mouseX, mouseY);
    }

    /** {@link #isInertUnderPanel} at the live cursor. */
    public static boolean isInertUnderPanelAtCursor() {
        return isCursorCoveredAtCursor();
    }
}
