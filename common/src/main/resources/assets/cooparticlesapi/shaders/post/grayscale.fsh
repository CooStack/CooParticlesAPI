    #version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform float progress = 1.0;

void main() {
    vec4 color = texture(scene, screen_uv);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    FragColor = vec4(mix(color.rgb, vec3(gray), clamp(progress, 0.0, 1.0)), color.a);
}
