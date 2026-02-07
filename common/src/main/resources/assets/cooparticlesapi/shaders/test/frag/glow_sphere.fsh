#version 330 core

in vec3 viewNormal;
out vec4 FragColor;

uniform vec3 color;
uniform float intensity;

void main() {
    float facing = max(0.0, dot(normalize(viewNormal), vec3(0.0, 0.0, -1.0)));
    float rim = pow(1.0 - facing, 2.0);
    float core = pow(facing, 1.5);
    float glow = core + rim * 0.6;
    FragColor = vec4(color * intensity * glow, glow);
}
