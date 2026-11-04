package com.trevlar.menukit.api.panel;

/**
 * How a panel draws this frame, apart from where it is laid out (7.6.0): scaled by
 * {@code scale} about the screen point {@code (pivotX, pivotY)}, in GUI px, and at
 * {@code alpha} opacity. A mod hands {@link Panel#transform} a supplier of these and
 * changes what it returns each frame, to grow a panel out of a point and fade it in.
 *
 * <p>Everything the panel draws takes the scale: its background, its elements, and the
 * slots it presents with their items. The alpha takes the fills, sprites and text; items
 * (a slot's stack, an {@code Icon} of an item) still draw opaque, since vanilla draws an
 * item with no color of its own to fade. Hide them meanwhile with a
 * {@link SlotItemLooks} look if they would show.
 *
 * <p>While a transform isn't {@link #IDENTITY} the panel takes no input: it claims no
 * point, its elements see no hover, click, wheel or key, and its slots are never hovered
 * or clicked, so a click meant for a panel still growing goes nowhere rather than to the
 * wrong place.
 *
 * @param scale  1 for the laid-out size; above 0
 * @param pivotX screen x the scale is about, in GUI px
 * @param pivotY screen y the scale is about, in GUI px
 * @param alpha  1 for opaque, 0 for invisible; clamped into that range
 */
public record PanelTransform(float scale, float pivotX, float pivotY, float alpha) {

    /** As laid out: full size and opaque. */
    public static final PanelTransform IDENTITY = new PanelTransform(1f, 0f, 0f, 1f);

    public PanelTransform {
        if (!(scale > 0f)) throw new IllegalArgumentException("scale must be above 0, was " + scale);
        alpha = Math.clamp(alpha, 0f, 1f);
    }

    /** Whether this draws the panel as laid out (the pivot doesn't matter at scale 1). */
    public boolean isIdentity() {
        return scale == 1f && alpha == 1f;
    }
}
