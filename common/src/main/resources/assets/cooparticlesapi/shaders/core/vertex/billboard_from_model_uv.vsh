#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 aUv;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;

out vec2 uv;

void main() {
    vec4 center = viewMat * transMat * vec4(0.0, 0.0, 0.0, 1.0);
    float sizeX = length(transMat[0].xyz);
    float sizeY = length(transMat[1].xyz);
    vec2 offset = vec2(pos.x * sizeX, pos.y * sizeY);
    gl_Position = projMat * vec4(center.xyz + vec3(offset, 0.0), 1.0);
    uv = aUv;
}
