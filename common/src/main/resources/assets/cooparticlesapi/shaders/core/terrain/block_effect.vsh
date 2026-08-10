#version 150

#coo_import <terrain_light_fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 BaseUV;
in ivec2 EffectUV;
in ivec2 LightUV;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform vec3 CameraPosition;
uniform vec3 CooEffectUvCameraPosition;
uniform int CooEffectUvMode;
uniform float CooGameTime;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 baseUv;
out vec2 effectUv;
out vec3 worldPosition;
out vec3 worldNormal;
flat out float effectElapsedTicks;

void main() {
    vec3 position = Position + ChunkOffset;

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexDistance = fog_distance(position, FogShape);
    int packedLightX = LightUV.x & 65535;
    int packedLightY = LightUV.y & 65535;
    ivec2 lightUv = ivec2(packedLightX & 255, packedLightY & 255);
    int activationTick = ((packedLightY >> 8) << 8) | (packedLightX >> 8);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, lightUv);
    baseUv = BaseUV;
    worldPosition = position + CameraPosition;
    vec3 effectUvPosition = position + CooEffectUvCameraPosition;
    vec2 packedEffectUv = (vec2(EffectUV) + 32768.0) / 65535.0;
    if (CooEffectUvMode == 2) {
        effectUv = mod(effectUvPosition.xz, 1024.0);
    } else if (CooEffectUvMode == 3) {
        effectUv = mod(effectUvPosition.xy, 1024.0);
    } else if (CooEffectUvMode == 4) {
        effectUv = mod(effectUvPosition.yz, 1024.0);
    } else {
        effectUv = packedEffectUv;
    }
    worldNormal = normalize(Normal);
    effectElapsedTicks = mod(CooGameTime - float(activationTick) + 65536.0, 65536.0);
}
