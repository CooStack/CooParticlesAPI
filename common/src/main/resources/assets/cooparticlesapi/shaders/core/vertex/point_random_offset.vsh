#version 330 core

layout (location = 0) in vec3 pos;

uniform mat4 projMat;
uniform mat4 transMat;
uniform mat4 viewMat;
uniform float offset;
uniform float time;
float random(vec3 st) {
    return fract(sin(dot(st, vec3(12.9898, 78.233, 45.164))) * 43758.5453123);
}
void main() {
    // 原始半径
    float r = length(pos);

    // 球面方向
    vec3 dir = normalize(pos);

    // 在球面上制造波动：可以用球面坐标的某个组合来做输入
    float theta = atan(pos.y, pos.x); // 经度
    float phi   = acos(pos.z / r);    // 纬度

    // 波动函数 (time 控制动画)
    float wave = sin(theta * 4.0 + time) * 0.1
    + sin(phi * 6.0 + time * 1.5) * 0.05;

    // 新的半径
    float newR = r + wave * offset;

    // 应用变换
    vec3 offsetPos = dir * newR;

    gl_Position = projMat * viewMat * transMat * vec4(offsetPos, 1.0);
}
