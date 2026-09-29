package cn.coostack.cooparticlesapi.cparticle.path

/**
 * # 路径图层的 GPU/CPU 共享协议
 *
 * 这里集中声明路径图层的数值布局与解释规则。GPU compute 与 CPU 模拟器读取同一份图层数据，
 * 并按同一套规则求值，因此两侧的**全部**布局常量都必须来自本对象，不能在任意一侧就地写死数字。
 *
 * ## 缓冲布局（`std430`，float 下标）
 * ```
 * [0 .. HEADER_FLOATS - 1]              表头
 * [POINT_BASE .. ]                      控制点表，每点 POINT_STRIDE 个 float
 * [SAMPLE_BASE .. ]                     预采样表，每样本 SAMPLE_STRIDE 个 float
 * ```
 *
 * 控制点表保存用户编辑的几何数据；预采样表保存按 [sampleCount] 均匀参数采样的位置、切线，
 * 以及沿曲线连续的横截面参考基。**预采样精度由 [sampleCount] 独立决定**，与控制点数无关：
 * 控制点数只决定控制数据的有效长度。读取预采样表时使用点采样，不做线性过滤冒充贝塞尔求值。
 *
 * ## 表头字段
 * | 下标 | 含义 |
 * | --- | --- |
 * | [H_MAGIC] | 固定魔数，用于校验这是路径图层而不是其他布局的图层 |
 * | [H_ABI] | 布局版本；改变字段含义或顺序时必须递增 |
 * | [H_SEGMENT_COUNT] | 段数（控制点数减一） |
 * | [H_POINT_COUNT] | 控制点数（含闭合接缝的重复点） |
 * | [H_SAMPLE_COUNT] | 预采样表样本数 |
 * | [H_CLOSED] | 几何是否首尾相连：0/1 |
 * | [H_SEGMENT_TYPE] | 段类型，取值见 [CParticlePathSegmentType.wireValue] |
 * | [H_SAMPLES_PER_SEGMENT] | 每段的均匀采样数，供 shader 从全局参数直接定位段 |
 * | [H_TOTAL_LENGTH] | 预采样弧长近似得到的曲线总长 |
 * | [H_LENGTH_TOLERANCE] | 采样精度描述：弧长近似相对误差上界（仅用于文档与诊断） |
 */
object CooPathLayerProtocol {
    /** 路径图层魔数；`0x43504154` 即 ASCII 的 "CPAT"。 */
    const val MAGIC = 0x43504154

    /**
     * 路径图层布局版本。
     *
     * 任何字段含义、顺序或步长变化都必须递增本值；使用端应在解析时拒绝不匹配的版本，
     * 而不是按旧规则解释新数据。
     */
    const val ABI_VERSION = 1

    /** 表头三个 float 一组的对齐基准，保证 `std430` 下的 vec3 读取不受 16 字节对齐影响。 */
    const val HEADER_FLOATS = 24

    /** 控制点表起始下标。 */
    const val POINT_BASE = HEADER_FLOATS

    /** 每个控制点的 float 数：位置 3 + 入控制柄 3 + 出控制柄 3。 */
    const val POINT_STRIDE = 12

    const val POINT_POSITION = 0
    const val POINT_IN_HANDLE = 3
    const val POINT_OUT_HANDLE = 6

    /** 控制点表之后的相对对齐填充，保持样本表按 4 float 对齐。 */
    const val POINT_TRAILING_PAD = 0

    /** 预采样表每个样本的 float 数：位置 3 + 切线 3 + 法线 3 + 副法线 3。 */
    const val SAMPLE_STRIDE = 12

    const val SAMPLE_POSITION = 0
    const val SAMPLE_TANGENT = 3
    const val SAMPLE_NORMAL = 6
    const val SAMPLE_BINORMAL = 9

    const val H_MAGIC = 0
    const val H_ABI = 1
    const val H_SEGMENT_COUNT = 2
    const val H_POINT_COUNT = 3
    const val H_SAMPLE_COUNT = 4
    const val H_CLOSED = 5
    const val H_SEGMENT_TYPE = 6
    const val H_SAMPLES_PER_SEGMENT = 7
    const val H_TOTAL_LENGTH = 8

    /**
     * 弧长近似的相对误差上界描述。
     *
     * 预采样表按均匀参数采样，弧长由相邻样本的弦长累加得到。对三次贝塞尔段，固定采样密度下
     * 弦长和的相对误差随曲率增大而增大；[H_LENGTH_TOLERANCE] 只用于记录“本路径按当前采样
     * 密度预期的相对误差量级”，使用端不应把它当成严格保证。
     */
    const val H_LENGTH_TOLERANCE = 9

    /**
     * 计算样本表在图层的起始 float 下标。
     *
     * @param pointCount 控制点数
     * @return 样本表起始下标
     */
    fun sampleBase(pointCount: Int): Int = POINT_BASE + pointCount * POINT_STRIDE + POINT_TRAILING_PAD

    /**
     * 计算图层容纳给定点数与样本数所需的 float 总量。
     *
     * @param pointCount 控制点数
     * @param sampleCount 样本数
     * @return 需要的 float 数量
     */
    fun floatCount(pointCount: Int, sampleCount: Int): Int =
        sampleBase(pointCount) + sampleCount * SAMPLE_STRIDE
}
