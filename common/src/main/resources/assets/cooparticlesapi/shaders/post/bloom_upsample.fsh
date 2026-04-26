#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D bright;
uniform float upsampleRadius = 1.0;

vec3 sampleTent(vec2 uv) {
    vec2 texel = (1.0 / max(vec2(textureSize(bright, 0)), vec2(1.0))) * max(upsampleRadius, 0.25);
    vec3 color = vec3(0.0);
    color += texture(bright, uv + vec2(-texel.x,  texel.y)).rgb;
    color += texture(bright, uv + vec2(0.0,       texel.y)).rgb * 2.0;
    color += texture(bright, uv + vec2( texel.x,  texel.y)).rgb;
    color += texture(bright, uv + vec2(-texel.x, 0.0)).rgb * 2.0;
    color += texture(bright, uv).rgb * 4.0;
    color += texture(bright, uv + vec2( texel.x, 0.0)).rgb * 2.0;
    color += texture(bright, uv + vec2(-texel.x, -texel.y)).rgb;
    color += texture(bright, uv + vec2(0.0,      -texel.y)).rgb * 2.0;
    color += texture(bright, uv + vec2( texel.x, -texel.y)).rgb;
    return color / 16.0;
}

void main() {
    FragColor = vec4(sampleTent(screen_uv), 1.0);
}
