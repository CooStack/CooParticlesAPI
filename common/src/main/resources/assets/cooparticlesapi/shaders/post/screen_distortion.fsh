#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform vec2 center = vec2(0.5);
uniform float strength = 0.04;
uniform float progress = 0.0;

void main() {
    vec2 centered = screen_uv - center;
    float wave = sin((centered.x + centered.y + progress) * 42.0) * strength;
    vec2 uv = screen_uv + normalize(centered + vec2(0.0001)) * wave;
    FragColor = texture(scene, uv);
}
