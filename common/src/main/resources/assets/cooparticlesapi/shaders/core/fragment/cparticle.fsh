#version 150

// ================= cparticle GPU 粒子渲染 - 片元着色器 =================

in vec2 vUv;
in vec4 vColor;
in vec2 vLightUv;
in float vFogDistance;

uniform sampler2D uMainTexture; // 当前系统绑定的图集或独立纹理
uniform sampler2D uLightmap; // 光照贴图
uniform float uFogStart;
uniform float uFogEnd;
uniform vec4 uFogColor;
uniform int uDepthOnly;

out vec4 fragColor;

void main() {
    vec4 tex = texture(uMainTexture, vUv);
    // 与本项目覆盖的 particle.fsh 一致: 极低 alpha 才丢弃
    if (tex.a * vColor.a <= 0.001) {
        discard;
    }
    if (uDepthOnly != 0) {
        fragColor = vec4(0.0);
        return;
    }
    vec4 color = tex * vColor;
    vec4 light = texture(uLightmap, vLightUv);
    color.rgb *= light.rgb;

    // 原版线性雾
    float fogValue = vFogDistance < uFogStart
        ? 0.0
        : (vFogDistance > uFogEnd ? 1.0 : (vFogDistance - uFogStart) / (uFogEnd - uFogStart));
    color.rgb = mix(color.rgb, uFogColor.rgb, fogValue * uFogColor.a);

    fragColor = color;
}
