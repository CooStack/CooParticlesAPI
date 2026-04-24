#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D tex;
uniform vec4 tint;
uniform float sourceBoost;
uniform float alphaCutoff;
uniform float emissiveCutoff;
uniform float alphaWeight;
uniform float emissiveWeight;
uniform bool fullQuadMask;
uniform float fullQuadMaskSoftness;
uniform bool useSpriteUv;
uniform vec4 spriteUvRect;

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

float maxChannel(vec3 value) {
    return max(value.r, max(value.g, value.b));
}

float rectangularFeather(vec2 sampleUv, float softness) {
    vec2 toEdge = min(sampleUv, vec2(1.0) - sampleUv);
    float edgeDistance = min(toEdge.x, toEdge.y);
    return smoothstep(0.0, clamp(softness, 1.0e-4, 0.45), edgeDistance);
}

vec2 resolveUv(vec2 baseUv) {
    if (!useSpriteUv) {
        return baseUv;
    }
    return spriteUvRect.xy + baseUv * spriteUvRect.zw;
}

void main() {
    vec4 rawSample = texture(tex, resolveUv(uv));
    vec3 boostedRgb = rawSample.rgb * tint.rgb * max(sourceBoost, 0.0);
    vec4 sampled = vec4(boostedRgb, rawSample.a * tint.a);
    if (fullQuadMask) {
        float face = rectangularFeather(uv, fullQuadMaskSoftness);
        if (face <= 1.0e-4) {
            discard;
        }
        fragColor = vec4(max(tint.rgb, vec3(0.0)) * face, face);
        return;
    }

    float alphaMask = smoothstep(alphaCutoff, alphaCutoff + 0.02, sampled.a) * alphaWeight;
    float emissiveMask = smoothstep(
        emissiveCutoff,
        emissiveCutoff + 0.02,
        luminance(sampled.rgb)
    ) * emissiveWeight;
    float mask = clamp(max(alphaMask, emissiveMask), 0.0, 1.0);
    if (mask <= 1.0e-4) {
        discard;
    }

    float sourceAlpha = clamp(
        max(sampled.a * alphaWeight, maxChannel(sampled.rgb) * emissiveWeight),
        0.0,
        1.0
    );
    fragColor = vec4(sampled.rgb * mask, max(mask, sourceAlpha));
}
