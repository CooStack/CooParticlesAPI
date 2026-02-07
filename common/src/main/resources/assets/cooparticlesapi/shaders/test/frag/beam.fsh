#version 330 core

in vec2 uv;
out vec4 FragColor;

uniform sampler2D beamTex;
uniform vec4 color;
uniform float time;

void main() {
    vec2 scrollUv = vec2(uv.x, uv.y + time * 0.6);
    vec4 tex = texture(beamTex, scrollUv);
    float edge = smoothstep(0.0, 0.15, uv.x) * smoothstep(1.0, 0.85, uv.x);
    float fade = smoothstep(0.0, 0.1, uv.y) * smoothstep(1.0, 0.9, uv.y);
    float alpha = tex.a;
    if (alpha <= 0.001) {
        alpha = tex.r;
    }
    FragColor = vec4(tex.rgb * color.rgb, alpha * edge * fade * color.a);
}
