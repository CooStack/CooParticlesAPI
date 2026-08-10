#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec4 vertexColor;
layout (location = 2) in vec2 vertexUv;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;

out vec4 fragColor;

void main() {
    fragColor = vertexColor;
    gl_Position = projMat * viewMat * transMat * vec4(pos, 1.0);
}
