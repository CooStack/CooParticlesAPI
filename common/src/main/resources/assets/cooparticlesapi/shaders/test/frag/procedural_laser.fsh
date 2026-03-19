#version 330 core

in vec2 uv;
out vec4 FragColor;

uniform vec4 beamColor;
uniform float time;
uniform float pulseSpeed;
uniform float coreWidth;
uniform float edgeSoftness;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    float centered = abs(uv.x - 0.5);
    float core = 1.0 - smoothstep(coreWidth, coreWidth + edgeSoftness, centered);
    float envelope = smoothstep(0.02, 0.15, uv.y) * smoothstep(1.0, 0.86, uv.y);
    float streak = 0.72 + 0.28 * sin((uv.y + time * pulseSpeed) * 34.0);
    float rim = pow(1.0 - saturate(centered / max(coreWidth * 2.0, 0.001)), 3.0);

    vec3 color = beamColor.rgb * (core * 1.4 + rim * 0.8) * streak;
    float alpha = beamColor.a * envelope * saturate(core + rim * 0.55);
    FragColor = vec4(color, alpha);
}
