package com.trevorschoeny.menukit.core;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * MenuKit-owned {@link RenderPipeline}s for GUI rendering. Currently holds
 * one pipeline: {@link #GUI_BRIGHTNESS_INVERTED}, used by
 * {@code Button.sprite(...)}'s pressed-state visual.
 *
 * <p>Config mirrors vanilla's {@code RenderPipelines.GUI_TEXTURED}
 * (POSITION_TEX_COLOR vertex format, TRANSLUCENT blend, NO_DEPTH_TEST,
 * DynamicTransforms + Projection UBOs, Sampler0) with the fragment-shader
 * stage swapped to a custom shader that inverts each pixel's HSL lightness
 * channel while preserving hue and saturation.
 *
 * <p>The location identifier ({@code menukit:pipeline/gui_brightness_inverted})
 * is namespace-scoped to avoid colliding with vanilla or other-mod pipelines.
 * Shaders live at {@code assets/menukit/shaders/core/button_brightness_invert.vsh}
 * and {@code .fsh}.
 *
 * <p>Class is loaded lazily on first reference; static-field-initialized
 * pipeline is fine because compilation is triggered by Mojang's renderer
 * on first draw, not at class-load time.
 */
@ApiStatus.Internal
public final class MenuKitRenderPipelines {

    private MenuKitRenderPipelines() {}

    /**
     * GUI textured pipeline whose fragment shader inverts the per-pixel
     * HSL lightness (hue + saturation preserved). Drives the "pressed"
     * affordance for custom-sprite buttons — see
     * {@code Button.SpriteButton.renderBackground}.
     */
    public static final RenderPipeline GUI_BRIGHTNESS_INVERTED = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("menukit", "pipeline/gui_brightness_inverted"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexShader(Identifier.fromNamespaceAndPath("menukit", "core/button_brightness_invert"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("menukit", "core/button_brightness_invert"))
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
}
