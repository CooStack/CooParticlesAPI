#version 330 core

in vec2 screen_uv;
out vec4 fragColor;

uniform sampler2D sceneDepth;

uniform int persistentBloomCount;
uniform vec2 screenSize;
uniform vec3 cameraWorldPos;
uniform mat4 projMat;
uniform mat3 viewRotationMat;
uniform mat3 inverseViewRotationMat;

const int MAX_PERSISTENT_BLOOMS = 8;
const float SKY_DEPTH = 0.999999;

uniform vec3 bloomPositions[MAX_PERSISTENT_BLOOMS];
uniform vec3 bloomColors[MAX_PERSISTENT_BLOOMS];
uniform vec4 bloomData[MAX_PERSISTENT_BLOOMS];
uniform vec4 bloomStyleData[MAX_PERSISTENT_BLOOMS];

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
    bool rightValid = projectWorldToScreen(
        center + inverseViewRotationMat * vec3(radius, 0.0, 0.0),
        rightUv,
        rightDepth
    );
    bool upValid = projectWorldToScreen(
        center + inverseViewRotationMat * vec3(0.0, radius, 0.0),
        upUv,
        upDepth
    );

    float radiusPixels = 0.0;
    if (rightValid) {
        radiusPixels = max(radiusPixels, length((rightUv - centerUv) * screenSize));
    }
    if (upValid) {
        radiusPixels = max(radiusPixels, length((upUv - centerUv) * screenSize));
    }
    return radiusPixels;
}

float depthVisibilityAt(vec2 uv, float receiverDepth, float projectedRadiusPx) {
    float sceneDepthAtUv = texture(sceneDepth, clamp(uv, vec2(0.001), vec2(0.999))).r;
    if (sceneDepthAtUv >= SKY_DEPTH) {
        return 1.0;
    }

    float farTolerance = 1.0 - smoothstep(2.0, 32.0, projectedRadiusPx);
    float depthBias = 0.0018 + farTolerance * 0.0042 + min(projectedRadiusPx, 48.0) * 0.00010;
    return smoothstep(receiverDepth - depthBias, receiverDepth + depthBias * 2.4, sceneDepthAtUv);
}

float centerVisibility(vec2 centerUv, float receiverDepth, float projectedRadiusPx) {
    if (centerUv.x < -0.45 || centerUv.x > 1.45 || centerUv.y < -0.45 || centerUv.y > 1.45) {
        return 0.0;
    }

    vec2 sampleStep = vec2(clamp(projectedRadiusPx * 0.018, 1.0, 4.0)) / screenSize;
    float center = depthVisibilityAt(centerUv, receiverDepth, projectedRadiusPx);
    float left = depthVisibilityAt(centerUv + vec2(-sampleStep.x, 0.0), receiverDepth, projectedRadiusPx);
    float right = depthVisibilityAt(centerUv + vec2(sampleStep.x, 0.0), receiverDepth, projectedRadiusPx);
    float up = depthVisibilityAt(centerUv + vec2(0.0, sampleStep.y), receiverDepth, projectedRadiusPx);
    float down = depthVisibilityAt(centerUv + vec2(0.0, -sampleStep.y), receiverDepth, projectedRadiusPx);

    float averageVisibility = (center * 2.0 + left + right + up + down) / 6.0;
    float strongestTap = max(center, max(max(left, right), max(up, down)));
    return max(averageVisibility, strongestTap * 0.76);
}

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec3 accumulatedGlow = vec3(0.0);
    float accumulatedAlpha = 0.0;
    vec2 pixelPosition = screen_uv * screenSize;

    for (int i = 0; i < persistentBloomCount; ++i) {
        vec3 bloomPosition = bloomPositions[i];
        vec3 bloomColor = bloomColors[i];
        vec4 metadata = bloomData[i];
        vec4 style = bloomStyleData[i];
        float worldRadius = max(metadata.x, 0.01);
        float bloomIntensity = max(metadata.y, 0.0);
        float softness = clamp(metadata.z, 0.05, 0.95);
        float softOcclusionFloor = clamp(metadata.w, 0.0, 0.90);
        float haloRadiusScale = clamp(style.x, 1.0, 4.5);
        float brightnessNormalization = clamp(style.y, 0.6, 2.0);
        float haloOpacity = clamp(style.z, 0.0, 1.0);

        vec2 centerUv;
        float centerDepth;
        if (!projectWorldToScreen(bloomPosition, centerUv, centerDepth)) {
            continue;
        }

        float projectedRadiusPx = max(projectRadiusPixels(bloomPosition, worldRadius, centerUv), 0.75);
        float haloRadiusPx = max(projectedRadiusPx * haloRadiusScale, projectedRadiusPx * 1.10 + 1.5);
        vec2 centerPx = centerUv * screenSize;

        if (centerPx.x < -haloRadiusPx || centerPx.x > screenSize.x + haloRadiusPx ||
            centerPx.y < -haloRadiusPx || centerPx.y > screenSize.y + haloRadiusPx) {
            continue;
        }

        float sourceVisibility = centerVisibility(centerUv, centerDepth, projectedRadiusPx);
        float pixelTransmittance = depthVisibilityAt(screen_uv, centerDepth, projectedRadiusPx);
        float haloVisibilityFloor = mix(softOcclusionFloor * 0.35, softOcclusionFloor, sourceVisibility);
        float haloVisibility = max(pixelTransmittance, haloVisibilityFloor);
        if (haloVisibility <= 1.0e-4) {
            continue;
        }

        float distPx = length(pixelPosition - centerPx);
        float coreRadiusPx = max(projectedRadiusPx * 0.92, 1.0);
        float coreNorm = distPx / max(coreRadiusPx, 1.0);
        float haloNorm = distPx / max(haloRadiusPx, 1.0);

        float core = exp2(-coreNorm * coreNorm * 6.8);
        float innerHalo = exp2(-coreNorm * coreNorm * 2.9);
        float haloBody = exp2(-haloNorm * haloNorm * mix(3.0, 2.3, softness));
        float haloTail = exp2(-haloNorm * haloNorm * mix(1.35, 0.92, softness));

        float radiusNormalization = clamp(pow(18.0 / max(haloRadiusPx, 1.0), 0.14), 0.92, 1.06);
        float energy = bloomIntensity * brightnessNormalization * radiusNormalization * haloVisibility;
        vec3 coreColor = mix(bloomColor, vec3(1.0), 0.18);
        vec3 haloColor = mix(bloomColor, vec3(1.0), 0.08);
        vec3 contribution =
            coreColor * energy * (core * (0.03 + sourceVisibility * 0.03)) +
            haloColor * energy * (
                innerHalo * (0.05 + sourceVisibility * 0.03 + softness * 0.03) +
                haloBody * (0.08 + softness * 0.03) +
                haloTail * (0.04 + softness * 0.02)
            );

        float alphaContribution = clamp(
            core * (0.02 + sourceVisibility * 0.04) +
            innerHalo * (0.04 + sourceVisibility * 0.04) +
            haloBody * (0.06 + softness * 0.02) +
            haloTail * (0.03 + softness * 0.02 + haloVisibilityFloor * 0.03),
            0.0,
            1.0
        ) * haloVisibility * haloOpacity;

        accumulatedGlow += contribution;
        accumulatedAlpha = clamp(accumulatedAlpha + alphaContribution * (1.0 - accumulatedAlpha), 0.0, 1.0);
    }

    accumulatedAlpha = clamp(accumulatedAlpha + luminance(accumulatedGlow) * 0.006, 0.0, 1.0);
    fragColor = vec4(accumulatedGlow, accumulatedAlpha);
}
