package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.MK;
import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;

import org.jspecify.annotations.Nullable;

/**
 * MenuKit's look or vanilla's, for every element: how controls draw, which
 * colour text takes, what a list or a divider looks like. The look changes how an
 * element draws, never its size or behaviour, so a screen can switch without its
 * layout moving.
 *
 * <h3>Variants</h3>
 *
 * <ul>
 *   <li><b>{@link #MK}</b>, MenuKit's own look, the default: raised grey
 *       controls on a light grey panel, dark text. Buttons switch to
 *       {@link PanelStyle#INSET} when pressed and {@link PanelStyle#DARK} when
 *       disabled.</li>
 *   <li><b>{@link #VANILLA}</b>, the look of the screens reached from the pause
 *       menu (Options, Controls): vanilla's button, checkbox and tab sprites,
 *       white text with a shadow, vanilla's separators and darker list
 *       background. Hover and pressed both use the highlighted button sprite
 *       (vanilla has no pressed sprite); MenuKit adds its pressed overlay.</li>
 * </ul>
 *
 * <h3>One switch, and the override (6.1.0)</h3>
 *
 * The look is set once, for a whole screen ({@code MKScreen.elementStyle}) or a
 * panel ({@code Panel.Builder.elementStyle}), and every element inside follows
 * it, down through sections, scroll areas, tabs, rows and any element built from
 * other elements. It travels with the frame in {@link RenderContext#style()}. An
 * element's own {@code style(...)} wins over what it inherits, and a container's
 * own style is what its children inherit. {@link #resolve} is the rule.
 *
 * <h3>Text by role</h3>
 *
 * {@link #textColor(TextRole)} and {@link #textShadow()}: dark grey without a
 * shadow on MenuKit's panel, vanilla's white and greys with a shadow on its
 * backdrop. Text drawn on a control (a button's label) stays white in both looks;
 * only its disabled grey follows the look.
 *
 * <h3>Where vanilla has no twin</h3>
 *
 * A radio button, an info box, a progress bar, a colour swatch and a consumer's
 * own sprites keep MenuKit's drawing in both looks. {@link Slider} and
 * {@link TextField} are vanilla's own widgets and look the same in both.
 */
public enum ControlStyle {
    MK,
    VANILLA;

    // ── The switch: what an element draws in ───────────────────────────

    /**
     * The look an element draws in: its own, when it was given one with
     * {@code style(...)}, else the one it inherits from its screen, panel or
     * container. The same rule decides a panel's look against its screen's.
     *
     * @param own       the element's (or panel's) own style, or {@code null} for none
     * @param inherited the style around it
     */
    public static ControlStyle resolve(@Nullable ControlStyle own, ControlStyle inherited) {
        return own != null ? own : inherited;
    }

    // ── Text by role ───────────────────────────────────────────────────

    /**
     * The colour of text in {@code role} on this look's surface. MenuKit's look:
     * dark grey ({@link ElementConstants#TEXT_DARK}) for headings and body,
     * {@link ElementConstants#TEXT_MUTED}, and {@link ElementConstants#TEXT_DISABLED}.
     * Vanilla's: its own {@code CommonColors}, white for headings and body (the
     * Options title, a setting's name), {@code GRAY} for secondary lines (the world
     * list's details) and {@code LIGHT_GRAY} for disabled text (an inactive
     * button's label).
     */
    public int textColor(TextRole role) {
        return switch (this) {
            case MK -> switch (role) {
                case HEADING, BODY -> ElementConstants.TEXT_DARK;
                case MUTED -> ElementConstants.TEXT_MUTED;
                case DISABLED -> ElementConstants.TEXT_DISABLED;
            };
            case VANILLA -> switch (role) {
                case HEADING, BODY -> CommonColors.WHITE;
                case MUTED -> CommonColors.GRAY;
                case DISABLED -> CommonColors.LIGHT_GRAY;
            };
        };
    }

    /** Whether text on this look's surface has a drop shadow: vanilla's does, MenuKit's container text does not. */
    public boolean textShadow() {
        return this == VANILLA;
    }

    // ── Vanilla button sprite atlas ────────────────────────────────────
    //
    // Identifiers match vanilla AbstractButton.SPRITES exactly, verified
    // against 26.2 bytecode (net.minecraft.client.gui.components
    // .AbstractButton's static initializer). The 3-sprite set is
    // (enabled, disabled, enabledFocused) per WidgetSprites' 3-arg
    // constructor.

    private static final Identifier VANILLA_ENABLED =
            Identifier.withDefaultNamespace("widget/button");
    private static final Identifier VANILLA_DISABLED =
            Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier VANILLA_HIGHLIGHTED =
            Identifier.withDefaultNamespace("widget/button_highlighted");

    /**
     * Picks the vanilla button sprite for a given interactive state.
     * Disabled takes priority (matches vanilla); otherwise highlighted
     * fires on hover OR pressed (vanilla doesn't distinguish those).
     */
    public static Identifier vanillaButtonSprite(boolean enabled,
                                                  boolean hoveredOrPressed) {
        if (!enabled) return VANILLA_DISABLED;
        if (hoveredOrPressed) return VANILLA_HIGHLIGHTED;
        return VANILLA_ENABLED;
    }

    /**
     * Renders a vanilla button-style background sprite at the given
     * bounds, automatically picking the right sprite per state.
     * Convenience used by {@link Button} and {@link Dropdown} when
     * {@link #VANILLA} is selected.
     *
     * <p>Wraps {@code GuiGraphicsExtractor.blitSprite} with the same
     * {@code RenderPipelines.GUI_TEXTURED} pipeline + white tint vanilla
     * uses. Caller is responsible for any state overlays (the vanilla
     * highlighted sprite IS the hover affordance, no additional overlay
     * needed, unlike the MK path).
     */
    public static void renderVanillaButton(GuiGraphicsExtractor graphics,
                                            int x, int y, int width, int height,
                                            boolean enabled,
                                            boolean hoveredOrPressed) {
        Identifier sprite = vanillaButtonSprite(enabled, hoveredOrPressed);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                x, y, width, height, 0xFFFFFFFF);
    }

    /**
     * Phase 18t follow-up, renders the "pressed/engaged" visual overlay
     * for a vanilla-styled control. Drawn on top of the existing button
     * sprite to communicate "this is being pressed (Button) or this
     * control is engaged (Dropdown trigger while popover open)."
     *
     * <p>Two layered effects:
     * <ul>
     *   <li><b>Inverted inner bevel</b>, vanilla's button sprite has a
     *       1px raised bevel (lighter top/left, darker bottom/right).
     *       The overlay swaps those: darker top/left, lighter
     *       bottom/right, visually reads as "sunken in."</li>
     *   <li><b>Translucent dark fill across the interior</b>, softens
     *       the button's brightness, reinforcing the pressed-in
     *       reading.</li>
     * </ul>
     *
     * <p>Drawn over the outer 1px frame (untouched) and over the
     * existing sprite's bevel pixels (replaced by the inverted
     * lines). The interior dark overlay covers the gradient region
     * inside the bevel.
     */
    public static void renderVanillaPressedOverlay(GuiGraphicsExtractor graphics,
                                                    int x, int y,
                                                    int width, int height) {
        // Inverted bevel: 1px inside the outer frame.
        // Top: dark (inverted from vanilla's light).
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF373737);
        // Left: dark.
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFF373737);
        // Bottom: light gray (inverted from vanilla's dark, NOT pure
        // white, which read as too harsh against the gray button
        // surface; light gray sits in the same family as vanilla's
        // own bevel highlights).
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF888888);
        // Right: light gray.
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xFF888888);

        // Translucent dark overlay across the interior (inside the
        // inverted bevel). Softens brightness, reinforces "pressed in."
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x40000000);
    }

    /**
     * Renders a vanilla-styled popover background for an open dropdown
     * (or any future overlay container that should match the VANILLA
     * trigger aesthetic). Uses {@code widget/button_disabled}, vanilla's
     * darker, more uniform gray gradient, so the popover reads as a
     * distinct surface beneath the trigger ({@code widget/button}'s
     * lighter gradient) without falling back to MK's RAISED panel look
     * (which would be jarring against the vanilla-styled trigger above).
     *
     * <p>9-slice handles the stretch from button-sized sprite to
     * popover-sized region; corners stay crisp, middle stretches
     * uniformly.
     */
    public static void renderVanillaPopoverBackground(GuiGraphicsExtractor graphics,
                                                       int x, int y,
                                                       int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_DISABLED,
                x, y, width, height, 0xFFFFFFFF);
    }
}
