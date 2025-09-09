#version 330 core
in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D lowerRes; // 较低分辨率模糊图
uniform sampler2D higherRes; // 当前层模糊图
uniform float intensity;     // 控制叠加强度

void main()
{
    vec3 low = texture(lowerRes, screen_uv).rgb;
    vec3 high = texture(higherRes, screen_uv).rgb;

    // 把低分辨率结果拉伸到高分辨率并混合
    vec3 result = high + low * intensity;
    FragColor = vec4(result, 1.0);
}