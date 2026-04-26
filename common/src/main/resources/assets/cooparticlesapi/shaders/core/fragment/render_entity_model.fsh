#version 330 core

in vec4 fragColor;
out vec4 FragColor;

uniform float intensity;

void main() {
    FragColor = vec4(fragColor.rgb * max(intensity, 0.0), fragColor.a);
}
