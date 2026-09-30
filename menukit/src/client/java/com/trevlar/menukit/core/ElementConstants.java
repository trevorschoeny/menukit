package com.trevlar.menukit.core;

/**
 * The colours and spacings MenuKit's elements share (6.0.0 plan, decision 5).
 * Each used to be declared again in every element that drew it, a
 * {@code 0xFF808080} disabled grey in five files and a {@code 0x30FFFFFF} hover
 * overlay in seven; one change now reaches every element.
 *
 * <p><b>ARGB (26.2).</b> Every colour carries an explicit alpha byte: vanilla's
 * text draw silently discards a colour whose alpha is zero.
 */
public final class ElementConstants {

    private ElementConstants() {}

    // ── Text ───────────────────────────────────────────────────────────

    /** Dark grey text, no shadow: vanilla's container-label colour on a light panel. */
    public static final int TEXT_DARK = 0xFF404040;

    /** White text, drawn with a shadow: on a dark panel, a button, a HUD. */
    public static final int TEXT_LIGHT = 0xFFFFFFFF;

    /**
     * Text of a disabled element, and all text inside a disabled panel or
     * container (the disabled look, §0066). Mid grey reads as "off" on both light
     * and dark backgrounds.
     */
    public static final int TEXT_DISABLED = 0xFF808080;

    // ── Overlays ───────────────────────────────────────────────────────

    /** Translucent white filled inside a control's border while hovered or focused. */
    public static final int HOVER_OVERLAY = 0x30FFFFFF;

    /** Translucent black over a sprite-drawn control while it is disabled. */
    public static final int DISABLED_OVERLAY = 0x80000000;

    // ── Spacing ────────────────────────────────────────────────────────

    /** Horizontal padding each side of a label drawn on a control (a button, a labelled toggle). */
    public static final int LABEL_PAD = 6;

    /** Vertical padding above and below a label that wrapped onto several lines. */
    public static final int LABEL_VPAD = 4;

    /** The square of a checkbox or radio button. */
    public static final int BOX_SIZE = 10;

    /** Space between that square and its label. */
    public static final int BOX_GAP = 4;
}
