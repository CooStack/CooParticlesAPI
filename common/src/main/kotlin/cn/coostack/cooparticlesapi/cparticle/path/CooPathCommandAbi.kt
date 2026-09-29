package cn.coostack.cooparticlesapi.cparticle.path

import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce

/**
 * # 路径位置约束命令的 GPU/CPU 共用 ABI
 *
 * 路径命令复用既有 [cn.coostack.cooparticlesapi.cparticle.force.ForceCommand] 的 16-float payload，
 * 因此**不需要**为路径单独加宽 Command 缓冲，也不会改变其他 Force 的数值布局。
 *
 * ## payload 下标（相对 `ForceCommand` header 之后）
 *
 * 四个 `vec4` 分组与 shader 的 `p0..p3` 一一对应，字段刻意对齐到 4 的倍数，
 * 使 compute 侧可以直接按分量取值而不必再做下标换算。
 *
 * | 下标 | 分组 | 含义 |
 * | --- | --- | --- |
 * | [P_SLOT] | `p0.x` | 路径槽位号；由路径图层映射表解释 |
 * | [P_MODE_PACK] | `p0.y` | 播放模式 / 进度模式 / 结束模式 / 前方轴模式 的打包值 |
 * | [P_OFFSET_RADIUS] | `p0.z` | 环绕半径，`<= 0` 表示不启用环绕偏移 |
 * | [P_LAYER_VERSION] | `p0.w` | 打包时的图层重建版本，用于拒绝陈旧基址 |
 * | [P_PLAY_PERIOD] | `p1.x` | 一个完整播放循环的时长（tick）；`<= 0` 表示沿用粒子寿命 |
 * | [P_PHASE] | `p1.y` | 环绕初始相位（弧度） |
 * | [P_ANGULAR_VELOCITY] | `p1.z` | 环绕角速度（弧度 / tick） |
 * | [P_ANGULAR_SCALE] | `p1.w` | 每粒子稳定随机的角速度倍率 |
 * | [P_BINDING_QUAT .. +3] | `p2.xyzw` | 路径本地空间到目标空间的旋转四元数 |
 * | [P_BINDING_SCALE .. +2] | `p3.xyz` | 绑定缩放 |
 * | [P_FACE_MOTION] | `p3.w` | 显式朝向开关；0 保留姿态，1 按前方轴跟随运动 |
 *
 * 绑定变换用「四元数 + 缩放」而不是 3x4 矩阵，是为了在 16 float 预算内同时表达旋转与缩放。
 * 平移不在这里：命令与粒子位置都在 system 原点相对坐标里，路径自然随 system 移动。
 *
 * 默认保留粒子姿态。显式开启朝向跟随时，路径按前向轴接管完整姿态，不叠加已有欧拉角偏移。
 *
 * ## payload 预算
 * Force Command 的 payload 固定为 [CParticleForce.STRIDE] 个 float（16 个下标 `0..15`）。
 * [USED_FLOATS] 必须始终落在这个预算内：越界写会踩到下一条命令的 header（类型字段），
 * 使后续命令被解释成另一种力。绑定字段因此紧跟在 [P_ANGULAR_SCALE] 之后，
 * 而不是像早期布局那样从下标 10 开始——那种布局需要到下标 16，必然越界。
 */
object CooPathCommandAbi {
    /** 路径位置约束命令写入 payload 下标 0 的类型编号。 */
    const val TYPE_PATH_CONSTRAINT = 21

    /** 四元数分量数。 */
    const val QUATERNION_COMPONENTS = 4

    /** 缩放分量数。 */
    const val SCALE_COMPONENTS = 3

    /** 子模式各占的 bit 数。 */
    const val MODE_BITS = 4

    const val MODE_SHIFT_PLAY = 0
    const val MODE_SHIFT_PROGRESS = MODE_SHIFT_PLAY + MODE_BITS
    const val MODE_SHIFT_END = MODE_SHIFT_PROGRESS + MODE_BITS
    const val MODE_SHIFT_FORWARD = MODE_SHIFT_END + MODE_BITS
    /** 出生偏移模式占 bit 16..19，不改变既有四组模式的位布局。 */
    const val MODE_SHIFT_OFFSET = MODE_SHIFT_FORWARD + MODE_BITS

    const val P_SLOT = 0
    const val P_MODE_PACK = 1
    const val P_OFFSET_RADIUS = 2
    const val P_LAYER_VERSION = 3
    const val P_PLAY_PERIOD = 4
    const val P_PHASE = 5
    const val P_ANGULAR_VELOCITY = 6
    const val P_ANGULAR_SCALE = 7
    const val P_BINDING_QUAT = 8
    const val P_BINDING_SCALE = P_BINDING_QUAT + QUATERNION_COMPONENTS
    /** 显式朝向开关，写入剩余的 p3.w；默认 0，不改写粒子姿态。 */
    const val P_FACE_MOTION = P_BINDING_SCALE + SCALE_COMPONENTS

    /**
     * payload 中实际使用的 float 数。
     *
     * 必须 `<= PAYLOAD_FLOATS`；否则绑定缩放会写到下一条命令的 header 上。
     */
    const val USED_FLOATS = P_FACE_MOTION + 1

    /**
     * Force payload 可用的 float 数。
     *
     * 等于 [CParticleForce.STRIDE]：`ForceCommand` 的 header 占用独立的 4 个 float，
     * Force 自己的 payload 紧随其后并有 16 个下标的预算。绑定字段的排布必须保证
     * [USED_FLOATS] 不超过本值，否则会踩到下一条命令的 header。
     */
    const val PAYLOAD_FLOATS = CParticleForce.STRIDE

    /**
     * `ForceCommand` header 占用的 float 数。
     *
     * header 为 `type, selectorMode, selectorValue, selectorMask`，之后才是 16-float 的 Force payload；
     * GPU 与 CPU 都从这里换算 payload 起点。
     */
    const val FORCE_PAYLOAD_OFFSET = 4

    /**
     * 把四种子模式打包成一个 float 可精确保存的整数。
     *
     * @param playMode 播放模式
     * @param progressMode 进度映射模式
     * @param endMode 终点处理模式
     * @param forwardAxisMode 前方轴模式；取值见 [CParticlePathForwardAxis]
     * @param offsetMode 出生偏移模式，默认不启用
     * @return 打包后的 32-bit 整数
     */
    @JvmStatic
    fun packModes(
        playMode: CParticlePathPlayMode,
        progressMode: CParticlePathProgressMode,
        endMode: CParticlePathEndMode,
        forwardAxisMode: Int,
        offsetMode: CParticlePathOffsetMode = CParticlePathOffsetMode.NONE,
    ): Int =
        (playMode.wireValue shl MODE_SHIFT_PLAY) or
            (progressMode.wireValue shl MODE_SHIFT_PROGRESS) or
            (endMode.wireValue shl MODE_SHIFT_END) or
            (forwardAxisMode shl MODE_SHIFT_FORWARD) or
            (offsetMode.wireValue shl MODE_SHIFT_OFFSET)

    /** 解码出生偏移模式；未知值保留默认严格路径行为。 */
    fun offsetModeOf(packed: Int): CParticlePathOffsetMode =
        CParticlePathOffsetMode.entries.firstOrNull {
            it.wireValue == ((packed ushr MODE_SHIFT_OFFSET) and MODE_MASK)
        } ?: CParticlePathOffsetMode.NONE

    /** 取出播放模式。 */
    @JvmStatic
    fun playModeOf(packed: Int): Int = (packed ushr MODE_SHIFT_PLAY) and MODE_MASK

    /** 取出进度映射模式。 */
    @JvmStatic
    fun progressModeOf(packed: Int): Int = (packed ushr MODE_SHIFT_PROGRESS) and MODE_MASK

    /** 取出终点处理模式。 */
    @JvmStatic
    fun endModeOf(packed: Int): Int = (packed ushr MODE_SHIFT_END) and MODE_MASK

    /** 取出前方轴模式。 */
    @JvmStatic
    fun forwardAxisModeOf(packed: Int): Int = (packed ushr MODE_SHIFT_FORWARD) and MODE_MASK

    private const val MODE_MASK = (1 shl MODE_BITS) - 1

    /**
     * ABI 中播放模式 / 进度模式 / 结束模式 / 段类型的数值标识。
     *
     * 这些值与枚举的 `wireValue` 一一对应。缓存副本的目的只是让 GPU 与 CPU 两侧都能按**名字**
     * 引用协议常量，而不是在求值代码里重复写裸数字；改变任一标识必须同步枚举与 shader 常量。
     */
    const val PLAY_ONCE = 0
    const val PLAY_LOOP = 1
    const val PLAY_PING_PONG = 2

    const val PROGRESS_PARAMETER = 0
    const val PROGRESS_ARC_LENGTH = 1

    const val END_HOLD = 0
    const val END_DISAPPEAR = 1

    const val SEGMENT_LINEAR = 0
    const val SEGMENT_BEZIER = 1
}
