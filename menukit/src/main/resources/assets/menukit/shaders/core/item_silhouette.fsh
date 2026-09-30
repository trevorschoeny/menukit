#version 330

// MenuKit: the coloured item outline's silhouette (SlotRendering.drawItemOutline).
// Every pixel the sprite covers is drawn in the vertex colour, keeping the
// sprite's own coverage as alpha, so four copies offset one pixel each way,
// drawn under the item, leave a one-pixel line of that colour around the item's
// shape. Uniforms and inputs mirror vanilla's core/position_tex_color.fsh, so it
// plugs into the same POSITION_TEX_COLOR vertex format.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).a * vertexColor.a;
    if (coverage == 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, coverage) * ColorModulator;
}
