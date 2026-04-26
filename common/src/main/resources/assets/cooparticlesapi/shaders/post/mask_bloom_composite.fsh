#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D bloom;
uniform sampler2D mask;
uniform vec3 tint = vec3(1.0);
uniform float intensity = 1.0;
uniform float baseMaskIntensity = 0.0;

void main() {
    vec4 blurred = texture(bloom, screen_uv);
    vec4 sourceMask = texture(mask, screen_uv);
    vec3 glow = (blurred.rgb + sourceMask.rgb * baseMaskIntensity) * tint * intensity;
    FragColor = vec4(glow, max(blurred.a, sourceMask.a));
}
