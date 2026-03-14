#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D glowMask;
uniform sampler2D distortionMask;
uniform sampler2D sceneTex;
uniform sampler2D blurredGlow;
uniform sampler2D sceneDepth;

float luminance(vec3 value) {
    return dot(value, vec3(0.2126, 0.7152, 0.0722));
}

vec3 softLimit(vec3 value, float limit) {
    return value / (1.0 + value / max(limit, 1.0e-3));
}

void main() {
    vec4 glow = texture(glowMask, screen_uv);
    vec4 distortion = texture(distortionMask, screen_uv);
    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec3 blurred = texture(blurredGlow, screen_uv).rgb;

    vec2 bend = distortion.xy;
    float coverage = clamp(distortion.w, 0.0, 1.0);
    float bendAmount = length(bend);
    float glowCoverage = clamp(glow.a, 0.0, 1.0);
    float blurredLuma = clamp(luminance(blurred) * 0.16, 0.0, 1.0);
    float sourcePresence = clamp(glowCoverage * 0.78 + coverage * 0.18 + blurredLuma, 0.0, 1.0);

    vec3 bentScene = texture(
        sceneTex,
        clamp(screen_uv + bend * (0.42 + coverage * 0.06), vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 core = glow.rgb * (0.18 + glowCoverage * 0.16);
    vec3 halo = blurred * (0.34 + glowCoverage * 0.18 + coverage * 0.08) +
        glow.rgb * (0.04 + glowCoverage * 0.06);

    vec3 color = mix(scene, bentScene, coverage * 0.02 + bendAmount * 2.0);
    color += core;
    color += halo * (0.40 + sourcePresence * 0.10);
    color = mix(color, color * 1.01 + halo * 0.02, sourcePresence * 0.04);
    color = softLimit(color, 8.2);

    FragColor = vec4(color, 1.0);
}
