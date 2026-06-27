package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;
import com.trevorschoeny.menukit.inject.VanillaScreenPanelRegistry;
import com.trevorschoeny.menukit.mixin.ScreenAccessor;

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
 * MK-managed focus registration + screen-level focus utilities.
 *
 * <h3>Why this exists</h3>
 *
 * MenuKit's panel system intercepts clicks within panel bounds (the
 * opacity contract). That interception SUPPRESSES vanilla's natural
 * focus-transition mechanism, which only fires when a vanilla widget
 * actively claims a click via {@code Screen.children()} iteration.
 * On empty-space clicks (in-panel OR outside-panel), no widget claims,
 * so vanilla never calls {@code Screen.setFocused(...)} — a previously-
 * focused widget stays focused indefinitely and silently swallows
 * subsequent keystrokes.
 *
 * <p>Two complementary fixes restore expected UX:
 * <ol>
 *   <li><b>In-panel empty-space clicks</b> — the opacity-eat code paths
 *       (in {@link com.trevorschoeny.menukit.inject.VanillaScreenPanelAdapter}
 *       and {@link com.trevorschoeny.menukit.inject.ScreenPanelRegistry})
 *       call {@code screen.setFocused(null)} before returning the eat.</li>
 *   <li><b>Outside-panel clicks</b> — handled by an {@link #init}-installed
 *       Fabric {@code ScreenMouseEvents.afterMouseClick} listener. After
 *       vanilla's dispatch runs, if the focused widget is MK-managed
 *       (registered via {@link #addWidget}) AND the click was outside its
 *       bounds, the listener clears focus. Scoping to MK-managed widgets
 *       avoids changing vanilla's behavior for non-MK widgets on the
 *       same screen.</li>
 * </ol>
 *
 * <p>The outside-panel path uses a Fabric API event rather than a mixin
 * because {@code Screen} doesn't override {@code mouseClicked} in 1.21.11
 * — it inherits the default method from {@code ContainerEventHandler}, so
 * there's no bytecode in {@code Screen.class} for a mixin to inject into.
 * The Fabric event is the upstream-approved injection point.
 *
 * <h3>Consumer contract</h3>
 *
 * Consumer mods that register focusable widgets via MK (TextField,
 * Keybindery's SearchBox, future MK-adjacent input widgets) call
 * {@link #addWidget} instead of {@code ScreenAccessor.menuKit$addWidget}
 * directly. This wraps the underlying registration AND opts the widget
 * into MK-managed focus semantics. Pair with {@link #removeWidget} on
 * teardown.
 *
 * <p>{@link #clear} is the canonical "blur whatever is focused right now"
 * helper — usable as defense-in-depth before triggering input modes that
 * should start from a clean focus state (chord capture, modal confirms,
 * navigation handoffs).
 *
 * <h3>Lifecycle</h3>
 *
 * Tracking is keyed by {@link Screen} reference via {@link WeakHashMap},
 * so screen GC naturally evicts entries. Widget references inside the
 * per-screen set are strong — widget lifetime is dominated by the
 * screen's own children-list ownership, so the strong reference doesn't
 * extend lifetime beyond what vanilla already does.
 */
public final class MKFocus {

    /**
     * Per-screen registry of MK-managed widgets. WeakHashMap-keyed so
     * closed screens don't leak. Single-threaded — Minecraft's client UI
     * runs on the render thread; no synchronization needed.
     */
    private static final WeakHashMap<Screen, Set<GuiEventListener>> MANAGED =
            new WeakHashMap<>();

    private MKFocus() {}

    // ── Initialization ──────────────────────────────────────────────────

    /**
     * Registers the global {@code ScreenEvents.AFTER_INIT} listener that
     * wires {@code ScreenMouseEvents.afterMouseClick} on every opened
     * screen for outside-bounds focus janitor. Called once from
     * {@code MenuKitClient.onInitializeClient}.
     *
     * <p>The per-screen listeners are GC'd naturally when the screen
     * instance is collected; no explicit teardown.
     */
    public static void init() {
        ScreenEvents.AFTER_INIT.register(MKFocus::onScreenInit);
    }

    private static void onScreenInit(Minecraft client, Screen screen,
                                      int scaledWidth, int scaledHeight) {
        ScreenMouseEvents.afterMouseClick(screen).register(MKFocus::onAfterMouseClick);
    }

    /**
     * {@code AfterMouseClick} handler — outside-bounds focus janitor for
     * clicks that vanilla actually processed (out-of-panel clicks).
     * Delegates to {@link #blurOnOutsideBounds} so both this path and
     * the panel-adapter in-panel dispatch sites share one rule.
     */
    private static boolean onAfterMouseClick(Screen screen, MouseButtonEvent event,
                                              boolean consumed) {
        blurOnOutsideBounds(screen, event.x(), event.y());
        return false;  // AFTER events don't consume; return value unused.
    }

    /**
     * Core focus-janitor rule, shared by the {@code afterMouseClick}
     * listener AND the MK panel-adapter in-panel dispatch sites.
     *
     * <p>If a focused MK-managed widget exists AND the click landed
     * OUTSIDE its bounds, clear focus on the screen. Inside-bounds
     * clicks leave focus alone — letting either vanilla's natural
     * setFocused-on-claim flow handle the transition, or the widget
     * legitimately retain focus on a no-op interaction (e.g., clicking
     * the already-focused TextField again).
     *
     * <p>Scope: only MK-managed widgets (registered via
     * {@link #addWidget}). Non-MK focused widgets keep vanilla focus
     * semantics — no behavioral change for non-MK consumers sharing
     * a screen with MK panels.
     *
     * <p>Called from:
     * <ul>
     *   <li>{@link #onAfterMouseClick} — after vanilla's Screen.mouseClicked
     *       (out-of-panel clicks)</li>
     *   <li>{@code VanillaScreenPanelAdapter.mouseClicked} — after any
     *       in-panel dispatch path (overlay match, element claim, or
     *       opacity-eat fallthrough)</li>
     *   <li>{@code ScreenPanelRegistry.dispatchCoveredClick} — after the
     *       opaque-at-cursor dispatch for container screens</li>
     * </ul>
     * The unified rule means clicking any MK button, dropdown, etc.
     * that doesn't itself take focus blurs a focused MK text input —
     * matching vanilla's "claimant gets focus, others lose it" behavior
     * for non-focus-taking MK elements.
     */
    public static void blurOnOutsideBounds(Screen screen, double mouseX, double mouseY) {
        GuiEventListener focused = screen.getFocused();
        if (focused == null) return;
        if (!isManaged(screen, focused)) return;
        if (focused.isMouseOver(mouseX, mouseY)) return;
        screen.setFocused(null);
    }

    // ── Widget registration + focus utilities ───────────────────────────

    /**
     * Registers a widget with the given screen for input dispatch AND
     * MK-managed focus semantics. Wraps
     * {@link ScreenAccessor#menuKit$addWidget} — the widget is added to
     * the screen's {@code children} + {@code narratables} lists (so
     * vanilla's input pipeline dispatches to it) and recorded in MK's
     * focus-tracking set (so {@code MenuKitFocusJanitorMixin} clears its
     * focus on outside-bounds clicks).
     *
     * <p>Returns the widget for fluent-style chaining (matching vanilla's
     * {@code addWidget} return signature).
     */
    public static <T extends GuiEventListener & NarratableEntry> T addWidget(
            Screen screen, T widget) {
        T result = ((ScreenAccessor) screen).menuKit$addWidget(widget);
        MANAGED.computeIfAbsent(screen, k -> new HashSet<>()).add(result);
        return result;
    }

    /**
     * Symmetric counterpart to {@link #addWidget}. Removes the widget
     * from the screen's input pipeline AND from MK's focus-tracking
     * set. Safe to call even if the widget was never registered (no-op).
     */
    public static void removeWidget(Screen screen, GuiEventListener widget) {
        ((ScreenAccessor) screen).menuKit$removeWidget(widget);
        Set<GuiEventListener> set = MANAGED.get(screen);
        if (set != null) {
            set.remove(widget);
            if (set.isEmpty()) MANAGED.remove(screen);
        }
    }

    /**
     * Clears focus from any widget currently focused on the given screen.
     * Convenience pass-through to {@code screen.setFocused(null)} —
     * exposed at MK's API surface for discoverability and to give
     * consumers a defense-in-depth path (e.g., before triggering a
     * chord-capture flow or other input mode that should start from a
     * clean focus state).
     *
     * <p>The opacity-eat code paths and the focus-janitor mixin also
     * clear focus automatically in their respective scenarios — this
     * method is for consumer-driven clears outside those flows.
     */
    public static void clear(Screen screen) {
        screen.setFocused(null);
    }

    /**
     * Tests whether a widget is MK-managed on the given screen. Used by
     * the {@code afterMouseClick} listener and the widget-hover-suppression
     * mixin to scope their behavior to MK-registered widgets (the suppressor
     * SKIPS suppression for MK-managed widgets so MK-placed widgets like
     * {@code TextField}'s wrapped EditBox keep their hover / cursor
     * feedback even when "underneath" the panel they belong to).
     */
    public static boolean isManaged(Screen screen, GuiEventListener widget) {
        Set<GuiEventListener> set = MANAGED.get(screen);
        return set != null && set.contains(widget);
    }

    /**
     * Post-Phase 18r-5: is the cursor currently <em>covered</em> by any visible
     * MK content on the active screen? A point is covered when some panel claims
     * it via the unified {@link ScreenPanelRegistry#panelClaimsPoint} test — an
     * opaque background (minus per-element holes), an active overlay (e.g., an
     * open Dropdown popover extending beyond its owning panel), or a solid
     * interactive element. Combines queries across {@link ScreenPanelRegistry}
     * (container-screen + lambda-active adapters) AND {@link
     * com.trevorschoeny.menukit.inject.VanillaScreenPanelRegistry}
     * (non-container vanilla-screen adapters) — one claim definition for all.
     *
     * <p>Used by the widget-hover-suppression mixin to stop vanilla
     * buttons / list rows from rendering hover highlights when the cursor
     * is "over" them but visually covered by MK content. The opacity-eat
     * input path routes the click away; this closes the visual loop.
     */
    public static boolean isCursorCovered(double mouseX, double mouseY) {
        if (ScreenPanelRegistry.anyPanelCoversPoint(mouseX, mouseY)) return true;
        if (ScreenPanelRegistry.hasActiveOverlayAt(mouseX, mouseY)) return true;
        Screen screen = Minecraft.getInstance().screen;
        if (VanillaScreenPanelRegistry.hasOpaqueRegionAt(screen, mouseX, mouseY)) return true;
        return false;
    }

    /**
     * No-arg variant of {@link #isCursorCovered} for callers
     * without mouse coords as parameters (e.g., the tooltip-suppression
     * mixin which fires inside {@code GuiGraphics.setTooltipForNextFrame}).
     * Reads cursor position from {@code MouseHandler} and converts to
     * GUI-scaled coords using the same formula as
     * {@code MenuKitModalMouseHandlerMixin}.
     */
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
     * THE unified inertness predicate — the single source of truth every
     * suppression site consults so they cannot disagree about whether vanilla
     * content at a screen point is covered by MenuKit content. A point is inert
     * iff vanilla there should receive <em>nothing</em>: no click / release /
     * scroll, no hover, no highlight, no tooltip.
     *
     * <p>Inert iff EITHER:
     * <ul>
     *   <li>a visible modal-tracking panel exists — a modal claims the WHOLE
     *       screen, so everything behind it is inert; OR</li>
     *   <li>some panel <em>covers</em> (mouseX, mouseY) — its opaque background
     *       (minus per-element click-through holes), an active overlay, OR a
     *       solid interactive element — across container, lambda, AND vanilla-
     *       screen adapters, all through the one
     *       {@link com.trevorschoeny.menukit.inject.ScreenPanelRegistry#panelClaimsPoint}
     *       test (see {@link #isCursorCovered}).</li>
     * </ul>
     *
     * <p>Every hover / highlight / tooltip suppressor and the press/release/
     * scroll eat all reduce to this one question, so a new vanilla surface (a
     * creative tab, a new widget kind) can't fall through a per-site predicate
     * gap. The input path's dispatch-returning twin (it also returns the panel
     * to route the input to) is
     * {@link com.trevorschoeny.menukit.inject.ScreenPanelRegistry#findCoveringPanelAt}.
     */
    public static boolean isInertUnderPanel(double mouseX, double mouseY) {
        return ScreenPanelRegistry.hasAnyVisibleModalTracking()
                || isCursorCovered(mouseX, mouseY);
    }

    /**
     * No-coord variant of {@link #isInertUnderPanel} — reads the cursor
     * position from {@code MouseHandler} (for suppressors that fire without
     * coords, e.g. the tooltip and list-hover mixins).
     */
    public static boolean isInertUnderPanelAtCursor() {
        return ScreenPanelRegistry.hasAnyVisibleModalTracking()
                || isCursorCoveredAtCursor();
    }
}
