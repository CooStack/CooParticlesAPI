#version 330 core

in vec2 uv;

uniform sampler2D tex;
uniform vec4 tint;
uniform float sourceBoost;
uniform float alphaCutoff;
uniform float emissiveCutoff;
uniform float alphaWeight;
uniform float emissiveWeight;
uniform bool fullQuadMask;
uniform float fullQuadMaskSoftness;

out vec4 FragColor;

void main() {
    vec4 texel = texture(tex, uv);
    float softness = clamp(fullQuadMaskSoftness, 0.001, 1.0);
    float alphaMask = smoothstep(alphaCutoff, alphaCutoff + 0.001 + softness, texel.a) * alphaWeight;
    float emissive = max(max(texel.r, texel.g), texel.b);
    float emissiveMask = smoothstep(emissiveCutoff, emissiveCutoff + 0.001 + softness, emissive) * emissiveWeight;
    float mask = clamp(max(alphaMask, emissiveMask), 0.0, 1.0);

    if (fullQuadMask) {
        vec2 edgeUv = abs(uv - vec2(0.5)) * 2.0;
        float edge = max(edgeUv.x, edgeUv.y);
        float quadMask = 1.0 - smoothstep(1.0 - softness, 1.0, edge);
        mask = max(mask, quadMask);
    }

    if (mask <= 0.001) {
        discard;
    }

    vec3 sourceColor = max(texel.rgb, vec3(mask)) * tint.rgb * max(sourceBoost, 0.0);
    FragColor = vec4(sourceColor, mask * tint.a);
}
