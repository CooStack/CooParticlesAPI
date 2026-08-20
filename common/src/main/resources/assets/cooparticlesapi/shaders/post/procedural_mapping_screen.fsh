#version 150

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform sampler2D SceneDepthNoHand;
uniform sampler2D TerrainOpaqueDepth;
uniform sampler2D TerrainTranslucentDepthBefore;
uniform sampler2D TerrainTranslucentDepthAfter;
uniform mat4 cooInverseViewProjection;
uniform vec4 CooMappingRegion;
uniform float CooMappingProgress = 0.0;
uniform float Blackness = 1.0;
uniform float Feather = 0.06;

vec3 reconstructRelativePosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 relative = cooInverseViewProjection * clip;
    return relative.xyz / max(abs(relative.w), 0.00001);
}

void main() {
    vec4 scene = texture(SceneColor, screen_uv);
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
    float radius = max(CooMappingRegion.w * progress, 0.0001);
    float featherWidth = max(CooMappingRegion.w * Feather, 0.0001);
    float distanceToCenter = distance(relativePosition, CooMappingRegion.xyz);
    float circularMask = 1.0 - smoothstep(
        max(radius - featherWidth, 0.0),
        radius,
        distanceToCenter
    );
    float mask = circularMask * clamp(Blackness, 0.0, 1.0);
    FragColor = vec4(mix(scene.rgb, vec3(0.0), mask), scene.a);
}
