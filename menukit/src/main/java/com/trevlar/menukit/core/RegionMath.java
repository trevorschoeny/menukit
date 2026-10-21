package com.trevlar.menukit.core;

import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenOrigin;

import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Coordinate math for panel regions. Given the anchor frame's bounds (or the
 * screen size), the panel's size and the current stacking {@code prefix}, it
 * returns the panel's screen-space origin, or {@link Optional#empty()} when the
 * panel does not fit.
 *
 * <p>Pure: no registry state, no Panel references, no side effects, so
 * {@code /mkverify all} can run the math on made-up inputs without opening a
 * screen.
 *
 * <p>Two resolvers cover every placement: {@link #resolveMenu} for an
 * {@link OutsideRegion} around a reference rectangle, and {@link #resolveInside}
 * for an {@link InsideRegion} spot on the screen. {@link #resolveHud} is
 * {@link #resolveMenu} for the HUD's reference, the hotbar: no safe-area clamp, and
 * it starts beyond the offhand slot on the side vanilla draws it. The panel hosts
 * ({@code com.trevlar.menukit.inject.PanelHost}) supply the inputs and treat
 * {@link Optional#empty()} as "not placed this frame".
 */
@ApiStatus.Internal
public final class RegionMath {

    private RegionMath() {}

    // Available width: how much outer width a panel may take before it crosses
    // the screen-edge margin, given how it is anchored. These three cover every
    // anchor shape, and each placement context (menu, HUD, vanilla screen,
    // centered screen, anchor) calls the one that matches the way it grows.
    // None takes the panel's width, so measuring never depends on placing: every
    // directional region pins one edge and grows into the budget.

    /** Room for a panel whose left edge is pinned at {@code originX} and which
     *  grows rightward, before the right screen-edge margin. */
    public static int growRightWidth(int originX, int sw, int margin) {
        return sw - margin - originX;
    }

    /** Room for a panel whose right edge is pinned at {@code rightEdgeX} and
     *  which grows leftward, before the left screen-edge margin. */
    public static int growLeftWidth(int rightEdgeX, int margin) {
        return rightEdgeX - margin;
    }

    /** Room for a panel centered on {@code centerX} that must stay clear of
     *  both screen-edge margins. Symmetric about the center, so the nearer edge
     *  decides. */
    public static int centeredWidth(int centerX, int sw, int margin) {
        return 2 * Math.min(centerX - margin, sw - margin - centerX);
    }

    // Available height: the same three, turned 90 degrees. A panel anchored above
    // or below the frame works out how much outer height it may take before
    // crossing the top or bottom margin, then scrolls within that instead of
    // running off-screen.

    /** Room for a panel whose top edge is pinned at {@code originY} and which
     *  grows downward, before the bottom screen-edge margin. */
    public static int growDownHeight(int originY, int sh, int margin) {
        return sh - margin - originY;
    }

    /** Room for a panel whose bottom edge is pinned at {@code bottomEdgeY} and
     *  which grows upward, before the top screen-edge margin. */
    public static int growUpHeight(int bottomEdgeY, int margin) {
        return bottomEdgeY - margin;
    }

    /** Room for a panel centered on {@code centerY} that must stay clear of
     *  both screen-edge margins. Symmetric about the center, so the nearer edge
     *  decides. */
    public static int centeredHeight(int centerY, int sh, int margin) {
        return 2 * Math.min(centerY - margin, sh - margin - centerY);
    }

    /**
     * Outer height (padding included) a menu region panel has before it crosses
     * the screen-edge margin, given the menu frame (extended by its chrome) and
     * the screen height. The height counterpart of {@link #availableMenuWidth}. It
     * follows {@link #resolveMenu}'s per-region Y geometry, so a too-tall panel
     * scrolls within exactly the room its anchor leaves toward the screen edge,
     * instead of rendering off-screen and then being clamped over the frame. The
     * caller subtracts twice the panel's padding to get the content-height limit
     * for {@link com.trevlar.menukit.api.panel.Panel#setAvailableContentHeight}.
     *
     * <p>As in {@link #availableMenuWidth}, the stacking prefix is ignored: the
     * budget is for the region's anchor edge. That over-estimates for the second
     * and later panels in a vertical stack; a single panel and horizontal-flow
     * regions are exact.
     *
     * @param gap the panel's gap off the frame, as {@link #resolveMenu} takes it
     */
    public static int availableMenuHeight(OutsideRegion region, Reference b,
                                          int sh, int margin, int gap) {
        int topPos = b.topPos();
        int imageHeight = b.imageHeight();
        int frameTop = topPos;
        int frameBottom = topPos + imageHeight;
        int centerY = topPos + imageHeight / 2;
        return switch (region) {
            // Above the frame: grows up toward the top margin.
            case TOP_CENTER, TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT ->
                    growUpHeight(frameTop - gap, margin);
            // Below the frame: grows down toward the bottom margin.
            case BOTTOM_CENTER, BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT ->
                    growDownHeight(frameBottom + gap, sh, margin);
            // Pinned at the frame top: grows down toward the bottom margin.
            case RIGHT_ALIGN_TOP, LEFT_ALIGN_TOP ->
                    growDownHeight(frameTop, sh, margin);
            // Pinned at the frame bottom: grows up toward the top margin.
            case RIGHT_ALIGN_BOTTOM, LEFT_ALIGN_BOTTOM ->
                    growUpHeight(frameBottom, margin);
            // Beside the frame, centred on it: clear of both margins.
            case LEFT_CENTER, RIGHT_CENTER -> centeredHeight(centerY, sh, margin);
            // CENTER stays within the frame; frame height is the safe ceiling.
            case CENTER -> Math.min(imageHeight, centeredHeight(centerY, sh, margin));
        };
    }

    /**
     * The frame band (since 6.1.0): the outer height (padding included) a panel in a side
     * region has beside the screen's frame, from its anchored edge to the frame's far
     * edge. A vertical Flow wraps its columns there ({@code Panel.setFrameHeight}).
     *
     * <ul>
     *   <li>{@code RIGHT_ALIGN_TOP}, {@code LEFT_ALIGN_TOP}: the panel's top is the
     *       reference's top plus the siblings stacked above it; the band runs down to
     *       the frame's bottom ({@code topPos + imageHeight}).</li>
     *   <li>{@code RIGHT_ALIGN_BOTTOM}, {@code LEFT_ALIGN_BOTTOM}: the mirror image,
     *       from the panel's bottom up to the frame's top.</li>
     *   <li>{@code RIGHT_CENTER}, {@code LEFT_CENTER}: centred on the reference's middle,
     *       as far as both of the frame's ends allow, so a wrapped column stays centred
     *       and inside the frame.</li>
     *   <li>Every other region is above, below or inside the frame, with no frame
     *       beside it: {@code -1}.</li>
     * </ul>
     *
     * The reference and the frame differ for an anchor panel: it is placed against
     * the anchor's box but its band runs to the container screen's frame, so a column
     * beside the player inventory wraps where the screen ends, not where the box
     * does. On a container screen's own host they are the same frame. Never negative
     * for a side region (a panel already past the frame's edge has a band of 0, and a
     * wrapping element then puts one child per column).
     *
     * @param region    the panel's region
     * @param reference what the region resolves around (the frame, or an anchor's box)
     * @param frame     the screen's frame the band runs to
     * @param prefix    the extent of the siblings stacked before the panel in the region,
     *                  gaps included, as {@link #resolveMenu} takes it
     */
    public static int frameBandHeight(OutsideRegion region, Reference reference, Reference frame, int prefix) {
        int frameTop = frame.topPos();
        int frameBottom = frame.topPos() + frame.imageHeight();
        return switch (region) {
            case RIGHT_ALIGN_TOP, LEFT_ALIGN_TOP ->
                    Math.max(0, frameBottom - (reference.topPos() + prefix));
            case RIGHT_ALIGN_BOTTOM, LEFT_ALIGN_BOTTOM ->
                    Math.max(0, reference.topPos() + reference.imageHeight() - prefix - frameTop);
            case RIGHT_CENTER, LEFT_CENTER -> {
                int middle = reference.topPos() + reference.imageHeight() / 2;
                yield Math.max(0, 2 * Math.min(middle - frameTop, frameBottom - middle));
            }
            default -> -1;
        };
    }

    /**
     * Outer width (padding included) a menu region panel has before it crosses
     * the screen-edge margin, given the menu frame (extended by its chrome) and
     * the screen width. It follows {@link #resolveMenu}'s per-region anchor
     * geometry, so the budget and the origin agree on where the panel sits. The
     * caller subtracts twice the panel's padding to get the content budget for
     * {@link com.trevlar.menukit.api.panel.Panel#setAvailableContentWidth}.
     *
     * <p>Horizontal-flow regions (TOP_ALIGN, BOTTOM_ALIGN) ignore the stacking
     * prefix: the budget is for the region's anchor edge, which over-estimates for
     * the second and later panels in a horizontal stack. That case is rare; a
     * single panel and every vertical-flow region are exact.
     *
     * @param gap the panel's gap off the frame, as {@link #resolveMenu} takes it
     */
    public static int availableMenuWidth(OutsideRegion region, Reference b,
                                         int sw, int margin, int gap) {
        int leftPos = b.leftPos();
        int imageWidth = b.imageWidth();
        int centerX = leftPos + imageWidth / 2;
        return switch (region) {
            case RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM, RIGHT_CENTER ->
                    growRightWidth(leftPos + imageWidth + gap, sw, margin);
            case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM, LEFT_CENTER ->
                    growLeftWidth(leftPos - gap, margin);
            case TOP_ALIGN_LEFT, BOTTOM_ALIGN_LEFT ->
                    growRightWidth(leftPos, sw, margin);
            case TOP_ALIGN_RIGHT, BOTTOM_ALIGN_RIGHT ->
                    growLeftWidth(leftPos + imageWidth, margin);
            case TOP_CENTER, BOTTOM_CENTER ->
                    centeredWidth(centerX, sw, margin);
            // CENTER stays within the frame (resolveMenu rejects pw > imageWidth);
            // the frame is itself on-screen, so frame width is the safe ceiling.
            case CENTER -> Math.min(imageWidth, centeredWidth(centerX, sw, margin));
        };
    }

    /**
     * Outer width available to a screen-edge-anchored panel (an
     * {@link InsideRegion} spot in any context). Every spot is inset from one edge
     * and should keep the same inset from the opposite edge, so the budget is the
     * screen width minus the inset on both sides.
     */
    public static int availableScreenEdgeWidth(int sw, int inset) {
        return sw - 2 * inset;
    }

    // InsideRegion: one resolver, parameterised by the context's insets.

    /**
     * How a context places {@link InsideRegion} spots. This is the only thing that
     * differs between the HUD, a vanilla screen and a standalone screen's chrome,
     * which all share one resolver.
     *
     * @param edge         inset from each screen edge a spot touches
     * @param gap          stacking gap between siblings on one spot, for a panel that
     *                     declares none ({@code PanelPosition.gapOr})
     * @param belowCenter  when positive, {@link InsideRegion#CENTER} flows down from
     *                     this far below the screen centre (the HUD clears the
     *                     crosshair); when zero, CENTER is centred like the other
     *                     middle-row spots
     * @param hideOverflow when true, a panel that does not fit its spot's axis is not
     *                     placed ({@link Optional#empty()}); when false it always
     *                     resolves and a too-big panel overhangs the far edge
     */
    public record Insets(int edge, int gap, int belowCenter, boolean hideOverflow) {
        /** The HUD: 4 px in, 2 px stacking, CENTER below the crosshair, hide on overflow. */
        public static final Insets HUD = new Insets(RegionConstants.EDGE_INSET,
                RegionConstants.MENU_STACK_GAP, RegionConstants.CENTER_CROSSHAIR_CLEARANCE, true);
        /** A vanilla screen that is not a container: 4 px in, 4 px stacking, no crosshair, hide on overflow. */
        public static final Insets SCREEN = new Insets(RegionConstants.EDGE_INSET,
                RegionConstants.SCREEN_STACK_GAP, 0, true);
        /** Screen chrome on a standalone or container screen: the safe-area margin, always placed. */
        public static final Insets CHROME = new Insets(RegionConstants.SCREEN_EDGE_MARGIN,
                RegionConstants.MENU_STACK_GAP, 0, false);
        /** A notification: HUD geometry, but always shown (a toast never silently vanishes). */
        public static final Insets NOTIFICATION = new Insets(RegionConstants.EDGE_INSET,
                RegionConstants.MENU_STACK_GAP, RegionConstants.CENTER_CROSSHAIR_CLEARANCE, false);
    }

    /**
     * Places a {@code pw x ph} panel (padding-inclusive) on an {@link InsideRegion}
     * spot of an {@code sw x sh} screen, {@code prefix} pixels along the spot's
     * stacking axis from its anchor edge. Every spot stacks vertically: the top row
     * grows down, the bottom row grows up, the middle row grows down from its
     * centred start.
     *
     * <p>The X axis: LEFT spots pin to the inset, CENTER spots centre, RIGHT spots
     * pin to the right inset. The Y axis mirrors it; the middle row is centred on the
     * screen, except {@link InsideRegion#CENTER} with {@code insets.belowCenter() > 0}
     * (the HUD), which starts that far below the centre so it clears the crosshair.
     *
     * <p>Returns empty only when {@code insets.hideOverflow()} and the stack would run
     * past the spot's available height (screen height minus both insets, halved for
     * the middle row).
     */
    public static Optional<ScreenOrigin> resolveInside(InsideRegion region,
            int sw, int sh, int pw, int ph, int prefix, Insets insets) {
        int e = insets.edge();
        boolean hudCenter = region == InsideRegion.CENTER && insets.belowCenter() > 0;
        if (insets.hideOverflow()) {
            int available = switch (region) {
                case TOP_LEFT, TOP_CENTER, TOP_RIGHT,
                     BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> sh - 2 * e;
                case LEFT_CENTER, RIGHT_CENTER -> sh / 2 - e;
                case CENTER -> hudCenter ? sh / 2 - insets.belowCenter() - e : sh / 2 - e;
            };
            if (prefix + ph > available) return Optional.empty();
        }
        int x = switch (region) {
            case TOP_LEFT, LEFT_CENTER, BOTTOM_LEFT -> e;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (sw - pw) / 2;
            case TOP_RIGHT, RIGHT_CENTER, BOTTOM_RIGHT -> sw - pw - e;
        };
        int y = switch (region) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> e + prefix;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> sh - ph - e - prefix;
            case LEFT_CENTER, RIGHT_CENTER -> (sh - ph) / 2 + prefix;
            case CENTER -> hudCenter
                    ? sh / 2 + insets.belowCenter() + prefix
                    : (sh - ph) / 2 + prefix;
        };
        return Optional.of(new ScreenOrigin(x, y));
    }

    // Menu regions. Each panel brings its own gap (PanelPosition.gap, since 7.1.0;
    // RegionConstants.MENU_STACK_GAP when it declares none), the same in the menu,
    // anchor and HUD contexts, so the gap is a parameter here and never a constant.

    /**
     * Whether {@code region}'s gap off the reference lies along its stacking axis
     * (since 7.1.0). True only for the four regions centred on a side (TOP_CENTER,
     * BOTTOM_CENTER, LEFT_CENTER, RIGHT_CENTER), which stack straight away from the
     * frame: their first panel's gap off the frame and every later panel's gap off its
     * sibling are the same axis, so the room they need beside the frame is their sum.
     * Every other region stacks along the frame edge, so its gap off the frame is
     * across the stack (a RIGHT_ALIGN_TOP panel's x) and its first panel starts flush
     * with the aligned end. CENTER does not stack.
     */
    public static boolean gapAlongStack(OutsideRegion region) {
        return switch (region) {
            case TOP_CENTER, BOTTOM_CENTER, LEFT_CENTER, RIGHT_CENTER -> true;
            default -> false;
        };
    }

    /**
     * The {@code prefix} {@link #resolveMenu} (or {@link #resolveHud}) takes for the
     * next panel in {@code region} (since 7.1.0), given where the stack so far ends
     * and the next panel's own {@code gap}.
     *
     * <p>The gap is the space before the panel. For the first panel in a region
     * ({@code stackEnd} null) the prefix is 0: the resolver itself puts the gap
     * between the panel and the reference. For a later panel the gap goes between
     * it and the sibling before it: added here for the regions that stack along
     * the frame edge, and by the resolver for the four side-centred regions, whose
     * frame gap is already on the stacking axis (which is why
     * {@link #stackEnd} counts their gap into the stack instead).
     *
     * @param stackEnd where the region's stack ends, from {@link #stackEnd}, or
     *                 {@code null} when nothing is stacked there yet this frame
     * @param gap      the next panel's gap ({@code PanelPosition.gapOr})
     */
    public static int stackPrefix(OutsideRegion region, @Nullable Integer stackEnd, int gap) {
        if (stackEnd == null) return 0;
        return gapAlongStack(region) ? stackEnd : stackEnd + gap;
    }

    /**
     * Where {@code region}'s stack ends once a panel {@code axial} pixels long on the
     * stacking axis has been placed at {@code prefix} with {@code gap} (since 7.1.0):
     * the {@code stackEnd} the next {@link #stackPrefix} call takes. For the four
     * side-centred regions it includes the panel's gap, because the resolver adds that gap on
     * the stacking axis and the next panel must start beyond it.
     */
    public static int stackEnd(OutsideRegion region, int prefix, int axial, int gap) {
        return prefix + axial + (gapAlongStack(region) ? gap : 0);
    }

    /**
     * Resolves a menu region panel's origin. A CENTER panel larger than the
     * frame returns {@link Optional#empty()}. Any other region is clamped into
     * the screen's safe area, and returns empty only when the panel is larger
     * than the safe area itself.
     *
     * @param region  the region the panel belongs to
     * @param bounds  the vanilla menu's container-frame bounds this frame
     * @param pw      the panel's width (from {@link Panel#getWidth()})
     * @param ph      the panel's height (from {@link Panel#getHeight()})
     * @param prefix  how far along the stacking axis the panel starts, from
     *                {@link #stackPrefix}: 0 for the first panel in the region, and for
     *                a later one the visible preceding panels' extent plus the gaps
     *                between them
     * @param gap     the panel's own gap ({@code PanelPosition.gapOr}, since 7.1.0):
     *                its distance off the reference on the side the region sits
     * @param sw      GUI-scaled screen width, for the safe-area check (edge
     *                regions extend toward the screen edge, not the menu edge)
     * @param sh      GUI-scaled screen height
     */
    public static Optional<ScreenOrigin> resolveMenu(
            OutsideRegion region, Reference bounds,
            int pw, int ph, int prefix, int gap, int sw, int sh) {

        // CENTER sits inside the frame (modal dialogs centered in the menu), so it
        // must fit the frame. Edge regions are checked after the origin is
        // computed, against the screen's safe area (below): they exist to extend
        // past the narrow menu frame toward the screen edge, so the menu's size is
        // the wrong limit. Checking against it once made BOTTOM_ALIGN_RIGHT hide
        // any panel wider than 176 px.
        if (region == OutsideRegion.CENTER) {
            if (pw > bounds.imageWidth() || ph > bounds.imageHeight()) return Optional.empty();
        }

        ScreenOrigin origin = outsideOrigin(region, bounds, pw, ph, prefix, gap);

        // Edge regions stay inside the screen's safe area: a panel that would cross
        // SCREEN_EDGE_MARGIN is clamped back inside rather than hidden. It may then
        // overlap the menu frame, which is right: a wide or tall panel that doesn't
        // fit beside the frame belongs on screen, over the frame, not gone. It is
        // hidden only when it is larger than the safe area itself, with nowhere on
        // screen to put it.
        if (region != OutsideRegion.CENTER) {
            int m = RegionConstants.SCREEN_EDGE_MARGIN;
            if (pw > sw - 2 * m || ph > sh - 2 * m) return Optional.empty();
            int cx = Math.max(m, Math.min(origin.x(), sw - m - pw));
            int cy = Math.max(m, Math.min(origin.y(), sh - m - ph));
            origin = new ScreenOrigin(cx, cy);
        }
        return Optional.of(origin);
    }

    /**
     * The vanilla hotbar on an {@code sw x sh} GUI (since 7.1.0): 182 x 22, centred,
     * flush with the bottom edge, where {@code Hud} draws {@code hud/hotbar}. The HUD's
     * reference, so an {@link OutsideRegion} on the HUD means "around the hotbar".
     */
    public static Reference hotbar(int sw, int sh) {
        return new Reference(sw / 2 - HOTBAR_WIDTH / 2, sh - HOTBAR_HEIGHT, HOTBAR_WIDTH, HOTBAR_HEIGHT);
    }

    /** The vanilla hotbar sprite's size, in GUI pixels. */
    public static final int HOTBAR_WIDTH = 182, HOTBAR_HEIGHT = 22;

    /**
     * The width of the gap between the hearts and the hunger bar, in GUI pixels: each
     * row is 81 px wide, flush with one end of the 182 px hotbar, so 20 px are left
     * in the middle.
     */
    public static final int STATUS_COLUMN_WIDTH = HOTBAR_WIDTH - 2 * 81;

    /**
     * The base of the HUD's status column on an {@code sw x sh} GUI: a rectangle
     * {@link #STATUS_COLUMN_WIDTH} wide, centred on the hotbar: the column's fixed
     * origin. {@code TOP_CENTER} stacks its panels up from the top, the first one
     * {@link RegionConstants#MENU_STACK_GAP} (2 px) above it.
     *
     * <p>With the status bars shown ({@code statusBars}: survival and adventure, where
     * {@code Hud} draws hearts, hunger and the XP bar), the first panel rests right on
     * the XP level number, level with the hearts and armour rows: the number is drawn
     * at {@code sh - 24 - 9 - 2} with a 1 px outline, so its top is {@code sh - 36}, and
     * the base sits the default gap below that. The column stays there whether the
     * number shows or not (it hides at level 0), so it doesn't jump when the player's
     * first level arrives. Without them (creative, spectator) the base is the hotbar's
     * top.
     */
    public static Reference statusColumn(int sw, int sh, boolean statusBars) {
        int top = statusBars ? sh - 36 + RegionConstants.MENU_STACK_GAP : sh - HOTBAR_HEIGHT;
        return new Reference(sw / 2 - STATUS_COLUMN_WIDTH / 2, top, STATUS_COLUMN_WIDTH, sh - top);
    }

    /**
     * The vanilla offhand slot's sprite width, in GUI pixels (since 7.1.0). While the
     * offhand holds something, {@code Hud.extractItemHotbar} draws
     * {@code hud/hotbar_offhand_left} (29 x 24) at {@code hotbarLeft - 29}, or
     * {@code hud/hotbar_offhand_right} at {@code hotbarRight}: flush against the
     * hotbar's edge, a 22 px slot frame on the far side and 7 transparent pixels
     * between it and the bar. So the slot's outer edge is 29 px beyond the hotbar's.
     */
    public static final int OFFHAND_SLOT_WIDTH = 29;

    /**
     * Places a {@code pw x ph} panel around the HUD's reference, the hotbar (since
     * 7.1.0). The same geometry as {@link #resolveMenu} (outside the box, {@code gap} off
     * it, stacked {@code prefix} away from the aligned end) without the safe-area clamp:
     * the hotbar sits on the window's bottom edge, so clamping would lift every panel
     * aligned to its bottom 6 px off where it belongs. A panel that would leave the
     * window instead is not placed, as an {@link InsideRegion} spot on the HUD that
     * overflows is not.
     *
     * <p>Offhand-aware: {@code offhand} is the side vanilla draws the offhand slot on
     * this frame ({@link HumanoidArm#LEFT} when the main arm is right, {@code RIGHT}
     * when it is left), or {@code null} while it draws none (the offhand is empty,
     * or a spectator's hotbar is up). The two side regions on that side
     * ({@code LEFT_ALIGN_*} or {@code RIGHT_ALIGN_*}) then start {@code gap} beyond the
     * slot instead of {@code gap} beyond the hotbar, so an icon there never covers it.
     * Every other region, including the TOP and BOTTOM ones that only run along the
     * hotbar, and the side regions on the far side, is placed as before. Every HUD
     * panel in an OutsideRegion comes through here, so no caller decides this.
     */
    public static Optional<ScreenOrigin> resolveHud(OutsideRegion region, Reference hotbar,
                                                    @Nullable HumanoidArm offhand,
                                                    int pw, int ph, int prefix, int gap, int sw, int sh) {
        ScreenOrigin o = outsideOrigin(region, hotbar, pw, ph, prefix, gap);
        // Push the panel out past the offhand slot when it is drawn on this
        // region's side: left regions move further left, right regions further
        // right, by the slot sprite's whole width. The stack inside the region is
        // untouched (the shift is the same for every panel in it).
        int shift = offhandShift(region, offhand);
        if (shift != 0) o = new ScreenOrigin(o.x() + shift, o.y());
        boolean onScreen = o.x() >= 0 && o.y() >= 0 && o.x() + pw <= sw && o.y() + ph <= sh;
        return onScreen ? Optional.of(o) : Optional.empty();
    }

    /**
     * How far a panel in {@code region} moves sideways so it starts beyond the
     * offhand slot drawn on {@code offhand}'s side: {@code -29} for a left region
     * with the slot on the left, {@code +29} for a right region with it on the
     * right, {@code 0} otherwise (no slot, the other side, or a region above or
     * below the hotbar).
     */
    private static int offhandShift(OutsideRegion region, @Nullable HumanoidArm offhand) {
        if (offhand == null) return 0;
        boolean leftRegion = switch (region) {
            case LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM, LEFT_CENTER -> true;
            default -> false;
        };
        boolean rightRegion = switch (region) {
            case RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM, RIGHT_CENTER -> true;
            default -> false;
        };
        if (leftRegion && offhand == HumanoidArm.LEFT) return -OFFHAND_SLOT_WIDTH;
        if (rightRegion && offhand == HumanoidArm.RIGHT) return OFFHAND_SLOT_WIDTH;
        return 0;
    }

    /**
     * Where a {@code pw x ph} panel sits around {@code bounds} in {@code region},
     * {@code prefix} pixels along its stacking axis from the aligned end and
     * {@code gap} pixels off the reference, before any clamping: the one geometry
     * every outside placement shares. For the four side-centred regions the gap and
     * the prefix are on the same axis, so both add up (see {@link #stackPrefix}).
     */
    private static ScreenOrigin outsideOrigin(OutsideRegion region, Reference bounds,
                                              int pw, int ph, int prefix, int gap) {
        int leftPos = bounds.leftPos();
        int topPos = bounds.topPos();
        int imageWidth = bounds.imageWidth();
        int imageHeight = bounds.imageHeight();

        return switch (region) {
            case RIGHT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos + imageWidth + gap,
                    topPos + prefix);
            case RIGHT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos + imageWidth + gap,
                    topPos + imageHeight - ph - prefix);
            case LEFT_ALIGN_TOP -> new ScreenOrigin(
                    leftPos - pw - gap,
                    topPos + prefix);
            case LEFT_ALIGN_BOTTOM -> new ScreenOrigin(
                    leftPos - pw - gap,
                    topPos + imageHeight - ph - prefix);
            case TOP_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos - ph - gap);
            case TOP_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos - ph - gap);
            case BOTTOM_ALIGN_LEFT -> new ScreenOrigin(
                    leftPos + prefix,
                    topPos + imageHeight + gap);
            case BOTTOM_ALIGN_RIGHT -> new ScreenOrigin(
                    leftPos + imageWidth - pw - prefix,
                    topPos + imageHeight + gap);
            // TOP_CENTER and BOTTOM_CENTER: centered horizontally, stacking
            // vertically away from the frame. X is centered on the frame as for
            // CENTER; Y follows TOP_ALIGN and BOTTOM_ALIGN (above or below the
            // frame by the panel's gap, offset by the prefix).
            case TOP_CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos - ph - gap - prefix);
            case BOTTOM_CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos + imageHeight + gap + prefix);
            // LEFT_CENTER and RIGHT_CENTER: the same turned on their side, centred
            // vertically and stacking horizontally away from the frame.
            case LEFT_CENTER -> new ScreenOrigin(
                    leftPos - pw - gap - prefix,
                    topPos + (imageHeight - ph) / 2);
            case RIGHT_CENTER -> new ScreenOrigin(
                    leftPos + imageWidth + gap + prefix,
                    topPos + (imageHeight - ph) / 2);
            // CENTER: centered in the menu's frame. One position, so several CENTER
            // panels overlap; the consumer shows one at a time (modal dialogs).
            case CENTER -> new ScreenOrigin(
                    leftPos + (imageWidth - pw) / 2,
                    topPos + (imageHeight - ph) / 2);
        };
    }
}
