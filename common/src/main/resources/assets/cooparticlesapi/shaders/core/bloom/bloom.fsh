#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform sampler2D bloomBlur;
uniform float intensity = 1.0;

void main() {
    vec3 hdrColor = texture(scene, screen_uv).rgb;
    vec3 bloomColor = texture(bloomBlur, screen_uv).rgb;
    hdrColor += bloomColor * intensity;// additive blending
    FragColor = vec4(hdrColor, 1.);
}