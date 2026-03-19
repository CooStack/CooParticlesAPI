#version 330 core

in vec2 screen_uv;

uniform sampler2D scene;
uniform float lod = .0;

out vec4 FragColor;

const vec3 tentKernel[9] = vec3[9](
vec3(-1.0, 1.0, 1.0), vec3(0.0, 1.0, 2.0), vec3(1.0, 1.0, 1.0),
vec3(-1.0, 0.0, 2.0), vec3(0.0, 0.0, 4.0), vec3(1.0, 0.0, 2.0),
vec3(-1.0, -1.0, 1.0), vec3(0.0, -1.0, 2.0), vec3(1.0, -1.0, 1.0)
);

// Function to calculate perceived brightness (luminance)
float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 color = vec4(0.0);
    int lodIndex = max(int(floor(lod + 0.5)), 0);
    vec2 texelSize = 1.0 / vec2(textureSize(scene, lodIndex));
    float weight = 0.0;

    for (int i = 0;i < 9; ++i) {
        vec3 kernel = tentKernel[i];
        vec4 sampledColor = textureLod(scene, screen_uv + kernel.xy * texelSize, float(lodIndex));
        float l = kernel.z / (1.0 + luminance(sampledColor.rgb));
        color += sampledColor * l;
        weight += l;
    }

    color /= weight;
    FragColor = color;
}
