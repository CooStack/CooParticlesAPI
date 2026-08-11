#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D Input;
uniform float Sigma = 14.0;
uniform float Range = 10.0;
uniform bool Horizontal = true;

const int GAUSSIAN_SAMPLES = 10;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Input, 0));
    vec2 direction = Horizontal ? vec2(texel.x, 0.0) : vec2(0.0, texel.y);
    float sigma = max(abs(Sigma), 0.1);
    float radius = clamp(abs(Range), 1.0, 32.0);
    vec4 color = texture(Input, screen_uv);
    float totalWeight = 1.0;
    for (int i = 1; i <= GAUSSIAN_SAMPLES; i++) {
        float sampleOffset = radius * float(i) / float(GAUSSIAN_SAMPLES);
        float weight = exp(-0.5 * (sampleOffset * sampleOffset) / (sigma * sigma));
        vec2 offset = direction * sampleOffset;
        color += texture(Input, screen_uv + offset) * weight;
        color += texture(Input, screen_uv - offset) * weight;
        totalWeight += weight * 2.0;
    }
    FragColor = color / totalWeight;
}
