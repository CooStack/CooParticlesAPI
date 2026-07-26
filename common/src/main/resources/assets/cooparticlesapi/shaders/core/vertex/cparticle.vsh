#version 150

// ================= cparticle GPU 粒子渲染 - 顶点着色器 =================
// 无 per-vertex 属性: 四个角由 gl_VertexID 生成 (TRIANGLE_STRIP x4)
// 7 个 vec4 实例属性 (divisor=1), 布局与 CParticleStore 一致

in vec4 iPosAge;     // pos.xyz (系统原点相对), age
in vec4 iPrevMaxAge; // prevPos.xyz, maxAge
in vec4 iVelFlags;   // vel.xyz, flags(整数 float)
in vec4 iSizeRot;    // sizeW, sizeH, yaw, pitch
in vec4 iAxisRoll;   // axis.xyz, roll
in vec4 iUv;         // u0 v0 u1 v1
in vec4 iColor;      // r g b a

uniform mat4 uProj;
uniform mat4 uView;
uniform mat4 uPrevGroupMat;
uniform mat4 uGroupMat;      // 整组变换 (scripted), 默认单位阵
uniform vec3 uOriginRelCam;  // 系统原点 - 相机位置 (CPU 双精度相减)
uniform float uPartial;      // tick 插值
uniform vec3 uCamLeft;       // 相机左向量 (q * X̂, 与原版粒子渲染基一致)
uniform vec3 uCamUp;         // 相机上向量
uniform int uFogShape;       // 0=球 1=圆柱

// 生命周期曲线: [t0..t7, v0..v7]
uniform int uAlphaKeys;
uniform float uAlphaCurve[16];
uniform int uSizeKeys;
uniform float uSizeCurve[16];
uniform float uSystemTime;
uniform float uCurveCycleTicks;
uniform float uColorCycleTicks;
uniform float uColorCycleSpatialScale;

uniform int uTransitionEnabled;
uniform vec4 uTransitionParams; // alphaScale, sizeScale, colorProgress, hasColor
uniform vec3 uTransitionColorFrom;
uniform vec3 uTransitionColorTo;
uniform float uAlphaTransitionScale;

out vec2 vUv;
out vec4 vColor;
out vec2 vLightUv;
out float vFogDistance;

const int FLAG_ALIVE = 1;
const float TAU = 6.28318530718;

float sampleAlphaCurve(float t) {
    if (uAlphaKeys <= 0) return 1.0;
    if (t <= uAlphaCurve[0]) return uAlphaCurve[8];
    for (int i = 1; i < uAlphaKeys; i++) {
        if (t <= uAlphaCurve[i]) {
            float t0 = uAlphaCurve[i - 1];
            float t1 = uAlphaCurve[i];
            float f = t1 > t0 ? (t - t0) / (t1 - t0) : 0.0;
            return mix(uAlphaCurve[8 + i - 1], uAlphaCurve[8 + i], f);
        }
    }
    return uAlphaCurve[8 + uAlphaKeys - 1];
}

float sampleSizeCurve(float t) {
    if (uSizeKeys <= 0) return 1.0;
    if (t <= uSizeCurve[0]) return uSizeCurve[8];
    for (int i = 1; i < uSizeKeys; i++) {
        if (t <= uSizeCurve[i]) {
            float t0 = uSizeCurve[i - 1];
            float t1 = uSizeCurve[i];
            float f = t1 > t0 ? (t - t0) / (t1 - t0) : 0.0;
            return mix(uSizeCurve[8 + i - 1], uSizeCurve[8 + i], f);
        }
    }
    return uSizeCurve[8 + uSizeKeys - 1];
}

mat3 rotXYZ(float pitch, float yaw, float roll) {
    float cx = cos(pitch), sx = sin(pitch);
    float cy = cos(yaw), sy = sin(yaw);
    float cz = cos(roll), sz = sin(roll);
    // R = Rx * Ry * Rz (与 JOML Quaternionf.rotateXYZ 一致)
    mat3 rx = mat3(1.0, 0.0, 0.0, 0.0, cx, sx, 0.0, -sx, cx);
    mat3 ry = mat3(cy, 0.0, -sy, 0.0, 1.0, 0.0, sy, 0.0, cy);
    mat3 rz = mat3(cz, sz, 0.0, -sz, cz, 0.0, 0.0, 0.0, 1.0);
    return rx * ry * rz;
}

vec3 perpendicularOf(vec3 axis) {
    vec3 ref = abs(axis.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    return normalize(cross(axis, ref));
}

// Rodrigues: v 绕单位轴 axis 旋转 angle
vec3 rotateAroundAxis(vec3 v, vec3 axis, float angle) {
    float c = cos(angle), s = sin(angle);
    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

void main() {
    int flags = int(iVelFlags.w + 0.5);
    if ((flags & FLAG_ALIVE) == 0) {
        // 死槽位: 输出被裁剪的退化位置
        gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
        vUv = vec2(0.0);
        vColor = vec4(0.0);
        vLightUv = vec2(0.0);
        vFogDistance = 0.0;
        return;
    }

    float maxAge = max(iPrevMaxAge.w, 1.0);
    float age = clamp(iPosAge.w + uPartial, 0.0, maxAge);
    float lifeT = clamp(age / maxAge, 0.0, 1.0);
    float curveT = uCurveCycleTicks > 0.0
        ? fract(uSystemTime / uCurveCycleTicks)
        : lifeT;
    bool applyTransition = uTransitionEnabled != 0;

    // 位置插值 (prev -> cur) + 整组变换 + 相机相对
    vec3 previousRel = (uPrevGroupMat * vec4(iPrevMaxAge.xyz, 1.0)).xyz;
    vec3 currentRel = (uGroupMat * vec4(iPosAge.xyz, 1.0)).xyz;
    vec3 rel = mix(previousRel, currentRel, uPartial) + uOriginRelCam;

    // 四角: (-1,-1) (1,-1) (-1,1) (1,1) — 与原版 ±1 * size 的半径语义一致
    vec2 corner = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1)) * 2.0 - 1.0;
    float sizeScale = sampleSizeCurve(curveT);
    if (applyTransition) {
        sizeScale *= uTransitionParams.y;
    }
    vec2 size = iSizeRot.xy * sizeScale;
    vec2 local = corner * size;

    int mode = (flags >> 1) & 3;
    float roll = iAxisRoll.w;
    vec3 offset;
    if (mode == 0) {
        // BILLBOARD: 相机平面 + 平面内 roll (基向量与原版 q*X̂/q*Ŷ 一致)
        float cr = cos(roll), sr = sin(roll);
        vec2 lr = vec2(local.x * cr - local.y * sr, local.x * sr + local.y * cr);
        offset = uCamLeft * lr.x + uCamUp * lr.y;
    } else if (mode == 1) {
        // AXIS_BILLBOARD: 绕固定轴的圆柱广告牌 (对齐 MinecraftRendererUtil.axialBillboardBasis)
        vec3 axis = iAxisRoll.xyz;
        float axisLen = length(axis);
        axis = axisLen > 1e-6 ? axis / axisLen : vec3(0.0, 1.0, 0.0);
        vec3 toCamera = -rel; // 相机在粒子坐标系中的方向
        vec3 flat0 = toCamera - axis * dot(toCamera, axis);
        vec3 face = length(flat0) > 1e-6 ? normalize(flat0) : perpendicularOf(axis);
        vec3 right = cross(face, axis);
        right = length(right) > 1e-6 ? normalize(right) : perpendicularOf(axis);
        if (abs(roll) > 1e-6) {
            right = rotateAroundAxis(right, axis, roll);
        }
        offset = right * local.x + axis * local.y;
    } else {
        // ROTATION: 自由欧拉角 (对齐 Quaternionf.rotateXYZ(pitch, yaw, roll))
        offset = rotXYZ(iSizeRot.w, iSizeRot.z, roll) * vec3(local, 0.0);
    }

    vec3 posRelCam = rel + offset;
    gl_Position = uProj * uView * vec4(posRelCam, 1.0);

    // UV: x=+1 -> u1, y=+1 -> v0 (与 ControlableParticle.addDoubleSidedQuad 完全一致)
    float ut = 0.5 + corner.x * 0.5;
    float vt = 0.5 - corner.y * 0.5;
    vUv = vec2(mix(iUv.x, iUv.z, ut), mix(iUv.y, iUv.w, vt));

    vec3 particleColor = iColor.rgb;
    if (uColorCycleTicks > 0.0) {
        float angularPhase = atan(iPosAge.z, iPosAge.x) / TAU;
        float colorT = fract(
            uSystemTime / uColorCycleTicks + angularPhase * uColorCycleSpatialScale
        );
        particleColor = 0.5 + 0.5 * cos(TAU * (colorT + vec3(0.0, 0.3333333, 0.6666667)));
    }
    if (applyTransition && uTransitionParams.w > 0.5) {
        particleColor = mix(uTransitionColorFrom, uTransitionColorTo, uTransitionParams.z);
    }
    float alphaScale = sampleAlphaCurve(curveT);
    if (applyTransition) {
        alphaScale *= uTransitionParams.x;
    }
    alphaScale *= uAlphaTransitionScale;
    vColor = vec4(particleColor, iColor.a * alphaScale);

    int blockLight = (flags >> 3) & 15;
    int skyLight = (flags >> 7) & 15;
    vLightUv = vec2(
        (float(blockLight) * 16.0 + 8.0) / 256.0,
        (float(skyLight) * 16.0 + 8.0) / 256.0
    );

    // 原版雾距离
    vFogDistance = uFogShape == 1
        ? max(length(posRelCam.xz), abs(posRelCam.y))
        : length(posRelCam);
}
