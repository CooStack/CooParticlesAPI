#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D SceneColor;
uniform sampler2D EffectColor;
uniform sampler2D BloomAtlas;
uniform int MipLevels = 3;

const float BSL_BLOOM_MIX = 0.2;
const float BSL_HDR_SCALE = 32.0;

// 场景颜色已经完成色调映射，直接核心与 BSL Bloom 都按相同透射率合成。
vec3 compositeHdrBloom(vec3 sceneColor, vec3 bloomColor) {
    vec3 scene = clamp(sceneColor, vec3(0.0), vec3(1.0));
    vec3 transmission = exp(-max(bloomColor, vec3(0.0)) * BSL_BLOOM_MIX);
    return vec3(1.0) - (vec3(1.0) - scene) * transmission;
}

vec3 decodeBloom(vec3 encodedBloom) {
    vec3 bloom = max(encodedBloom, vec3(0.0));
    bloom *= bloom;
    return bloom * bloom * BSL_HDR_SCALE;
}

vec4 sampleBloomTile(float lod, vec2 coord, vec2 offset) {
    vec2 atlasSize = max(vec2(textureSize(BloomAtlas, 0)), vec2(1.0));
    float resolutionScale = 1.25 * min(720.0, atlasSize.y) / atlasSize.y;
    vec2 uv = (coord / exp2(lod) + offset) * resolutionScale;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) {
        return vec4(0.0);
    }
    vec4 encodedBloom = texture(BloomAtlas, uv);
    return vec4(decodeBloom(encodedBloom.rgb), encodedBloom.a);
}

float bayer2(vec2 position) {
    position = floor(position);
    return fract(position.x * 0.5 + position.y * position.y * 0.75);
}

float bayer4(vec2 position) {
    return bayer2(position * 0.5) * 0.25 + bayer2(position);
}

float bayer8(vec2 position) {
    return bayer4(position * 0.5) * 0.25 + bayer2(position);
}

vec3 ditherRgba8(vec3 color, float threshold) {
    vec3 scaled = clamp(color, vec3(0.0), vec3(1.0)) * 255.0;
    return floor(scaled + threshold + 1.0e-4) / 255.0;
}

vec4 reconstructBloom() {
    vec2 atlasSize = max(vec2(textureSize(BloomAtlas, 0)), vec2(1.0));
    vec2 view = 1.0 / atlasSize;
    int levels = clamp(MipLevels, 1, 4);
    vec4 blur1 = sampleBloomTile(1.0, screen_uv, vec2(0.0, 0.0) + vec2(0.5, 0.0) * view);
    if (levels == 1) return blur1;

    vec4 blur2 = sampleBloomTile(2.0, screen_uv, vec2(0.50, 0.0) + vec2(4.5, 0.0) * view);
    if (levels == 2) return (blur1 * 1.23 + blur2) / 2.23;

    vec4 blur3 = sampleBloomTile(3.0, screen_uv, vec2(0.50, 0.25) + vec2(4.5, 4.0) * view);
    if (levels == 3) return (blur1 * 1.71 + blur2 * 1.52 + blur3) / 4.23;

    vec4 blur4 = sampleBloomTile(4.0, screen_uv, vec2(0.625, 0.25) + vec2(8.5, 4.0) * view);
    return (blur1 * 2.46 + blur2 * 2.25 + blur3 * 1.71 + blur4) / 7.42;
}

void main() {
    vec4 sceneSample = texture(SceneColor, screen_uv);
    vec3 effectCore = max(texture(EffectColor, screen_uv).rgb, vec3(0.0));
    vec3 hdrBloom = reconstructBloom().rgb;
    vec3 bloomResult = compositeHdrBloom(sceneSample.rgb, hdrBloom);
    vec3 result = bloomResult + effectCore;
    FragColor = vec4(ditherRgba8(result, bayer8(gl_FragCoord.xy)), sceneSample.a);
}
