#version 330 core

in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;
in vec3 worldPos3;

layout (location = 0) out vec4 HoleMask;
layout (location = 1) out vec4 DistortionMask;

uniform float time;
uniform float coreRadius;
uniform float distortionStrength;
uniform vec3 diskNormal;
uniform float diskThickness;
uniform float diskWidth;
uniform float spinSpeed;
uniform vec3 ringColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    vec3 spherePos = normalize(localPos);
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    float facing = saturate(abs(dot(normal, viewDir)));
    float edge = 1.0 - facing;
    float radial = sqrt(saturate(1.0 - facing * facing));
    float radialMask = 1.0 - smoothstep(coreRadius + 0.72, coreRadius + 1.12, radial);

    vec3 diskUp = normalize(diskNormal);
    vec3 guideAxis = abs(diskUp.y) < 0.95 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 diskRight = normalize(cross(guideAxis, diskUp));
    vec3 diskForward = normalize(cross(diskUp, diskRight));
    float diskHeight = dot(spherePos, diskUp);
    vec3 diskProjected = normalize(spherePos - diskUp * diskHeight + diskRight * 1.0e-4);
    float orbitAngle = atan(dot(diskProjected, diskForward), dot(diskProjected, diskRight));
    vec3 orbitDir = normalize(cross(diskUp, diskProjected));
    float diskBand = exp(-pow(abs(diskHeight) / max(diskThickness * 1.7, 1.0e-3), 2.0));
    float viewPlane = dot(viewDir, diskUp);
    float nearSide = smoothstep(-diskThickness * 2.2, diskThickness * 0.45, diskHeight * sign(viewPlane + 1.0e-4));
    float farSide = 1.0 - nearSide;
    float shadowRadius = coreRadius;
    float photonOrbit = shadowRadius + 0.070;
    float diskInnerRadius = shadowRadius + 0.11;
    float diskOuterRadius = shadowRadius + 0.54 + diskWidth * 0.62;
    float diskSpan = max(diskOuterRadius - diskInnerRadius, 1.0e-3);
    float orbitRadius = max(radial, diskInnerRadius);
    float diskRadiusNorm = saturate((orbitRadius - diskInnerRadius) / diskSpan);
    float innerCut = smoothstep(diskInnerRadius - 0.012, diskInnerRadius + 0.020, radial);
    float outerCut = 1.0 - smoothstep(diskOuterRadius - 0.12, diskOuterRadius + 0.05, radial);
    float diskWindow = innerCut * outerCut;
    float inclination = sqrt(saturate(1.0 - dot(diskUp, viewDir) * dot(diskUp, viewDir)));
    float azimuthFront = dot(spherePos, diskForward);
    float azimuthUp = dot(spherePos, normalize(diskForward + vec3(0.0, 1.10, 0.0)));
    float upperArcMask = smoothstep(0.02, 0.86, azimuthUp + inclination * 0.55);
    float frontSlice = smoothstep(-0.34, 0.18, azimuthFront);

    float eventHorizon = 1.0 - smoothstep(shadowRadius, shadowRadius + 0.028, radial);
    float shadowShell = 1.0 - smoothstep(shadowRadius + 0.008, shadowRadius + 0.18, radial);
    float photonRing = exp(-pow((radial - photonOrbit) * 36.0, 2.0));
    float lensBase = pow(1.0 - smoothstep(shadowRadius, shadowRadius + 0.95, radial), 1.9);
    float emissivity = pow(max(orbitRadius / max(diskInnerRadius, 1.0e-3), 1.0), -1.65);
    emissivity *= mix(1.0, 0.62, diskRadiusNorm);
    float beta = 0.55 * sqrt(saturate(diskInnerRadius / orbitRadius));
    float gamma = inversesqrt(max(1.0 - beta * beta, 1.0e-3));
    float mu = dot(orbitDir, viewDir);
    float doppler = 1.0 / max(gamma * (1.0 - beta * mu), 0.18);
    float beaming = pow(clamp(doppler, 0.45, 2.8), 3.0);
    float gravRedshift = sqrt(saturate(1.0 - (shadowRadius * 0.78) / max(orbitRadius, shadowRadius + 0.03)));
    float spiralPhase = orbitAngle * 9.0 - time * (1.6 + spinSpeed * 1.5) - radial * 7.0;
    float swirlA = 0.5 + 0.5 * sin(spiralPhase);
    float swirlB = 0.5 + 0.5 * sin(spiralPhase * 1.9 + time * (2.5 + spinSpeed * 2.7));
    float filament = mix(0.72, 1.35, pow(swirlB, 3.5));
    float physicalFlux = diskWindow * diskBand * emissivity * gravRedshift * filament;
    float farArc = physicalFlux * farSide * upperArcMask * (0.70 + inclination * 1.35);
    farArc *= exp(-pow((radial - (photonOrbit + 0.020 + inclination * 0.060)) * 14.0, 2.0));
    farArc *= smoothstep(shadowRadius + 0.04, shadowRadius + 0.18, radial);
    float lensedHalo = physicalFlux * farSide * upperArcMask;
    lensedHalo *= exp(-pow((radial - (photonOrbit + 0.155 + inclination * 0.115)) * 8.5, 2.0));
    lensedHalo *= (0.38 + inclination * 1.55);
    float lensBoost = 0.45 + inclination * 1.2;
    float diskInfluence = radialMask * (farArc * 1.65 + lensedHalo * 1.10 + photonRing * 1.05);
    float gravityField = pow(1.0 - smoothstep(shadowRadius * 0.88, shadowRadius + 0.92, radial), 1.18);

    vec2 dir = length(normal.xy) > 1.0e-5 ? normalize(normal.xy) : vec2(0.0, 1.0);
    vec2 tangent = vec2(-dir.y, dir.x);
    float lens = lensBase * (0.92 + edge * 3.10) + gravityField * 1.48;
    lens *= 1.0 + photonRing * 1.18 + (farArc * 0.12 + lensedHalo * 0.18) * lensBoost;
    lens *= radialMask;

    vec2 bend = -dir * lens * distortionStrength * (2.55 + edge * 1.85);
    bend += tangent * (photonRing * 1.45 + farArc * 1.15 + lensedHalo * 1.65)
        * distortionStrength * 0.38 * ((swirlA - 0.5) * 0.16 + clamp(doppler - 1.0, -0.2, 1.2) * 0.58);

    vec3 photonColor = mix(vec3(1.0, 0.97, 0.94), ringColor, 0.20);
    vec3 farDiskColor = mix(vec3(0.86, 0.89, 0.98), ringColor, 0.20);
    vec3 haloColor = mix(vec3(1.0, 0.96, 0.88), ringColor, 0.36);
    vec3 accretionColor = photonColor * photonRing * (0.18 + inclination * 0.06)
        + farDiskColor * farArc * (0.16 + inclination * 0.14)
        + haloColor * lensedHalo * (0.42 + inclination * 0.36);
    accretionColor *= radialMask * 0.34;

    float darkness = clamp(eventHorizon + shadowShell * 1.06 + lens * 0.22, 0.0, 1.0);
    float coverage = clamp(gravityField * 0.84 + lens * 0.72 + photonRing * 0.18 + diskInfluence * 0.18, 0.0, 1.0);
    float veil = clamp(eventHorizon + shadowShell * 0.10 + photonRing * 0.04 + farArc * 0.02 + lensedHalo * 0.10, 0.0, 1.0);

    HoleMask = vec4(accretionColor, veil);
    DistortionMask = vec4(bend, darkness, coverage);
}
