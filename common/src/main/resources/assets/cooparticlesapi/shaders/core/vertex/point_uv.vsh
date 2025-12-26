#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 aUv;
uniform mat4 projMat;
uniform mat4 transMat;
uniform mat4 viewMat;


out vec2 uv;


void main() {
    gl_Position = projMat * viewMat * transMat * vec4(pos, 1.);
    uv = aUv;
}