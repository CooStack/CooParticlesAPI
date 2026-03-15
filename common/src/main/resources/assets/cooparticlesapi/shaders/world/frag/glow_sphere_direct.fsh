#version 330 core

in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;

out vec4 FragColor;

uniform float time;
uniform vec3 color;
uniform float intensity;
uniform float haloIntensity;
uniform float haloRadiusScale;
uniform float haloCenterSuppress;
uniform float haloCenterSuppressRadius;
uniform float fresnelStrength;
uniform float animationSpeed;
uniform float directOpacity;
uniform float overbrightClamp;
uniform float coreOpacityScale;
uniform float bodyOpacityScale;
uniform float haloOpacityScale;
uniform float shellOpacityScale;
uniform float silhouetteFadeStart;
uniform float solidCoreFill;
uniform float outerShellOpacity;
uniform float distortionOpacity;

const float CORE_WHITENESS = 0.14;
const float SHELL_VISIBILITY = 0.045;
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

    float facing = saturate(dot(normal, viewDir));
    float radial = sqrt(saturate(1.0 - facing * facing));
    float edge = 1.0 - facing;
    float flowTime = time * animationSpeed;
    float sourceNoise = fbm(spherePos * 4.6 + vec3(flowTime * 0.18, -flowTime * 0.14, flowTime * 0.12));
    float shimmer = 0.96 + 0.04 * sin(flowTime * 1.9 + spherePos.x * 6.0 + spherePos.y * 4.0);
    float pulse = 0.95 + 0.05 * sin(flowTime * 1.4 + spherePos.z * 4.2);

    vec3 coreWhite = vec3(1.16, 1.20, 1.26);
    vec3 coreColor = mix(color, coreWhite, CORE_WHITENESS);
    vec3 bodyColor = mix(color, coreWhite, CORE_WHITENESS * 0.24);
    vec3 haloColor = mix(color, coreWhite, CORE_WHITENESS * 0.08);

    float core = 1.0 - smoothstep(0.0, mix(0.26 + SHELL_VISIBILITY * 0.08, 0.96, solidCoreFill), radial);
    float body = 1.0 - smoothstep(0.0, 0.74 + SHELL_VISIBILITY * 0.16, radial);
    float haloShape = mix(2.4, 0.85, saturate((max(haloRadiusScale, 1.0) - 1.0) / 1.18));
    float haloMask = pow(1.0 - smoothstep(0.08, 1.0, radial), haloShape);
    float haloCenterMask = mix(
        1.0,
        smoothstep(0.0, max(haloCenterSuppressRadius, 1.0e-3), radial),
        saturate(haloCenterSuppress)
    );
    float shell = pow(smoothstep(0.66, 1.0, edge), max(1.55, fresnelStrength + 0.18)) * SHELL_VISIBILITY;
    float silhouetteFade = 1.0 - smoothstep(silhouetteFadeStart, 1.0, radial);
    float filledCore = max(core, (1.0 - smoothstep(0.0, 0.98, radial)) * solidCoreFill);
    body *= max(outerShellOpacity, 0.16);
    haloMask *= haloCenterMask;
    haloMask *= max(outerShellOpacity, 0.24);
    shell *= max(outerShellOpacity, 0.10);

    vec3 emissive = coreColor * filledCore * (1.26 + intensity * 0.36) * coreOpacityScale;
    emissive += bodyColor * body * (0.30 + intensity * 0.16) * (0.94 + sourceNoise * 0.10) * shimmer * bodyOpacityScale;
    emissive += haloColor * haloMask * (0.22 + haloIntensity * 0.26) * haloOpacityScale;
    emissive += haloColor * shell * (0.10 + haloIntensity * 0.11) * shellOpacityScale;
    emissive *= pulse;
    emissive *= mix(0.78, 1.0, silhouetteFade);

    float glowEnergy = saturate(luminance(emissive) * 0.12);
    float alpha = saturate(
        filledCore * 0.84 * coreOpacityScale +
        body * 0.28 * bodyOpacityScale +
        haloMask * 0.26 * haloOpacityScale +
        shell * 0.12 * shellOpacityScale +
        glowEnergy * 0.18 +
        distortionOpacity * 0.06
    );

    emissive = softLimit(emissive, overbrightClamp);
    emissive *= directOpacity;
    alpha = saturate(alpha * directOpacity * silhouetteFade);

    FragColor = vec4(emissive, alpha);
}
