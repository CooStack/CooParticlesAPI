#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform float progress = 0.0;
uniform mat4 cooViewProjection;
uniform vec3 cooEffectCenterRelative;
uniform vec3 lineColor = vec3(0.58, 0.94, 1.0);
uniform float orbHeight = 14.0;
uniform float darkness = 0.12;

void main() {
    vec3 sourceColor = texture(scene, screen_uv).rgb;
    float clampedProgress = clamp(progress, 0.0, 1.0);
    float fade = 1.0 - smoothstep(0.84, 1.0, clampedProgress);
    vec3 origin = cooEffectCenterRelative + vec3(0.0, orbHeight, 0.0);
    vec4 effectClip = cooViewProjection * vec4(origin, 1.0);
    float effectVisible = step(0.0001, effectClip.w);
    float activeDarkness = smoothstep(0.04, 0.24, clampedProgress) * fade;
    vec3 darkenedColor = sourceColor * mix(1.0, darkness, activeDarkness);

    float peak = max(sourceColor.r, max(sourceColor.g, sourceColor.b));
    float floorValue = min(sourceColor.r, min(sourceColor.g, sourceColor.b));
    float luminance = dot(sourceColor, vec3(0.2126, 0.7152, 0.0722));
    float chroma = peak - floorValue;
    float brightEmission = smoothstep(0.55, 0.95, peak);
    float coloredEmission = smoothstep(0.08, 0.24, chroma)
        * smoothstep(0.12, 0.42, luminance);
    float emissionPreserve = max(brightEmission, coloredEmission);
    vec3 color = mix(darkenedColor, sourceColor, emissionPreserve);

    float flash = smoothstep(0.70, 0.76, clampedProgress)
        * (1.0 - smoothstep(0.82, 0.90, clampedProgress));
    color += lineColor * flash * 0.10 * effectVisible;
    FragColor = vec4(color, 1.0);
}
