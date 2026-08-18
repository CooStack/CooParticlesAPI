#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform float threshold = 0.85;
uniform float softKnee = 0.35;
uniform float intensity = 1.0;

void main() {
    vec3 sourceColor = texture(scene, screen_uv).rgb;
    float brightness = max(max(sourceColor.r, sourceColor.g), sourceColor.b);
    float knee = max(softKnee, 0.0001);
    float contribution = smoothstep(threshold - knee, threshold + knee, brightness);
    FragColor = vec4(sourceColor * contribution * max(intensity, 0.0), contribution);
}
