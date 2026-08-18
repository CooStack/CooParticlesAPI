#version 150 core

in vec3 aPosition;
in vec3 aNormal;
in vec2 aTexCoord;
in vec4 aVertexColor;
in vec4 aCurrentPositionAge;
in vec4 aPreviousPositionLifetime;
in vec4 aCurrentRotation;
in vec4 aPreviousRotation;
in vec4 aCurrentScaleLight;
in vec4 aPreviousScaleMaterial;
in vec4 aInstanceColor;
in vec4 aClipPlayback;
in vec4 aSeedFlagsId;
in vec4 aNodeWorldRow0;
in vec4 aNodeWorldRow1;
in vec4 aNodeWorldRow2;

uniform mat4 uView;
uniform mat4 uProjection;
uniform vec3 uCameraPosition;
uniform bool uIrisEntitySpace;
uniform float uPartialTick;
uniform bool uFullBright;
uniform vec4 uBaseColorFactor;

out vec2 vTexCoord;
out vec4 vColor;
out vec3 vWorldNormal;
flat out ivec2 vLightUv;
out vec3 tfEntityPosition;
flat out uint tfEntityColor;
out vec2 tfEntityUv;
flat out uint tfEntityOverlay;
flat out uint tfEntityLight;
flat out uint tfEntityNormal;

vec3 rotateByQuaternion(vec4 rotation, vec3 value) {
    return value + 2.0 * cross(rotation.xyz, cross(rotation.xyz, value) + rotation.w * value);
}

vec4 interpolateQuaternion(vec4 previousRotation, vec4 currentRotation, float progress) {
    vec4 target = dot(previousRotation, currentRotation) < 0.0 ? -currentRotation : currentRotation;
    return normalize(mix(previousRotation, target, progress));
}

uint packUnormColor(vec4 color) {
    uvec4 bytes = uvec4(round(clamp(color, 0.0, 1.0) * 255.0));
    return bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
}

uint packSnormNormal(vec3 normal) {
    ivec3 bytes = ivec3(round(clamp(normal, -1.0, 1.0) * 127.0));
    return (uint(bytes.x) & 255u)
        | ((uint(bytes.y) & 255u) << 8u)
        | ((uint(bytes.z) & 255u) << 16u);
}

void main() {
    float progress = clamp(uPartialTick, 0.0, 1.0);
    vec3 position = mix(aPreviousPositionLifetime.xyz, aCurrentPositionAge.xyz, progress);
    vec3 scale = mix(aPreviousScaleMaterial.xyz, aCurrentScaleLight.xyz, progress);
    vec4 rotation = interpolateQuaternion(aPreviousRotation, aCurrentRotation, progress);
    mat4 nodeWorldMatrix = transpose(mat4(
        aNodeWorldRow0,
        aNodeWorldRow1,
        aNodeWorldRow2,
        vec4(0.0, 0.0, 0.0, 1.0)
    ));
    vec3 nodePosition = (nodeWorldMatrix * vec4(aPosition, 1.0)).xyz;
    vec3 worldPosition = position + rotateByQuaternion(rotation, nodePosition * scale);

    vec3 particleBasisX = rotateByQuaternion(rotation, vec3(scale.x, 0.0, 0.0));
    vec3 particleBasisY = rotateByQuaternion(rotation, vec3(0.0, scale.y, 0.0));
    vec3 particleBasisZ = rotateByQuaternion(rotation, vec3(0.0, 0.0, scale.z));
    mat3 combinedLinear = mat3(particleBasisX, particleBasisY, particleBasisZ) * mat3(nodeWorldMatrix);

    vec3 cameraRelativePosition = worldPosition - uCameraPosition;
    gl_Position = uProjection * uView * vec4(cameraRelativePosition, 1.0);
    vTexCoord = aTexCoord;
    vColor = aVertexColor * aInstanceColor;
    vWorldNormal = normalize(transpose(inverse(combinedLinear)) * aNormal);
    int packedLight = int(round(aCurrentScaleLight.w));
    vLightUv = uFullBright
        ? ivec2(240, 240)
        : ivec2(packedLight & 65535, (packedLight >> 16) & 65535);
    // Iris entity shader 接收已经完成模型变换的相机相对坐标。
    tfEntityPosition = cameraRelativePosition;
    tfEntityColor = packUnormColor(vColor * uBaseColorFactor);
    tfEntityUv = vTexCoord;
    tfEntityOverlay = 655360u;
    tfEntityLight = uint(vLightUv.x) | (uint(vLightUv.y) << 16u);
    tfEntityNormal = packSnormNormal(vWorldNormal);
}
