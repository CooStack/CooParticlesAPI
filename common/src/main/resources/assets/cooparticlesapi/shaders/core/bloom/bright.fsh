#version 330 core

in vec2 screen_uv;
layout (location = 0) out vec4 FragColor;
layout (location = 1) out vec4 BrightColor;

uniform sampler2D scene;
uniform float threshold = 0.8;

void main() {
    vec4 color = texture(scene, screen_uv);
    // 计算亮度
    float brightness = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    // 只保留超过阈值的亮部
    BrightColor = vec4(color.rgb, 1.) * (1 + threshold);
    FragColor = color;
}