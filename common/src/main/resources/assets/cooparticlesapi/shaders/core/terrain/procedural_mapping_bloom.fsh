#version 330 core

#coo_import <terrain_light_fog.glsl>

uniform sampler2D BaseSampler;
uniform sampler2D SceneColor;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform vec4 CooMappingRegion;
uniform vec3 CooMappingRegionSize;
uniform int CooMappingRegionType;
uniform float CooMappingProgress;
uniform int CooMappingDepthAvailable;
uniform int CooIrisComposite;
uniform int CooMappingComposition;
uniform float CooAlphaCutoff;
uniform float RingRadius;
uniform float RingWidth;
uniform float RingIntensity;
uniform vec3 RingColor;
uniform float RingProgress;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
in vec3 worldPosition;
in vec3 worldNormal;

layout(location = 0) out vec4 FragColor;
layout(location = 1) out vec4 MaskColor;

float ringBand(float distanceValue, float width) {
    return 1.0 - smoothstep(width, width * 2.2, abs(distanceValue));
}

float signedRegionDistance(vec3 delta, float progress) {
    if (CooMappingRegionType == 1) {
        vec3 halfExtents = CooMappingRegionSize * progress;
        vec3 q = abs(delta) - halfExtents;
        return length(max(q, vec3(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0);
    }
    if (CooMappingRegionType == 2) {
        vec2 q = vec2(length(delta.xz), abs(delta.y)) -
            vec2(CooMappingRegionSize.x, CooMappingRegionSize.y) * progress;
        return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0);
    }
    return length(delta) - CooMappingRegion.w * progress;
}

void main() {
    vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
    if (atlasColor.a < CooAlphaCutoff) {
        discard;
    }

    vec3 delta = worldPosition - CooMappingRegion.xyz;
    float progress = clamp(CooMappingProgress, 0.0, 1.0);
    float regionDistance = signedRegionDistance(delta, progress);
    float regionMask = 1.0 - smoothstep(0.0, max(RingWidth, 0.01), regionDistance);
    float ringCoordinate = CooMappingRegionType == 0
        ? length(delta) - RingRadius
        : -regionDistance - RingRadius;
    float ring = ringBand(ringCoordinate, max(RingWidth, 0.01)) * regionMask;
    ring *= progress * clamp(RingProgress, 0.0, 1.0);

    // ADDITIVE 合成只保留效果区域，避免覆盖原场景颜色。
    if (CooMappingComposition == 2 && ring <= 0.0) {
        discard;
    }

    vec3 baseColor = atlasColor.rgb;
    if (CooIrisComposite != 0) {
        baseColor = texture(SceneColor, gl_FragCoord.xy / max(ScreenSize, vec2(1.0))).rgb;
    }

    float facing = mix(0.72, 1.0, abs(normalize(worldNormal).y));
    vec3 emissive = RingColor * ring * RingIntensity * facing;
    float ringAlpha = atlasColor.a * clamp(ring, 0.0, 1.0);
    float effectAlpha = CooMappingComposition == 2 ? atlasColor.a : ringAlpha;
    vec3 mappedColor = baseColor + emissive;
    vec3 outputColor = mix(baseColor, mappedColor, clamp(ring * 1.4, 0.0, 1.0));
    float outputAlpha = atlasColor.a;
    if (CooMappingComposition != 0) {
        outputAlpha *= clamp(ring, 0.0, 1.0);
    }

    vec4 color = vec4(outputColor, outputAlpha);
    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    vec3 foggedEmissive = emissive * fogFade;
    FragColor = CooMappingComposition == 2
        ? vec4(foggedEmissive, effectAlpha)
        : (CooIrisComposite != 0
            ? color
            : linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor));
    MaskColor = vec4(foggedEmissive, effectAlpha);
}
