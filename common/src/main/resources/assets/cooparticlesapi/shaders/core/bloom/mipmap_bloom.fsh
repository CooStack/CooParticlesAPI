#version 330 core
in vec2 screen_uv;
layout (location = 0) out vec4 FragColor;

uniform sampler2D image;
uniform sampler2D bright;
uniform float uBloomIntensity;

void main() {
    vec3 color = texture2D(image, screen_uv).rgb;
    vec3 bloom = texture2D(bright, screen_uv).rgb;
    FragColor = vec4(color + bloom * uBloomIntensity, 1.0);
}
