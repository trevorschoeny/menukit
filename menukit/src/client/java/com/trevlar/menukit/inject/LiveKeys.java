package com.trevlar.menukit.inject;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What a list of live keys has made, kept in step with the keys (Fluorite): the one rule a
 * keyed list of tabs ({@code ContainerTabs.keyed}) and a kind of section per key
 * ({@code ContainerRegion.sections}) both follow. Render thread only.
 */
@ApiStatus.Internal
final class LiveKeys {

    private LiveKeys() {}

    /**
     * {@code was}, each key's value in the keys' order, brought to {@code keys}: a key that is new
     * gets {@code make}'s value, one that is gone takes its value with it, one that stays keeps
     * the one it had. Keys compare with {@code equals}, {@code null} among them; a repeated key
     * has one value, the first. A {@code null} list is no keys. A key whose {@code make} throws
     * keeps a {@code null} value, so it isn't made again every frame, and is logged once as
     * {@code what} of {@code whose}.
     *
     * @return the new map, or {@code null} when the keys are the ones {@code was} has
     */
    static <V> @Nullable Map<Object, @Nullable V> resync(Map<Object, @Nullable V> was, @Nullable List<?> keys,
                                                        Function<Object, V> make, String what, String whose) {
        List<?> now = keys == null ? List.of() : keys;
        // ponytail: a keys list with a repeated key never matches, so it rebuilds the map each frame; the values are kept either way.
        if (was.size() == now.size() && new ArrayList<>(was.keySet()).equals(now)) return null;
        Map<Object, @Nullable V> out = new LinkedHashMap<>();
        for (Object k : now) {
            if (out.containsKey(k)) continue;
            if (was.containsKey(k)) {
                out.put(k, was.get(k));
                continue;
            }
            V made = null;
            try {
                made = make.apply(k);
            } catch (RuntimeException e) {
                Guard.failed(make, what, whose, e);
            }
            out.put(k, made);
        }
        return out;
    }
}
