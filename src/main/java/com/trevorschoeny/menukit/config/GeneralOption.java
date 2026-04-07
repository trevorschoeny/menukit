package com.trevorschoeny.menukit.config;

/**
 * A typed descriptor for a family-wide general config option.
 * Carries the key, default value, and expected Java type so that
 * {@link MKFamily#getGeneral} and {@link MKFamily#setGeneral} can
 * return/accept the correct type without casts at the call site.
 *
 * <p>Handles GSON number normalization internally: GSON deserializes
 * all JSON numbers as {@code Double}, so when the expected type is
 * {@code Integer} or {@code Float}, the stored {@code Double} is
 * converted automatically.
 *
 * <p>Usage:
 * <pre>{@code
 * // Define once (static final is ideal)
 * public static final GeneralOption<Boolean> SHOW_BUTTON =
 *     new GeneralOption<>("show_button", true, Boolean.class);
 *
 * // Read
 * boolean show = family.getGeneral(SHOW_BUTTON);
 *
 * // Write
 * family.setGeneral(SHOW_BUTTON, false);
 * }</pre>
 *
 * @param key          unique identifier used as the JSON key in config storage
 * @param defaultValue returned when the key has no stored value
 * @param type         the expected Java type (Boolean.class, Integer.class, etc.)
 * @param <T>          the option value type
 */
public record GeneralOption<T>(String key, T defaultValue, Class<T> type) {

    /**
     * Coerces a raw value (typically from GSON deserialization) to the
     * expected type. Returns {@link #defaultValue} if the value is null
     * or not coercible.
     *
     * <p>Why this exists: GSON's default deserializer turns all JSON
     * numbers into {@code Double}. So a stored {@code 3} comes back as
     * {@code 3.0}. This method handles that conversion for Integer and
     * Float targets, plus identity casts for Boolean and String.
     */
    @SuppressWarnings("unchecked")
    public T coerce(Object raw) {
        if (raw == null) return defaultValue;

        // Fast path: already the right type (Boolean, String, exact Number match)
        if (type.isInstance(raw)) return type.cast(raw);

        // GSON number normalization: Double -> Integer or Float
        if (raw instanceof Number n) {
            if (type == Integer.class) return (T) Integer.valueOf(n.intValue());
            if (type == Float.class)   return (T) Float.valueOf(n.floatValue());
            if (type == Double.class)  return (T) Double.valueOf(n.doubleValue());
            if (type == Long.class)    return (T) Long.valueOf(n.longValue());
        }

        // Not coercible — fall back to default rather than crash
        return defaultValue;
    }
}
