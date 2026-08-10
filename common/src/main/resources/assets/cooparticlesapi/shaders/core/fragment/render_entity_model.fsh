#version 330 core

in vec4 fragColor;
layout(location = 0) out vec4 FragColor;
layout(location = 1) out vec4 MaskColor;

uniform float intensity;
uniform float BloomIntensity = 1.0;

void main() {
    FragColor = vec4(fragColor.rgb * max(intensity, 0.0), fragColor.a);
    MaskColor = vec4(fragColor.rgb * max(BloomIntensity, 0.0), fragColor.a);
}
