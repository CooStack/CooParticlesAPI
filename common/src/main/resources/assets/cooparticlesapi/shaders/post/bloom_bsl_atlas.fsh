#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D BloomInput;
uniform int BloomLevels = 7;

const float BSL_WEIGHT[6] = float[6](0.03, 0.15, 0.32, 0.32, 0.15, 0.03);

vec4 sampleBloom(vec2 uv, vec2 gradientX, vec2 gradientY) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) {
        return vec4(0.0);
    }
    return textureGrad(BloomInput, uv, gradientX, gradientY);
}

// 把当前片元映射到一个 atlas tile。两像素 gutter 隔开相邻 tile，显式导数保留 BSL 的隐式 mip 过滤。
vec4 bloomTile(
    float lod,
    vec2 coord,
    vec2 offset,
    vec2 view,
    float pixelWidth,
    float pixelHeight
) {
    float scale = exp2(lod);
    vec2 tileCoord = (coord - offset) * scale;
    vec2 gradientX = dFdx(tileCoord);
    vec2 gradientY = dFdy(tileCoord);
    vec2 padding = vec2(0.5) + 2.0 * view * scale;
    if (abs(tileCoord.x - 0.5) >= padding.x || abs(tileCoord.y - 0.5) >= padding.y) {
        return vec4(0.0);
    }

    vec4 bloom = vec4(0.0);
    for (int x = 0; x < 6; x++) {
        for (int y = 0; y < 6; y++) {
            float weight = BSL_WEIGHT[x] * BSL_WEIGHT[y];
            vec2 pixelOffset = vec2(
                (float(x) - 2.5) * pixelWidth,
                (float(y) - 2.5) * pixelHeight
            );
            bloom += sampleBloom(tileCoord + pixelOffset * scale, gradientX, gradientY) * weight;
        }
    }
    return bloom;
}

void main() {
    vec2 inputSize = max(vec2(textureSize(BloomInput, 0)), vec2(1.0));
    vec2 view = 1.0 / inputSize;
    float viewHeight = inputSize.y;
    float aspectRatio = inputSize.x / viewHeight;
    float pixelHeight = 0.8 / min(720.0, viewHeight);
    float pixelWidth = pixelHeight / aspectRatio;
    vec2 bloomCoord = screen_uv * viewHeight * 0.8 / min(720.0, viewHeight);
    int levels = clamp(BloomLevels, 1, 7);
    vec4 blur = vec4(0.0);

    // BSL 以 720p 为采样基准，并把七个尺度写入固定 atlas 区域。
    if (levels >= 1) blur += bloomTile(1.0, bloomCoord, vec2(0.0, 0.0), view, pixelWidth, pixelHeight);
    if (levels >= 2) blur += bloomTile(2.0, bloomCoord, vec2(0.50, 0.0) + vec2(4.0, 0.0) * view, view, pixelWidth, pixelHeight);
    if (levels >= 3) blur += bloomTile(3.0, bloomCoord, vec2(0.50, 0.25) + vec2(4.0, 4.0) * view, view, pixelWidth, pixelHeight);
    if (levels >= 4) blur += bloomTile(4.0, bloomCoord, vec2(0.625, 0.25) + vec2(8.0, 4.0) * view, view, pixelWidth, pixelHeight);
    if (levels >= 5) blur += bloomTile(5.0, bloomCoord, vec2(0.6875, 0.25) + vec2(12.0, 4.0) * view, view, pixelWidth, pixelHeight);
    if (levels >= 6) blur += bloomTile(6.0, bloomCoord, vec2(0.625, 0.3125) + vec2(8.0, 8.0) * view, view, pixelWidth, pixelHeight);
    if (levels >= 7) blur += bloomTile(7.0, bloomCoord, vec2(0.640625, 0.3125) + vec2(12.0, 8.0) * view, view, pixelWidth, pixelHeight);

    FragColor = vec4(max(blur.rgb, vec3(0.0)), 1.0);
}
