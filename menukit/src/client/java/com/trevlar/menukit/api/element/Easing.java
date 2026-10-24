package com.trevlar.menukit.api.element;

/**
 * An easing curve (7.4.0): eased progress for a linear progress {@code t}, both from 0 to
 * 1. A {@link Transition} runs one; any function of {@code t} with {@code f(0) = 0} and
 * {@code f(1) = 1} is one, so a mod can pass its own as a lambda.
 */
@FunctionalInterface
public interface Easing {

    /** Eased progress for linear progress {@code t}, which is between 0 and 1. */
    float apply(float t);

    /** Even speed. */
    Easing LINEAR = t -> t;

    /** Fast, then settling: a cubic ease-out. */
    Easing EASE_OUT = t -> {
        float u = 1f - t;
        return 1f - u * u * u;
    };

    /** Slow at both ends: a cubic ease-in-out. */
    Easing EASE_IN_OUT = t -> t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;

    /** Past the end and back, a small spring: the ease-out-back MenuKit's hotbar slides use. */
    Easing EASE_OUT_BACK = t -> {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    };
}
