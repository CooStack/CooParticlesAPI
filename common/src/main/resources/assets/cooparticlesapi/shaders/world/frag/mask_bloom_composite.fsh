#version 330 core

in vec2 screen_uv;
out vec4 fragColor;

uniform sampler2D sceneTex;
uniform sampler2D filteredMask;
uniform sampler2D blurredMask;
uniform vec3 tint;
uniform float bloomIntensity;
uniform float baseMaskIntensity;

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

float maxChannel(vec3 value) {
    return max(value.r, max(value.g, value.b));
}

vec3 contentDrivenEnergy(vec4 sample) {
    vec3 rgb = max(sample.rgb, vec3(0.0));
    float peak = maxChannel(rgb);
    float mask = max(sample.a, luminance(rgb));
    if (peak <= 1.0e-4) {
        return vec3(mask);
    }
    vec3 hue = rgb / peak;
    return hue * max(mask, peak);
}

void main() {
    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec4 source = texture(filteredMask, screen_uv);
    vec4 blurred = texture(blurredMask, screen_uv);
    float sourceCoverage = clamp(source.a, 0.0, 1.0);
    float blurCoverage = clamp(blurred.a, 0.0, 1.0);

    vec3 sourceEnergy = contentDrivenEnergy(source) * tint;
    vec3 blurEnergy = contentDrivenEnergy(blurred) * tint;

    vec3 core = sourceEnergy * max(baseMaskIntensity, 0.0) * mix(1.0, 1.14, sourceCoverage);
    vec3 bloom = blurEnergy * max(bloomIntensity, 0.0) * mix(0.92, 1.24, blurCoverage);
    vec3 shoulder = bloom * (0.06 + blurCoverage * 0.10);
    fragColor = vec4(scene + core + bloom + shoulder, 1.0);
}
