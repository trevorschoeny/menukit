package com.trevlar.menukit.api;

import net.minecraft.util.ARGB;

/**
 * An opacity every GUI draw takes while it is set (8.0.0, used by
 * {@link com.trevlar.menukit.api.panel.PanelTransform}'s panel fade). Vanilla's GUI draws have
 * no alpha of their own, so {@code MKDrawAlphaMixin} multiplies it into the color of each fill,
 * sprite, tiled sprite and text as it is recorded while {@link #push} is in effect. Items have
 * no color to multiply and stay opaque; a mod fading a decoration drawn beside an item copy
 * (a count, a durability bar, an {@link ItemOutline#silhouettes} outline) gets the fade by
 * wrapping that draw in {@link #push}/{@link #pop}. The item itself stays opaque either way.
 *
 * <p>Nests: {@link #push} multiplies into what is set and returns it for {@link #pop}.
 * Render thread only.
 */
public final class DrawAlpha {

    private DrawAlpha() {}

    private static float alpha = 1f;

    /** Multiplies {@code a} into the opacity in effect. @return the opacity before, for {@link #pop} */
    public static float push(float a) {
        float before = alpha;
        alpha = before * a;
        return before;
    }

    /** Puts back the opacity {@link #push} returned. */
    public static void pop(float before) {
        alpha = before;
    }

    /** {@code argb} under the opacity in effect: untouched at full opacity. */
    public static int apply(int argb) {
        return alpha >= 1f ? argb : ARGB.multiplyAlpha(argb, alpha);
    }
}
