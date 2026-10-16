package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.element.ElementConstants;
import com.trevlar.menukit.api.element.TextRole;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.CommonColors;

import org.jspecify.annotations.Nullable;

/**
 * The kind of screen an element is on, which decides how it draws (6.1.0). There
 * is one set of elements; each draws itself for the kind of screen it is on. No
 * element, panel or screen picks a look any other way.
 *
 * <ul>
 *   <li><b>{@link #CONTAINER}</b>: inventories, chests, anything built on
 *       vanilla's {@link AbstractContainerScreen}. MenuKit's own look: raised grey
 *       controls on a light grey panel, dark text without a shadow.</li>
 *   <li><b>{@link #OPTIONS}</b>: the screens reached from the pause menu and the
 *       title screen (Options, Video Settings, Controls, Create World). Vanilla's
 *       look there: its stone buttons, checkbox, tab and slot sprites, white text
 *       with a shadow, its separators and darker list background, over the blurred
 *       world or the panorama.</li>
 * </ul>
 *
 * <h3>Where the kind comes from ({@link #of})</h3>
 *
 * <ul>
 *   <li>A panel injected into a vanilla screen: the host knows. An
 *       {@link AbstractContainerScreen} is a container screen, any other vanilla
 *       screen an options screen, so a panel on Controls gets the options look with
 *       no code.</li>
 *   <li>An anchor's panels: the kind of the screen the anchor is on.</li>
 *   <li>A standalone {@link MKScreen}: the kind it declared when it was created
 *       ({@code super(title, ScreenKind.OPTIONS, panels)}), {@link #CONTAINER} when it
 *       declared none, which is how every standalone screen looked before 6.1.0.</li>
 *   <li>The HUD: {@link #CONTAINER}, the look it has always had.</li>
 *   <li>A dialog or overlay follows the screen it opens on, since it is a panel on
 *       that screen; so does the dialog an {@code ItemPicker} opens.</li>
 * </ul>
 *
 * <h3>What the kind changes</h3>
 *
 * Drawing only, never size or behaviour: a panel's background, every control's
 * sprite and every text colour. A piece with no vanilla twin (a radio button, a
 * colour swatch, an info box, a consumer's own sprite) keeps MenuKit's drawing on
 * both kinds; only its text follows the kind.
 */
public enum ScreenKind {
    /** Inventories, chests, anything built on {@link AbstractContainerScreen}: MenuKit's grey look. */
    CONTAINER,
    /** Options, Controls, Create World and the like: the pause-menu look. */
    OPTIONS;

    /**
     * The kind of {@code screen}. Every host on a screen, and every screen MenuKit
     * opens over another, uses this. A standalone {@link MKScreen} is the
     * kind it declared; any other container screen is {@link #CONTAINER}; any other
     * screen is {@link #OPTIONS}. No screen (the HUD, the game itself) is
     * {@link #CONTAINER}.
     */
    public static ScreenKind of(@Nullable Screen screen) {
        if (screen == null) return CONTAINER;
        // Checked first: an MKScreen is a plain Screen, and its own declaration wins.
        if (screen instanceof MKScreen mk) return mk.screenKind();
        if (screen instanceof AbstractContainerScreen<?>) return CONTAINER;
        return OPTIONS;
    }

    /**
     * The colour of text in {@code role} on this kind's surface. Container: dark
     * grey ({@link ElementConstants#TEXT_DARK}) for headings and body,
     * {@link ElementConstants#TEXT_MUTED}, {@link ElementConstants#TEXT_DISABLED}.
     * Options: vanilla's own {@code CommonColors}, white for headings and body (the
     * Options title, a setting's name), {@code GRAY} for secondary lines (the world
     * list's details) and {@code LIGHT_GRAY} for disabled text (an inactive
     * button's label).
     */
    public int textColor(TextRole role) {
        return switch (this) {
            case CONTAINER -> switch (role) {
                case HEADING, BODY -> ElementConstants.TEXT_DARK;
                case MUTED -> ElementConstants.TEXT_MUTED;
                case DISABLED -> ElementConstants.TEXT_DISABLED;
            };
            case OPTIONS -> switch (role) {
                case HEADING, BODY -> CommonColors.WHITE;
                case MUTED -> CommonColors.GRAY;
                case DISABLED -> CommonColors.LIGHT_GRAY;
            };
        };
    }

    /**
     * Whether text on this kind's surface has a drop shadow: an options screen's
     * does (vanilla's menu text), a container's does not (vanilla's container
     * labels).
     */
    public boolean textShadow() {
        return this == OPTIONS;
    }
}
