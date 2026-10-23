package com.trevlar.menukit.api.element;

import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A timed change between two states, kept per key (Fluorite): one duration and one
 * {@link Easing}, and for each key the mod names (a slot, a container, a tab) where that key
 * is on its way. Each frame the mod says which way each key is going and gets its eased
 * progress back, 0 at the start and 1 at the end:
 *
 * <pre>{@code
 * static final Transition OPENING = Transition.of(250, Easing.EASE_OUT);
 *
 * float t = OPENING.toward(boxKey, isOpen(boxKey));
 * }</pre>
 *
 * <ul>
 *   <li><b>Forward</b> ({@code toward(key, true)}) plays toward 1 and <b>holds</b> there.</li>
 *   <li><b>Back</b> ({@code toward(key, false)}) plays toward 0, in reverse, the same curve
 *       run backwards. A key at rest at 0 is forgotten, so a key never seen and a key played
 *       all the way back read alike.</li>
 *   <li><b>Turning mid-way</b> starts back from where it had got to, not from an end.</li>
 * </ul>
 *
 * <p>Progress is a pure function of when the key last turned and now, on vanilla's clock
 * ({@code Util.getMillis()}, the clock every MenuKit animation reads), so it keeps running
 * whatever draws or doesn't in between, and a key keeps its place across screens for as long
 * as the mod keeps the same {@code Transition} and passes an equal key. Keys are compared
 * with {@code equals}: a record of the things that name the key works, made fresh each frame.
 * A key held forward is kept until it plays back, so pass keys from a bounded set.
 *
 * <p>Render thread only.
 */
public final class Transition {

    private final int millis;
    private final Easing easing;

    /** Where a key is going: linear progress {@code from} when it turned at {@code at}, toward 1 or 0. */
    private record Run(float from, long at, boolean forward) {}

    private final Map<Object, Run> runs = new HashMap<>();

    private Transition(int millis, Easing easing) {
        this.millis = millis;
        this.easing = easing;
    }

    /**
     * A transition that takes {@code millis} from one end to the other, eased by {@code easing}.
     *
     * @throws IllegalArgumentException when {@code millis} is not positive
     */
    public static Transition of(int millis, Easing easing) {
        if (millis <= 0) throw new IllegalArgumentException("Transition: millis must be positive, got " + millis);
        return new Transition(millis, Objects.requireNonNull(easing, "easing"));
    }

    /**
     * {@code key}'s eased progress now, going forward (toward 1) or back (toward 0). Call it
     * every frame the key is drawn; the first forward call starts it from 0.
     */
    public float toward(Object key, boolean forward) {
        return toward(key, forward, Util.getMillis());
    }

    /**
     * {@link #toward(Object, boolean)} at {@code nowMillis}, on {@code Util.getMillis()}'s
     * scale: for a mod running its own clock, and for checks.
     */
    public float toward(Object key, boolean forward, long nowMillis) {
        long now = nowMillis;
        Objects.requireNonNull(key, "key");
        Run run = runs.get(key);
        if (run == null) {
            if (!forward) return easing.apply(0f);
            run = new Run(0f, now, true);
            runs.put(key, run);
        } else if (run.forward() != forward) {
            run = new Run(linear(run, now), now, forward);
            runs.put(key, run);
        }
        float p = linear(run, now);
        if (!forward && p == 0f) runs.remove(key);
        return easing.apply(p);
    }

    /** {@code run}'s linear progress at {@code now}, held at the end it went to. */
    private float linear(Run run, long now) {
        float moved = (float) (now - run.at()) / millis;
        float p = run.forward() ? run.from() + moved : run.from() - moved;
        return Math.max(0f, Math.min(1f, p));
    }
}
