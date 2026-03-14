#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 aUv;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;
uniform vec2 screenSize;
uniform vec2 sizePx;

out vec2 uv;

void main() {
    vec4 clipCenter = projMat * viewMat * transMat * vec4(0.0, 0.0, 0.0, 1.0);
    if (clipCenter.w <= 1.0e-5) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        uv = aUv;
        return;
    }

    vec2 safeScreenSize = max(screenSize, vec2(1.0, 1.0));
    vec2 offsetNdc = (pos.xy * sizePx * 2.0) / safeScreenSize;
    gl_Position = vec4(clipCenter.xy + offsetNdc * clipCenter.w, clipCenter.z, clipCenter.w);
    uv = aUv;
}
