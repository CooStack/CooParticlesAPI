#version 150

in vec4 vColor;
out vec4 fragColor;

void main() {
    // 核心：HDR 输出
    fragColor = vec4(vColor.rgb * 8.0, vColor.a);
}
