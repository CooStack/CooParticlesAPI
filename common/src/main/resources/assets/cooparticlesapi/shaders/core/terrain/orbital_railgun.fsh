#version 330 core

#coo_import <terrain_light_fog.glsl>

uniform sampler2D BaseSampler;
uniform sampler2D SceneColor;
uniform vec3 OrbitalCenter;
uniform float OrbitalRadius;
uniform float OrbitalLineWidth;
uniform vec3 OrbitalColor;
uniform float OrbitalDuration;
uniform float CooAlphaCutoff;
uniform vec2 ScreenSize;
uniform int CooIrisComposite;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
in vec3 worldPosition;
in vec3 worldNormal;
flat in float effectElapsedTicks;

layout(location = 0) out vec4 FragColor;
layout(location = 1) out vec4 MaskColor;

float band(float distanceValue, float width) {
    return 1.0 - smoothstep(width, width * 2.4, abs(distanceValue));
}

void main() {
    vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
    if (atlasColor.a < CooAlphaCutoff) {
        discard;
    }

    float timeline = clamp(effectElapsedTicks / max(OrbitalDuration, 1.0), 0.0, 1.0);
    float expansion = smoothstep(0.10, 0.72, timeline);
    float detonation = smoothstep(0.66, 0.80, timeline);
    float fade = 1.0 - smoothstep(0.84, 1.0, timeline);
    float radialDistance = length((worldPosition - OrbitalCenter).xz);
    float shellRadius = mix(2.0, OrbitalRadius, expansion);
    float mainShell = band(radialDistance - shellRadius, OrbitalLineWidth);
    float trailingShell = band(
        radialDistance - max(shellRadius - 7.0, 0.0),
        OrbitalLineWidth * 0.72
    ) * expansion;
    float impactShell = band(
        radialDistance - mix(1.0, OrbitalRadius * 0.62, detonation),
        OrbitalLineWidth * 1.35
    ) * detonation;
    float shell = max(mainShell, max(trailingShell * 0.72, impactShell)) * fade;
    float broadGlow = exp(-abs(radialDistance - shellRadius) * 0.28) * expansion * fade;
    float facing = mix(0.72, 1.0, abs(normalize(worldNormal).y));
    float glowWeight = max(shell, broadGlow);
    vec3 emissive = OrbitalColor * (shell * 4.5 + broadGlow * 0.55) * facing;

    vec3 baseColor = atlasColor.rgb;
    if (CooIrisComposite != 0) {
        baseColor = texture(SceneColor, gl_FragCoord.xy / max(ScreenSize, vec2(1.0))).rgb;
    }
    vec4 color = vec4(baseColor + emissive, atlasColor.a);
    FragColor = CooIrisComposite != 0
        ? color
        : linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    MaskColor = vec4(emissive, atlasColor.a * clamp(shell + broadGlow, 0.0, 1.0));
}
