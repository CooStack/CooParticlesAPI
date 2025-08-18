#version 330 core

in vec2 screen_uv;

out vec4 FragColor;
uniform sampler2D tex;
void main() {
    vec4 color = texture(tex, screen_uv);
    FragColor = color;

//    FragColor = vec4(1.);
}