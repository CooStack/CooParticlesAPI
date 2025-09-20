#version 330 core
in vec2 screen_uv;
out vec4 BrightColor;

uniform sampler2D bright;
uniform float uOffset;
uniform float intensity;

void main() {
    vec4 brightColor = texture(bright, screen_uv);
    vec2 size = 1.0 / vec2(textureSize(bright, 0));
    vec2 o = size * uOffset;

    vec3 blur =
    texture(bright, screen_uv + vec2(o.x, 0.0)).rgb +
    texture(bright, screen_uv + vec2(-o.x, 0.0)).rgb +
    texture(bright, screen_uv + vec2(0.0, o.y)).rgb +
    texture(bright, screen_uv + vec2(0.0, -o.y)).rgb +
    texture(bright, screen_uv + vec2(o.x, o.y)).rgb +
    texture(bright, screen_uv + vec2(-o.x, o.y)).rgb +
    texture(bright, screen_uv + vec2(o.x, -o.y)).rgb +
    texture(bright, screen_uv + vec2(-o.x, -o.y)).rgb;

    blur *= 0.125;
    BrightColor = vec4(blur * intensity, 1.0);
}
