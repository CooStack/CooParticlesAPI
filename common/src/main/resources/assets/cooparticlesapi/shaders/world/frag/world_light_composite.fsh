#version 330 core

in vec2 screen_uv;
out vec4 fragColor;

uniform sampler2D sceneTex;
uniform sampler2D sceneDepth;

uniform int worldLightCount;
uniform vec2 screenSize;
uniform vec3 cameraWorldPos;
uniform mat4 projMat;
uniform mat3 viewRotationMat;
uniform mat4 inverseProjMat;
uniform mat3 inverseViewRotationMat;

const int MAX_WORLD_LIGHTS = 8;
const int WORLD_LIGHT_POINT = 0;
const int WORLD_LIGHT_DISK = 1;
const float SKY_DEPTH = 0.999999;
const int SHADOW_BLOCKER_STEPS = 4;
const int SHADOW_FILTER_STEPS = 4;

const vec2 SHADOW_FILTER_OFFSETS[SHADOW_FILTER_STEPS] = vec2[](
    vec2(-0.65, -1.0),
    vec2(0.65, 1.0),
    vec2(-0.28, 0.55),
    vec2(0.28, -0.55)
);

uniform vec3 lightPositions[MAX_WORLD_LIGHTS];
uniform vec3 lightColors[MAX_WORLD_LIGHTS];
uniform vec3 lightNormals[MAX_WORLD_LIGHTS];
uniform vec4 lightData[MAX_WORLD_LIGHTS];

vec3 reconstructWorldPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = inverseProjMat * clip;
    view /= max(view.w, 1.0e-5);
    return cameraWorldPos + inverseViewRotationMat * view.xyz;
}

bool projectWorldToScreen(vec3 worldPosition, out vec2 uv, out float depth) {
    vec3 cameraRelative = worldPosition - cameraWorldPos;
    vec3 viewPosition = viewRotationMat * cameraRelative;
    vec4 clip = projMat * vec4(viewPosition, 1.0);
    if (clip.w <= 1.0e-5) {
        uv = vec2(-1.0);
        depth = 1.0;
        return false;
    }

    vec3 ndc = clip.xyz / clip.w;
    uv = ndc.xy * 0.5 + 0.5;
    depth = ndc.z * 0.5 + 0.5;
    return ndc.z > -1.0 && ndc.z < 1.0;
}

vec3 worldPositionFromPixelOffset(vec2 uv, vec2 pixelOffset) {
    vec2 targetUv = clamp(uv + pixelOffset / screenSize, vec2(0.0), vec2(1.0));
    float depth = texture(sceneDepth, targetUv).r;
    if (depth >= SKY_DEPTH) {
        return vec3(1.0e20);
    }
    return reconstructWorldPosition(targetUv, depth);
}

vec3 chooseDerivative(vec3 negativeSample, vec3 centerSample, vec3 positiveSample) {
    bool negativeValid = negativeSample.x < 1.0e10;
    bool positiveValid = positiveSample.x < 1.0e10;

    if (negativeValid && positiveValid) {
        return positiveSample - negativeSample;
    }
    if (positiveValid) {
        return positiveSample - centerSample;
    }
    if (negativeValid) {
        return centerSample - negativeSample;
    }
    return vec3(0.0);
}

vec3 reconstructNormal(vec2 uv, vec3 worldPosition) {
    vec3 left = worldPositionFromPixelOffset(uv, vec2(-1.0, 0.0));
    vec3 right = worldPositionFromPixelOffset(uv, vec2(1.0, 0.0));
    vec3 down = worldPositionFromPixelOffset(uv, vec2(0.0, -1.0));
    vec3 up = worldPositionFromPixelOffset(uv, vec2(0.0, 1.0));
    vec3 dX = chooseDerivative(left, worldPosition, right);
    vec3 dY = chooseDerivative(down, worldPosition, up);
    if (length(dX) <= 1.0e-6 || length(dY) <= 1.0e-6) {
        return normalize(cameraWorldPos - worldPosition);
    }
    vec3 normal = normalize(cross(dX, dY));
    vec3 viewDirection = normalize(cameraWorldPos - worldPosition);
    if (dot(normal, viewDirection) < 0.0) {
        normal = -normal;
    }
    return normal;
}

float pointLightFalloff(float distanceToLight, float lightRadius, float softness) {
    float normalizedDistance = distanceToLight / max(lightRadius, 1.0e-4);
    float cutoff = 1.0 - smoothstep(1.0 - softness, 1.0, normalizedDistance);
    float inverseSquareLike = 1.0 / (1.0 + normalizedDistance * normalizedDistance * 4.0);
    return cutoff * inverseSquareLike;
}

float diskLightFalloff(vec3 worldPosition, vec3 lightPosition, vec3 lightNormal, float lightRadius, float softness) {
    vec3 toSurface = worldPosition - lightPosition;
    float planeDistance = abs(dot(toSurface, lightNormal));
    vec3 radialVector = toSurface - lightNormal * dot(toSurface, lightNormal);
    float radialDistance = length(radialVector);
    float radialFactor = 1.0 - smoothstep(lightRadius * (1.0 - softness), lightRadius, radialDistance);
    float thickness = max(lightRadius * 0.18, 0.35);
    float planeFactor = 1.0 - smoothstep(thickness * (1.0 - softness), thickness, planeDistance);
    return radialFactor * planeFactor;
}

float sampleShadowOcclusion(float rayDepth, vec2 sampleUv, float t) {
    if (sampleUv.x <= 0.001 || sampleUv.x >= 0.999 || sampleUv.y <= 0.001 || sampleUv.y >= 0.999) {
        return 0.0;
    }

    float sceneDepthAtSample = texture(sceneDepth, sampleUv).r;
    if (sceneDepthAtSample >= SKY_DEPTH) {
        return 0.0;
    }

    float depthBias = mix(0.0014, 0.0062, t);
    float depthDelta = rayDepth - sceneDepthAtSample;
    if (depthDelta <= depthBias) {
        return 0.0;
    }
    return smoothstep(depthBias, depthBias * 5.0, depthDelta);
}

float computeShadowVisibility(vec3 worldPosition, vec3 normal, vec3 lightPosition, float distanceToLight, float softness) {
    if (distanceToLight <= 0.35) {
        return 1.0;
    }

    vec3 rayStart = worldPosition + normal * clamp(distanceToLight * 0.02, 0.08, 0.32);
    vec2 lightUv;
    float lightDepth;
    if (!projectWorldToScreen(lightPosition, lightUv, lightDepth)) {
        return 1.0;
    }

    vec2 rayPixels = (lightUv - screen_uv) * screenSize;
    float rayLengthPx = length(rayPixels);
    if (rayLengthPx <= 1.5) {
        return 1.0;
    }

    vec2 rayDirectionPx = rayPixels / rayLengthPx;
    vec2 rayPerpendicularPx = vec2(-rayDirectionPx.y, rayDirectionPx.x);

    float blockerOcclusion = 0.0;
    float blockerWeight = 0.0;
    for (int step = 0; step < SHADOW_BLOCKER_STEPS; ++step) {
        float t = (float(step) + 1.0) / float(SHADOW_BLOCKER_STEPS + 1);
        vec3 blockerPosition = mix(rayStart, lightPosition, t);
        vec2 blockerUv;
        float blockerDepth;
        if (!projectWorldToScreen(blockerPosition, blockerUv, blockerDepth)) {
            continue;
        }

        float occlusion = sampleShadowOcclusion(blockerDepth, blockerUv, t);
        blockerOcclusion += occlusion;
        blockerWeight += occlusion * t;
    }

    if (blockerOcclusion <= 1.0e-4) {
        return 1.0;
    }

    float averageBlockerT = blockerWeight / blockerOcclusion;
    float centerLineOcclusion = clamp(blockerOcclusion / float(SHADOW_BLOCKER_STEPS), 0.0, 1.0);
    float penumbraPx = mix(1.0, rayLengthPx * 0.18, averageBlockerT) * (0.55 + softness * 1.55);
    float filterOcclusion = 0.0;

    for (int tap = 0; tap < SHADOW_FILTER_STEPS; ++tap) {
        float tapPhase = (float(tap) + 0.5) / float(SHADOW_FILTER_STEPS);
        float t = mix(max(0.18, averageBlockerT * 0.55), min(0.98, averageBlockerT + 0.22), tapPhase);
        vec3 samplePosition = mix(rayStart, lightPosition, t);
        vec2 sampleUv;
        float sampleDepth;
        if (!projectWorldToScreen(samplePosition, sampleUv, sampleDepth)) {
            continue;
        }

        vec2 offsetUv = (rayPerpendicularPx * SHADOW_FILTER_OFFSETS[tap].y * penumbraPx +
            rayDirectionPx * SHADOW_FILTER_OFFSETS[tap].x * penumbraPx * 0.28) / screenSize;
        filterOcclusion += sampleShadowOcclusion(sampleDepth, sampleUv + offsetUv, t);
    }

    float filteredOcclusion = filterOcclusion / float(SHADOW_FILTER_STEPS);
    float shadow = clamp(mix(centerLineOcclusion, filteredOcclusion, 0.72), 0.0, 1.0);
    return 1.0 - shadow;
}

void main() {
    vec4 sceneColor = texture(sceneTex, screen_uv);
    float rawDepth = texture(sceneDepth, screen_uv).r;
    if (rawDepth >= SKY_DEPTH || worldLightCount <= 0) {
        fragColor = sceneColor;
        return;
    }

    vec3 worldPosition = reconstructWorldPosition(screen_uv, rawDepth);
    vec3 normal = reconstructNormal(screen_uv, worldPosition);
    vec3 accumulatedLight = vec3(0.0);

    for (int i = 0; i < worldLightCount; i++) {
        vec3 lightPosition = lightPositions[i];
        vec3 lightColor = lightColors[i];
        vec3 lightNormal = normalize(lightNormals[i]);
        vec4 metadata = lightData[i];
        float lightRadius = metadata.x;
        float lightIntensity = metadata.y;
        int lightShape = int(metadata.z + 0.5);
        float softness = clamp(metadata.w, 0.05, 0.95);

        vec3 toLight = lightPosition - worldPosition;
        float distanceToLight = length(toLight);
        if (distanceToLight >= lightRadius) {
            continue;
        }

        vec3 lightDirection = distanceToLight > 1.0e-4 ? toLight / distanceToLight : normal;
        float diffuse = dot(normal, lightDirection);
        if (diffuse <= 0.0) {
            continue;
        }

        float attenuation = pointLightFalloff(distanceToLight, lightRadius, softness);
        if (lightShape == WORLD_LIGHT_DISK) {
            attenuation *= diskLightFalloff(worldPosition, lightPosition, lightNormal, lightRadius, softness);
        }
        attenuation *= computeShadowVisibility(worldPosition, normal, lightPosition, distanceToLight, softness);
        if (attenuation <= 1.0e-4) {
            continue;
        }

        accumulatedLight += lightColor * (lightIntensity * attenuation * diffuse);
    }

    fragColor = vec4(sceneColor.rgb + accumulatedLight, sceneColor.a);
}
