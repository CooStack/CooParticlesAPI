#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform vec2 center = vec2(0.5);
uniform vec2 screenSize = vec2(1.0);
uniform float radius = 0.22;
uniform float feather = 0.08;

void main() {
    vec2 aspect = vec2(screenSize.x / max(screenSize.y, 1.0), 1.0);
    float dist = length((screen_uv - center) * aspect);
    float alpha = 1.0 - smoothstep(radius, radius + feather, dist);
    FragColor = vec4(vec3(alpha), alpha);
}
