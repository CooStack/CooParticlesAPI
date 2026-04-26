#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D bright;
uniform float blurRadius = 3.0;
uniform int iterations = 1;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(bright, 0));
    vec4 color = texture(bright, screen_uv) * 0.227027;
    int radius = max(1, min(12, int(blurRadius) * max(1, iterations)));
    for (int i = 1; i <= radius; i++) {
        float weight = 0.316216 / float(i + 1);
        vec2 offset = vec2(0.0, texel.y * float(i));
        color += texture(bright, screen_uv + offset) * weight;
        color += texture(bright, screen_uv - offset) * weight;
    }
    FragColor = color;
}
