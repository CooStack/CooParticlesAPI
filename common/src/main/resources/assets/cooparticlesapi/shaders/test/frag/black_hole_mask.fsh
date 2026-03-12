#version 330 core

in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;

layout (location = 0) out vec4 HoleMask;
layout (location = 1) out vec4 DistortionMask;

uniform float time;
uniform float coreRadius;
uniform float distortionStrength;
uniform vec3 ringColor;

void main() {
    vec3 spherePos = normalize(localPos);
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    float facing = clamp(abs(dot(normal, viewDir)), 0.0, 1.0);
    float edge = 1.0 - facing;
    float radial = sqrt(clamp(1.0 - facing * facing, 0.0, 1.0));
    float azimuth = atan(spherePos.z, spherePos.x);
    float latitude = spherePos.y;
    float diskBand = exp(-pow(abs(latitude) * 9.5, 2.0));
    float polarFade = 1.0 - smoothstep(0.55, 0.95, abs(latitude));

    float eventHorizon = 1.0 - smoothstep(coreRadius, coreRadius + 0.045, radial);
    float shadowShell = 1.0 - smoothstep(coreRadius + 0.03, coreRadius + 0.24, radial);
    float lensBase = pow(1.0 - smoothstep(coreRadius, 1.0, radial), 1.85);
    float photonRing = exp(-pow((radial - (coreRadius + 0.065)) * 28.0, 2.0)) * (0.55 + diskBand * 0.85);
    float diskInner = exp(-pow((radial - (coreRadius + 0.15)) * 18.0, 2.0)) * diskBand;
    float diskOuter = exp(-pow((radial - (coreRadius + 0.32)) * 8.0, 2.0)) * diskBand * polarFade;
    float swirlA = 0.5 + 0.5 * sin(time * 5.0 + azimuth * 4.0 - radial * 15.0);
    float swirlB = 0.5 + 0.5 * sin(time * 2.6 - azimuth * 6.0 + radial * 10.0);
    float diskNoise = 0.45 + swirlA * 0.95 + swirlB * 0.65;
    float disk = diskInner * (1.10 + swirlA * 1.35) + diskOuter * (0.45 + swirlB * 0.90);
    disk *= diskNoise;
    float gravityField = pow(1.0 - smoothstep(coreRadius * 0.55, 1.02, radial), 0.62);
    float outerShear = pow(1.0 - smoothstep(coreRadius + 0.16, 1.0, radial), 1.10);

    vec2 dir = length(normal.xy) > 1.0e-5 ? normalize(normal.xy) : vec2(0.0, 1.0);
    vec2 tangent = vec2(-dir.y, dir.x);
    float lens = lensBase * (0.55 + edge * 2.35) + gravityField * 1.05 + outerShear * 0.42;
    lens *= 1.0 + photonRing * 0.95 + diskInner * 0.38 + diskBand * 0.12;

    vec2 bend = -dir * lens * distortionStrength * (2.20 + edge * 1.40);
    bend += tangent * (photonRing * 1.95 + diskInner * 1.20 + diskOuter * 0.65 + gravityField * 0.22)
        * distortionStrength * 1.55 * (swirlA * 2.0 - 1.0);

    vec3 hotCoreColor = mix(ringColor, vec3(1.0, 0.95, 0.82), 0.45);
    vec3 outerDiskColor = mix(ringColor * vec3(1.0, 0.72, 0.42), ringColor, 0.35);
    vec3 accretionColor = hotCoreColor * photonRing * 2.40
        + hotCoreColor * diskInner * 1.55
        + outerDiskColor * diskOuter * 0.95;

    float darkness = clamp(eventHorizon + shadowShell * 0.82 + lens * 0.16, 0.0, 1.0);
    float coverage = clamp(gravityField * 0.92 + lens * 0.46 + photonRing * 0.85 + disk * 0.22, 0.0, 1.0);
    float veil = clamp(eventHorizon + shadowShell * 0.78 + photonRing * 0.32 + diskBand * 0.08, 0.0, 1.0);

    HoleMask = vec4(accretionColor, veil);
    DistortionMask = vec4(bend, darkness, coverage);
}
