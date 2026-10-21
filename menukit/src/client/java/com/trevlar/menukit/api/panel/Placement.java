package com.trevlar.menukit.api.panel;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Where a panel goes in one mode (7.4.0): what it is placed against, and its position there.
 * A panel declares one for each {@link ModeScope} it shows in, with
 * {@link Panel.Builder#in(ModeScope, Placement)}, and the two modes' placements can be
 * entirely different: a screen region in survival and beside an anchor in creative, say.
 *
 * <ul>
 *   <li>{@link #screen(PanelPosition)}: against the host's own reference, as every
 *       {@code position(...)} was before: the menu frame on a container screen, the screen
 *       on any other, the game window or the hotbar on the HUD, a standalone screen's main
 *       panel.</li>
 *   <li>{@link #around(Anchor, PanelPosition)}: in the regions around an {@link Anchor}'s
 *       box, stacking with the anchor's other panels: what an {@link AnchorPanelAdapter} did
 *       with {@code on(anchor)}.</li>
 *   <li>{@link #at(Anchor, int, int)}: at the anchor's box's top-left plus an offset, at the
 *       panel's natural size. No stacking, no room budget, and nothing keeps it on the
 *       screen: two panels at one spot overlap, and one past the edge is cut.</li>
 * </ul>
 *
 * A placement with an anchor shows wherever that anchor's box is on the open screen: the
 * box is its presence. An anchor with no box this frame (its thing not on this menu) leaves
 * the panel out, and it claims nothing.
 *
 * <p>Placements are values: two built the same way are equal.
 *
 * @param anchor   the anchor the panel is placed against, or {@code null} for the host's own reference
 * @param position where, against it
 */
public record Placement(@Nullable Anchor anchor, PanelPosition position) {

    /**
     * The origin an {@link #at} placement's position carries: the anchor's box's top-left,
     * which the host fills in. Asked directly it gives nothing, so the panel is skipped
     * rather than put anywhere else.
     */
    private static final Supplier<ScreenOrigin> BOX_ORIGIN = () -> null;

    /**
     * A placement against {@code anchor}, or the host's own reference without one.
     *
     * @throws IllegalArgumentException for an unplaced position: a panel with no placement declares none instead
     */
    public Placement {
        Objects.requireNonNull(position, "position");
        if (!position.isPlaced()) {
            throw new IllegalArgumentException("A Placement needs a position; a panel that has none declares no placement");
        }
    }

    /** Against the host's own reference: the menu frame, the screen, the HUD, a standalone screen's main panel. */
    public static Placement screen(PanelPosition position) {
        return new Placement(null, position);
    }

    /**
     * In the regions around {@code anchor}'s box: {@code region(OutsideRegion)} stacks with
     * its other panels there; {@code center()} and {@code pixel(...)} work as anywhere.
     */
    public static Placement around(Anchor anchor, PanelPosition position) {
        return new Placement(Objects.requireNonNull(anchor, "anchor"), position);
    }

    /**
     * At {@code anchor}'s box's top-left plus {@code (dx, dy)}, at the panel's natural size:
     * no stacking, no room budget, no keeping it on the screen. Negative offsets go left and
     * up of the box.
     */
    public static Placement at(Anchor anchor, int dx, int dy) {
        return new Placement(Objects.requireNonNull(anchor, "anchor"), PanelPosition.pixel(BOX_ORIGIN).offset(dx, dy));
    }

    /** Whether {@code position} is an {@link #at} placement's: its origin is its anchor's box's top-left. */
    @ApiStatus.Internal
    public static boolean fromBox(PanelPosition position) {
        return position.pixelOrigin() == BOX_ORIGIN;
    }
}
