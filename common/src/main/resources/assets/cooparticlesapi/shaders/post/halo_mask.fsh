#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform vec2 center = vec2(0.5);
uniform sampler2D depth;
uniform float radius = 1.0;
uniform float feather = 0.25;
uniform float depthFade = 1.0;
uniform float sourceDepth = 1.0;
uniform bool hasDepth = false;
uniform bool throughWalls = false;

void main() {
    vec2 delta = screen_uv - center;
    float dist = length(delta) * 2.0;
    float normalizedRadius = radius > 1.0 ? clamp(radius / 8.0, 0.03, 1.25) : radius;
    float halo = 1.0 - smoothstep(normalizedRadius, normalizedRadius + feather, dist);
    float sceneDepth = hasDepth ? texture(depth, screen_uv).r : 1.0;
    float visible = 1.0 - smoothstep(0.001, 0.03, sourceDepth - sceneDepth);
    float depthFactor = throughWalls ? 1.0 : mix(1.0, visible, clamp(depthFade, 0.0, 1.0));
    float attenuatedHalo = halo * depthFactor;
    FragColor = vec4(vec3(attenuatedHalo), attenuatedHalo);
}
