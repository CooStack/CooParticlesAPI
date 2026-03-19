#version 330 core

in vec2 screen_uv;
out vec4 fragColor;

uniform sampler2D sourceMask;
uniform float threshold;
uniform float thresholdSoftness;

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 mask = texture(sourceMask, screen_uv);
    float source = max(mask.a, luminance(mask.rgb));
    float softness = max(thresholdSoftness, 1.0e-4);
    float gate = smoothstep(threshold - softness, threshold + softness, source);
    fragColor = vec4(mask.rgb * gate, mask.a * gate);
}
