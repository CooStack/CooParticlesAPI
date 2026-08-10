#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D bright;
uniform float blurRadius = 3.0;
uniform int Axis = 0;
uniform int Iteration = 0;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(bright, 0));
    vec2 direction = Axis == 0 ? vec2(texel.x, 0.0) : vec2(0.0, texel.y);
    float spread = max(1.0, blurRadius) * (1.0 + float(Iteration) * 0.15);
    vec4 color = texture(bright, screen_uv) * 0.227027;
    for (int i = 1; i <= 4; i++) {
        float weight = 0.316216 / float(i + 1);
        vec2 offset = direction * float(i) * spread;
        color += texture(bright, screen_uv + offset) * weight;
        color += texture(bright, screen_uv - offset) * weight;
    }
    FragColor = color;
}
