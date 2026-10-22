#version 330
#extension GL_ARB_separate_shader_objects : require

// MenuKit: the colored item outline's silhouette (SlotRendering.drawItemOutline).
// Every pixel the sprite covers is drawn in the vertex color, keeping the
// sprite's own coverage as alpha, so four copies offset one pixel each way,
// drawn under the item, leave a one-pixel line of that color around the item's
// shape. Uniforms and inputs mirror vanilla's core/position_tex_color.fsh, so it
// plugs into the same POSITION_TEX_COLOR vertex format.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).a * vertexColor.a;
    if (coverage == 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, coverage) * ColorModulator;
}
