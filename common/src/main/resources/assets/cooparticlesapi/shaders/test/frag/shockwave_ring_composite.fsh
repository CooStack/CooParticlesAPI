#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D sceneTex;
uniform sampler2D sceneDepth;
uniform vec2 screenSize;
uniform vec2 effectCenterUv;
uniform float radiusPx;
uniform float thicknessPx;
uniform float centerDepth01;
uniform vec3 tint;
uniform float distortionStrength;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec2 offsetPx = (screen_uv - effectCenterUv) * screenSize;
    float distPx = length(offsetPx);
    float band = 1.0 - smoothstep(radiusPx, radiusPx + thicknessPx, distPx);
    band *= smoothstep(radiusPx - thicknessPx, radiusPx, distPx);
    if (band <= 1.0e-4) {
        FragColor = vec4(scene, 1.0);
        return;
    }

    float depthMask = texture(sceneDepth, screen_uv).r + 0.0004 < centerDepth01 ? 0.0 : 1.0;
    vec2 dir = distPx > 1.0e-4 ? offsetPx / distPx : vec2(0.0, 1.0);
    vec2 bendUv = dir * band * distortionStrength / max(screenSize, vec2(1.0));
    vec3 refracted = texture(sceneTex, clamp(screen_uv + bendUv, vec2(0.001), vec2(0.999))).rgb;
    vec3 highlighted = refracted + tint * band * 0.22;
    vec3 finalColor = mix(scene, highlighted, band * depthMask);
    FragColor = vec4(finalColor, 1.0);
}
