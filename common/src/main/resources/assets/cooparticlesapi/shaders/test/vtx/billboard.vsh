#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 aUv;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;
uniform vec2 size;

out vec2 uv;

void main() {
    vec4 center = viewMat * transMat * vec4(0.0, 0.0, 0.0, 1.0);
    vec2 scaled = pos.xy * size;
    gl_Position = projMat * vec4(center.xyz + vec3(scaled, 0.0), 1.0);
    uv = aUv;
}
