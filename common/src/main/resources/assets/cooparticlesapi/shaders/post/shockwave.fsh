#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform vec2 center = vec2(0.5);
uniform float radius = 0.25;
uniform float feather = 0.1;
uniform float strength = 0.08;
uniform float progress = 0.0;

void main() {
    vec2 delta = screen_uv - center;
    float dist = length(delta);
    float normalizedRadius = radius > 1.0 ? clamp(radius / 16.0, 0.03, 1.25) : radius;
    float edge = 1.0 - smoothstep(normalizedRadius, normalizedRadius + feather, abs(dist - normalizedRadius));
    vec2 uv = screen_uv + normalize(delta + vec2(0.0001)) * edge * strength * (1.0 - progress);
    FragColor = texture(scene, uv);
}
