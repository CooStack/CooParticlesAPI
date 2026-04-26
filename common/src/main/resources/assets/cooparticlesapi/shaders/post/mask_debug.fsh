#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform sampler2D mask;
uniform vec4 color = vec4(0.0, 1.0, 0.2, 0.6);

void main() {
    vec4 base = texture(scene, screen_uv);
    float maskValue = texture(mask, screen_uv).a;
    FragColor = mix(base, color, color.a * maskValue);
}
