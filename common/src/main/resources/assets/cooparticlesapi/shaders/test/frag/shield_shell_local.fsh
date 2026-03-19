#version 330 core

in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;

out vec4 FragColor;

uniform vec3 shieldColor;
uniform float shieldStrength;
uniform float time;

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

void main() {
    vec3 normal = normalize(viewNormal);
    vec3 viewDir = normalize(-viewPos);
    vec3 spherePos = normalize(localPos);
    float fresnel = pow(1.0 - saturate(dot(normal, viewDir)), 3.6);
    float radial = length(localPos);
    float shell = smoothstep(0.76, 0.98, radial);

    float latitude = abs(spherePos.y);
    float longitudeBands = abs(sin(atan(spherePos.z, spherePos.x) * 6.0 + time * 1.8));
    float latitudeBands = abs(sin(latitude * 18.0 - time * 2.2));
    float grid = smoothstep(0.78, 0.98, longitudeBands * latitudeBands);
    float noise = noise3(spherePos * 9.0 + vec3(time * 0.8));
    float pulse = 0.78 + 0.22 * sin(time * 3.0 + latitude * 12.0);

    vec3 color = shieldColor * (0.20 + shell * 0.18);
    color += shieldColor * fresnel * (0.55 + shieldStrength * 0.45);
    color += vec3(0.82, 0.96, 1.0) * grid * 0.22;
    color += shieldColor * noise * 0.08 * pulse;

    float alpha = shell * (0.08 + shieldStrength * 0.10) + fresnel * (0.18 + shieldStrength * 0.18) + grid * 0.10;
    FragColor = vec4(color, saturate(alpha));
}
