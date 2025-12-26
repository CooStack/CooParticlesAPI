#version 330 core

in vec2 uv;
sampler2D tex;

out vec4 FragColor;

uniform vec3 color;
void main() {
    vec4 t = texture(tex, uv);
    FragColor =t;
}