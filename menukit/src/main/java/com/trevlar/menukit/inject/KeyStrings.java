package com.trevlar.menukit.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * The text grammar for saved slot group and set ids: parts joined by {@code |},
 * each part escaped so a {@code |} inside an id can never split it
 * ({@code %} becomes {@code %25}, then {@code |} becomes {@code %7C}; reading
 * undoes them in the opposite order). Readable in a config file, unambiguous for
 * any id a mod chooses.
 */
final class KeyStrings {

    private KeyStrings() {}

    static String join(String... parts) {
        StringJoiner out = new StringJoiner("|");
        for (String p : parts) out.add(escape(p));
        return out.toString();
    }

    static List<String> split(String text) {
        Objects.requireNonNull(text, "text");
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\\|", -1)) out.add(unescape(raw));
        return out;
    }

    private static String escape(String s) {
        return s.replace("%", "%25").replace("|", "%7C");
    }

    private static String unescape(String s) {
        return s.replace("%7C", "|").replace("%25", "%");
    }
}
