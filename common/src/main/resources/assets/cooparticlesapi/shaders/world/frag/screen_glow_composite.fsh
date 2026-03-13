#version 330 core

in vec2 screen_uv;
out vec4 fragColor;

uniform sampler2D sceneTex;
uniform sampler2D sceneDepth;

uniform int screenGlowCount;
uniform vec2 screenSize;
uniform vec3 cameraWorldPos;
uniform mat4 projMat;
uniform mat3 viewRotationMat;
uniform mat3 inverseViewRotationMat;

const int MAX_SCREEN_GLOWS = 8;
const float SKY_DEPTH = 0.999999;

uniform vec3 glowPositions[MAX_SCREEN_GLOWS];
uniform vec3 glowColors[MAX_SCREEN_GLOWS];
uniform vec4 glowData[MAX_SCREEN_GLOWS];

bool projectWorldToScreen(vec3 worldPosition, out vec2 uv, out float depth) {
    vec3 cameraRelative = worldPosition - cameraWorldPos;
    vec3 viewPosition = viewRotationMat * cameraRelative;
    vec4 clip = projMat * vec4(viewPosition, 1.0);
    if (clip.w <= 1.0e-5) {
        uv = vec2(-2.0);
        depth = 1.0;
        return false;
    }

    vec3 ndc = clip.xyz / clip.w;
    uv = ndc.xy * 0.5 + 0.5;
    depth = ndc.z * 0.5 + 0.5;
    return true;
}

float projectRadiusPixels(vec3 center, float radius, vec2 centerUv) {
    vec2 rightUv;
    vec2 upUv;
    float rightDepth;
    float upDepth;
    bool rightValid = projectWorldToScreen(center + inverseViewRotationMat * vec3(radius, 0.0, 0.0), rightUv, rightDepth);
    bool upValid = projectWorldToScreen(center + inverseViewRotationMat * vec3(0.0, radius, 0.0), upUv, upDepth);

    float radiusPixels = 0.0;
    if (rightValid) {
        radiusPixels = max(radiusPixels, length((rightUv - centerUv) * screenSize));
    }
    if (upValid) {
        radiusPixels = max(radiusPixels, length((upUv - centerUv) * screenSize));
    }
    return radiusPixels;
}

float centerVisibility(vec2 centerUv, float centerDepth, float projectedRadiusPx) {
    if (centerUv.x < -0.45 || centerUv.x > 1.45 || centerUv.y < -0.45 || centerUv.y > 1.45) {
        return 0.0;
    }

    float sceneDepthAtCenter = texture(sceneDepth, clamp(centerUv, vec2(0.001), vec2(0.999))).r;
    if (sceneDepthAtCenter >= SKY_DEPTH) {
        return 1.0;
    }

    float depthBias = 0.0014 + min(projectedRadiusPx, 48.0) * 0.00018;
    return smoothstep(centerDepth - depthBias, centerDepth + depthBias * 2.6, sceneDepthAtCenter);
}

float pixelDepthShield(float receiverDepth, float projectedRadiusPx) {
    float pixelDepth = texture(sceneDepth, screen_uv).r;
    if (pixelDepth >= SKY_DEPTH) {
        return 1.0;
    }

    float depthBias = 0.0012 + min(projectedRadiusPx, 56.0) * 0.00016;
    return smoothstep(receiverDepth - depthBias, receiverDepth + depthBias * 2.4, pixelDepth);
}

void main() {
    vec4 sceneColor = texture(sceneTex, screen_uv);
    vec3 accumulatedGlow = vec3(0.0);
    vec2 pixelPosition = screen_uv * screenSize;

    for (int i = 0; i < screenGlowCount; ++i) {
        vec3 glowPosition = glowPositions[i];
        vec3 glowColor = glowColors[i];
        vec4 metadata = glowData[i];
        float worldRadius = max(metadata.x, 0.01);
        float glowIntensity = metadata.y;
        float softness = clamp(metadata.z, 0.05, 0.95);
        float haloProfile = clamp(metadata.w, 0.0, 1.0);
        float orbMode = step(1.0e-4, haloProfile);
        float orbNearBoost = haloProfile;

        vec2 centerUv;
        float centerDepth;
        if (!projectWorldToScreen(glowPosition, centerUv, centerDepth)) {
            continue;
        }

        float projectedRadiusPx = max(projectRadiusPixels(glowPosition, worldRadius, centerUv), 0.65);
        float smallRadiusBoost = 1.0 - smoothstep(8.0, 30.0, projectedRadiusPx);
        float defaultHaloRadiusPx = projectedRadiusPx * (2.05 + softness * 1.45) +
            smallRadiusBoost * (12.0 + glowIntensity * 2.6);
        float orbHaloRadiusPx = projectedRadiusPx * mix(
            2.10 + softness * 0.80,
            3.20 + softness * 1.10,
            orbNearBoost
        ) + sqrt(projectedRadiusPx) * (
            2.10 + glowIntensity * mix(0.16, 0.28, orbNearBoost)
        );
        float haloRadiusPx = mix(defaultHaloRadiusPx, orbHaloRadiusPx, orbMode);
        vec2 centerPx = centerUv * screenSize;

        if (centerPx.x < -haloRadiusPx || centerPx.x > screenSize.x + haloRadiusPx ||
            centerPx.y < -haloRadiusPx || centerPx.y > screenSize.y + haloRadiusPx) {
            continue;
        }

        float visibility = centerVisibility(centerUv, centerDepth, projectedRadiusPx);
        if (visibility <= 1.0e-4) {
            continue;
        }

        float depthShield = pixelDepthShield(centerDepth, projectedRadiusPx);
        if (depthShield <= 1.0e-4) {
            continue;
        }

        float distPx = length(pixelPosition - centerPx);
        float defaultCoreRadiusPx = max(projectedRadiusPx * 0.92, 1.1);
        float defaultHaloNorm = distPx / max(haloRadiusPx, 1.0);
        float defaultCoreNorm = distPx / max(defaultCoreRadiusPx, 1.0);
        float defaultCore = exp2(-defaultCoreNorm * defaultCoreNorm * 6.0);
        float defaultHaloFalloff = mix(9.0, 4.6, smallRadiusBoost);
        float defaultHalo = exp2(-defaultHaloNorm * defaultHaloNorm * defaultHaloFalloff);
        float defaultRim = smoothstep(defaultCoreRadiusPx * 0.68, defaultCoreRadiusPx * 1.10, distPx) *
            (1.0 - smoothstep(defaultCoreRadiusPx * 1.12, haloRadiusPx * 0.94, distPx));
        float defaultEnergy = glowIntensity * visibility * depthShield * smallRadiusBoost;
        vec3 defaultContribution = glowColor * defaultEnergy * (
            defaultHalo * (0.14 + softness * 0.12) +
                defaultCore * 0.045 +
                defaultRim * (0.045 + softness * 0.03)
            );

        float orbCoreRadiusPx = max(projectedRadiusPx * mix(0.84, 0.66, orbNearBoost), 0.85);
        float orbCoreNorm = distPx / max(orbCoreRadiusPx, 1.0);
        float orbHaloNorm = distPx / max(orbHaloRadiusPx, 1.0);
        float orbCore = exp2(-orbCoreNorm * orbCoreNorm * mix(15.0, 10.5, orbNearBoost));
        float orbInnerHalo = exp2(-orbCoreNorm * orbCoreNorm * mix(4.4, 3.0, orbNearBoost));
        float orbHalo = exp2(-orbHaloNorm * orbHaloNorm * mix(4.8, 2.5, orbNearBoost));
        float orbEnergy = glowIntensity * visibility * depthShield * mix(0.86, 1.02, orbNearBoost);
        vec3 orbCoreColor = mix(glowColor, vec3(1.0), 0.68 + orbNearBoost * 0.18);
        vec3 orbHaloColor = mix(glowColor, orbCoreColor, 0.12);
        vec3 orbContribution =
            orbCoreColor * orbEnergy * (orbCore * (0.34 + orbNearBoost * 0.10)) +
            orbHaloColor * orbEnergy * (
                orbInnerHalo * (0.12 + softness * 0.04) +
                orbHalo * (0.28 + softness * 0.10 + orbNearBoost * 0.12)
            );

        accumulatedGlow += mix(defaultContribution, orbContribution, orbMode);
    }

    fragColor = vec4(sceneColor.rgb + accumulatedGlow, sceneColor.a);
}
