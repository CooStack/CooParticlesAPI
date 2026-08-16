package cn.coostack.cooparticlesapi.coofx.playback.track

/**
 * 定义 glTF 动画采样器的插值语义。该枚举值与 glTF 的 sampler.interpolation 字符串一一对应，
 * 不承载缓动曲线，也不能用现有 Animate 或 Bezier 实现替代。
 *
 * @property serializedName glTF 中使用的稳定序列化名称
 */
enum class CooFxTrackInterpolation(val serializedName: String) {
    /** 保持前一个关键帧的值，只有到达下一个关键帧时间点时才发生跳变。 */
    STEP("STEP"),

    /** 标量和向量执行分量线性插值，四元数执行归一化的最短路径球面插值。 */
    LINEAR("LINEAR"),

    /** 按 glTF 三元组布局读取入切线、值和出切线，并使用按秒缩放的三次 Hermite 插值。 */
    CUBICSPLINE("CUBICSPLINE")
}

/**
 * 保存一个已规范化的 glTF 浮点轨道。普通轨道每个关键帧保存一个值；CUBICSPLINE 轨道按
 * `inTangent, value, outTangent` 顺序保存三组值。构造时复制输入数组，避免导入缓冲区后续修改轨道。
 *
 * @param timesSeconds 严格递增且非负的关键帧秒数
 * @param keyframeData 按 glTF 插值布局排列的关键帧分量
 * @param componentCount 每个值包含的分量数
 * @param interpolation glTF 插值方式
 * @param quaternion 是否把四个分量解释为 `(x, y, z, w)` 四元数并归一化输出
 */
class CooFxTrack(
    timesSeconds: FloatArray,
    keyframeData: FloatArray,
    val componentCount: Int,
    val interpolation: CooFxTrackInterpolation,
    val quaternion: Boolean = false
) {
    private val times = timesSeconds.copyOf()
    private val data = keyframeData.copyOf()

    val keyframeCount: Int
        get() = times.size

    val startTimeSeconds: Float
        get() = times.first()

    val endTimeSeconds: Float
        get() = times.last()

    init {
        require(times.isNotEmpty()) { "轨道至少需要一个关键帧" }
        require(componentCount > 0) { "轨道分量数必须为正数" }
        require(!quaternion || componentCount == 4) { "四元数轨道必须包含四个分量" }
        times.forEachIndexed { index, time ->
            require(time.isFinite() && time >= 0F) { "关键帧时间必须为有限非负数" }
            require(index == 0 || time > times[index - 1]) { "关键帧时间必须严格递增" }
        }
        require(data.all(Float::isFinite)) { "轨道数据必须全部为有限数" }
        val valuesPerKeyframe = if (interpolation == CooFxTrackInterpolation.CUBICSPLINE) 3 else 1
        require(data.size == keyframeCount * componentCount * valuesPerKeyframe) {
            "轨道数据长度与关键帧布局不匹配"
        }
    }

    internal fun timeAt(index: Int): Float = times[index]

    internal fun valueAt(keyframeIndex: Int, componentIndex: Int, cubicPart: Int = 1): Float {
        val part = if (interpolation == CooFxTrackInterpolation.CUBICSPLINE) cubicPart else 0
        return data[(keyframeIndex * (if (interpolation == CooFxTrackInterpolation.CUBICSPLINE) 3 else 1) + part) * componentCount + componentIndex]
    }
}
