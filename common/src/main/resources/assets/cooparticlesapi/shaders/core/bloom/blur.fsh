#version 330 core

in vec2 screen_uv;
//layout (location = 0) out vec4 FragColor;
//layout (location = 1) out vec4 BrightColor;
out vec4 BrightColor;
//uniform sampler2D image;
uniform sampler2D bright;
uniform float weight[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
uniform bool horizontal;
vec3 gass(vec2 tex_offset, bool h) {
    vec3 v = vec3(0.);
    if (h) {
        for (int i = 1; i < 5; ++i) {
            v += texture(bright, screen_uv + vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
            v += texture(bright, screen_uv - vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
        }
    } else {
        for (int i = 1; i < 5; ++i) {
            v += texture(bright, screen_uv + vec2(0.0, tex_offset.y * i)).rgb * weight[i];
            v += texture(bright, screen_uv - vec2(0.0, tex_offset.y * i)).rgb * weight[i];
        }
    }
    return v;
}
void main() {
    vec2 tex_offset = 1.0 / textureSize(bright, 0);// 获取纹理大小
//    vec4 color = texture(image, screen_uv);
    vec3 result = texture(bright, screen_uv).rgb * weight[0];
    result += gass(tex_offset, horizontal);
    //    FragColor = color;
    BrightColor = vec4(result, 1.);
}

