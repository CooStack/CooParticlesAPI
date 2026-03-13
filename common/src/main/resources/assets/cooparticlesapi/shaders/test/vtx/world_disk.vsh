#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 aUv;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;

out vec2 uv;
out vec3 viewPos;
out vec3 localPos;

void main() {
    vec4 worldPos = transMat * vec4(pos, 1.0);
    vec4 localViewPos = viewMat * worldPos;
    uv = aUv;
    localPos = pos;
    viewPos = localViewPos.xyz;
    gl_Position = projMat * localViewPos;
}
