#version 330 core

in vec2 uv;
out vec4 FragColor;

uniform vec3 smokeColor;
uniform float time;
uniform float alpha;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i + vec2(0.0, 0.0));
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 centered = uv * 2.0 - 1.0;
    float radial = length(centered);
    float body = 1.0 - smoothstep(0.32, 1.0, radial);
    float swirl = noise2(centered * 4.5 + vec2(time * 0.8, -time * 0.6));
    float breakup = noise2(centered * 8.0 - vec2(time * 1.2, time * 0.7));
    float smoke = body * mix(swirl, breakup, 0.5);
    float edge = pow(1.0 - saturate(radial), 2.2);
    float outAlpha = alpha * saturate(smoke * 0.95 + edge * 0.25);
    FragColor = vec4(smokeColor * (0.65 + breakup * 0.35), outAlpha);
}
