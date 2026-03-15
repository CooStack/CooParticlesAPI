#version 330 core

layout (location = 0) in vec3 pos;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;
out vec3 viewNormal;
out vec3 viewPos;
out vec3 localPos;

void main() {
    vec4 worldPos = transMat * vec4(pos, 1.0);
    vec4 localViewPos = viewMat * worldPos;
    vec3 normal = normalize(pos);
    viewNormal = normalize(mat3(viewMat * transMat) * normal);
    viewPos = localViewPos.xyz;
    localPos = pos;
    gl_Position = projMat * localViewPos;
}
