package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.element.ElementConstants;
import com.trevlar.menukit.api.element.TextRole;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.CommonColors;

import org.jspecify.annotations.Nullable;

/**
 * The look of the screen an element is on, which decides how it draws (6.1.0;
 * renamed in 7.3.0). There is one set of elements; each draws itself for the look
 * of the screen it is on. No element, panel or screen picks a look any other way.
 *
 * <ul>
 *   <li><b>{@link #PANEL}</b>, the Panel look: inventories, chests, anything built on
 *       vanilla's {@link AbstractContainerScreen}. MenuKit's own look: raised gray
 *       controls on a light gray panel, dark text without a shadow.</li>
 *   <li><b>{@link #DEFAULT}</b>, the Default look: the screens reached from the pause
 *       menu and the title screen (vanilla's Options, Video Settings, Controls, Create
 *       World). Vanilla's look there: its stone buttons, checkbox, tab and slot
 *       sprites, white text with a shadow, its separators and darker list background,
 *       over the blurred world or the panorama.</li>
 * </ul>
 *
 * <h3>Renamed in 7.3.0</h3>
 *
 * The looks were {@code OPTIONS} (now {@link #DEFAULT}) and {@code CONTAINER} (now
 * {@link #PANEL}). The old names stay until 8.0.0 as deprecated aliases: static
 * fields holding the same two constants, so a mod built against 7.2.0 keeps working,
 * and {@code ScreenKind.OPTIONS == ScreenKind.DEFAULT}. They are not enum constants:
 * {@link #values()} lists only the two looks, and a {@code switch} names the new ones.
 *
 * <h3>Where the look comes from ({@link #of})</h3>
 *
 * <ul>
 *   <li>A panel injected into a vanilla screen: the host knows. A vanilla
 *       {@link AbstractContainerScreen} has the Panel look, any other vanilla screen
 *       the Default look, so a panel on Controls gets the Default look with no code.</li>
 *   <li>An anchor's panels: the look of the screen the anchor is on.</li>
 *   <li>A standalone {@link MKScreen}: the look it declared when it was created
 *       ({@code super(title, ScreenKind.DEFAULT, panels)}), {@link #PANEL} when it
 *       declared none, which is how every standalone screen looked before 6.1.0.</li>
 *   <li>The HUD: {@link #PANEL}, the look it has always had.</li>
 *   <li>A dialog or overlay follows the screen it opens on, since it is a panel on
 *       that screen; so does the dialog an {@code ItemPicker} opens.</li>
 * </ul>
 *
 * <h3>What the look changes</h3>
 *
 * Drawing only, never size or behavior: a panel's background, every control's
 * sprite and every text color. A piece with no vanilla twin (a radio button, a
 * color swatch, an info box, a consumer's own sprite) keeps MenuKit's drawing in
 * both looks; only its text follows the look.
 */
public enum ScreenKind {
    /** The Panel look: inventories, chests, anything built on {@link AbstractContainerScreen}. MenuKit's gray. */
    PANEL,
    /** The Default look: vanilla's Options, Controls, Create World and the like. The pause-menu look. */
    DEFAULT;

    // The 7.2.0 names. Enum constants can't alias each other, so these are plain static
    // fields holding the same instances: bytecode that reads ScreenKind.OPTIONS (a getstatic)
    // resolves to DEFAULT, and == against either name agrees. Removed in 8.0.0.

    /** @deprecated Renamed {@link #DEFAULT} in 7.3.0; removed in 8.0.0. */
    @Deprecated(since = "7.3.0", forRemoval = true)
    public static final ScreenKind OPTIONS = DEFAULT;

    /** @deprecated Renamed {@link #PANEL} in 7.3.0; removed in 8.0.0. */
    @Deprecated(since = "7.3.0", forRemoval = true)
    public static final ScreenKind CONTAINER = PANEL;

    /**
     * The look of {@code screen}. Every host on a screen, and every screen MenuKit
     * opens over another, uses this. A standalone {@link MKScreen} is the look it
     * declared; any other {@link AbstractContainerScreen} is {@link #PANEL}; any other
     * screen is {@link #DEFAULT}. No screen (the HUD, the game itself) is {@link #PANEL}.
     */
    public static ScreenKind of(@Nullable Screen screen) {
        if (screen == null) return PANEL;
        // Checked first: an MKScreen is a plain Screen, and its own declaration wins.
        if (screen instanceof MKScreen mk) return mk.screenKind();
        if (screen instanceof AbstractContainerScreen<?>) return PANEL;
        return DEFAULT;
    }

    /**
     * The color of text in {@code role} in this look. Panel: dark gray
     * ({@link ElementConstants#TEXT_DARK}) for headings and body,
     * {@link ElementConstants#TEXT_MUTED}, {@link ElementConstants#TEXT_DISABLED}. An
     * element's own text on a panel surface follows the surface's fill from these
     * ({@code AbstractPanelElement.textColor}).
     * Default: vanilla's own {@code CommonColors}, white for headings and body (the
     * Options title, a setting's name), {@code GRAY} for secondary lines (the world
     * list's details) and {@code LIGHT_GRAY} for disabled text (an inactive
     * button's label).
     */
    public int textColor(TextRole role) {
        return switch (this) {
            case PANEL -> switch (role) {
                case HEADING, BODY -> ElementConstants.TEXT_DARK;
                case MUTED -> ElementConstants.TEXT_MUTED;
                case DISABLED -> ElementConstants.TEXT_DISABLED;
            };
            case DEFAULT -> switch (role) {
                case HEADING, BODY -> CommonColors.WHITE;
                case MUTED -> CommonColors.GRAY;
                case DISABLED -> CommonColors.LIGHT_GRAY;
            };
        };
    }

    /**
     * Whether text in this look has a drop shadow: the Default look's does (vanilla's
     * menu text), the Panel look's does not (vanilla's container labels).
     */
    public boolean textShadow() {
        return this == DEFAULT;
    }
}
