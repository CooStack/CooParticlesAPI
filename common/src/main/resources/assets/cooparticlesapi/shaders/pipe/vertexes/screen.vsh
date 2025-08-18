#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 aUv;

out vec2 screen_uv;
void main() {
    gl_Position = vec4(pos.xy,0., 1.);
    screen_uv = aUv;
}