#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform sampler2D mask;
uniform vec4 color = vec4(1.0, 0.75, 0.25, 1.0);
uniform float intensity = 1.0;

void main() {
    vec4 base = texture(scene, screen_uv);
    float halo = texture(mask, screen_uv).a;
    FragColor = vec4(base.rgb + color.rgb * halo * intensity, base.a);
}
