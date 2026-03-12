#version 330 core

in vec2 uv;

layout (location = 0) out vec4 HoleMask;
layout (location = 1) out vec4 DistortionMask;

uniform float time;
uniform float coreRadius;
uniform float distortionStrength;
uniform vec3 ringColor;

void main() {
    vec2 centered = uv * 2.0 - 1.0;
    float radius = length(centered);
    if (radius > 1.0) {
        HoleMask = vec4(0.0);
        DistortionMask = vec4(0.0);
        return;
    }

    vec2 dir = radius > 1.0e-4 ? centered / radius : vec2(0.0, 1.0);
    vec2 tangent = vec2(-dir.y, dir.x);

    float core = 1.0 - smoothstep(coreRadius, coreRadius + 0.12, radius);
    float lens = 1.0 - smoothstep(coreRadius, 1.0, radius);
    float ringBand = exp(-pow((radius - (coreRadius + 0.18)) * 9.0, 2.0));
    float swirl = 0.5 + 0.5 * sin(time * 6.0 + atan(centered.y, centered.x) * 3.0 - radius * 16.0);
    float ring = ringBand * (0.45 + swirl * 0.75);

    vec2 offset = -dir * lens * distortionStrength;
    offset += tangent * ringBand * distortionStrength * 0.35 * (swirl * 2.0 - 1.0);

    float darkMask = clamp(core + lens * 0.25, 0.0, 1.0);
    HoleMask = vec4(ringColor * ring, darkMask);
    DistortionMask = vec4(offset, lens, clamp(lens + ringBand * 0.5, 0.0, 1.0));
}
