#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D bright;
uniform float blurRadius = 4.0;
uniform int Axis = 0;
uniform int Iteration = 0;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(bright, 0));
    vec2 direction = Axis == 0 ? vec2(texel.x, 0.0) : vec2(0.0, texel.y);
    float spread = max(1.0, blurRadius) * (1.0 + float(Iteration) * 0.15);
    vec4 color = texture(bright, screen_uv) * 0.227027;
    for (int sampleIndex = 1; sampleIndex <= 4; sampleIndex++) {
        float weight = 0.316216 / float(sampleIndex + 1);
        vec2 offset = direction * float(sampleIndex) * spread;
        color += texture(bright, clamp(screen_uv + offset, vec2(0.001), vec2(0.999))) * weight;
        color += texture(bright, clamp(screen_uv - offset, vec2(0.001), vec2(0.999))) * weight;
    }
    FragColor = color;
}
