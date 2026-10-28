#version 150

// ================= cparticle GPU 粒子渲染 - 片元着色器 =================

in vec2 vUv;
in vec2 vMaskUv;
in vec3 vMaskTint;
in vec4 vColor;
in vec2 vLightUv;
in vec2 vFogDistance;

uniform sampler2D uMainTexture; // 当前系统绑定的基础图集或独立纹理
uniform sampler2D uMaskTexture; // 可选的额外纹理蒙版
uniform int uHasMask;
uniform sampler2D uLightmap; // 光照贴图
uniform float uFogEnvironmentalStart;
uniform float uFogEnvironmentalEnd;
uniform float uFogRenderDistanceStart;
uniform float uFogRenderDistanceEnd;
uniform vec4 uFogColor;
uniform int uDepthOnly;
uniform int uCoverageMask;
uniform int uPremultiplyRgbByAlpha;

out vec4 fragColor;

// 与 26.3 原版 fog.glsl 一致，先处理端点以避免重合范围产生除零。
float linearFogValue(float distanceToCamera, float start, float end) {
    if (distanceToCamera <= start) return 0.0;
    if (distanceToCamera >= end) return 1.0;
    return (distanceToCamera - start) / (end - start);
}

void main() {
    vec4 tex = texture(uMainTexture, vUv);
    if (uHasMask != 0) {
        vec4 mask = texture(uMaskTexture, vMaskUv);
        mask.rgb *= vMaskTint;
        tex.rgb *= mix(vec3(1.0), mask.rgb, mask.a);
        tex.a *= mask.a;
    }
    // 与库内 coo_particle 程序一致，仅丢弃极低透明度。
    if (tex.a * vColor.a < 0.001) {
        discard;
    }
    vec4 color = tex * vColor;
    vec4 light = texture(uLightmap, vLightUv);
    color.rgb *= light.rgb;

    // 环境雾使用球形距离，视距雾使用柱形距离，两者取较强的遮蔽量。
    float fogValue = max(
        linearFogValue(vFogDistance.x, uFogEnvironmentalStart, uFogEnvironmentalEnd),
        linearFogValue(vFogDistance.y, uFogRenderDistanceStart, uFogRenderDistanceEnd)
    );
    color.rgb = mix(color.rgb, uFogColor.rgb, fogValue * uFogColor.a);
    if (uCoverageMask != 0) {
        // 只记录软覆盖度；Mapping 不使用本地粒子 shader 的 RGB。
        float coverage = clamp(tex.a * vColor.a, 0.0, 1.0);
        fragColor = vec4(coverage);
        return;
    }
    if (uDepthOnly != 0) {
        fragColor = vec4(0.0);
        return;
    }
    if (uPremultiplyRgbByAlpha != 0) {
        color.a = clamp(color.a, 0.0, 1.0);
        color.rgb = clamp(color.rgb, 0.0, 1.0) * color.a;
    }

    fragColor = color;
}
