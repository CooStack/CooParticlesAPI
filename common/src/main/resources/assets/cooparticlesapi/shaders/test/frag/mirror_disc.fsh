#version 330 core

in vec2 uv;
out vec4 FragColor;

uniform vec3 mirrorTint;
uniform float reflectivity;
uniform float rimStrength;
uniform float time;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec3 fakeReflection(vec2 coord) {
    float horizon = saturate(1.0 - coord.y * 0.65);
    vec3 sky = mix(vec3(0.30, 0.46, 0.82), vec3(0.94, 0.86, 0.62), horizon);
    float band = 0.55 + 0.45 * sin(coord.y * 24.0 + time * 1.8);
    float shear = 0.6 + 0.4 * cos(coord.x * 18.0 - time * 1.1);
    return sky * (0.85 + band * 0.08 + shear * 0.08);
}

void main() {
    vec2 centered = uv * 2.0 - 1.0;
    float radial = length(centered);
    if (radial > 1.0) {
        discard;
    }

    float rim = smoothstep(0.62, 1.0, radial);
    vec2 reflectedUv = vec2(1.0 - uv.x, uv.y) + vec2(sin(time * 0.9 + uv.y * 8.0), 0.0) * 0.02;
    vec3 reflection = fakeReflection(reflectedUv * 2.0 - 1.0);
    vec3 body = mix(reflection, mirrorTint, 0.18);
    body += vec3(1.0) * rim * rimStrength * 0.28;
    float alpha = saturate(0.18 + reflectivity * 0.36 + rim * 0.18);
    FragColor = vec4(body, alpha);
}
