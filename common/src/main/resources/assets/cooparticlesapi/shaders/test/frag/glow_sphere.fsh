#version 330 core

in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;

layout (location = 0) out vec4 GlowMask;
layout (location = 1) out vec4 DistortionMask;

uniform float time;
uniform vec3 color;
uniform float intensity;
uniform float haloIntensity;
uniform float haloRadiusScale;
uniform float fresnelStrength;
uniform float animationSpeed;
uniform float projectedRadiusPx;
uniform float farPersistence;
uniform float directOpacity;
uniform float overbrightClamp;
uniform float coreWhiteness;
uniform float shellVisibility;
uniform float sourceHaloSpread;
uniform float solidCoreFill;
uniform float outerShellOpacity;
uniform float distortionOpacity;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

vec3 softLimit(vec3 value, float limit) {
    return value / (1.0 + value / max(limit, 1.0e-3));
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += noise3(p) * amp;
        p = p * 2.02 + vec3(7.1, 5.4, 3.7);
        amp *= 0.5;
    }
    return sum;
}

void main() {
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    vec3 spherePos = normalize(localPos);
    float pixelFootprint = max(length(fwidth(localPos)), 1.0e-4);
    float derivativeMinified = smoothstep(0.022, 0.16, pixelFootprint);
    float projectedMinified = 1.0 - smoothstep(14.0, 34.0, projectedRadiusPx);
    float minified = max(derivativeMinified, projectedMinified);

    float facing = saturate(dot(normal, viewDir));
    float radial = sqrt(saturate(1.0 - facing * facing));
    float edge = 1.0 - facing;
    float flowTime = time * animationSpeed;
    float sourceNoise = fbm(spherePos * 4.6 + vec3(flowTime * 0.18, -flowTime * 0.14, flowTime * 0.12));
    float shimmer = 0.96 + 0.04 * sin(flowTime * 1.9 + spherePos.x * 6.0 + spherePos.y * 4.0);
    float pulse = 0.95 + 0.05 * sin(flowTime * 1.4 + spherePos.z * 4.2);

    vec3 coreWhite = vec3(1.34, 1.34, 1.38);
    vec3 coreColor = mix(color, coreWhite, coreWhiteness);
    vec3 bodyColor = mix(color, coreWhite, coreWhiteness * 0.32);
    vec3 haloColor = mix(color, coreWhite, coreWhiteness * 0.12);

    float core = 1.0 - smoothstep(0.0, mix(0.18 + shellVisibility * 0.08, 0.96, solidCoreFill), radial);
    float body = 1.0 - smoothstep(0.0, 0.58 + shellVisibility * 0.12, radial);
    float haloMask = 1.0 - smoothstep(0.12, 1.0, radial / max(sourceHaloSpread, 1.0));
    float shell = pow(smoothstep(0.62, 1.0, edge), max(1.4, fresnelStrength + 0.2)) * shellVisibility;
    float filledCore = max(core, (1.0 - smoothstep(0.0, 0.98, radial)) * solidCoreFill);
    body *= outerShellOpacity;
    haloMask *= outerShellOpacity;
    shell *= outerShellOpacity;

    vec3 emissive = coreColor * filledCore * (1.18 + intensity * 0.28);
    emissive += bodyColor * body * (0.18 + intensity * 0.10) * (0.94 + sourceNoise * 0.10) * shimmer;
    emissive += haloColor * haloMask * (0.08 + haloIntensity * 0.11) * (1.0 + minified * 0.18);
    emissive += haloColor * shell * (0.015 + haloIntensity * 0.024);
    emissive *= pulse;

    float glowEnergy = saturate(luminance(emissive) * 0.12);
    float glow = saturate(
        filledCore * 0.58 +
        body * 0.22 +
        haloMask * 0.38 +
        shell * 0.10 +
        glowEnergy
    );
    vec2 bend = normal.xy * (0.00022 + shell * 0.00040 + minified * 0.00018);
    float thickness = mix(facing, 1.0 - radial, 0.12);
    float coverage = saturate((filledCore * 0.04 + body * 0.10 + haloMask * 0.14 + shell * 0.12) * distortionOpacity);
    float subPixelBoost = 1.0 + minified * (0.72 + haloIntensity * 0.04);

    emissive *= mix(1.0, 1.10 + farPersistence * 0.06, minified);
    emissive = softLimit(emissive, overbrightClamp);
    emissive *= directOpacity;
    glow = saturate(glow * subPixelBoost * directOpacity);
    bend *= directOpacity * distortionOpacity;
    coverage = saturate((coverage + minified * 0.02 * distortionOpacity) * directOpacity);

    GlowMask = vec4(emissive, glow);
    DistortionMask = vec4(bend, thickness, coverage);
}
