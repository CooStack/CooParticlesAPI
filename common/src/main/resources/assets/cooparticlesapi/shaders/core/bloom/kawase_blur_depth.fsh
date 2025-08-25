#version 330 core
in vec2 screen_uv;
out vec4 BrightColor;

uniform sampler2D bright;   // 高亮通道
uniform sampler2D depth;    // 深度缓冲
uniform float uOffset;      // Kawase 偏移
uniform float intensity;    // Bloom 强度

void main() {
    vec2 texelSize = 1.0 / textureSize(bright, 0);

    // 当前像素的深度
    float centerDepth = texture(depth, screen_uv).r;

    vec3 blur = vec3(0.0);
    float count = 0.0;

    // 采样偏移坐标
    vec2 offsets[8] = vec2[](
    vec2( uOffset, 0.0), vec2(-uOffset, 0.0),
    vec2(0.0,  uOffset), vec2(0.0, -uOffset),
    vec2( uOffset,  uOffset), vec2(-uOffset,  uOffset),
    vec2( uOffset, -uOffset), vec2(-uOffset, -uOffset)
    );

    for (int i = 0; i < 8; i++) {
        vec2 uv = screen_uv + offsets[i] * texelSize;

        float sampleDepth = texture(depth, uv).r;

        // 只允许 sampleDepth <= centerDepth 的（避免穿透玩家）
        if (sampleDepth <= centerDepth + 0.001) {
            blur += texture(bright, uv).rgb;
            count += 1.0;
        }
    }

    if (count > 0.0) {
        blur /= count;
    }

    BrightColor = vec4(blur * intensity, 1.0);
}
