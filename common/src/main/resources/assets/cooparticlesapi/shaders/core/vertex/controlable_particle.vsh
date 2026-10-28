#version 330

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec2 LightUV;

uniform mat4 uView;
uniform mat4 uProj;

out vec2 vUv;
out vec4 vColor;
out vec2 vLightUv;
out vec2 vFogDistance;

void main() {
    gl_Position = uProj * uView * vec4(Position, 1.0);
    vUv = UV0;
    vColor = Color;
    vLightUv = LightUV;
    vFogDistance = vec2(length(Position), max(length(Position.xz), abs(Position.y)));
}
