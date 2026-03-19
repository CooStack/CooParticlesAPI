#version 330 core

layout (location = 0) in vec3 pos;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;

out vec3 localPos;
out vec3 viewPos;

void main() {
    vec4 worldPos = transMat * vec4(pos, 1.0);
    vec4 localViewPos = viewMat * worldPos;
    localPos = pos;
    viewPos = localViewPos.xyz;
    gl_Position = projMat * localViewPos;
}
