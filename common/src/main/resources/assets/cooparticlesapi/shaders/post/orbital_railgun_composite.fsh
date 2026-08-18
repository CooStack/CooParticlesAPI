#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform float progress = 0.0;
uniform mat4 cooViewProjection;
uniform vec3 cooEffectCenterRelative;
uniform vec3 lineColor = vec3(0.58, 0.94, 1.0);
uniform float orbHeight = 14.0;
uniform float chromaticStrength = 0.012;

vec3 projectedEffectCenter() {
    vec3 relative = cooEffectCenterRelative + vec3(0.0, orbHeight, 0.0);
    vec4 clip = cooViewProjection * vec4(relative, 1.0);
    if (clip.w <= 0.0001) {
        return vec3(0.5, 0.5, 0.0);
    }
    return vec3(clip.xy / clip.w * 0.5 + 0.5, 1.0);
}

vec2 clampedUv(vec2 value) {
    return clamp(value, vec2(0.001), vec2(0.999));
}

void main() {
    float clampedProgress = clamp(progress, 0.0, 1.0);
    vec3 projected = projectedEffectCenter();
    vec2 effectUv = projected.xy;
    float effectVisible = projected.z;
    vec2 delta = screen_uv - effectUv;
    float distanceFromEffect = length(delta);
    vec2 direction = normalize(delta + vec2(0.00001));
    float chromaticEnvelope = smoothstep(0.62, 0.74, clampedProgress)
        * (1.0 - smoothstep(0.92, 1.0, clampedProgress))
        * effectVisible;
    float chromaticOffset = chromaticStrength * chromaticEnvelope
        * smoothstep(0.04, 0.9, distanceFromEffect);

    vec3 base = texture(scene, screen_uv).rgb;
    float red = texture(scene, clampedUv(screen_uv + direction * chromaticOffset)).r;
    float green = texture(scene, screen_uv).g;
    float blue = texture(scene, clampedUv(screen_uv - direction * chromaticOffset)).b;
    vec3 chromatic = mix(base, vec3(red, green, blue), chromaticEnvelope);

    float flashEnvelope = smoothstep(0.69, 0.75, clampedProgress)
        * (1.0 - smoothstep(0.78, 0.88, clampedProgress));
    float flash = exp(-distanceFromEffect * 7.0) * flashEnvelope * effectVisible;
    vec3 color = chromatic + mix(lineColor, vec3(1.0), 0.6) * flash * 0.8;
    FragColor = vec4(color, 1.0);
}
