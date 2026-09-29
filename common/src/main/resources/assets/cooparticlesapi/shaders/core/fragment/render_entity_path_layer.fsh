#version 330 core

// ================= 路径图层共用示例 - 片元 =================
// 只把顶点阶段算好的颜色输出；路径几何与采样位置全部来自共享图层。

in vec4 fragColor;
in float fragT;

out vec4 outColor;

void main() {
    // 沿路径做一点明暗变化，便于观察采样密度是否足够。
    float shading = 0.75 + 0.25 * sin(fragT * 24.0);
    outColor = vec4(fragColor.rgb * shading, fragColor.a);
}
