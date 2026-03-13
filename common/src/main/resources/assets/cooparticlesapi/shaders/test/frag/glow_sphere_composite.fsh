#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D glowMask;
uniform sampler2D distortionMask;
uniform sampler2D sceneTex;
uniform sampler2D blurredGlow;
uniform sampler2D sceneDepth;

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

    vec3 bentScene = texture(sceneTex, clamp(screen_uv + bend * (0.85 + coverage * 0.10), vec2(0.001), vec2(0.999))).rgb;
    float sourcePresence = clamp(glow.a * 0.82 + coverage * 0.22 + length(blurred) * 0.30, 0.0, 1.0);
    vec3 core = glow.rgb * (0.24 + glow.a * 0.30);
    vec3 halo = blurred * (1.36 + glow.a * 0.92 + coverage * 0.16) + glow.rgb * (0.12 + glow.a * 0.18);

    vec3 color = mix(scene, bentScene, coverage * 0.04 + bendAmount * 4.0);
    color += core;
    color += halo * (0.92 + sourcePresence * 0.22);
    color = mix(color, color * 1.02 + halo * 0.08, sourcePresence * 0.10);
    color = softLimit(color, 8.5);

    FragColor = vec4(color, 1.0);
}
