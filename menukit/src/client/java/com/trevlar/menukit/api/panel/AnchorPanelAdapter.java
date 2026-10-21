package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Places a {@link Panel} on {@link Anchor}s: rectangles another library names and
 * supplies per screen (a slot group's box, the pause menu's button column). On every open
 * screen where an anchor's box is present, the anchor's host places its panels against that
 * box this frame.
 *
 * <pre>{@code
 * Panel row = Panel.builder("mymod:pause_row")
 *         .add(...)
 *         .in(ModeScope.BOTH, Placement.around(PAUSE_BUTTONS, PanelPosition.region(OutsideRegion.BOTTOM_CENTER)))
 *         .build();
 * new AnchorPanelAdapter(row);
 * }</pre>
 *
 * <h3>Placement is the panel's</h3>
 * The panel's placements name their anchors ({@link Placement#around},
 * {@link Placement#at}), one per mode scope, and the panel shows wherever the anchor its
 * current mode's placement names is on the open screen. Around an anchor it declares
 * {@code region(OutsideRegion)} (stacking with the anchor's other panels), {@code center()}
 * or {@code pixel(...)}. An anchor has no screen spots, so {@code screenAnchor},
 * {@code main()} and unplaced panels are rejected at construction.
 *
 * <h3>Targeting</h3>
 * The anchors are in the placements; nothing else is needed. The deprecated {@link #on}
 * puts the panel's screen placements around each anchor it names instead. Without
 * {@code on}, a {@link Placement#screen} on the panel has no anchor to be placed against:
 * the first screen open fails loudly, naming the panel and the scope, rather than the panel
 * never showing in that mode.
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
        this(panel, padding, null);
    }

    /**
     * Declares the panel for {@code modId}, or for the calling mod when it is {@code null}:
     * MenuKit's shared panels credit the mod whose contribution declares the panel.
     */
    AnchorPanelAdapter(Panel panel, int padding, @Nullable String modId) {
        Declarations.requireOpen("AnchorPanelAdapter(" + panel.id() + ")");
        PanelHost.requirePlacements(PanelHost.Kind.ANCHOR, panel);
        this.panel = panel;
        this.padding = padding;
        this.modId = modId != null ? modId : PanelHost.captureCallerModId();
        this.seq = PanelHost.nextSeq();
        ScreenPanelRegistry.declare(this);
    }

    /**
     * Places the panel's screen placements ({@link Placement#screen}) around each of
     * {@code anchors}, in their own mode scopes. Call at most once.
     *
     * @deprecated since 7.4.0, the anchor is part of the placement:
     *             {@code in(ModeScope.BOTH, Placement.around(anchor, position))} on the panel
     *             is {@code position(position)} with this call, and needs no {@code on}.
     */
    @Deprecated(since = "7.4.0", forRemoval = true)
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

    /** The anchors {@link #on} named, or {@code null} without it. */
    public @Nullable List<Anchor> getTargets() { return targets; }

    /** Whether {@link #on} was called. */
    public boolean isTargetingDeclared() { return targets != null; }

    @ApiStatus.Internal
    public String modId() { return modId; }

    @ApiStatus.Internal
    public int seq() { return seq; }
}
