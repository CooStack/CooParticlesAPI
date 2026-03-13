#version 330 core

layout (location = 0) in vec3 pos;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;

out vec3 viewNormal;
out vec3 viewPos;
out vec3 localPos;
out vec3 worldPos3;
out vec3 centerViewPos;

void main() {
    vec4 worldPos = transMat * vec4(pos, 1.0);
    vec4 localViewPos = viewMat * worldPos;
    vec4 centerView = viewMat * transMat * vec4(0.0, 0.0, 0.0, 1.0);
    vec3 normal = normalize(pos);
    viewNormal = normalize(mat3(viewMat * transMat) * normal);
    viewPos = localViewPos.xyz;
    localPos = pos;
    worldPos3 = worldPos.xyz;
    centerViewPos = centerView.xyz;
    gl_Position = projMat * localViewPos;
}
