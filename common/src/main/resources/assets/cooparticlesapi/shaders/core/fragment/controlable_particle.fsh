#version 330

in vec2 vUv;
in vec4 vColor;
in vec2 vLightUv;
in vec2 vFogDistance;

uniform sampler2D uMainTexture;
uniform sampler2D uLightmap;
uniform float uFogEnvironmentalStart;
uniform float uFogEnvironmentalEnd;
uniform float uFogRenderDistanceStart;
uniform float uFogRenderDistanceEnd;
uniform vec4 uFogColor;

out vec4 fragColor;

// 与原版 26.3 保持相同的雾端点，重合区间不进行除法。
float linearFogValue(float distanceToCamera, float start, float end) {
    if (distanceToCamera <= start) return 0.0;
    if (distanceToCamera >= end) return 1.0;
    return (distanceToCamera - start) / (end - start);
}

void main() {
    vec4 color = texture(uMainTexture, vUv) * vColor;
    if (color.a < 0.001) {
        discard;
    }
    color.rgb *= texture(uLightmap, vLightUv).rgb;
    float fogValue = max(
        linearFogValue(vFogDistance.x, uFogEnvironmentalStart, uFogEnvironmentalEnd),
        linearFogValue(vFogDistance.y, uFogRenderDistanceStart, uFogRenderDistanceEnd)
    );
    color.rgb = mix(color.rgb, uFogColor.rgb, fogValue * uFogColor.a);
    color.a = clamp(color.a, 0.0, 1.0);
    color.rgb = clamp(color.rgb, 0.0, 1.0) * color.a;
    fragColor = color;
}
