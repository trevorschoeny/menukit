package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelStyle;
import com.trevlar.menukit.window.Declarations;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Puts a {@link Panel} on vanilla container screens. Constructing an adapter
 * declares it; the library renders the panel and routes its input on every
 * targeted screen, with no consumer mixin.
 *
 * <pre>{@code
 * Panel panel = Panel.builder("mymod:controls")
 *         .add(new Button(0, 0, 90, 16, Component.literal("Press"), b -> {}))
 *         .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP).priority(10))
 *         .build();
 * new ScreenPanelAdapter(panel).on(InventoryScreen.class);
 * }</pre>
 *
 * <h3>Placement is the panel's</h3>
 * Where the panel sits is its {@link com.trevlar.menukit.core.PanelPosition}
 * (§0065): {@code region(OutsideRegion)} around the menu frame (extended by the
 * screen's chrome: creative tab rows, an open recipe book),
 * {@code screenAnchor(InsideRegion)} on the screen, {@code center()} as an overlay,
 * or {@code pixel(...)}. The adapter only says which screens it appears on. An
 * unplaced panel, or a {@code main()} one, is rejected at construction.
 *
 * <h3>Targeting</h3>
 * With no {@link #on}, {@link #onAny}, {@link #onPlayerInventory} or
 * {@link #onMatching} call the panel appears on every container screen (default-on,
 * opt-out, like {@code MKCContainerPanel}). Declare targeting at most once.
 *
 * <h3>Frozen after init</h3>
 * Construct adapters from your client initializer. Construction, targeting and
 * {@link #unregister()} throw once MenuKit's declarations freeze at client start.
 * A panel that comes and goes at runtime keeps its adapter and gates itself with
 * {@link Panel#showWhen}.
 *
 * <h3>Padding</h3>
 * Content padding sits between the panel's outer edge and its elements. The
 * one-argument constructor uses {@link Panel#interiorPadding()} ({@code 0} for
 * {@link PanelStyle#NONE}, {@link #DEFAULT_PADDING} otherwise).
 */
public final class ScreenPanelAdapter {

    /** Default content padding for styled panels. */
    public static final int DEFAULT_PADDING = 7;

    private final Panel panel;
    private final int padding;
    private final String modId;
    private final int seq;

    /** Declared targets; null with no class list. */
    private @Nullable List<Class<? extends AbstractContainerScreen<?>>> targets = null;
    /** Declared matcher; null with no matcher. */
    private @Nullable ScreenMatcher matcher = null;
    /** Whether targeting was declared (a second declaration is a bug). */
    private boolean targetingDeclared = false;

    /**
     * Declares the panel on container screens with the panel's own interior padding.
     *
     * @throws IllegalArgumentException if the panel's position cannot be placed on a
     *         container screen (unplaced, or {@code main()})
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public ScreenPanelAdapter(Panel panel) {
        this(panel, panel.interiorPadding());
    }

    /**
     * Declares the panel on container screens with explicit content padding
     * ({@code 0} for flush edges).
     *
     * @throws IllegalArgumentException if the panel's position cannot be placed on a
     *         container screen (unplaced, or {@code main()})
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public ScreenPanelAdapter(Panel panel, int padding) {
        Declarations.requireOpen("ScreenPanelAdapter(" + panel.getId() + ")");
        PanelHost.requireSupported(PanelHost.Kind.CONTAINER, panel, panel.getPosition());
        this.panel = panel;
        this.padding = padding;
        this.modId = PanelHost.captureCallerModId();
        this.seq = PanelHost.nextSeq();
        ScreenPanelRegistry.declare(this);
    }

    // ── Teardown ────────────────────────────────────────────────────────

    /**
     * Withdraws this adapter: the next screen that opens no longer shows the panel.
     * Idempotent. A declaration like any other: throws after MenuKit's declarations
     * froze. To hide a panel at runtime, gate it with {@link Panel#showWhen}.
     */
    public void unregister() {
        Declarations.requireOpen("ScreenPanelAdapter(" + panel.getId() + ").unregister()");
        ScreenPanelRegistry.withdraw(this);
    }

    // ── Targeting ───────────────────────────────────────────────────────

    /**
     * Narrows the panel to screens that are instances of one of
     * {@code screenClasses} (class ancestry: {@code ChestScreen} covers modded
     * subclasses). {@code InventoryScreen} alone is survival only; use
     * {@link #onPlayerInventory()} for the player inventory in both modes (§0051).
     *
     * @throws IllegalStateException    if targeting was already declared, or after
     *                                  MenuKit's declarations froze
     * @throws IllegalArgumentException if {@code screenClasses} is empty
     */
    @SafeVarargs
    public final ScreenPanelAdapter on(Class<? extends AbstractContainerScreen<?>>... screenClasses) {
        requireUndeclared();
        if (screenClasses.length == 0) {
            throw new IllegalArgumentException("Adapter for panel '" + panel.getId()
                    + "': .on() requires at least one screen class. Use .onAny() for every screen.");
        }
        this.targets = List.of(screenClasses);
        return this;
    }

    /** Every container screen, said explicitly. The default when nothing is declared. */
    public ScreenPanelAdapter onAny() {
        requireUndeclared();
        return this;
    }

    /**
     * The player inventory in both game modes: {@code InventoryScreen} and
     * {@code CreativeModeInventoryScreen} are siblings, so {@code .on(InventoryScreen.class)}
     * alone silently misses creative.
     */
    public ScreenPanelAdapter onPlayerInventory() {
        return on(InventoryScreen.class, CreativeModeInventoryScreen.class);
    }

    /**
     * Every screen a {@link ScreenMatcher} accepts: the parity-shaped targeting mode
     * ({@code ScreenMatcher.all()} with an {@code allExcept} opt-out), which
     * {@code MKCContainerPanel} uses to scope its chrome.
     */
    public ScreenPanelAdapter onMatching(ScreenMatcher matcher) {
        requireUndeclared();
        if (matcher == null) {
            throw new IllegalArgumentException("Adapter for panel '" + panel.getId()
                    + "': onMatching(...) requires a non-null ScreenMatcher.");
        }
        this.matcher = matcher;
        return this;
    }

    private void requireUndeclared() {
        Declarations.requireOpen("ScreenPanelAdapter(" + panel.getId() + ") targeting");
        if (targetingDeclared) {
            throw new IllegalStateException("Adapter for panel '" + panel.getId()
                    + "' already declared targeting. Call .on(...) / .onAny() / .onMatching(...) once.");
        }
        targetingDeclared = true;
    }

    /** Whether this adapter's panel appears on {@code screen}. */
    public boolean matches(AbstractContainerScreen<?> screen) {
        if (matcher != null) return matcher.matches(screen.getClass());
        if (targets == null) return true;
        for (Class<? extends AbstractContainerScreen<?>> target : targets) {
            if (target.isInstance(screen)) return true;
        }
        return false;
    }

    // ── Accessors ──────────────────────────────────────────────────────

    /** The panel this adapter declares. */
    public Panel getPanel() { return panel; }

    /** The content padding inside the panel's edge. */
    public int getPadding() { return padding; }

    /** The registering mod (the tie-break after priority). */
    String modId() { return modId; }

    /** The registration sequence (the last tie-break). */
    int seq() { return seq; }

    /**
     * The panel's outer top-left on {@code screen} this frame, or empty when it is
     * hidden, not placed, or not on that screen. For drawing a sibling decoration
     * alongside the panel without re-deriving placement. The content area starts at
     * {@code origin + getPadding()}.
     */
    public Optional<ScreenOrigin> getOrigin(AbstractContainerScreen<?> screen) {
        PanelHost host = ScreenPanelRegistry.contextHost(screen);
        if (host == null) return Optional.empty();
        PanelHost.Placed placed = host.placedOf(panel);
        return placed == null ? Optional.empty() : Optional.of(new ScreenOrigin(placed.x(), placed.y()));
    }
}
