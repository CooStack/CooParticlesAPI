#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform float threshold = 1.0;
uniform float softKnee = 0.5;

void main() {
    vec4 source = texture(scene, screen_uv);
    if (threshold <= 0.0) {
        FragColor = source;
        return;
    }

    vec3 color = source.rgb;
    float brightness = max(max(color.r, color.g), color.b);
    float knee = max(softKnee, 0.0001);
    float contribution = smoothstep(threshold - knee, threshold + knee, brightness);
    FragColor = vec4(color * contribution, source.a * contribution);
}
