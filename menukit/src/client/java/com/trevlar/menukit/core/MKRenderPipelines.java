package com.trevlar.menukit.core;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * MenuKit-owned {@link RenderPipeline}s for GUI rendering. Currently holds
 * one pipeline: {@link #GUI_BRIGHTNESS_INVERTED}, used by
 * {@code Button.sprite(...)}'s pressed-state visual.
 *
 * <p>Config mirrors vanilla's 26.2 {@code RenderPipelines.GUI_TEXTURED}
 * (MATRICES_PROJECTION + SAMPLER0 bind groups, TRANSLUCENT color target,
 * POSITION_TEX_COLOR vertex binding, QUADS topology, no depth state — GUI
 * ordering is handled by strata now) with the fragment-shader stage swapped
 * to a custom shader that inverts each pixel's HSL lightness channel while
 * preserving hue and saturation.
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
public final class MKRenderPipelines {

    private MKRenderPipelines() {}

    /**
     * GUI textured pipeline whose fragment shader inverts the per-pixel
     * HSL lightness (hue + saturation preserved). Drives the "pressed"
     * affordance for custom-sprite buttons — see
     * {@code Button.SpriteButton.renderBackground}.
     */
    // 26.2 builder shape (Vulkan-era): UBO/sampler declarations became bind-group
    // layouts; blend became a color-target state; vertex format+mode split into
    // vertex binding + primitive topology; the depth-test line is dropped
    // entirely — vanilla's GUI pipelines declare no depth state (GUI ordering
    // moved to render strata). Bind groups mirror vanilla's GUI_TEXTURED:
    // MATRICES_PROJECTION carries the DynamicTransforms + Projection UBOs the
    // shader consumes; SAMPLER0 carries the texture sampler.
    public static final RenderPipeline GUI_BRIGHTNESS_INVERTED = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("menukit", "pipeline/gui_brightness_inverted"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexShader(Identifier.fromNamespaceAndPath("menukit", "core/button_brightness_invert"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("menukit", "core/button_brightness_invert"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();
}
