#version 330 core

in vec2 screen_uv;

uniform sampler2D scene;
out vec4 FragColor;

void main(){
    vec4 color = texture2D(scene,screen_uv);
    color.b += 0.1;
    FragColor = color;
}