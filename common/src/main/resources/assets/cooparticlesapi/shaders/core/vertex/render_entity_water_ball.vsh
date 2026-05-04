#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec4 vertexColor;
layout (location = 2) in vec2 vertexUv;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;
uniform float time;
uniform float radius;

out vec4 fragColor;
out vec2 texUv;
out vec3 viewPos;
out vec3 viewNormal;

const float TAU = 6.28318530718;

void main() {
    float safeRadius = max(radius, 0.001);
    vec3 normal = normalize(pos / safeRadius);
    float broadWave = sin((vertexUv.x * 3.2 + time * 0.13) * TAU + vertexUv.y * 4.0) * 0.006;
    float crossWave = sin((vertexUv.x * -2.0 + vertexUv.y * 2.6 + time * 0.08) * TAU) * 0.004;
    vec3 displaced = pos + normal * (broadWave + crossWave) * safeRadius;
    vec4 worldPosition = transMat * vec4(displaced, 1.0);
    vec4 cameraPosition = viewMat * worldPosition;

    fragColor = vertexColor;
    texUv = vertexUv;
    viewPos = cameraPosition.xyz;
    viewNormal = normalize(mat3(viewMat * transMat) * normal);
    gl_Position = projMat * cameraPosition;
}
