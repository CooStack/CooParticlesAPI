#version 330 core
in vec2 screen_uv;
//layout (location = 0) out vec4 FragColor;   // 保留原色
//layout (location = 1) out vec4 BrightColor; // 模糊后的高亮结果
out vec4 BrightColor; // 模糊后的高亮结果

//uniform sampler2D image;   // 原场景
uniform sampler2D bright;  // 高亮通道
uniform float uOffset;     // Kawase 偏移
uniform float intensity; // Bloom 强度

void main() {
//    // 原图颜色
//    vec4 sceneColor = texture2D(image, screen_uv);
//    FragColor = sceneColor;
    vec4 brightColor = texture2D(bright, screen_uv);
    vec2 size = 1.0 / textureSize(bright, 0);
    // Kawase 模糊采样
    vec2 o = size * uOffset;
    vec3 blur =
    texture2D(bright, screen_uv + vec2(o.x, 0.0)).rgb +
    texture2D(bright, screen_uv + vec2(-o.x, 0.0)).rgb +
    texture2D(bright, screen_uv + vec2(0.0, o.y)).rgb +
    texture2D(bright, screen_uv + vec2(0.0, -o.y)).rgb +
    texture2D(bright, screen_uv + vec2(o.x, o.y)).rgb +
    texture2D(bright, screen_uv + vec2(-o.x, o.y)).rgb +
    texture2D(bright, screen_uv + vec2(o.x, -o.y)).rgb +
    texture2D(bright, screen_uv + vec2(-o.x, -o.y)).rgb;

    blur *= 0.125; // 平均值

    // Bloom 结果（模糊 + 强度）
    BrightColor = vec4(blur * intensity, 1.0);
}
