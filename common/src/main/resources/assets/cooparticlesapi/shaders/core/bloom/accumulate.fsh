#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene; // mipmapped bloom texture
uniform float intensity = 1.0;
uniform int levels;

void main() {
    vec3 result = vec3(0.0);

    for (int i = 0; i < levels; i++) {
        // 取每一层 mip
        vec3 mipColor = textureLod(scene, screen_uv, float(i)).rgb;
        // 可以给不同层不同权重，比如 1/(i+1)
        result += mipColor * (intensity / pow(2.0, i));
    }

    FragColor = vec4(result, 1.0);
}
