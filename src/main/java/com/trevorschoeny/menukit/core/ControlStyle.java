package com.trevorschoeny.menukit.core;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Visual style for INTERACTIVE controls — currently {@link Button} and
 * {@link Dropdown}/{@link DropdownMulti}. Distinct from {@link PanelStyle}
 * because panels (containers) and controls (interactive primitives) are
 * different architectural categories — a panel that "looks like a button"
 * miscommunicates that it's clickable, and PanelStyle has no notion of
 * the per-state sprite swaps (hover, disabled) that vanilla button
 * rendering needs.
 *
 * <h3>Variants</h3>
 *
 * <ul>
 *   <li><b>{@link #MK}</b> — the existing MenuKit look. Buttons use a
 *       {@link PanelStyle#RAISED} background with a translucent hover
 *       overlay, switch to {@link PanelStyle#INSET} when pressed and
 *       {@link PanelStyle#DARK} when disabled. Dropdown triggers use
 *       {@code RAISED}. Default when no style is specified — existing
 *       consumers see no change.</li>
 *   <li><b>{@link #VANILLA}</b> — vanilla Minecraft's button sprite
 *       atlas ({@code widget/button}, {@code widget/button_highlighted},
 *       {@code widget/button_disabled}). Square corners, textured gray
 *       gradient. Matches the look of vanilla menu screens (Options,
 *       Controls, Pause). Hover and pressed both use the highlighted
 *       sprite (vanilla doesn't have a separate pressed visual).</li>
 * </ul>
 *
 * <h3>Scope</h3>
 *
 * Applied to the interactive control's BACKGROUND only. Text rendering
 * (label color, font, alignment) is unaffected — use {@link MKText} for
 * that. For {@link Dropdown} and {@link DropdownMulti}, a single
 * {@code .style(...)} on the builder applies to BOTH the trigger and the
 * popover — VANILLA trigger gets {@code widget/button}; VANILLA popover
 * gets {@code widget/button_disabled} (vanilla's darker uniform gray)
 * so the popover reads as a distinct surface beneath the trigger.
 * Consumers don't separately style the popover.
 */
public enum ControlStyle {
    MK,
    VANILLA;

    // ── Vanilla button sprite atlas ────────────────────────────────────
    //
    // Identifiers match vanilla AbstractButton.SPRITES exactly — verified
    // from 1.21.11 bytecode (net.minecraft.client.gui.components
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
     * <p>Wraps {@code GuiGraphics.blitSprite} with the same
     * {@code RenderPipelines.GUI_TEXTURED} pipeline + white tint vanilla
     * uses. Caller is responsible for any state overlays (the vanilla
     * highlighted sprite IS the hover affordance — no additional overlay
     * needed, unlike the MK path).
     */
    public static void renderVanillaButton(GuiGraphics graphics,
                                            int x, int y, int width, int height,
                                            boolean enabled,
                                            boolean hoveredOrPressed) {
        Identifier sprite = vanillaButtonSprite(enabled, hoveredOrPressed);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                x, y, width, height, 0xFFFFFFFF);
    }

    /**
     * Renders a vanilla-styled popover background for an open dropdown
     * (or any future overlay container that should match the VANILLA
     * trigger aesthetic). Uses {@code widget/button_disabled} — vanilla's
     * darker, more uniform gray gradient — so the popover reads as a
     * distinct surface beneath the trigger ({@code widget/button}'s
     * lighter gradient) without falling back to MK's RAISED panel look
     * (which would be jarring against the vanilla-styled trigger above).
     *
     * <p>9-slice handles the stretch from button-sized sprite to
     * popover-sized region; corners stay crisp, middle stretches
     * uniformly.
     */
    public static void renderVanillaPopoverBackground(GuiGraphics graphics,
                                                       int x, int y,
                                                       int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_DISABLED,
                x, y, width, height, 0xFFFFFFFF);
    }
}
