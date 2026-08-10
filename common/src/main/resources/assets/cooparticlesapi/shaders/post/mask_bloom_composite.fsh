#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D SceneColor;
uniform sampler2D Bloom;
uniform float Intensity = 3.0;

void main() {
    vec4 scene = texture(SceneColor, screen_uv);
    vec3 glow = texture(Bloom, screen_uv).rgb * max(Intensity, 0.0);
    FragColor = vec4(scene.rgb + glow, scene.a);
}
