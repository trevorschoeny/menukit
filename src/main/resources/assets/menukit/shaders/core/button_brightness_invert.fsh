#version 330

// MenuKit — fragment shader for SpriteButton's "pressed" affordance.
// Inverts the LIGHTNESS channel of each pixel (in HSL space) while
// preserving HUE and SATURATION. Result: blacks → whites, dark blues →
// light blues (same hue), light greys → dark greys.
//
// Why HSL-L inversion and not RGB inversion: per-channel RGB inversion
// (color = 1 - color) rotates the HUE 180° — a dark blue would become
// yellow, a dark red would become cyan. That's the wrong affordance for
// "this button is being pressed; show its complement-brightness state."
// HSL-L inversion keeps the hue identity stable.
//
// Uniforms / inputs mirror vanilla's core/position_tex_color.fsh so this
// shader plugs into the same POSITION_TEX_COLOR vertex format used by
// RenderPipelines.GUI_TEXTURED.

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

// ── RGB ↔ HSL helpers ─────────────────────────────────────────────────
//
// Standard formulas (see e.g. css-color-3 §4.2.4). Operate on normalized
// 0–1 components. RGB→HSL→RGB round-trips losslessly within float32
// precision for input pixels we care about (8-bit-per-channel sprites).

vec3 rgb2hsl(vec3 rgb) {
    float maxC = max(max(rgb.r, rgb.g), rgb.b);
    float minC = min(min(rgb.r, rgb.g), rgb.b);
    float L = (maxC + minC) * 0.5;
    float H = 0.0;
    float S = 0.0;
    if (maxC != minC) {
        float d = maxC - minC;
        S = L > 0.5 ? d / (2.0 - maxC - minC) : d / (maxC + minC);
        if (maxC == rgb.r) {
            H = (rgb.g - rgb.b) / d + (rgb.g < rgb.b ? 6.0 : 0.0);
        } else if (maxC == rgb.g) {
            H = (rgb.b - rgb.r) / d + 2.0;
        } else {
            H = (rgb.r - rgb.g) / d + 4.0;
        }
        H /= 6.0;
    }
    return vec3(H, S, L);
}

float hue2rgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 0.5)       return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    return p;
}

vec3 hsl2rgb(vec3 hsl) {
    float H = hsl.x;
    float S = hsl.y;
    float L = hsl.z;
    if (S == 0.0) {
        // Achromatic — short-circuit to avoid divide-shaped paths in the
        // hue rebuild. Greys invert cleanly: L → 1 - L on each channel.
        return vec3(L);
    }
    float q = L < 0.5 ? L * (1.0 + S) : L + S - L * S;
    float p = 2.0 * L - q;
    return vec3(
        hue2rgb(p, q, H + 1.0 / 3.0),
        hue2rgb(p, q, H),
        hue2rgb(p, q, H - 1.0 / 3.0)
    );
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }

    // RGB → HSL → invert L → HSL → RGB.
    // Alpha passes through unchanged so the sprite's edge mask is preserved.
    vec3 hsl = rgb2hsl(color.rgb);
    hsl.z = 1.0 - hsl.z;
    vec3 inverted = hsl2rgb(hsl);

    fragColor = vec4(inverted, color.a) * ColorModulator;
}
