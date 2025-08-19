#version 330 core
in vec2 screen_uv;
layout (location = 0) out vec4 FragColor;
layout (location = 1) out vec4 BrightColor;

uniform sampler2D scene;
uniform sampler2D bright;

void main() {
    vec3 sum = vec3(0.0);
    vec2 texelSize = 1.0 / textureSize(bright, 0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            sum += texture2D(bright, screen_uv + vec2(x, y) * texelSize).rgb;
        }
    }
    FragColor = texture2D(scene, screen_uv);
    BrightColor = vec4(sum / 9.0, 1.0);
}
