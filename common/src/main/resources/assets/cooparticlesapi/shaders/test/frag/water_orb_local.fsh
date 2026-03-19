#version 330 core

in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;

out vec4 FragColor;

uniform float time;
uniform vec3 waterTint;
uniform float waveStrength;
uniform float rimStrength;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
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

vec3 fakeEnvironment(vec3 dir) {
    float sky = saturate(dir.y * 0.5 + 0.5);
    vec3 horizon = vec3(0.62, 0.74, 0.92);
    vec3 zenith = vec3(0.20, 0.40, 0.78);
    vec3 ground = vec3(0.10, 0.20, 0.14);
    return mix(mix(ground, horizon, sky), zenith, sky * sky);
}

void main() {
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    vec3 spherePos = normalize(localPos);
    float fresnel = pow(1.0 - saturate(dot(normal, viewDir)), 2.3);
    float radial = length(localPos);

    float waveA = noise3(spherePos * 5.0 + vec3(time * 0.45));
    float waveB = noise3(spherePos.yzx * 8.0 - vec3(time * 0.62));
    float ripple = mix(waveA, waveB, 0.5) * waveStrength;

    vec3 refractedDir = refract(-viewDir, normal, 0.78);
    vec3 reflectedDir = reflect(-viewDir, normal);
    vec3 env = fakeEnvironment(mix(refractedDir, reflectedDir, fresnel * 0.4));

    float foam = pow(fresnel, 1.3) * rimStrength;
    float body = 1.0 - smoothstep(0.72, 1.0, radial);
    float caustic = (0.45 + 0.55 * sin(time * 4.5 + spherePos.y * 10.0 + ripple * 3.0)) * body;

    vec3 color = mix(env, waterTint, 0.56);
    color += waterTint * caustic * 0.35;
    color += vec3(0.90, 0.98, 1.0) * foam * 0.42;
    color += vec3(0.12, 0.24, 0.34) * body * 0.18;

    float alpha = 0.10 + body * 0.14 + fresnel * 0.36;
    FragColor = vec4(color, saturate(alpha));
}
