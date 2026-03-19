#version 330 core

in vec2 uv;
out vec4 FragColor;

uniform vec4 color;
uniform float time;

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
    float body = 1.0 - smoothstep(0.18, 1.0, radial);
    float swirl = noise2(centered * 4.8 + vec2(time * 0.6, -time * 0.4));
    float breakup = noise2(centered * 8.5 - vec2(time * 0.9, time * 0.7));
    float soft = smoothstep(0.0, 0.22, uv.x) * smoothstep(1.0, 0.78, uv.x)
        * smoothstep(0.0, 0.16, uv.y) * smoothstep(1.0, 0.84, uv.y);
    float pulse = 0.74 + 0.26 * sin(time * 1.8 + uv.y * 6.2831);
    float alpha = mix(swirl, breakup, 0.5) * body * soft * pulse * color.a;
    FragColor = vec4(color.rgb, alpha);
}
