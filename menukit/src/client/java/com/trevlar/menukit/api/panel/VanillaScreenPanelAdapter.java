package com.trevlar.menukit.api.panel;

import org.jetbrains.annotations.ApiStatus;

import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.window.Declarations;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Puts a {@link Panel} on vanilla <em>non-container</em> screens: Options,
 * Controls, KeyBinds, world select, the title screen, and any other
 * {@link Screen} that is not a container screen. The sibling of
 * {@link ScreenPanelAdapter}; both declare into {@link ScreenPanelRegistry}, which
 * builds one host per open screen.
 *
 * <pre>{@code
 * Panel panel = Panel.builder("mymod:options-help")
 *         .add(...)
 *         .position(PanelPosition.screenAnchor(InsideRegion.TOP_RIGHT))
 *         .build();
 * new VanillaScreenPanelAdapter(panel, 0).on(OptionsScreen.class);
 * }</pre>
 *
 * <h3>Placement is the panel's</h3>
 * A non-container screen has no frame, so the panel declares
 * {@code screenAnchor(InsideRegion)} (4px in from the edges it touches; siblings on
 * one spot stack 4px apart), {@code center()}, or {@code pixel(...)}. A
 * {@code region(...)}, {@code main()} or unplaced panel is rejected at construction.
 *
 * <h3>Targeting is required</h3>
 * Call {@link #on} or {@link #onAny} exactly once. An adapter with no targeting fails
 * loudly at the first screen open: an everywhere-default makes no sense across the
 * title screen, Options and world select.
 *
 * <h3>Input</h3>
 * The panel claims input by the one rule ({@link PanelHost#claimsPoint}): an opaque
 * panel its whole rectangle, a transparent one its solid elements. Vanilla widgets
 * under a claim neither hover nor click.
 *
 * <h3>Frozen after init</h3>
 * Construction, targeting and {@link #unregister()} throw once MenuKit's
 * declarations freeze at client start. Gate a panel at runtime with
 * {@link Panel#visibleWhen}.
 */
public final class VanillaScreenPanelAdapter {

    /** Default content padding for styled panels ({@link ScreenPanelAdapter#DEFAULT_PADDING}). */
    public static final int DEFAULT_PADDING = ScreenPanelAdapter.DEFAULT_PADDING;

    private final Panel panel;
    private final int padding;
    private final String modId;
    private final int seq;

    private @Nullable List<Class<? extends Screen>> targets = null;
    private boolean targetedAny = false;

    /**
     * Declares the panel with its own interior padding ({@code 0} for
     * {@link PanelStyle#NONE}, {@link #DEFAULT_PADDING} otherwise).
     *
     * @throws IllegalArgumentException if a vanilla screen cannot place the panel's position
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public VanillaScreenPanelAdapter(Panel panel) {
        this(panel, Objects.requireNonNull(panel, "panel must not be null").interiorPadding());
    }

    /**
     * Declares the panel with explicit content padding.
     *
     * @throws IllegalArgumentException if a vanilla screen cannot place the panel's position
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public VanillaScreenPanelAdapter(Panel panel, int padding) {
        Objects.requireNonNull(panel, "panel must not be null");
        Declarations.requireOpen("VanillaScreenPanelAdapter(" + panel.id() + ")");
        PanelHost.requireSupported(PanelHost.Kind.VANILLA_SCREEN, panel, panel.getPosition());
        this.panel = panel;
        this.padding = padding;
        this.modId = PanelHost.captureCallerModId();
        this.seq = PanelHost.nextSeq();
        ScreenPanelRegistry.declare(this);
    }

    // ── Targeting ───────────────────────────────────────────────────────

    /**
     * The screens this panel appears on, by class ancestry (OR across classes). Call
     * exactly once.
     */
    @SafeVarargs
    public final VanillaScreenPanelAdapter on(Class<? extends Screen>... screenClasses) {
        requireUndeclared();
        if (screenClasses.length == 0) {
            throw new IllegalArgumentException("VanillaScreenPanelAdapter for panel '" + panel.id()
                    + "': .on() requires at least one screen class.");
        }
        this.targets = List.of(screenClasses);
        return this;
    }

    /**
     * Every non-container screen. Rare (the panel would show on the title screen,
     * Options, world select...); {@link #on} is usually what you mean.
     */
    public VanillaScreenPanelAdapter onAny() {
        requireUndeclared();
        this.targetedAny = true;
        return this;
    }

    private void requireUndeclared() {
        Declarations.requireOpen("VanillaScreenPanelAdapter(" + panel.id() + ") targeting");
        if (targets != null || targetedAny) {
            throw new IllegalStateException("VanillaScreenPanelAdapter for panel '" + panel.id()
                    + "' already declared targeting. Call .on(...) or .onAny() exactly once.");
        }
    }

    /** Whether this adapter's panel appears on {@code screen}. */
    public boolean matches(Screen screen) {
        if (targetedAny) return true;
        if (targets == null) return false;
        for (Class<? extends Screen> target : targets) {
            if (target.isInstance(screen)) return true;
        }
        return false;
    }

    /** Whether {@link #on} or {@link #onAny} was called. */
    public boolean isTargetingDeclared() { return targets != null || targetedAny; }

    // ── Teardown ────────────────────────────────────────────────────────

    /**
     * Withdraws this adapter from screens opened from now on. Idempotent. Throws after
     * MenuKit's declarations froze; gate at runtime with {@link Panel#visibleWhen}.
     */
    public void unregister() {
        Declarations.requireOpen("VanillaScreenPanelAdapter(" + panel.id() + ").unregister()");
        ScreenPanelRegistry.withdraw(this);
    }

    // ── Accessors ──────────────────────────────────────────────────────

    /** The panel this adapter declares. */
    public Panel getPanel() { return panel; }

    /** The content padding inside the panel's edge. */
    public int getPadding() { return padding; }

    /** The declared target classes, or {@code null} (none, or {@link #onAny}). */
    public @Nullable List<Class<? extends Screen>> getTargets() { return targets; }

    /** Whether {@link #onAny} was called. */
    public boolean isTargetedAny() { return targetedAny; }

    @ApiStatus.Internal
    public String modId() { return modId; }

    @ApiStatus.Internal
    public int seq() { return seq; }
}
