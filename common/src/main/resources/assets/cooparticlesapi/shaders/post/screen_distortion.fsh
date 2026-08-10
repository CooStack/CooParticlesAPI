#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform vec2 center = vec2(0.5);
uniform float strength = 0.04;
uniform float radius = 0.35;
uniform float progress = 0.0;

void main() {
    vec2 centered = screen_uv - center;
    float distanceFromCenter = length(centered);
    float influence = 1.0 - smoothstep(max(radius * 0.65, 0.0001), max(radius, 0.0002), distanceFromCenter);
    float wave = sin((centered.x + centered.y + progress) * 42.0) * strength;
    vec2 uv = screen_uv + normalize(centered + vec2(0.0001)) * wave * influence;
    FragColor = texture(scene, uv);
}
