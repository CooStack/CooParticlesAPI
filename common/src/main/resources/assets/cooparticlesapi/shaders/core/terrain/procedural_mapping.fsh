#version 150

#coo_import <terrain_light_fog.glsl>

uniform sampler2D BaseSampler;
uniform sampler2D TerrainDepth;
uniform sampler2D SceneColor;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform vec4 CooMappingRegion;
uniform float CooMappingProgress;
uniform int CooMappingDepthAvailable;
uniform int CooIrisComposite;
uniform int CooMappingComposition;
uniform float CooAlphaCutoff;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
in vec3 worldPosition;

out vec4 fragColor;

void main() {
    vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
    vec3 baseColor = atlasColor.rgb;
    if (CooIrisComposite != 0) {
        baseColor = texture(SceneColor, gl_FragCoord.xy / max(ScreenSize, vec2(1.0))).rgb;
    }

    vec3 delta = worldPosition - CooMappingRegion.xyz;
    float distanceToCenter = length(delta);
    float mask = 0.0;
    if (CooMappingRegion.w > 0.0 && distanceToCenter <= CooMappingRegion.w) {
        mask = 1.0 - smoothstep(CooMappingRegion.w * 0.85, CooMappingRegion.w, distanceToCenter);
    }
    if (CooMappingDepthAvailable == 0) {
        mask = 0.0;
    }
    mask *= clamp(CooMappingProgress + 0.001, 0.0, 1.0);

    // 零遮罩保留原色；有效遮罩通过进度驱动中性亮度结果。
    vec3 mappedColor = baseColor * mix(0.65, 1.0, CooMappingProgress);
    vec3 outputColor = mix(baseColor, mappedColor, mask);
    float outputAlpha = atlasColor.a;
    if (CooMappingComposition != 0) {
        outputAlpha *= mask;
    }
    vec4 color = vec4(outputColor, outputAlpha);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
