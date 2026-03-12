#version 330 core

in vec3 viewNormal;
in vec3 viewPos;

layout (location = 0) out vec4 GlowMask;
layout (location = 1) out vec4 DistortionMask;

uniform float time;
uniform vec3 color;
uniform float intensity;

void main() {
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    float facing = clamp(dot(normal, viewDir), 0.0, 1.0);
    float rim = pow(1.0 - facing, 1.4);
    float core = pow(facing, 0.42);
    float aura = pow(1.0 - facing, 0.72);
    float shimmer = 0.5 + 0.5 * sin(time * 5.5 + normal.y * 11.0 + normal.x * 7.0);
    float caustic = 0.5 + 0.5 * sin(time * 3.2 - normal.z * 10.0 + normal.x * 5.0);

    vec3 emissive = color * intensity * (
        core * 1.40 +
            rim * (2.60 + shimmer * 1.15) +
            aura * (1.10 + caustic * 0.95)
        );
    float glow = clamp(core * 0.92 + rim * 1.80 + aura * 1.05, 0.0, 1.0);
    vec2 bend = normal.xy * (0.014 + rim * 0.060 + aura * 0.030) * (0.95 + intensity * 0.48);
    float thickness = facing;
    float coverage = clamp(core * 0.92 + rim * 1.45 + aura * 1.12, 0.0, 1.0);

    GlowMask = vec4(emissive, glow);
    DistortionMask = vec4(bend, thickness, coverage);
}
