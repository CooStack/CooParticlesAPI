#version 330 core

in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;

out vec4 FragColor;

uniform float time;
uniform vec3 color;
uniform float intensity;
uniform float alpha;
uniform float rimPower;
uniform float fillStrength;
uniform float pulseSpeed;
uniform float noiseScale;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

void main() {
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    float facing = saturate(dot(normal, viewDir));
    float fresnel = pow(1.0 - facing, max(rimPower, 0.01));
    float radial = length(localPos);
    float body = 1.0 - smoothstep(fillStrength, 1.0, radial);
    float flow = noise3(localPos * max(noiseScale, 0.01) + vec3(time * pulseSpeed * 0.35));
    float pulse = 0.82 + 0.18 * sin(time * pulseSpeed * 2.4 + radial * 8.0);

    vec3 emissive = color * (body * (0.28 + intensity * 0.18));
    emissive += color * fresnel * (0.22 + intensity * 0.38);
    emissive += color * flow * 0.10 * intensity;
    emissive *= pulse;

    float finalAlpha = saturate(alpha * (body * 0.75 + fresnel * 0.9));
    FragColor = vec4(emissive, finalAlpha);
}
