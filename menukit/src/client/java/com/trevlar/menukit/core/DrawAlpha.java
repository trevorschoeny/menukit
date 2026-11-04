package com.trevlar.menukit.core;

import net.minecraft.util.ARGB;

import org.jetbrains.annotations.ApiStatus;

/**
 * An opacity every GUI draw takes while it is set (7.6.0): what fades a panel under a
 * {@link com.trevlar.menukit.api.panel.PanelTransform}. Vanilla's GUI draws have no alpha of
 * their own, so {@code MKDrawAlphaMixin} multiplies it into the color of each fill, sprite,
 * tiled sprite and text as it is recorded. Items have no color to multiply and stay opaque.
 *
 * <p>Nests: {@link #push} multiplies into what is set and returns it for {@link #pop}.
 * Render thread only.
 */
@ApiStatus.Internal
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
