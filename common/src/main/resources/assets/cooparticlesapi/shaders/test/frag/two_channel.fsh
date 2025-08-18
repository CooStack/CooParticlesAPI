#version 330 core


layout (location=0) out vec4 FragColor;
layout (location=1) out vec4 BackColor;

in vec2 screen_uv;

uniform sampler2D scene;
void main(){
    vec4 color = texture(scene, screen_uv);
    FragColor = color;
    BackColor = vec4(1.0, 0.8, 0.8, color.a);
}
