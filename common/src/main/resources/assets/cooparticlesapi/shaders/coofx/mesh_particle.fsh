#version 150 core

#coo_import <terrain_light_fog.glsl>

uniform sampler2D uBaseColor;
uniform sampler2D uEmissiveTexture;
uniform sampler2D uLightmap;
uniform vec4 uBaseColorFactor;
uniform vec3 uEmissiveFactor;
uniform float uEmissiveStrength;
uniform bool uHasBaseColorTexture;
uniform bool uHasEmissiveTexture;
uniform bool uUseAlphaCutoff;
uniform bool uEmissiveOnly;
uniform float uAlphaCutoff;

in vec2 vTexCoord;
in vec4 vColor;
in vec3 vWorldNormal;
flat in ivec2 vLightUv;

out vec4 fragColor;

void main() {
    vec4 sampled = uHasBaseColorTexture ? texture(uBaseColor, vTexCoord) : vec4(1.0);
    vec4 color = sampled * uBaseColorFactor * vColor;
    if (uUseAlphaCutoff && color.a < uAlphaCutoff) {
        discard;
    }
    vec3 litColor = color.rgb * minecraft_sample_lightmap(uLightmap, vLightUv).rgb;
    vec3 emissiveSample = uHasEmissiveTexture ? texture(uEmissiveTexture, vTexCoord).rgb : vec3(1.0);
    vec3 emissiveColor = emissiveSample * uEmissiveFactor * uEmissiveStrength * vColor.rgb;
    fragColor = uEmissiveOnly
        ? vec4(emissiveColor, 0.0)
        : vec4(litColor + emissiveColor, color.a);
}
