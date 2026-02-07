#version 330 core

in vec2 uv;
out vec4 FragColor;

uniform sampler2D smokeTex;
uniform vec4 color;
uniform float time;

void main() {
    vec2 flow = vec2(time * 0.05, time * 0.03);
    vec2 uvA = uv + flow;
    vec2 uvB = uv - flow * 0.6;
    float noiseA = texture(smokeTex, uvA).r;
    float noiseB = texture(smokeTex, uvB).g;
    float noise = mix(noiseA, noiseB, 0.5);
    float soft = smoothstep(0.0, 0.25, uv.x) * smoothstep(1.0, 0.75, uv.x)
        * smoothstep(0.0, 0.2, uv.y) * smoothstep(1.0, 0.8, uv.y);
    float pulse = 0.7 + 0.3 * sin(time * 2.5 + uv.y * 6.2831);
    float alpha = noise * soft * pulse * color.a;
    FragColor = vec4(color.rgb, alpha);
}
