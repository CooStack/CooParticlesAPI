#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec4 vColor;
layout(location = 0) out vec4 fragColor;

void main() {
    // 核心：HDR 输出
    fragColor = vec4(vColor.rgb * 8.0, vColor.a);
}
