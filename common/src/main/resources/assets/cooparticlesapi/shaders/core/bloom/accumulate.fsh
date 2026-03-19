#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene; // mipmapped bloom texture
uniform float intensity = 1.0;
uniform int levels;

vec4 sampleMipTent(vec2 uv, int lodIndex) {
    vec2 texelSize = 1.0 / vec2(textureSize(scene, lodIndex));
    vec4 accum = vec4(0.0);
    float weightSum = 0.0;

    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            float wx = (x == 0) ? 2.0 : 1.0;
            float wy = (y == 0) ? 2.0 : 1.0;
            float weight = wx * wy;
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            accum += textureLod(scene, uv + offset, float(lodIndex)) * weight;
            weightSum += weight;
        }
    }

    return accum / max(weightSum, 1.0e-4);
}

void main() {
    vec4 result = vec4(0.0);
    float weightSum = 0.0;

    for (int i = 0; i < levels; i++) {
        vec4 mipColor = sampleMipTent(screen_uv, i);
        float weight = 1.0 / pow(2.0, i);
        result += mipColor * weight;
        weightSum += weight;
    }

    if (weightSum <= 1.0e-4) {
        FragColor = vec4(0.0);
        return;
    }

    vec4 bloom = result / weightSum;
    FragColor = vec4(bloom.rgb * intensity, clamp(bloom.a, 0.0, 1.0));
}
