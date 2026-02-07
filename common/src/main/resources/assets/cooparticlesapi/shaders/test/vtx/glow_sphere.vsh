#version 330 core

layout (location = 0) in vec3 pos;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;

out vec3 viewNormal;

void main() {
    vec3 normal = normalize(pos);
    viewNormal = normalize(mat3(viewMat * transMat) * normal);
    gl_Position = projMat * viewMat * transMat * vec4(pos, 1.0);
}
