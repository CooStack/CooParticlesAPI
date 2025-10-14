#version 330 core
out vec4 FragColor;

in vec2 screen_uv;

uniform sampler2D bright;
uniform bool horizontal;

const int KERNEL_RADIUS = 10;  // 半径，等于最大采样距离
uniform float sigma = 2.; // 高斯标准差（越大越模糊）
uniform float range = 2.;

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

void main() {
    vec2 texelSize = 1.0 / textureSize(bright, 0);
    vec3 result = vec3(0.0);

    // 归一化因子
    float sum = gaussian(0.0, sigma);
    result += texture(bright, screen_uv).rgb * sum;
    vec2 offset = horizontal ? vec2(texelSize.x, 0.0) : vec2(0.0, texelSize.y);
    // 循环采样
    for (int i = 1; i <= KERNEL_RADIUS; i++) {
        float w = gaussian(float(i), sigma);
        sum += 2.0 * w; // 对称采样，所以乘 2
        vec2 sampOffset = offset * float(i) * range;
        result += texture(bright, screen_uv + sampOffset).rgb * w;
        result += texture(bright, screen_uv - sampOffset).rgb * w;
    }

    // 权重归一化
    result /= sum;
    FragColor = vec4(result, 1.0);
}
