#version 150

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform sampler2D SceneDepthNoHand;
uniform sampler2D TerrainOpaqueDepth;
uniform sampler2D TerrainTranslucentDepthBefore;
uniform sampler2D TerrainTranslucentDepthAfter;
uniform sampler2D CParticleCoverageMask;
uniform mat4 cooInverseViewProjection;
uniform vec4 CooMappingRegion;
uniform vec3 CooMappingRegionSize;
uniform int CooMappingRegionType;
uniform int CooHasCParticleCoverage = 0;
uniform float CooMappingProgress = 0.0;
uniform float Blackness = 1.0;
uniform float Feather = 0.06;

vec3 reconstructRelativePosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 relative = cooInverseViewProjection * clip;
    return relative.xyz / max(abs(relative.w), 0.00001);
}

float mappingSignedDistance(vec3 relativePosition, float progress) {
    vec3 delta = relativePosition - CooMappingRegion.xyz;
    if (CooMappingRegionType == 1) {
        vec3 halfExtents = CooMappingRegionSize * progress;
        vec3 outside = max(abs(delta) - halfExtents, vec3(0.0));
        float inside = min(max(abs(delta).x - halfExtents.x, max(abs(delta).y - halfExtents.y, abs(delta).z - halfExtents.z)), 0.0);
        return length(outside) + inside;
    }
    if (CooMappingRegionType == 2) {
        vec2 radial = vec2(length(delta.xz), abs(delta.y));
        vec2 halfSize = vec2(CooMappingRegionSize.x, CooMappingRegionSize.y) * progress;
        vec2 outside = max(radial - halfSize, vec2(0.0));
        float inside = min(max(radial.x - halfSize.x, radial.y - halfSize.y), 0.0);
        return length(outside) + inside;
    }
    return length(delta) - CooMappingRegion.w * progress;
}

void main() {
    vec4 scene = texture(SceneColor, screen_uv);
    vec4 particleSample = CooHasCParticleCoverage != 0
        ? texture(CParticleCoverageMask, screen_uv)
        : vec4(0.0);
    float particleCoverage = CooHasCParticleCoverage != 0
        ? clamp(particleSample.r, 0.0, 1.0)
        : 0.0;
    float sceneDepth = texture(SceneDepth, screen_uv).r;
    float sceneDepthNoHand = texture(SceneDepthNoHand, screen_uv).r;
    float opaqueDepth = texture(TerrainOpaqueDepth, screen_uv).r;
    float translucentBeforeDepth = texture(TerrainTranslucentDepthBefore, screen_uv).r;
    float translucentAfterDepth = texture(TerrainTranslucentDepthAfter, screen_uv).r;
    float depthTolerance = max(0.0001, sceneDepth * 0.00005);
    bool handDepthChanged = abs(sceneDepth - sceneDepthNoHand) > depthTolerance;
    bool visibleOpaqueTerrain = !handDepthChanged && opaqueDepth < 0.999999 &&
        abs(sceneDepth - opaqueDepth) <= depthTolerance;
    bool visibleTranslucentTerrain = !handDepthChanged && translucentAfterDepth < 0.999999 &&
        abs(translucentAfterDepth - translucentBeforeDepth) > depthTolerance &&
        abs(sceneDepth - translucentAfterDepth) <= depthTolerance;

    if (!visibleOpaqueTerrain && !visibleTranslucentTerrain) {
        FragColor = scene;
        return;
    }

    float terrainDepth = visibleOpaqueTerrain ? opaqueDepth : translucentAfterDepth;
    vec3 relativePosition = reconstructRelativePosition(screen_uv, terrainDepth);
    float progress = clamp(CooMappingProgress, 0.0, 1.0);
    if (progress <= 0.000001) {
        FragColor = scene;
        return;
    }
    float shapeScale = CooMappingRegionType == 0
        ? CooMappingRegion.w
        : max(max(CooMappingRegionSize.x, CooMappingRegionSize.y), CooMappingRegionSize.z);
    float featherWidth = max(shapeScale * Feather, 0.0001);
    float signedDistance = mappingSignedDistance(relativePosition, progress);
    float shapeMask = 1.0 - smoothstep(-featherWidth, 0.0, signedDistance);
    // CParticle 已经经过 Iris 的粒子 shader；coverage 只阻止 Mapping 覆盖粒子像素。
    float mask = shapeMask * clamp(Blackness, 0.0, 1.0) * (1.0 - particleCoverage);
    FragColor = vec4(mix(scene.rgb, vec3(0.0), mask), scene.a);
}
