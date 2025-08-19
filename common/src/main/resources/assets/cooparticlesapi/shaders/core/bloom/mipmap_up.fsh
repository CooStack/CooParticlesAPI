#version 330 core
in vec2 screen_uv;
layout (location = 0) out vec4 FragColor;
layout (location = 1) out vec4 BrightColor;

uniform sampler2D scene;
uniform sampler2D bright;
uniform float intensity;

void main() {
    vec3 low = texture2D(bright, screen_uv).rgb;
    vec4 curr = texture2D(scene, screen_uv);
    // 线性混合
    vec3 blur = curr.rgb + low;
    FragColor = curr;
    BrightColor = vec4(blur * intensity, 1.0);
}
