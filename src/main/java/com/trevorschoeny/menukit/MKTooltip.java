package com.trevorschoeny.menukit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder for multi-line, styled tooltips.
 *
 * <p>Usage:
 * <pre>{@code
 * List<Component> tooltip = MKTooltip.builder()
 *     .title("Sort Inventory")
 *     .line("Sorts items by type and count")
 *     .line("Hold Shift for reverse", ChatFormatting.GRAY)
 *     .blank()
 *     .keybind("R")
 *     .build();
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public final class MKTooltip {

    private MKTooltip() {} // utility class

    /** Creates a new tooltip builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a single-line tooltip (convenience). */
    public static List<Component> of(String text) {
        return List.of(Component.literal(text));
    }

    /** Creates a single-line tooltip with formatting (convenience). */
    public static List<Component> of(String text, ChatFormatting... formatting) {
        return List.of(Component.literal(text).withStyle(formatting));
    }

    public static final class Builder {
        private final List<Component> lines = new ArrayList<>();

        private Builder() {}

        /** Adds a title line (white, no formatting). */
        public Builder title(String text) {
            lines.add(Component.literal(text));
            return this;
        }

        /** Adds a title line with a Component. */
        public Builder title(Component component) {
            lines.add(component);
            return this;
        }

        /** Adds a description line (gray by default). */
        public Builder line(String text) {
            lines.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
            return this;
        }

        /** Adds a description line with custom formatting. */
        public Builder line(String text, ChatFormatting... formatting) {
            lines.add(Component.literal(text).withStyle(formatting));
            return this;
        }

        /** Adds a line from a Component. */
        public Builder line(Component component) {
            lines.add(component);
            return this;
        }

        /** Adds a blank separator line. */
        public Builder blank() {
            lines.add(Component.empty());
            return this;
        }

        /** Adds a keybind hint line (e.g., "[R] to activate"). Rendered in dark gray. */
        public Builder keybind(String key) {
            lines.add(Component.literal("[" + key + "]").withStyle(ChatFormatting.DARK_GRAY));
            return this;
        }

        /** Adds a keybind hint with description (e.g., "[R] Sort inventory"). */
        public Builder keybind(String key, String description) {
            MutableComponent line = Component.literal("[" + key + "] ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(description).withStyle(ChatFormatting.GRAY));
            lines.add(line);
            return this;
        }

        /** Adds a warning line (yellow). */
        public Builder warning(String text) {
            lines.add(Component.literal(text).withStyle(ChatFormatting.YELLOW));
            return this;
        }

        /** Adds an error line (red). */
        public Builder error(String text) {
            lines.add(Component.literal(text).withStyle(ChatFormatting.RED));
            return this;
        }

        /** Builds the tooltip as an unmodifiable list of Components. */
        public List<Component> build() {
            return Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }
}
