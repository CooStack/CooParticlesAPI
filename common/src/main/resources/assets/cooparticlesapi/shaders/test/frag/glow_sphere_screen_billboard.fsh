#version 330 core

in vec2 uv;

layout (location = 0) out vec4 GlowMask;
layout (location = 1) out vec4 DistortionMask;

uniform float time;
uniform vec3 color;
uniform float intensity;
uniform float haloIntensity;
uniform float opacity;
uniform float softness;
uniform float coreWhiteness;
uniform float haloSpread;
uniform float overbrightClamp;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

vec3 softLimit(vec3 value, float limit) {
    return value / (1.0 + value / max(limit, 1.0e-3));
}

void main() {
    vec2 centered = uv * 2.0 - 1.0;
    float radial = length(centered);
    if (radial >= 1.2) {
        GlowMask = vec4(0.0);
        DistortionMask = vec4(0.0);
        return;
    }

    float pulse = 0.96 + 0.04 * sin(time * 2.1 + centered.x * 3.0 + centered.y * 2.0);
    vec3 coreWhite = vec3(1.32, 1.34, 1.40);
    vec3 coreColor = mix(color, coreWhite, coreWhiteness);
    vec3 bodyColor = mix(color, coreWhite, coreWhiteness * 0.34);
    vec3 haloColor = mix(color, coreWhite, coreWhiteness * 0.14);

    float coreRadius = mix(0.22, 0.34, softness);
    float bodyRadius = mix(0.48, 0.62, softness);
    float haloRadius = 0.82 + haloSpread * 0.24 + softness * 0.10;

    float core = 1.0 - smoothstep(0.0, coreRadius, radial);
    float body = 1.0 - smoothstep(0.0, bodyRadius, radial);
    float halo = 1.0 - smoothstep(0.12, haloRadius, radial);
    float rim = smoothstep(0.40, 0.96, radial) *
        (1.0 - smoothstep(0.96, 1.12 + softness * 0.06, radial));

    vec3 emissive = coreColor * core * (0.42 + intensity * 0.24);
    emissive += bodyColor * body * (0.12 + intensity * 0.08);
    emissive += haloColor * halo * (0.06 + haloIntensity * 0.15);
    emissive += haloColor * rim * (0.02 + haloIntensity * 0.05);
    emissive *= pulse;
    emissive = softLimit(emissive, overbrightClamp);
    emissive *= opacity;

    float glow = saturate(
        core * 0.82 +
        body * 0.38 +
        halo * 0.42 +
        rim * 0.12 +
        luminance(emissive) * 0.10
    ) * opacity;

    GlowMask = vec4(emissive, glow);
    DistortionMask = vec4(0.0);
}
