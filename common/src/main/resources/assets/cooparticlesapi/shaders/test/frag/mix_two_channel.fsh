#version 330 core

out vec4 FragColor;

in vec2 screen_uv;

uniform sampler2D scene;
uniform sampler2D back_scene;
uniform float delta;
void main(){
    vec4 color = texture(scene, screen_uv);
    vec4 back = texture(back_scene, screen_uv);
    //    FragColor = vec4(mix(back.rgb, color.rgb, abs(sin(delta*0.01))), color.a);
    FragColor = back;
}
