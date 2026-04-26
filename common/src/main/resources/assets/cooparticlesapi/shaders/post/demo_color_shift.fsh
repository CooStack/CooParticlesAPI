#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform float amount = 0.25;
uniform float progress = 0.0;

void main() {
    vec2 offset = vec2(amount * 0.01 * sin(progress * 6.28318), 0.0);
    float red = texture(scene, screen_uv + offset).r;
    float green = texture(scene, screen_uv).g;
    float blue = texture(scene, screen_uv - offset).b;
    vec4 base = texture(scene, screen_uv);
    FragColor = vec4(red, green, blue, base.a);
}
