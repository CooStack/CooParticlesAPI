#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform sampler2D bright;
uniform float intensity = 1.0;
uniform int mipLevels = 4;
// 曝光补偿倍率，默认 1.0 保持旧行为
uniform float exposure = 1.0;

const int MAX_BLOOM_MIPS = 6;
const vec3 TENT_KERNEL[9] = vec3[9](
    vec3(-1.0,  1.0, 1.0), vec3(0.0,  1.0, 2.0), vec3(1.0,  1.0, 1.0),
    vec3(-1.0,  0.0, 2.0), vec3(0.0,  0.0, 4.0), vec3(1.0,  0.0, 2.0),
    vec3(-1.0, -1.0, 1.0), vec3(0.0, -1.0, 2.0), vec3(1.0, -1.0, 1.0)
);

vec3 sampleMipTent(vec2 uv, int lodIndex) {
    vec2 texel = 1.0 / max(vec2(textureSize(bright, lodIndex)), vec2(1.0));
    vec3 color = vec3(0.0);
    float weightSum = 0.0;

    for (int i = 0; i < 9; i++) {
        vec3 kernel = TENT_KERNEL[i];
        color += textureLod(bright, uv + kernel.xy * texel, float(lodIndex)).rgb * kernel.z;
        weightSum += kernel.z;
    }

    return color / max(weightSum, 1.0e-4);
}

vec3 accumulateBloom(vec2 uv) {
    vec3 color = vec3(0.0);
    float weightSum = 0.0;
    int levels = clamp(mipLevels, 1, MAX_BLOOM_MIPS);

    for (int level = 0; level < MAX_BLOOM_MIPS; level++) {
        if (level >= levels) {
            break;
        }
        float weight = 1.0 / exp2(float(level));
        color += sampleMipTent(uv, level) * weight;
        weightSum += weight;
    }

    return color / max(weightSum, 1.0e-4);
}

void main() {
    vec4 base = texture(scene, screen_uv);
    vec3 bloom = accumulateBloom(screen_uv) * intensity * exposure;
    FragColor = vec4(base.rgb + bloom, base.a);
}
