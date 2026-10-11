#version 330

// MenuKit: vertex shader paired with button_brightness_invert.fsh. The same as
// vanilla's core/position_tex_color.vsh, shipped under the menukit namespace so
// the pipeline can declare both stages as menukit:core/button_brightness_invert
// without a cross-namespace shader reference. The fragment shader does the
// inversion.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;
}
