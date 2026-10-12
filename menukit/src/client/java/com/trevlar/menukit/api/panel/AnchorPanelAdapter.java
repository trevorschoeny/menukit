package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Places a {@link Panel} around an {@link Anchor}: a rectangle another library names
 * and supplies per screen (a slot group's box, the pause menu's button column). On
 * every open screen where the anchor's box is present, the anchor's host places its
 * panels around that box this frame.
 *
 * <pre>{@code
 * Panel row = Panel.builder("mymod:pause_row")
 *         .add(...)
 *         .position(PanelPosition.region(OutsideRegion.BOTTOM_CENTER))
 *         .build();
 * new AnchorPanelAdapter(row).on(PAUSE_BUTTONS);
 * }</pre>
 *
 * <h3>Placement is the panel's</h3>
 * The panel declares {@code region(OutsideRegion)} (around the anchor's box),
 * {@code center()} or {@code pixel(...)}. An anchor has no screen spots, so
 * {@code screenAnchor}, {@code main()} and unplaced panels are rejected at
 * construction.
 *
 * <h3>Targeting is required</h3>
 * {@link #on}, exactly once, with at least one anchor. An adapter with no targeting
 * fails loudly at the first screen open. The panel appears once per targeted anchor
 * present on the screen, and panels on one anchor stack in its regions.
 *
 * <h3>Claims</h3>
 * An anchored panel claims points by the same rule as every other panel: an opaque
 * one over a slot makes that slot inert (its hover, click and tooltip).
 *
 * <h3>Frozen after init</h3>
 * Construction, targeting and {@link #unregister()} throw once MenuKit's declarations
 * freeze at client start. Gate at runtime with {@link Panel#visibleWhen}.
 */
public final class AnchorPanelAdapter {

    /** Default content padding for styled panels. */
    public static final int DEFAULT_PADDING = ScreenPanelAdapter.DEFAULT_PADDING;

    private final Panel panel;
    private final int padding;
    private final String modId;
    private final int seq;

    /** Declared target anchors; null until targeting is declared. */
    private @Nullable List<Anchor> targets = null;

    /**
     * Declares the panel with its own interior padding ({@code 0} for
     * {@link PanelStyle#NONE}, {@link #DEFAULT_PADDING} otherwise).
     *
     * @throws IllegalArgumentException if an anchor cannot place the panel's position
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public AnchorPanelAdapter(Panel panel) {
        this(panel, panel.interiorPadding());
    }

    /**
     * Declares the panel with explicit content padding.
     *
     * @throws IllegalArgumentException if an anchor cannot place the panel's position
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public AnchorPanelAdapter(Panel panel, int padding) {
        Declarations.requireOpen("AnchorPanelAdapter(" + panel.id() + ")");
        PanelHost.requireSupported(PanelHost.Kind.ANCHOR, panel, panel.getPosition());
        this.panel = panel;
        this.padding = padding;
        this.modId = PanelHost.captureCallerModId();
        this.seq = PanelHost.nextSeq();
        ScreenPanelRegistry.declare(this);
    }

    /** Places the panel around each of {@code anchors}. Call exactly once. */
    public AnchorPanelAdapter on(Anchor... anchors) {
        Declarations.requireOpen("AnchorPanelAdapter(" + panel.id() + ") targeting");
        if (targets != null) {
            throw new IllegalStateException("AnchorPanelAdapter for panel '" + panel.id()
                    + "' already declared targeting. Call .on(...) exactly once.");
        }
        if (anchors.length == 0) {
            throw new IllegalArgumentException("AnchorPanelAdapter for panel '" + panel.id()
                    + "': .on() requires at least one anchor.");
        }
        this.targets = List.of(anchors);
        return this;
    }

    /**
     * Withdraws this adapter from screens opened from now on. Idempotent. Throws after
     * MenuKit's declarations froze; gate at runtime with {@link Panel#visibleWhen}.
     */
    public void unregister() {
        Declarations.requireOpen("AnchorPanelAdapter(" + panel.id() + ").unregister()");
        ScreenPanelRegistry.withdraw(this);
    }

    /** The panel this adapter declares. */
    public Panel getPanel() { return panel; }

    /** The content padding inside the panel's edge. */
    public int getPadding() { return padding; }

    /** The declared target anchors, or {@code null} before targeting. */
    public @Nullable List<Anchor> getTargets() { return targets; }

    /** Whether targeting was declared. */
    public boolean isTargetingDeclared() { return targets != null; }

    @ApiStatus.Internal
    public String modId() { return modId; }

    @ApiStatus.Internal
    public int seq() { return seq; }
}
