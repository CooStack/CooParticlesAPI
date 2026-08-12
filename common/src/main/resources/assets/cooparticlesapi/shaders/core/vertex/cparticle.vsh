#version 150

// ================= cparticle GPU 粒子渲染 - 顶点着色器 =================
// 无 per-vertex 属性: 六个三角形顶点由 gl_VertexID 生成 (TRIANGLES x6)
// 9 个 vec4 实例属性 (divisor=1), 布局与 CParticleStore 一致

in vec4 iPosAge;     // pos.xyz (系统原点相对), age
in vec4 iPrevMaxAge; // prevPos.xyz, maxAge
in vec4 iVelFlags;   // vel.xyz, flags(整数 float)
in vec4 iSizeRot;    // sizeW, sizeH, yaw, pitch
in vec4 iAxisRoll;   // axis.xyz, roll
in vec4 iAnimation;  // animationId, visualAgeBase, seedLow16, seedHigh16
in vec4 iColor;      // r g b a
in vec4 iAngularEpoch; // angularVelocity(pitch,yaw,roll), epochTick
in vec4 iAppearance; // appearanceDescriptorId, speedLimit/systemSentinel, maskAnimationId, packedMaskRgb8

uniform mat4 uProj;
uniform mat4 uView;
uniform mat4 uPrevGroupMat;
uniform mat4 uGroupMat;      // 整组变换 (scripted), 默认单位阵
uniform int uTransformParticleGeometry;
uniform vec3 uOriginRelCam;  // 系统原点 - 相机位置 (CPU 双精度相减)
uniform float uPartial;      // tick 插值
uniform vec3 uCamLeft;       // 相机左向量 (q * X̂, 与原版粒子渲染基一致)
uniform vec3 uCamUp;         // 相机上向量
uniform int uFogShape;       // 0=球 1=圆柱
uniform int uSystemTick;
uniform int uHasMask;
uniform int uIrisExpansion;
uniform samplerBuffer uAnimationLookup;
uniform samplerBuffer uAppearanceLookup;

// 生命周期曲线: [t0..t7, v0..v7]
uniform int uAlphaKeys;
uniform int uAlphaCurveType;
uniform float uAlphaCurve[16];
uniform vec4 uAlphaCurveHandles[8];
uniform int uScaleKeys;
uniform int uScaleCurveType;
uniform float uScaleCurve[16];
uniform vec4 uScaleCurveHandles[8];
uniform int uColorKeys;
uniform int uColorCurveType;
uniform float uColorCurveTimes[8];
uniform vec3 uColorCurveValues[8];
uniform vec4 uColorCurveOutHandles[8];
uniform vec4 uColorCurveInHandles[8];
uniform float uSystemTime;
uniform float uCurveCycleTicks;
uniform float uColorCycleTicks;
uniform float uColorCycleSpatialScale;

uniform int uTransitionEnabled;
uniform vec4 uTransitionParams; // reserved, reserved, progress, hasColor
uniform int uTransitionAlphaKeys;
uniform int uTransitionAlphaCurveType;
uniform float uTransitionAlphaCurve[16];
uniform vec4 uTransitionAlphaCurveHandles[8];
uniform int uTransitionScaleKeys;
uniform int uTransitionScaleCurveType;
uniform float uTransitionScaleCurve[16];
uniform vec4 uTransitionScaleCurveHandles[8];
uniform vec3 uTransitionColorFrom;
uniform vec3 uTransitionColorTo;
uniform int uAlphaTransitionKeys;
uniform int uAlphaTransitionCurveType;
uniform float uAlphaTransitionCurve[16];
uniform vec4 uAlphaTransitionCurveHandles[8];
uniform float uAlphaTransitionProgress;

out vec2 vUv;
out vec2 vMaskUv;
out vec3 vMaskTint;
out vec4 vColor;
out vec2 vLightUv;
out float vFogDistance;
out vec3 tfPosition;
out vec2 tfUv;
flat out uvec2 tfPacked;

const int FLAG_ALIVE = 1;
const int FLAG_RANDOM_AGE = 1 << 11;
const int FLAG_ROTATION_DIRECTION = 1 << 12;
const int FLAG_RANDOM_QUARTER_UV = 1 << 13;
const int FLAG_MASK_RANDOM_QUARTER_UV = 1 << 14;
const float FRAME_PROGRESS_RESOLUTION = 4096.0;
const float TAU = 6.28318530718;
const int APPEARANCE_TEXELS = 73;
const int CURVE_TYPE_RADIX = 16;
const int PACKED_CURVE_RADIX = 32;
const int CURVE_TYPE_BEZIER = 1;
const int BEZIER_SOLVE_ITERATIONS = 10;

uint packUnormColor(vec4 color) {
    uvec4 bytes = uvec4(round(clamp(color, 0.0, 1.0) * 255.0));
    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);
}

uint hash32(uint value) {
    uint x = value;
    x = (x ^ (x >> 16u)) * 0x7FEB352Du;
    x = (x ^ (x >> 15u)) * 0x846CA68Bu;
    return x ^ (x >> 16u);
}

uint instanceSeed() {
    return uint(iAnimation.z + 0.5) | (uint(iAnimation.w + 0.5) << 16u);
}

float hashUnitFloat(uint value) {
    return float(hash32(value) & 0x00FFFFFFu) / 16777216.0;
}

vec4 cropRandomQuarterUv(vec4 sourceUv) {
    uint seed = instanceSeed();
    float uOffset = hashUnitFloat(seed ^ 0xA511E9B3u) * 3.0;
    float vOffset = hashUnitFloat(seed ^ 0x63D83595u) * 3.0;
    vec2 span = sourceUv.zw - sourceUv.xy;
    return vec4(
        sourceUv.x + span.x * (uOffset + 1.0) * 0.25,
        sourceUv.y + span.y * vOffset * 0.25,
        sourceUv.x + span.x * uOffset * 0.25,
        sourceUv.y + span.y * (vOffset + 1.0) * 0.25
    );
}

vec3 unpackRgb8(float packedValue) {
    uint packedColor = uint(packedValue);
    return vec3(
        float(packedColor & 0xFFu),
        float((packedColor >> 8u) & 0xFFu),
        float((packedColor >> 16u) & 0xFFu)
    ) / 255.0;
}

vec4 resolveAnimationUv(int animationId, int flags, float maxAge, float visualAge) {
    vec4 metadata = texelFetch(uAnimationLookup, animationId);
    int frameOffset = max(int(metadata.x + 0.5), 0);
    int frameCount = max(int(metadata.y + 0.5), 1);
    int frameIndex;
    if ((flags & FLAG_RANDOM_AGE) != 0) {
        uint seed = instanceSeed();
        uint randomValue = hash32(seed ^ uint(uSystemTick) * 0x9E3779B9u);
        frameIndex = int(randomValue % uint(frameCount));
    } else {
        float frameAge = floor(clamp(visualAge, 0.0, maxAge) * FRAME_PROGRESS_RESOLUTION / maxAge);
        frameIndex = int(floor(frameAge * float(frameCount - 1) / FRAME_PROGRESS_RESOLUTION));
    }
    return texelFetch(uAnimationLookup, frameOffset + frameIndex);
}

int appearanceBase() {
    return max(int(iAppearance.x + 0.5), 0) * APPEARANCE_TEXELS;
}

float cubicBezierComponent(float parameter, float first, float firstControl, float secondControl, float second) {
    float inverse = 1.0 - parameter;
    float inverseSquared = inverse * inverse;
    float parameterSquared = parameter * parameter;
    return inverseSquared * inverse * first
        + 3.0 * inverseSquared * parameter * firstControl
        + 3.0 * inverse * parameterSquared * secondControl
        + parameterSquared * parameter * second;
}

float sampleBezierParameter(float t, float first, float firstControl, float secondControl, float second) {
    if (t <= first) return 0.0;
    if (t >= second) return 1.0;
    float low = 0.0;
    float high = 1.0;
    for (int iteration = 0; iteration < BEZIER_SOLVE_ITERATIONS; iteration++) {
        float middle = (low + high) * 0.5;
        if (cubicBezierComponent(middle, first, firstControl, secondControl, second) < t) {
            low = middle;
        } else {
            high = middle;
        }
    }
    return (low + high) * 0.5;
}

int curveKeyCount(int encoded) {
    return encoded % CURVE_TYPE_RADIX;
}

int curveType(int encoded) {
    return encoded / CURVE_TYPE_RADIX;
}

float sampleParticleScalarCurve(
    float t,
    int encoded,
    int anchorOffset,
    int handleOffset,
    int timeComponent,
    int valueComponent
) {
    int keyCount = curveKeyCount(encoded);
    if (keyCount <= 0) return 1.0;
    int base = appearanceBase();
    vec4 first = texelFetch(uAppearanceLookup, base + anchorOffset);
    if (t <= first[timeComponent]) return first[valueComponent];
    for (int i = 1; i < 8; i++) {
        if (i >= keyCount) break;
        vec4 previous = texelFetch(uAppearanceLookup, base + anchorOffset + i - 1);
        vec4 current = texelFetch(uAppearanceLookup, base + anchorOffset + i);
        if (t <= current[timeComponent]) {
            if (curveType(encoded) != CURVE_TYPE_BEZIER) {
                float progress = (t - previous[timeComponent]) / (current[timeComponent] - previous[timeComponent]);
                return mix(previous[valueComponent], current[valueComponent], progress);
            }
            vec4 previousHandle = texelFetch(uAppearanceLookup, base + handleOffset + i - 1);
            vec4 currentHandle = texelFetch(uAppearanceLookup, base + handleOffset + i);
            float parameter = sampleBezierParameter(
                t,
                previous[timeComponent],
                previous[timeComponent] + previousHandle.x / 100.0,
                current[timeComponent] + currentHandle.z / 100.0,
                current[timeComponent]
            );
            return cubicBezierComponent(
                parameter,
                previous[valueComponent],
                previous[valueComponent] + previousHandle.y,
                current[valueComponent] + currentHandle.w,
                current[valueComponent]
            );
        }
    }
    return texelFetch(uAppearanceLookup, base + anchorOffset + keyCount - 1)[valueComponent];
}

float sampleParticleAlphaCurve(float t) {
    int encoded = int(texelFetch(uAppearanceLookup, appearanceBase()).x + 0.5);
    return sampleParticleScalarCurve(t, encoded, 1, 25, 0, 1);
}

float sampleParticleScaleCurve(float t) {
    int encoded = int(texelFetch(uAppearanceLookup, appearanceBase()).y + 0.5);
    return sampleParticleScalarCurve(t, encoded, 1, 33, 2, 3);
}

float sampleParticleScaleXCurve(float t) {
    int encoded = int(texelFetch(uAppearanceLookup, appearanceBase()).z + 0.5);
    return sampleParticleScalarCurve(t, encoded, 9, 41, 0, 1);
}

float sampleParticleScaleYCurve(float t) {
    int packedMetadata = int(texelFetch(uAppearanceLookup, appearanceBase()).w + 0.5);
    return sampleParticleScalarCurve(t, packedMetadata % PACKED_CURVE_RADIX, 9, 49, 2, 3);
}

vec3 sampleParticleColorCurve(float t) {
    int base = appearanceBase();
    int packedMetadata = int(texelFetch(uAppearanceLookup, base).w + 0.5);
    int encoded = packedMetadata / PACKED_CURVE_RADIX;
    int keyCount = curveKeyCount(encoded);
    if (keyCount <= 0) return vec3(1.0);
    int colorBase = base + 17;
    vec4 first = texelFetch(uAppearanceLookup, colorBase);
    if (t <= first.x) return first.yzw;
    for (int i = 1; i < 8; i++) {
        if (i >= keyCount) break;
        vec4 previous = texelFetch(uAppearanceLookup, colorBase + i - 1);
        vec4 current = texelFetch(uAppearanceLookup, colorBase + i);
        if (t <= current.x) {
            if (curveType(encoded) != CURVE_TYPE_BEZIER) {
                float progress = (t - previous.x) / (current.x - previous.x);
                return mix(previous.yzw, current.yzw, progress);
            }
            vec4 previousHandle = texelFetch(uAppearanceLookup, base + 57 + i - 1);
            vec4 currentHandle = texelFetch(uAppearanceLookup, base + 65 + i);
            float parameter = sampleBezierParameter(
                t,
                previous.x,
                previous.x + previousHandle.x / 100.0,
                current.x + currentHandle.x / 100.0,
                current.x
            );
            return vec3(
                cubicBezierComponent(parameter, previous.y, previous.y + previousHandle.y, current.y + currentHandle.y, current.y),
                cubicBezierComponent(parameter, previous.z, previous.z + previousHandle.z, current.z + currentHandle.z, current.z),
                cubicBezierComponent(parameter, previous.w, previous.w + previousHandle.w, current.w + currentHandle.w, current.w)
            );
        }
    }
    return texelFetch(uAppearanceLookup, colorBase + keyCount - 1).yzw;
}

float sampleScalarCurve(
    float t,
    int keyCount,
    int interpolation,
    float anchors[16],
    vec4 handles[8]
) {
    if (keyCount <= 0) return 1.0;
    if (t <= anchors[0]) return anchors[8];
    for (int i = 1; i < 8; i++) {
        if (i >= keyCount) break;
        if (t <= anchors[i]) {
            if (interpolation != CURVE_TYPE_BEZIER) {
                float progress = (t - anchors[i - 1]) / (anchors[i] - anchors[i - 1]);
                return mix(anchors[8 + i - 1], anchors[8 + i], progress);
            }
            float parameter = sampleBezierParameter(
                t,
                anchors[i - 1],
                anchors[i - 1] + handles[i - 1].x / 100.0,
                anchors[i] + handles[i].z / 100.0,
                anchors[i]
            );
            return cubicBezierComponent(
                parameter,
                anchors[8 + i - 1],
                anchors[8 + i - 1] + handles[i - 1].y,
                anchors[8 + i] + handles[i].w,
                anchors[8 + i]
            );
        }
    }
    return anchors[8 + keyCount - 1];
}

vec3 sampleRgbCurve(
    float t,
    int keyCount,
    int interpolation,
    float times[8],
    vec3 values[8],
    vec4 outHandles[8],
    vec4 inHandles[8]
) {
    if (keyCount <= 0) return vec3(1.0);
    if (t <= times[0]) return values[0];
    for (int i = 1; i < 8; i++) {
        if (i >= keyCount) break;
        if (t <= times[i]) {
            if (interpolation != CURVE_TYPE_BEZIER) {
                float progress = (t - times[i - 1]) / (times[i] - times[i - 1]);
                return mix(values[i - 1], values[i], progress);
            }
            float parameter = sampleBezierParameter(
                t,
                times[i - 1],
                times[i - 1] + outHandles[i - 1].x / 100.0,
                times[i] + inHandles[i].x / 100.0,
                times[i]
            );
            return vec3(
                cubicBezierComponent(parameter, values[i - 1].x, values[i - 1].x + outHandles[i - 1].y, values[i].x + inHandles[i].y, values[i].x),
                cubicBezierComponent(parameter, values[i - 1].y, values[i - 1].y + outHandles[i - 1].z, values[i].y + inHandles[i].z, values[i].y),
                cubicBezierComponent(parameter, values[i - 1].z, values[i - 1].z + outHandles[i - 1].w, values[i].z + inHandles[i].w, values[i].z)
            );
        }
    }
    return values[keyCount - 1];
}

float sampleAlphaCurve(float t) {
    return sampleScalarCurve(t, uAlphaKeys, uAlphaCurveType, uAlphaCurve, uAlphaCurveHandles);
}

float sampleScaleCurve(float t) {
    return sampleScalarCurve(t, uScaleKeys, uScaleCurveType, uScaleCurve, uScaleCurveHandles);
}

vec3 sampleColorCurve(float t) {
    return sampleRgbCurve(
        t,
        uColorKeys,
        uColorCurveType,
        uColorCurveTimes,
        uColorCurveValues,
        uColorCurveOutHandles,
        uColorCurveInHandles
    );
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
        vMaskUv = vec2(0.0);
        vMaskTint = vec3(1.0);
        vColor = vec4(0.0);
        vLightUv = vec2(0.0);
        vFogDistance = 0.0;
        tfPosition = vec3(0.0, 0.0, -2.0);
        tfUv = vec2(0.0);
        tfPacked = uvec2(0u);
        return;
    }

    float maxAge = max(iPrevMaxAge.w, 1.0);
    float epochElapsed = max(float(uSystemTick) - iAngularEpoch.w, 0.0);
    float visualAge = iAnimation.y + epochElapsed;
    float age = clamp(visualAge + uPartial, 0.0, maxAge);
    float lifeT = clamp(age / maxAge, 0.0, 1.0);
    float curveT = uCurveCycleTicks > 0.0
        ? fract(uSystemTime / uCurveCycleTicks)
        : lifeT;
    bool applyTransition = uTransitionEnabled != 0;

    // 位置插值 (prev -> cur) + 整组变换 + 相机相对
    vec3 previousRel = (uPrevGroupMat * vec4(iPrevMaxAge.xyz, 1.0)).xyz;
    vec3 currentRel = (uGroupMat * vec4(iPosAge.xyz, 1.0)).xyz;
    vec3 rel = mix(previousRel, currentRel, uPartial) + uOriginRelCam;

    int mode = (flags >> 1) & 3;
    float rotationElapsed = epochElapsed + uPartial;
    float roll = iAxisRoll.w + iAngularEpoch.z * rotationElapsed;
    vec3 basisX;
    vec3 basisY;
    if (mode == 0) {
        // BILLBOARD: 相机平面 + 平面内 roll (基向量与原版 q*X̂/q*Ŷ 一致)
        float cr = cos(roll), sr = sin(roll);
        basisX = uCamLeft * cr + uCamUp * sr;
        basisY = -uCamLeft * sr + uCamUp * cr;
    } else if (mode == 1) {
        // AXIS_BILLBOARD: 绕固定轴的圆柱广告牌 (对齐 MinecraftRendererUtil.axialBillboardBasis)
        vec3 axis = iAxisRoll.xyz;
        float axisLen = length(axis);
        axis = axisLen > 1e-6 ? axis / axisLen : vec3(0.0, 1.0, 0.0);
        vec3 toCamera = -rel;
        vec3 flat0 = toCamera - axis * dot(toCamera, axis);
        vec3 face = length(flat0) > 1e-6 ? normalize(flat0) : perpendicularOf(axis);
        vec3 right = cross(face, axis);
        right = length(right) > 1e-6 ? normalize(right) : perpendicularOf(axis);
        if (abs(roll) > 1e-6) {
            right = rotateAroundAxis(right, axis, roll);
        }
        basisX = right;
        basisY = axis;
    } else {
        // ROTATION: 自由欧拉角 (对齐 Quaternionf.rotateXYZ(pitch, yaw, roll))
        float pitch = iSizeRot.w;
        float yaw = iSizeRot.z;
        if ((flags & FLAG_ROTATION_DIRECTION) != 0) {
            vec3 direction = iAxisRoll.xyz;
            float horizontalLength = length(direction.xz);
            if (horizontalLength > 0.0 || abs(direction.y) > 0.0) {
                pitch += atan(direction.y, horizontalLength);
                yaw += -atan(direction.z, direction.x);
            }
        }
        pitch += iAngularEpoch.x * rotationElapsed;
        yaw += iAngularEpoch.y * rotationElapsed;
        mat3 particleRotation = rotXYZ(pitch, yaw, roll);
        basisX = particleRotation * vec3(1.0, 0.0, 0.0);
        basisY = particleRotation * vec3(0.0, 1.0, 0.0);
    }

    // BILLBOARD 保持固定正面绕序；另两种模式翻转背向相机的面，维持原有双面可见语义。
    vec3 toCamera = -rel;
    vec3 facingNormal = cross(basisY, basisX);
    bool reverseWinding = mode != 0 && dot(facingNormal, toCamera) < 0.0;
    int triangleVertex = gl_VertexID % 6;
    int cornerIndex;
    if (reverseWinding) {
        cornerIndex = triangleVertex == 0 ? 0
            : (triangleVertex == 1 ? 1
            : (triangleVertex == 2 ? 2
            : (triangleVertex == 3 ? 2
            : (triangleVertex == 4 ? 1 : 3))));
    } else {
        cornerIndex = triangleVertex == 0 ? 0
            : (triangleVertex == 1 ? 2
            : (triangleVertex == 2 ? 1
            : (triangleVertex == 3 ? 1
            : (triangleVertex == 4 ? 2 : 3))));
    }
    vec2 corner = vec2(float(cornerIndex & 1), float((cornerIndex >> 1) & 1)) * 2.0 - 1.0;
    float uniformScale = sampleParticleScaleCurve(lifeT) * sampleScaleCurve(curveT);
    vec2 scale = uniformScale * vec2(
        sampleParticleScaleXCurve(lifeT),
        sampleParticleScaleYCurve(lifeT)
    );
    if (applyTransition) {
        scale *= sampleScalarCurve(
            uTransitionParams.z,
            uTransitionScaleKeys,
            uTransitionScaleCurveType,
            uTransitionScaleCurve,
            uTransitionScaleCurveHandles
        );
    }
    vec2 size = iSizeRot.xy * scale;
    vec2 local = corner * size;

    vec3 offset = basisX * local.x + basisY * local.y;
    if (uTransformParticleGeometry != 0) {
        float previousScale = length(uPrevGroupMat[0].xyz);
        float currentScale = length(uGroupMat[0].xyz);
        offset *= mix(previousScale, currentScale, uPartial);
    }

    vec3 posRelCam = rel + offset;
    gl_Position = uProj * uView * vec4(posRelCam, 1.0);

    // UV: x=+1 -> u1, y=+1 -> v0 (与 ControlableParticle.addDoubleSidedQuad 完全一致)
    float ut = 0.5 + corner.x * 0.5;
    float vt = 0.5 - corner.y * 0.5;
    vec4 animationUv = resolveAnimationUv(max(int(iAnimation.x + 0.5), 0), flags, maxAge, visualAge);
    if ((flags & FLAG_RANDOM_QUARTER_UV) != 0) {
        animationUv = cropRandomQuarterUv(animationUv);
    }
    vUv = vec2(
        mix(animationUv.x, animationUv.z, ut),
        mix(animationUv.y, animationUv.w, vt)
    );
    if (uHasMask != 0) {
        vec4 maskAnimationUv = resolveAnimationUv(max(int(iAppearance.z + 0.5), 0), flags, maxAge, visualAge);
        if ((flags & FLAG_MASK_RANDOM_QUARTER_UV) != 0) {
            maskAnimationUv = cropRandomQuarterUv(maskAnimationUv);
        }
        vMaskUv = vec2(
            mix(maskAnimationUv.x, maskAnimationUv.z, ut),
            mix(maskAnimationUv.y, maskAnimationUv.w, vt)
        );
        vMaskTint = unpackRgb8(iAppearance.w);
    } else {
        vMaskUv = vec2(0.0);
        vMaskTint = vec3(1.0);
    }

    vec3 particleColor = iColor.rgb;
    if (uColorCycleTicks > 0.0) {
        float angularPhase = atan(iPosAge.z, iPosAge.x) / TAU;
        float colorT = fract(
            uSystemTime / uColorCycleTicks + angularPhase * uColorCycleSpatialScale
        );
        particleColor = 0.5 + 0.5 * cos(TAU * (colorT + vec3(0.0, 0.3333333, 0.6666667)));
    }
    particleColor *= sampleParticleColorCurve(lifeT) * sampleColorCurve(curveT);
    if (applyTransition && uTransitionParams.w > 0.5) {
        particleColor = mix(uTransitionColorFrom, uTransitionColorTo, uTransitionParams.z);
    }
    float baseAlpha = iColor.a;
    if (uAlphaTransitionKeys > 0) {
        baseAlpha = sampleScalarCurve(
            uAlphaTransitionProgress,
            uAlphaTransitionKeys,
            uAlphaTransitionCurveType,
            uAlphaTransitionCurve,
            uAlphaTransitionCurveHandles
        );
    }
    float alphaScale = sampleParticleAlphaCurve(lifeT) * sampleAlphaCurve(curveT);
    if (applyTransition) {
        alphaScale *= sampleScalarCurve(
            uTransitionParams.z,
            uTransitionAlphaKeys,
            uTransitionAlphaCurveType,
            uTransitionAlphaCurve,
            uTransitionAlphaCurveHandles
        );
    }
    vColor = vec4(particleColor, baseAlpha * alphaScale);

    int blockLight = (flags >> 3) & 15;
    int skyLight = (flags >> 7) & 15;
    vLightUv = vec2(
        (float(blockLight) * 16.0 + 8.0) / 256.0,
        (float(skyLight) * 16.0 + 8.0) / 256.0
    );
    tfPosition = posRelCam;
    tfUv = vUv;
    tfPacked = uvec2(
        packUnormColor(vColor),
        uint(blockLight * 16) | (uint(skyLight * 16) << 16u)
    );

    // 原版雾距离
    vFogDistance = uFogShape == 1
        ? max(length(posRelCam.xz), abs(posRelCam.y))
        : length(posRelCam);
}
