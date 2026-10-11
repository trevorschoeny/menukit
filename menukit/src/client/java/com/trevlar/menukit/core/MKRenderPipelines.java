package com.trevlar.menukit.core;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * MenuKit's own {@link RenderPipeline}s for GUI rendering:
 * {@link #GUI_BRIGHTNESS_INVERTED}, the pressed look of a sprite button or sprite
 * toggle, and {@link #GUI_SILHOUETTE}, the coloured item outline
 * ({@code SlotRendering.drawItemOutline}).
 *
 * <p>Both copy vanilla's 26.3 {@code RenderPipelines.GUI_TEXTURED} setup
 * (GLOBALS, PROJECTION, DYNAMIC_TRANSFORMS and SAMPLER0 bind groups, TRANSLUCENT
 * colour target, POSITION_TEX_COLOR vertex binding, QUADS topology, no depth
 * state since GUI ordering is done by strata) with a custom fragment shader.
 *
 * <p>Locations are in the {@code menukit} namespace so they can't collide with
 * vanilla or other mods. Shaders live at
 * {@code assets/menukit/shaders/core/button_brightness_invert.vsh} and {@code .fsh}.
 *
 * <p>Initialising the pipelines in static fields is fine: the renderer compiles
 * them on first draw, not at class load.
 */
@ApiStatus.Internal
public final class MKRenderPipelines {

    private MKRenderPipelines() {}

    /**
     * GUI textured pipeline whose fragment shader inverts each pixel's HSL
     * lightness, keeping hue and saturation. Gives custom-sprite buttons their
     * pressed look (see {@code Button.SpriteButton.renderBackground}).
     */
    // The 26.2 builder: UBO and sampler declarations became bind-group layouts,
    // blend became a colour-target state, and vertex format and mode split into
    // vertex binding and primitive topology. There is no depth test, because
    // vanilla's GUI pipelines declare no depth state (ordering moved to render
    // strata). 26.3 split MATRICES_PROJECTION into PROJECTION and
    // DYNAMIC_TRANSFORMS (the two UBOs the shaders read) and put GLOBALS first,
    // in vanilla's order; SAMPLER0 carries the texture sampler.
    public static final RenderPipeline GUI_BRIGHTNESS_INVERTED = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("menukit", "pipeline/gui_brightness_inverted"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader(Identifier.fromNamespaceAndPath("menukit", "core/button_brightness_invert"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("menukit", "core/button_brightness_invert"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    /**
     * GUI textured pipeline whose fragment shader draws every visible pixel of
     * the texture in the vertex colour: a flat silhouette of a sprite. Used to
     * draw an item's outline from its own sprite in vanilla's item atlas
     * ({@code SlotRendering.drawItemOutline}). Same bind groups, vertex format and
     * blend as {@link #GUI_BRIGHTNESS_INVERTED}; the vertex stage is shared.
     */
    public static final RenderPipeline GUI_SILHOUETTE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("menukit", "pipeline/gui_silhouette"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader(Identifier.fromNamespaceAndPath("menukit", "core/button_brightness_invert"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("menukit", "core/item_silhouette"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();
}
