#version 330 core

in vec2 uv;
out vec4 FragCoord;
uniform sampler2D tex;

void main(){
    vec4 color = texture2D(tex, uv);
    if (color.a <= 1.0e-4) {
        discard;
    }
    FragCoord = color;
}
