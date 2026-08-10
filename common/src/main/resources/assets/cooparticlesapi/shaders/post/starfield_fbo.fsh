#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D CosmosSampler;
uniform sampler2D IridescenceSampler;
uniform vec2 screenSize;
uniform float progress;

void main() {
    float aspect = screenSize.x / max(screenSize.y, 1.0);
    vec2 centered = screen_uv - vec2(0.5);
    centered.x *= aspect;

    float time = progress * 100.0;
    vec2 firstFlowUv = centered * 0.58 + vec2(time, -time * 0.67);
    vec2 secondFlowUv = centered * 0.91 + vec2(-time * 0.43, time * 0.29);
    vec2 firstFlow = texture(IridescenceSampler, fract(firstFlowUv + vec2(0.5))).rg * 2.0 - 1.0;
    vec2 secondFlow = texture(IridescenceSampler, fract(secondFlowUv + vec2(0.5))).gb * 2.0 - 1.0;
    vec2 distortion = firstFlow * 0.055 + secondFlow * 0.025;

    vec2 cosmosUv = fract(centered * 0.72 + vec2(0.5) + distortion);
    vec3 cosmos = texture(CosmosSampler, cosmosUv).rgb;
    vec3 iridescence = texture(IridescenceSampler, fract(centered * 0.42 + distortion + vec2(0.5))).rgb;
    float highlights = smoothstep(0.62, 1.0, max(cosmos.r, max(cosmos.g, cosmos.b)));
    vec3 tint = mix(vec3(0.64, 0.80, 1.08), iridescence * 1.12, 0.28);
    vec3 color = cosmos * tint + iridescence * highlights * 0.12;
    FragColor = vec4(color, 1.0);
}
