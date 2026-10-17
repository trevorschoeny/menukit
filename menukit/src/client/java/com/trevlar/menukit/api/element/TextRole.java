package com.trevlar.menukit.api.element;

/**
 * What a piece of text is for, so the kind of screen decides its colour (6.1.0).
 * A label names its role; {@link com.trevlar.menukit.api.panel.ScreenKind#textColor}
 * turns the role into a colour for the surface that kind draws on: dark in the
 * Panel look's grey panel, white and greys on a Default-look screen's dark
 * backdrop.
 *
 * <pre>{@code
 * TextLabel.builder().text(Component.literal("Sorting")).role(TextRole.HEADING).build();
 * TextLabel.builder().text(Component.literal("Applies to chests too")).role(TextRole.MUTED).build();
 * }</pre>
 *
 * <p>A label with no role is {@link #BODY}. A colour given with
 * {@code color(argb)} wins over the role, and a disabled element draws in the
 * {@link #DISABLED} colour whatever its role.
 */
public enum TextRole {
    /** A title or a section's name. */
    HEADING,
    /** Ordinary text: a setting's name, a line of explanation. */
    BODY,
    /** Secondary text: a summary, a hint, a note under a setting. */
    MUTED,
    /** Text of something that is off right now. */
    DISABLED
}
