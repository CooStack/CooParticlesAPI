#version 330 core

in vec3 localPos;
in vec3 viewPos;

out vec4 FragColor;

uniform vec3 beamColor;
uniform float time;
uniform float pulseSpeed;
uniform float alpha;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    float radial = length(localPos.xz);
    float core = 1.0 - smoothstep(0.16, 0.48, radial);
    float rim = smoothstep(0.18, 0.52, radial) * (1.0 - smoothstep(0.52, 0.86, radial));
    float along = localPos.y;
    float streak = 0.72 + 0.28 * sin((along + time * pulseSpeed) * 15.0);
    float tipFade = smoothstep(0.0, 0.08, along) * smoothstep(1.0, 0.92, along);

    vec3 emissive = beamColor * (core * 1.8 + rim * 0.9) * streak;
    emissive += beamColor * rim * 0.4;
    float outAlpha = saturate((core + rim * 0.65) * alpha * tipFade);
    FragColor = vec4(emissive, outAlpha);
}
