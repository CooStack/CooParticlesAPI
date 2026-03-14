#version 330 core

in vec2 screen_uv;
out vec4 fragColor;

uniform sampler2D sceneTex;
uniform sampler2D persistentGlowMask;
uniform sampler2D blurredGlow;

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

vec3 softLimit(vec3 value, float limit) {
    return value / (1.0 + value / max(limit, 1.0e-3));
}

void main() {
    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec4 mask = texture(persistentGlowMask, screen_uv);
    vec3 blurred = texture(blurredGlow, screen_uv).rgb;

    float haloCoverage = clamp(mask.a, 0.0, 1.0);
    vec3 core = mask.rgb * (0.02 + haloCoverage * 0.02);
    vec3 halo = blurred * (0.34 + haloCoverage * 0.06) + mask.rgb * (0.02 + haloCoverage * 0.01);
    vec3 color = scene + core + halo;

    color = mix(color, color + halo * 0.006, clamp(luminance(halo) * 0.012 + haloCoverage * 0.012, 0.0, 0.03));
    fragColor = vec4(softLimit(color, 7.0), 1.0);
}
