#version 330 core

// ================= 路径图层共用示例 - 顶点 =================
// 从 CParticle 路径约束使用的同一个路径图层 SSBO 读取预采样表。
// 顶点位置约定：x = 预采样序号，y = 笔画侧的符号（-1 / +1），z 未使用。
// 绑定点 4 由 CooGpuDataBindingPoints.PATH_LAYER 固定，与 compute 路径约束一致。

layout (location = 0) in vec3 pos;
layout (location = 1) in vec4 vertexColor;
layout (location = 2) in vec2 vertexUv;

layout(std430, binding = 4) readonly buffer CooPathLayerBuffer {
    float pathLayer[];
};

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;
uniform float time;
uniform vec4 lineColor;
uniform float pathBase;
uniform float pathSampleCount;
uniform float pathCurveType;

out vec4 fragColor;
out float fragT;

// 单个预采样在图层中的 float 步长，必须与 CooPathLayer.CooPathPayload.SAMPLE_STRIDE 一致。
// 布局：位置 3 + 切线 3 + 累计弧长 1 + 法线 3 + 副法线 3。
const int SAMPLE_STRIDE = 13;
const int SAMPLE_POSITION = 0;
const int SAMPLE_NORMAL = 7;
const int SAMPLE_BINORMAL = 10;
// 笔画在横截面内的视觉半宽（世界单位）。
const float STROKE_HALF_WIDTH = 0.035;

void main() {
    int sampleIndex = int(pos.x + 0.5);
    int base = int(pathBase + 0.5);
    int sampleOffset = base + sampleIndex * SAMPLE_STRIDE;
    vec3 samplePosition = vec3(
        pathLayer[sampleOffset + SAMPLE_POSITION],
        pathLayer[sampleOffset + SAMPLE_POSITION + 1],
        pathLayer[sampleOffset + SAMPLE_POSITION + 2]
    );
    vec3 sampleNormal = vec3(
        pathLayer[sampleOffset + SAMPLE_NORMAL],
        pathLayer[sampleOffset + SAMPLE_NORMAL + 1],
        pathLayer[sampleOffset + SAMPLE_NORMAL + 2]
    );
    vec3 sampleBinormal = vec3(
        pathLayer[sampleOffset + SAMPLE_BINORMAL],
        pathLayer[sampleOffset + SAMPLE_BINORMAL + 1],
        pathLayer[sampleOffset + SAMPLE_BINORMAL + 2]
    );
    // 笔画宽度沿横截面展开：参考基由路径预采样表提供，因此不会在平行处突然翻转。
    vec3 lateral = normalize(sampleNormal + sampleBinormal * pos.y);
    vec3 displaced = samplePosition + lateral * (STROKE_HALF_WIDTH * pos.y);

    vec4 worldPosition = transMat * vec4(displaced, 1.0);
    vec4 cameraPosition = viewMat * worldPosition;

    float flow = 0.55 + 0.45 * sin(time * 0.12 - pos.x * 0.35);
    fragColor = vec4(lineColor.rgb, lineColor.a * flow) * vertexColor;
    fragT = pathSampleCount <= 1.0 ? 0.0 : pos.x / max(pathSampleCount - 1.0, 1.0);
    gl_Position = projMat * cameraPosition;
}
