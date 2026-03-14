#version 330 core

in vec2 uv;
out vec4 FragColor;

uniform vec4 color;
uniform float time;

void main() {
    vec2 centeredUv = uv * 2.0 - 1.0;
    float radial = exp2(-centeredUv.x * centeredUv.x * 10.0);
    float core = exp2(-centeredUv.x * centeredUv.x * 34.0);
    float tipFade = smoothstep(0.0, 0.08, uv.y) * (1.0 - smoothstep(0.90, 1.0, uv.y));
    float pulse = 0.92 + 0.08 * sin((uv.y * 15.0 - time * 3.5) * 3.1415926);
    float shimmer = 0.95 + 0.05 * sin((uv.y * 23.0 + centeredUv.x * 6.0) - time * 5.2);
    float alpha = (radial * 0.72 + core * 0.55) * tipFade;
    vec3 emissive = color.rgb * (0.78 + core * 1.24) * pulse * shimmer;
    FragColor = vec4(emissive, alpha * color.a);
}
