#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D Input;
uniform float Sigma = 14.0;
uniform float Range = 10.0;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Input, 0));
    float sigma = max(Sigma, 0.1);
    int radius = max(1, min(32, int(Range)));
    vec4 color = texture(Input, screen_uv);
    float totalWeight = 1.0;
    for (int i = 1; i <= radius; i++) {
        float sampleOffset = float(i);
        float weight = exp(-0.5 * sampleOffset * sampleOffset / (sigma * sigma));
        vec2 offset = vec2(0.0, texel.y * float(i));
        color += texture(Input, screen_uv + offset) * weight;
        color += texture(Input, screen_uv - offset) * weight;
        totalWeight += weight * 2.0;
    }
    FragColor = color / totalWeight;
}
