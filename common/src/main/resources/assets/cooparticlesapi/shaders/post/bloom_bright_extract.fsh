#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform float threshold = 1.0;
uniform float softKnee = 0.5;
uniform bool PremultipliedInput = false;
uniform float Intensity = 1.0;

void main() {
    vec4 source = texture(scene, screen_uv);
    vec3 premultipliedColor = PremultipliedInput
        ? source.rgb
        : source.rgb * source.a;
    float gain = max(Intensity, 0.0);
    if (threshold <= 0.0) {
        FragColor = vec4(premultipliedColor * gain, source.a);
        return;
    }

    float brightness = max(max(premultipliedColor.r, premultipliedColor.g), premultipliedColor.b);
    float knee = max(softKnee, 0.0001);
    float contribution = smoothstep(threshold - knee, threshold + knee, brightness);
    FragColor = vec4(premultipliedColor * contribution * gain, source.a * contribution);
}
