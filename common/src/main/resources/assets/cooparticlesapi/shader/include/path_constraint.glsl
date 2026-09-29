// ================= 路径位置约束 - compute 求值 =================
// 与 CParticlePathEvaluator / CParticlePathCommandPacker 使用同一套布局与数学。
// 图层由 CooPathLayer 定义：表头 -> 槽位映射表 -> 路径负载（表头 -> 控制点表 -> 预采样表）。
// 命令 payload 由 CooPathCommandAbi 定义；全部路径共用本 include 声明的一个绑定点。
//
// 本文件由 CooShaderSourceLoader 通过 #coo_import 内联，因此不能声明 #version，
// 也不能重复声明主 shader 已声明的 uniform 名。

layout(std430, binding = 4) readonly buffer PathLayerBuffer {
    float pathLayer[];
};

layout(std430, binding = 5) buffer PathEndChannelBuffer {
    int pathEndState[];
};

uniform int uPathLayerEnabled;
uniform int uPathEndCapacity;
uniform float uDeltaTicks;

const int TYPE_PATH_CONSTRAINT = 21;

const int PATH_LAYER_ABI = 1;
const int PATH_LAYER_MAGIC = 0x43504154;
const int PATH_SLOT_TABLE = 8;

const int PATH_HEADER_FLOATS = 24;
const int PATH_POINT_BASE = PATH_HEADER_FLOATS;
const int PATH_POINT_STRIDE = 12;
// 预采样布局：位置 3 + 切线 3 + 累计弧长 1 + 法线 3 + 副法线 3。
// 必须与 CooPathLayer.CooPathPayload 完全一致；累计弧长占用独立的一位，不与位置混写。
const int PATH_SAMPLE_STRIDE = 13;
const int PATH_SAMPLE_POSITION = 0;
const int PATH_SAMPLE_TANGENT = 3;
const int PATH_SAMPLE_DISTANCE = 6;
const int PATH_SAMPLE_NORMAL = 7;
const int PATH_SAMPLE_BINORMAL = 10;

const int PATH_H_MAGIC = 0;
const int PATH_H_ABI = 1;
const int PATH_H_SEGMENT_COUNT = 2;
const int PATH_H_POINT_COUNT = 3;
const int PATH_H_SAMPLE_COUNT = 4;
const int PATH_H_CLOSED = 5;
const int PATH_H_SEGMENT_TYPE = 6;
const int PATH_H_TOTAL_LENGTH = 8;
const int PATH_H_LAYER_REVISION = 10;
const int PATH_H_PARAM_DENOMINATOR = 11;

// 命令 payload 的字段下标（见 CooPathCommandAbi）不需要在这里声明：调用方按 p0..p3 分量
// 直接取值并作为参数传入 pathEvaluate，避免把“命令参数”与“路径几何”两个地址空间混在一起。
// payload 分组：p0 = 槽位/模式/半径/版本，p1 = 周期/相位/角速度/角速度倍率，
// p2 = 绑定四元数，p3 = 绑定缩放。

const int PATH_MODE_MASK = 15;
const int PATH_SHIFT_PROGRESS = 4;
const int PATH_SHIFT_END = 8;
const int PATH_SHIFT_FORWARD = 12;
const int PATH_SHIFT_OFFSET = 16;

const int PATH_PLAY_ONCE = 0;
const int PATH_PLAY_LOOP = 1;
const int PATH_PLAY_PING_PONG = 2;
const int PATH_END_DISAPPEAR = 1;

/** 路径提前结束标记，与 CParticleInstanceFlags.PATH_ENDED 一致。 */
const int PATH_FLAG_ENDED = 1 << 17;

int pathIntAt(float value) {
    return value >= 0.0 ? int(value + 0.5) : int(value - 0.5);
}

int pathHeader(int base, int index) {
    return pathIntAt(pathLayer[base + index]);
}

vec3 pathVec3At(int index) {
    return vec3(pathLayer[index], pathLayer[index + 1], pathLayer[index + 2]);
}

/** 读取路径负载表头缓存的图层版本；与命令携带的版本不一致说明基址已失效。 */
int pathPayloadRevision(int payloadBase) {
    return pathIntAt(pathLayer[payloadBase + PATH_H_LAYER_REVISION]);
}

/** 全局曲线参数处的位置；线性与三次贝塞尔共用同一入口。 */
vec3 pathPositionAt(int pointBase, int segmentCount, int curveType, float u) {
    float scaled = clamp(u, 0.0, 1.0) * float(segmentCount);
    int segment = min(int(scaled), segmentCount - 1);
    float local = clamp(scaled - float(segment), 0.0, 1.0);
    int firstIndex = pointBase + segment * PATH_POINT_STRIDE;
    int secondIndex = firstIndex + PATH_POINT_STRIDE;
    vec3 first = pathVec3At(firstIndex);
    vec3 second = pathVec3At(secondIndex);
    if (curveType == 0) {
        return first + (second - first) * local;
    }
    // 控制点布局为位置、入手柄、出手柄；段使用起点出手柄和终点入手柄。
    vec3 firstControl = first + pathVec3At(firstIndex + 6);
    vec3 secondControl = second + pathVec3At(secondIndex + 3);
    float inverse = 1.0 - local;
    float inverseSquared = inverse * inverse;
    float localSquared = local * local;
    return first * (inverseSquared * inverse)
        + firstControl * (3.0 * inverseSquared * local)
        + secondControl * (3.0 * inverse * localSquared)
        + second * (localSquared * local);
}

/**
 * 按累计弧长二分定位全局曲线参数；闭合路径的采样表按循环下标访问。
 *
 * 参数分母由图层表头提供：开放表的最后一个样本落在参数 1，闭合表还有一段回到首样本，
 * 两侧必须使用同一个分母，否则弧长映射在两端会偏差。
 */
float pathParameterAtDistance(int sampleBase, int sampleCount, bool closed, int parameterDenominator, float distance) {
    // 累计弧长表的有效下标恒为 `0..sampleCount - 1`：闭合表的收尾段只体现在总长里，
    // 因此二分上界不能取 sampleCount，否则会读到本路径之外的相邻数据。
    int lastIndex = sampleCount - 1;
    float low = 0.0;
    float high = float(lastIndex);
    for (int iteration = 0; iteration < 12; iteration++) {
        float middle = floor((low + high) * 0.5);
        float middleDistance =
            pathLayer[sampleBase + int(middle) * PATH_SAMPLE_STRIDE + PATH_SAMPLE_DISTANCE];
        if (middleDistance <= distance) low = middle; else high = middle;
        if (high - low <= 1.0) break;
    }
    int lowIndex = int(low);
    int highIndex = min(lowIndex + 1, lastIndex);
    float spanStart = pathLayer[sampleBase + lowIndex * PATH_SAMPLE_STRIDE + PATH_SAMPLE_DISTANCE];
    float spanEnd = pathLayer[sampleBase + highIndex * PATH_SAMPLE_STRIDE + PATH_SAMPLE_DISTANCE];
    float ratio = spanEnd - spanStart <= 1.0e-12 ? 0.0 : (distance - spanStart) / (spanEnd - spanStart);
    float denominator = max(float(parameterDenominator), 1.0);
    return clamp((low + clamp(ratio, 0.0, 1.0)) / denominator, 0.0, 1.0);
}

/** 采样位置与连续横截面参考基；位置线性插值，参考基取最近样本以保持正交。 */
void pathFrameAt(int sampleBase, int sampleCount, bool closed, float u, out vec3 position, out vec3 normal, out vec3 binormal) {
    bool cyclic = closed && sampleCount > 1;
    int maxIndex = sampleCount - 1;
    // 闭合表的参数分母是样本数，因此 `u = 1` 落在“最后一个样本回到首样本”的收尾段上；
    // 下标必须夹到 maxIndex，再由 nextIndex 取模回绕，不能把 sampleCount 当成合法下标。
    float scaled = clamp(u, 0.0, 1.0) * float(cyclic ? sampleCount : maxIndex);
    int index = clamp(int(scaled), 0, maxIndex);
    int nextIndex = cyclic ? (index + 1) % sampleCount : min(index + 1, maxIndex);
    float ratio = scaled - float(index);
    vec3 firstPosition = pathVec3At(sampleBase + index * PATH_SAMPLE_STRIDE + PATH_SAMPLE_POSITION);
    vec3 secondPosition = pathVec3At(sampleBase + nextIndex * PATH_SAMPLE_STRIDE + PATH_SAMPLE_POSITION);
    position = firstPosition + (secondPosition - firstPosition) * ratio;
    int basisIndex = ratio > 0.5 ? nextIndex : index;
    normal = pathVec3At(sampleBase + basisIndex * PATH_SAMPLE_STRIDE + PATH_SAMPLE_NORMAL);
    binormal = pathVec3At(sampleBase + basisIndex * PATH_SAMPLE_STRIDE + PATH_SAMPLE_BINORMAL);
}

/** 包装模式归一化，与 CParticlePathEvaluator.progress 完全一致。 */
float pathWrapProgress(float raw, int playMode) {
    if (playMode == PATH_PLAY_ONCE) return clamp(raw, 0.0, 1.0);
    if (playMode == PATH_PLAY_LOOP) return raw - floor(raw);
    float normalized = raw - floor(raw);
    return normalized <= 0.5 ? normalized * 2.0 : 2.0 - normalized * 2.0;
}

/**
 * 按绑定旋转与缩放把路径本地位置换算到目标空间。
 *
 * 四元数必须**归一化**后再用于旋转：Rodrigues 形式的三维旋转公式只在单位四元数下保持长度，
 * 直接代入未归一化的四元数会把位置整体缩放 `|q|²` 倍，粒子因此飞出路径。
 * 零四元数（命令未写绑定）回退为单位四元数。
 */
vec3 pathApplyBinding(vec3 local, vec4 quaternion, vec3 scale) {
    vec3 scaled = local * scale;
    float lengthSquared = dot(quaternion, quaternion);
    vec4 normalized = lengthSquared <= 1.0e-12 ? vec4(0.0, 0.0, 0.0, 1.0) : quaternion * inversesqrt(lengthSquared);
    vec3 axis = normalized.xyz;
    vec3 crossed = 2.0 * cross(axis, scaled);
    return scaled + normalized.w * crossed + cross(axis, crossed);
}

vec3 pathSafeNormalize(vec3 value) {
    float lengthSquared = dot(value, value);
    return lengthSquared <= 1.0e-18 ? vec3(0.0) : value * inversesqrt(lengthSquared);
}

// 显式跟随：将声明的本地前向轴旋转至运动方向，再解出渲染器使用的 XYZ 欧拉角。
vec3 pathOrientationAngles(vec3 direction, int mode) {
    vec3 source = mode == 1 ? vec3(0.0, 0.0, 1.0)
        : mode == 2 ? vec3(0.0, 1.0, 0.0)
        : mode == 3 ? vec3(-1.0, 0.0, 0.0)
        : mode == 4 ? vec3(0.0, 0.0, -1.0)
        : mode == 5 ? vec3(0.0, -1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 target = pathSafeNormalize(direction);
    vec4 q = vec4(cross(source, target), 1.0 + dot(source, target));
    if (dot(source, target) < -1.0 + 1.0e-6) {
        // 与 JOML rotationTo 的反平行阈值和翻转轴一致，避免 CPU/GPU 纹理相差半圈。
        vec3 axis = vec3(source.y, -source.x, 0.0);
        if (dot(axis, axis) == 0.0) axis = vec3(0.0, source.z, -source.y);
        q = vec4(axis, 0.0);
    }
    q = normalize(q);
    vec3 x = pathApplyBinding(vec3(1.0, 0.0, 0.0), q, vec3(1.0));
    vec3 y = pathApplyBinding(vec3(0.0, 1.0, 0.0), q, vec3(1.0));
    vec3 z = pathApplyBinding(vec3(0.0, 0.0, 1.0), q, vec3(1.0));
    if (abs(z.x) >= 0.999999) {
        return vec3(atan(y.z, y.y), asin(clamp(z.x, -1.0, 1.0)), 0.0);
    }
    return vec3(atan(-z.y, z.z), asin(clamp(z.x, -1.0, 1.0)), atan(-y.x, x.x));
}

/**
 * 把“模型前方轴对齐运动方向”的修正作用到方向向量上。
 *
 * 框架既有的朝向换算约定模型前方为本地 +X（见 CParticleGpuMath.directionAngles）。
 * 模型实际前方是别的轴时，先把方向旋转成“该轴对齐运动方向”对应的 +X 方向，
 * 模型才不会侧着前进。模式编号与 CParticlePathForwardAxis 一致。
 */
vec3 pathCorrectForwardAxis(vec3 direction, int forwardAxisMode) {
    if (forwardAxisMode == 0) return direction;
    vec3 modelForward;
    if (forwardAxisMode == 1) modelForward = vec3(0.0, 0.0, 1.0);
    else if (forwardAxisMode == 2) modelForward = vec3(0.0, 1.0, 0.0);
    else if (forwardAxisMode == 3) modelForward = vec3(-1.0, 0.0, 0.0);
    else if (forwardAxisMode == 4) modelForward = vec3(0.0, 0.0, -1.0);
    else if (forwardAxisMode == 5) modelForward = vec3(0.0, -1.0, 0.0);
    else return direction;
    float lengthSquared = dot(direction, direction);
    if (lengthSquared <= 1.0e-18) return direction;
    // q = normalize(cross(modelForward, direction), 1 + dot) 把模型前方轴转到运动方向；
    // 取它的逆作用到方向上，等价于“方向绕该轴反旋一次”，核心是一个 Rodrigues 旋转。
    vec3 axis = cross(modelForward, direction);
    float axisLengthSquared = dot(axis, axis);
    if (axisLengthSquared <= 1.0e-18) return direction;
    vec3 unitAxis = pathSafeNormalize(axis);
    float cosine = clamp(dot(modelForward, direction), -1.0, 1.0);
    float sine = sqrt(max(1.0 - cosine * cosine, 0.0));
    vec3 rotated;
    // 绕过原点、方向为 -unitAxis、夹角为 θ 的旋转（等价于 q 的逆）。
    vec3 term = cross(unitAxis, direction);
    rotated = direction * cosine - term * sine + unitAxis * dot(unitAxis, direction) * (1.0 - cosine);
    return rotated;
}

/** 在给定归一化进度与目标年龄上求本地空间位置（含环绕偏移）。 */
vec3 pathLocalAt(
    int payloadBase,
    float targetAge,
    float tau,
    int progressMode,
    float radius,
    float phase,
    float angularVelocity,
    float angularScale,
    int offsetMode,
    vec3 birthOffset,
    float birthAge,
    out vec3 normal,
    out vec3 binormal
) {
    int pointCount = pathHeader(payloadBase, PATH_H_POINT_COUNT);
    int sampleCount = pathHeader(payloadBase, PATH_H_SAMPLE_COUNT);
    bool closed = pathHeader(payloadBase, PATH_H_CLOSED) != 0;
    int pointBase = payloadBase + PATH_POINT_BASE;
    int sampleBase = pointBase + pointCount * PATH_POINT_STRIDE;
    float u = tau;
    if (progressMode != 0) {
        float totalLength = pathLayer[payloadBase + PATH_H_TOTAL_LENGTH];
        if (totalLength > 1.0e-9) {
            int parameterDenominator = pathHeader(payloadBase, PATH_H_PARAM_DENOMINATOR);
            u = pathParameterAtDistance(
                sampleBase,
                sampleCount,
                closed,
                parameterDenominator,
                clamp(tau, 0.0, 1.0) * totalLength
            );
        }
    }
    vec3 localPosition;
    pathFrameAt(sampleBase, sampleCount, closed, u, localPosition, normal, binormal);
    localPosition = pathPositionAt(pointBase, pathHeader(payloadBase, PATH_H_SEGMENT_COUNT),
        pathHeader(payloadBase, PATH_H_SEGMENT_TYPE), u);
    float angle = phase + angularVelocity * angularScale * targetAge;
    localPosition += normal * (cos(angle) * max(radius, 0.0)) + binormal * (sin(angle) * max(radius, 0.0));
    if (offsetMode == 1) return localPosition + birthOffset;
    if (offsetMode == 2) {
        float spin = angularVelocity * angularScale * (targetAge - birthAge);
        vec2 transverse = vec2(
            birthOffset.x * cos(spin) - birthOffset.y * sin(spin),
            birthOffset.x * sin(spin) + birthOffset.y * cos(spin));
        localPosition += normal * transverse.x + binormal * transverse.y
            + pathSafeNormalize(cross(normal, binormal)) * birthOffset.z;
    }
    return localPosition;
}

/**
 * 求路径约束的目标位置、运动方向与播放状态。
 *
 * 位置取“下一 tick 的进度”，方向取该处的前向差分，因此方向与这一 tick 真正发生的位移一致，
 * 同时包含沿程推进、环绕旋转与绑定变换带来的移动。循环回绕发生在差分区间内时改用单侧差分，
 * 避免把终点到起点的跳变当成运动方向。
 *
 * 全部播放参数都由调用方从**命令 payload**传入，而不是从路径图层读取：命令参数与路径几何
 * 位于两个不同的 SSBO（命令缓冲与路径图层），拿路径负载基址去索引图层只能读到几何表头，
 * 会把采样数当作周期、把段数当作半径，粒径与位置随之整体失真。
 *
 * @param payloadBase 路径负载在**路径图层**中的基址；只用于读取几何
 * @param age 粒子当前年龄
 * @param maxAge 粒子既有寿命，未显式给出周期时作为播放周期
 * @param playMode 播放模式
 * @param progressMode 进度映射模式
 * @param forwardAxisMode 模型前方轴模式
 * @param playPeriodTicks 一个完整播放循环的时长（tick）；`<= 0` 表示沿用粒子寿命
 * @param radius 环绕半径，`<= 0` 表示不启用环绕偏移
 * @param phase 环绕初始相位（弧度）
 * @param angularVelocity 环绕角速度（弧度 / tick）
 * @param angularScale 每粒子角速度倍率
 * @param quaternion 路径本地空间到目标空间的旋转四元数
 * @param scale 路径本地空间到目标空间的缩放
 * @param localPosition 输出：路径本地空间位置（含环绕偏移，不含绑定）
 * @param direction 输出：单位运动方向；退化时为零向量
 * @param bindingPosition 输出：应用绑定后的目标空间位置
 * @param reachedEnd 输出：单程模式是否已到达终点
 * @param wrapped 输出：本次推进是否跨越播放周期边界
 * @return 求值成功返回 `true`；几何不可用或周期无效时返回 `false`
 */
bool pathEvaluate(
    int payloadBase,
    float age,
    float maxAge,
    int playMode,
    int progressMode,
    int forwardAxisMode,
    float playPeriodTicks,
    float radius,
    float phase,
    float angularVelocity,
    float angularScale,
    vec4 quaternion,
    vec3 scale,
    int offsetMode,
    vec4 birth,
    out vec3 localPosition,
    out vec3 direction,
    out vec3 bindingPosition,
    out bool reachedEnd,
    out bool wrapped
) {
    float deltaTicks = uDeltaTicks > 0.0 ? uDeltaTicks : 1.0;
    int segmentCount = pathHeader(payloadBase, PATH_H_SEGMENT_COUNT);
    int pointCount = pathHeader(payloadBase, PATH_H_POINT_COUNT);
    int sampleCount = pathHeader(payloadBase, PATH_H_SAMPLE_COUNT);
    localPosition = vec3(0.0);
    direction = vec3(0.0);
    bindingPosition = vec3(0.0);
    reachedEnd = false;
    wrapped = false;
    if (segmentCount <= 0 || pointCount < 2 || sampleCount <= 0) return false;
    // playPeriodTicks 是一个完整循环的时长：单程走完一次、循环绕回一次、PingPong 完成一个往返。
    // `<= 0` 是“沿用粒子寿命”的哨兵，按同一相位换算，与 CParticlePathCpuEvaluator 一致。
    float period = playPeriodTicks > 0.0
        ? playPeriodTicks
        : (maxAge / max(deltaTicks, 1.0e-6));
    if (period <= 0.0) return false;

    float cycle = period;
    float targetAge = age + deltaTicks;
    // 三个进度都必须从**原始比例**映射一次：把已经映射过的进度再送进 pathWrapProgress
    // 会对 PingPong 做第二次折返映射，得到完全错误的相位。
    float targetTau = pathWrapProgress(targetAge / cycle, playMode);
    // 只有循环模式的参数会真正从 1 跳回 0；PingPong 的折返是连续推进，不属于跳变。
    wrapped = playMode == PATH_PLAY_LOOP && pathWrapProgress(age / cycle, playMode) > targetTau;

    vec3 normal;
    vec3 binormal;
    vec3 birthOffset = vec3(0.0);
    if (offsetMode != 0) {
        if (any(lessThanEqual(abs(scale), vec3(1.0e-9))) ||
            any(isnan(scale)) || any(isinf(scale)) ||
            any(isnan(birth)) || any(isinf(birth))) return false;
        vec3 initial = pathLocalAt(payloadBase, birth.w, pathWrapProgress(birth.w / cycle, playMode),
            progressMode, radius, phase, angularVelocity, angularScale,
            0, vec3(0.0), birth.w, normal, binormal);
        birthOffset = pathApplyBinding(birth.xyz, vec4(-quaternion.xyz, quaternion.w), vec3(1.0)) / scale - initial;
        if (offsetMode == 2) {
            birthOffset = vec3(dot(birthOffset, normal), dot(birthOffset, binormal),
                dot(birthOffset, pathSafeNormalize(cross(normal, binormal))));
        }
    }
    vec3 targetLocal = pathLocalAt(
        payloadBase, targetAge, targetTau, progressMode, radius, phase, angularVelocity, angularScale,
        offsetMode, birthOffset, birth.w, normal, binormal
    );
    // 差分区间取两个邻点映射值的较小/较大者，目标必然落在区间内。
    float rawLow = pathWrapProgress((targetAge - deltaTicks) / cycle, playMode);
    float rawHigh = pathWrapProgress((targetAge + deltaTicks) / cycle, playMode);
    float lowTau = min(rawLow, rawHigh);
    float highTau = max(rawLow, rawHigh);
    vec3 lowLocal = pathLocalAt(
        payloadBase, targetAge - deltaTicks, rawLow, progressMode, radius, phase, angularVelocity, angularScale,
        offsetMode, birthOffset, birth.w, normal, binormal
    );
    vec3 highLocal = pathLocalAt(
        payloadBase, targetAge + deltaTicks, rawHigh, progressMode, radius, phase, angularVelocity, angularScale,
        offsetMode, birthOffset, birth.w, normal, binormal
    );

    // 位移方向由“时间顺序”决定，而不是区间端点顺序：PingPong 回程时后一步落在更小的进度上，
    // 若始终做 high - low 会得到与真实运动相反的符号。
    vec3 delta = highLocal - lowLocal;
    // 方向只在差分区间退化时清零：LOOP 跨越周期边界时两端的取值区间被夹成退化区间，
    // 位置差分是虚假的，必须报告零方向由渲染端沿用上一帧。
    vec3 rawDirection = highTau - lowTau <= 1.0e-9 ? vec3(0.0) : pathSafeNormalize(delta);
    // 方向保持真实运动方向；轴约定只影响显式跟随时的姿态，不影响位置。
    direction = pathSafeNormalize(pathApplyBinding(rawDirection, quaternion, scale));
    localPosition = targetLocal;
    bindingPosition = pathApplyBinding(targetLocal, quaternion, scale);
    reachedEnd = playMode == PATH_PLAY_ONCE && targetTau >= 1.0;
    return true;
}
