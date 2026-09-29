package cn.coostack.cooparticlesapi.cparticle.path

import cn.coostack.cooparticlesapi.gpudata.CooGpuDataLayout

/**
 * # 路径图层布局
 *
 * **所有路径共用一张图层与一个着色器存储绑定点**。这是刻意的设计：
 * OpenGL 4.3 只保证 8 个着色器存储绑定点，CParticle 的 compute 模拟已经占用 0..3，
 * 如果每条路径独占一个绑定点，几条路径就会耗尽全部预算。
 *
 * ## 缓冲布局（float 下标）
 * ```
 * [HEADER .. SLOT_TABLE)                 图层表头
 * [SLOT_TABLE .. SLOT_TABLE + SLOT_COUNT)  槽位 → 路径基址 映射表
 * [base .. base + 路径占用)                每条路径的负载，见 [CooPathPayload]
 * ```
 *
 * 映射表用**下标即槽位号**的紧凑数组表达，不按任意路径 ID 分配稠密大数组；槽位数量固定为
 * [SLOT_COUNT]，超出时必须复用或回收路径，而不是无限增长。
 *
 * ## 为什么不需要每条路径传递基址
 * 路径可能因为增点而重新分配负载区间。基址放在映射表里，命令只需要携带槽位号，
 * 因此重新分配不会让已打包的命令失效，也不会触发 system 命令缓存失效。
 */
object CooPathLayer {
    /**
     * 路径图层使用的公共数据布局。
     *
     * `componentCount = 1`：整张图层按连续 float 解释，路径负载内部自行分组。
     */
    @JvmField
    val layout: CooGpuDataLayout = CooGpuDataLayout(
        id = "cooparticlesapi:path",
        componentCount = 1,
        componentNames = listOf("raw"),
    )

    /** 图层魔数；`0x43504154` 即 ASCII 的 "CPAT"。 */
    const val MAGIC = 0x43504154

    /** 图层 ABI 版本；字段含义、顺序或步长变化时必须递增。 */
    const val ABI_VERSION = 1

    /** 图层表头占用的 float 数。 */
    const val LAYER_HEADER_FLOATS = 8

    /**
     * 槽位表容量。
     *
     * 256 个槽位占 1 KiB，足以覆盖多个 emitter × 多个 sign 的共享路径，同时保持映射是紧凑的。
     */
    const val SLOT_COUNT = 256

    /** 映射表起始下标。 */
    const val SLOT_TABLE = LAYER_HEADER_FLOATS

    /** 路径负载起始下标。 */
    const val PAYLOAD_BASE = SLOT_TABLE + SLOT_COUNT

    const val LH_MAGIC = 0
    const val LH_ABI = 1
    const val LH_SLOT_COUNT = 2

    /**
     * 图层**重建版本**。
     *
     * 任何路径负载重新分配都会递增本值。命令打包时把本值写入参数，运行时会校验图层版本，
     * 因此引用了旧基址的陈旧命令会明确失败，而不是静默读到别的路径。
     */
    const val LH_REBUILD_VERSION = 3

    /** 当前已写入的路径数量。 */
    const val LH_PATH_COUNT = 4

    /** 图层总 float 长度，供诊断与容量校验使用。 */
    const val LH_TOTAL_FLOATS = 5

    /** 未被占用的槽位在映射表中的哨兵值。 */
    const val SLOT_FREE = -1

    /** 一个槽位允许占用的最大 float 数，防止单条路径误配置吃满图层。 */
    const val MAX_FLOATS_PER_PATH = 8192

    /**
     * 计算容纳 [slotCount] 条路径时的图层总 float 长度。
     *
     * @param slotCount 槽位数量
     * @return 图层 float 长度
     */
    fun layerFloats(slotCount: Int): Int = PAYLOAD_BASE + slotCount * MAX_FLOATS_PER_PATH

    /**
     * 路径负载内部布局。
     *
     * 结构与本对象同理：表头 → 控制点表 → 预采样表。基址由图层映射表提供，
     * 因此负载内部的全部下标都是**相对基址**的偏移。
     */
    object CooPathPayload {
        /** 负载表头 float 数。 */
        const val HEADER_FLOATS = 24

        /** 控制点表起始偏移。 */
        const val POINT_BASE = HEADER_FLOATS

        /** 每个控制点：位置 3 + 入控制柄 3 + 出控制柄 3。 */
        const val POINT_STRIDE = 12

        const val POINT_POSITION = 0
        const val POINT_IN_HANDLE = 3
        const val POINT_OUT_HANDLE = 6

        /**
         * 每个预采样的 float 数：位置 3 + 切线 3 + 累计弧长 1 + 法线 3 + 副法线 3。
         *
         * 累计弧长单独占一个 float，**不能**复用位置或切线的任何分量：弧长若写在位置分量上，
         * GPU 与 CPU 读到的“位置”就会变成弧长值，弧长反查也会因为所有距离相同而失效。
         */
        const val SAMPLE_STRIDE = 13

        const val SAMPLE_POSITION = 0
        const val SAMPLE_TANGENT = 3

        /** 从路径起点到本样本的累计弦长。 */
        const val SAMPLE_DISTANCE = 6

        const val SAMPLE_NORMAL = 7
        const val SAMPLE_BINORMAL = 10

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
         * 弧长近似的相对误差量级描述。
         *
         * 预采样按均匀参数采样、弦长累加得到弧长；本值记录“单个样本间距占总长的最大比例”，
         * 用于说明当前采样密度下的近似量级，不是严格误差上界。
         */
        const val H_LENGTH_TOLERANCE = 9

        /**
         * 本负载所属的图层重建版本。
         *
         * 图层重新分配基址后本值随负载一起更新；命令携带打包时的版本，运行时不匹配就整条跳过，
         * 因此陈旧命令不会读到别的路径。取值受 float 精确整数范围约束。
         */
        const val H_LAYER_REVISION = 10

        /**
         * 样本下标换算全局曲线参数时的分母。
         *
         * 开放表最后一个样本落在参数 1，分母是样本数减一；闭合表分母是样本数。
         * 由 CPU 侧写入、GPU 侧直接读取，避免两侧各自推断而出现弧长映射偏差。
         */
        const val H_PARAM_DENOMINATOR = 11

        /**
         * 计算样本表在负载内的起始偏移。
         *
         * @param pointCount 控制点数
         * @return 样本表相对基址的偏移
         */
        fun sampleBase(pointCount: Int): Int = POINT_BASE + pointCount * POINT_STRIDE

        /**
         * 计算一条路径在负载内需要的 float 数。
         *
         * @param pointCount 控制点数
         * @param sampleCount 预采样数
         * @return 需要的 float 数
         */
        fun floats(pointCount: Int, sampleCount: Int): Int =
            sampleBase(pointCount) + sampleCount * SAMPLE_STRIDE
    }
}
