#version 330 core
in vec2 screen_uv;
layout (location = 0) out vec4 FragColor;

uniform sampler2D image;   // 原场景
uniform sampler2D bright;  // 模糊后的高亮
uniform float uBloomIntensity;

void main() {
    vec3 scene = texture(image, screen_uv).rgb;
    vec3 bloom = texture(bright, screen_uv).rgb;

    FragColor = vec4(scene + bloom * uBloomIntensity, 1.0);
}
