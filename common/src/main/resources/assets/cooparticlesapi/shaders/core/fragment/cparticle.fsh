#version 150

// ================= cparticle GPU 粒子渲染 - 片元着色器 =================

in vec2 vUv;
in vec4 vColor;
in vec2 vLightUv;
in float vFogDistance;

uniform sampler2D uAtlas;    // 原版粒子图集
uniform sampler2D uLightmap; // 光照贴图
uniform float uFogStart;
uniform float uFogEnd;
uniform vec4 uFogColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(uAtlas, vUv);
    vec4 color = tex * vColor;
    // 与本项目覆盖的 particle.fsh 一致: 极低 alpha 才丢弃
    if (color.a <= 0.001) {
        discard;
    }
    vec4 light = texture(uLightmap, vLightUv);
    color.rgb *= light.rgb;

    // 原版线性雾
    float fogValue = vFogDistance < uFogStart
        ? 0.0
        : (vFogDistance > uFogEnd ? 1.0 : (vFogDistance - uFogStart) / (uFogEnd - uFogStart));
    color.rgb = mix(color.rgb, uFogColor.rgb, fogValue * uFogColor.a);

    fragColor = color;
}
