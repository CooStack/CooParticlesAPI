#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D sceneTex;
uniform sampler2D sceneDepth;
uniform vec2 screenSize;
uniform vec2 effectCenterUv;
uniform float radiusPx;
uniform float centerDepth01;
uniform float time;
uniform vec3 tint;
uniform float reflectivity;
uniform float rimStrength;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec2 localPx = (screen_uv - effectCenterUv) * screenSize;
    float distPx = length(localPx);
    float normalized = distPx / max(radiusPx, 1.0);
    if (normalized > 1.02) {
        FragColor = vec4(scene, 1.0);
        return;
    }

    float mask = 1.0 - smoothstep(0.92, 1.02, normalized);
    float rim = smoothstep(0.54, 0.98, normalized);
    float depthMask = texture(sceneDepth, screen_uv).r + 0.0004 < centerDepth01 ? 0.0 : 1.0;
    vec2 localUv = localPx / max(radiusPx, 1.0);
    vec2 reflectedUv = effectCenterUv + vec2(-localUv.x * 1.10, localUv.y * 0.94) * radiusPx / max(screenSize, vec2(1.0));
    reflectedUv += vec2(sin(time * 0.8 + localUv.y * 9.0), cos(time * 0.5 + localUv.x * 5.0)) * 0.0035;
    vec3 reflectedA = texture(sceneTex, clamp(reflectedUv, vec2(0.001), vec2(0.999))).rgb;
    vec3 reflectedB = texture(sceneTex, clamp(reflectedUv + vec2(0.004, -0.002), vec2(0.001), vec2(0.999))).rgb;
    vec3 reflected = mix(reflectedA, reflectedB, 0.45);
    float lum = dot(reflected, vec3(0.2126, 0.7152, 0.0722));
    vec3 mirrorColor = mix(reflected, vec3(lum), 0.32);
    mirrorColor = mix(mirrorColor, tint, 0.18);
    mirrorColor += reflected * mask * 0.12;
    mirrorColor += vec3(0.95, 0.98, 1.0) * rim * rimStrength * 0.22;

    vec3 finalColor = mix(scene, mirrorColor, mask * reflectivity * depthMask);
    FragColor = vec4(finalColor, 1.0);
}
