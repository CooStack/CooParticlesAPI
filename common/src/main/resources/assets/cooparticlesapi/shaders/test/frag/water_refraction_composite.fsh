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
uniform float refractionStrength;
uniform float rimStrength;
uniform float fillStrength;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec2 offsetPx = (screen_uv - effectCenterUv) * screenSize;
    float distPx = length(offsetPx);
    float normalized = distPx / max(radiusPx, 1.0);
    if (normalized > 1.08) {
        FragColor = vec4(scene, 1.0);
        return;
    }

    float sphereMask = 1.0 - smoothstep(0.94, 1.04, normalized);
    float rim = smoothstep(0.56, 0.98, normalized);
    float depthMask = texture(sceneDepth, screen_uv).r + 0.0004 < centerDepth01 ? 0.0 : 1.0;
    vec2 dir = distPx > 1.0e-4 ? offsetPx / distPx : vec2(0.0, 1.0);
    float waveA = sin(time * 3.0 + normalized * 13.0 + dir.x * 6.0);
    float waveB = cos(time * 2.1 - normalized * 17.0 + dir.y * 8.0);
    float wave = waveA * 0.6 + waveB * 0.4;
    vec2 bendUv = dir * wave * refractionStrength * (1.15 - normalized) / max(screenSize, vec2(1.0));
    vec2 swirlUv = vec2(-dir.y, dir.x) * waveB * refractionStrength * 0.45 / max(screenSize, vec2(1.0));
    vec3 refractR = texture(sceneTex, clamp(screen_uv + bendUv * 1.10 + swirlUv * 0.55, vec2(0.001), vec2(0.999))).rgb;
    vec3 refractB = texture(sceneTex, clamp(screen_uv + bendUv * 0.82 - swirlUv * 0.45, vec2(0.001), vec2(0.999))).rgb;
    vec3 refracted = vec3(refractR.r, (refractR.g + refractB.g) * 0.5, refractB.b);

    float caustic = pow(1.0 - normalized, 2.4) * (0.55 + 0.45 * sin(time * 4.2 + normalized * 24.0));
    float foam = pow(rim, 1.35);
    vec3 waterBody = mix(refracted, refracted * 0.76 + tint * 0.78, sphereMask * fillStrength);
    waterBody += tint * caustic * 0.22;
    waterBody += vec3(0.88, 0.96, 1.0) * foam * rimStrength * 0.18;
    vec3 finalColor = mix(scene, waterBody, sphereMask * depthMask);
    FragColor = vec4(finalColor, 1.0);
}
