package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

import cn.coostack.cooparticlesapi.utils.GraphMathHelper

/**
 * 关键帧曲线（线性插值）。
 *
 * 规则：
 * - 帧会按 `time` 升序保存
 * - `t` 落在相邻关键帧之间时，使用线性插值
 * - `t` 小于首帧或大于尾帧时，返回边界帧值
 */
class KeyframeFloatCurve(
    frames: List<FloatKeyframe> = listOf(FloatKeyframe(0.0, 0.0))
) : FloatCurve {
    private val keyframes: MutableList<FloatKeyframe> =
        frames.map { FloatKeyframe(it.time.coerceIn(0.0, 1.0), it.value) }
            .sortedBy { it.time }
            .toMutableList()

    init {
        if (keyframes.isEmpty()) {
            keyframes.add(FloatKeyframe(0.0, 0.0))
        }
    }

    /**
     * 批量替换关键帧列表。
     *
     * - 时间会被钳制到 `[0,1]`
     * - 内部会自动按时间升序排序
     */
    fun setFrames(frames: List<FloatKeyframe>) = apply {
        keyframes.clear()
        keyframes.addAll(
            frames.map { FloatKeyframe(it.time.coerceIn(0.0, 1.0), it.value) }
                .sortedBy { it.time }
        )
        if (keyframes.isEmpty()) {
            keyframes.add(FloatKeyframe(0.0, 0.0))
        }
    }

    /**
     * 追加一个关键帧。
     *
     * @param time 帧时间（会钳制到 `[0,1]`）
     * @param value 帧值
     */
    fun addFrame(time: Double, value: Double) = apply {
        keyframes.add(FloatKeyframe(time.coerceIn(0.0, 1.0), value))
        keyframes.sortBy { it.time }
    }

    override fun sample(t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        if (keyframes.size == 1) {
            return keyframes[0].value
        }
        if (clamped <= keyframes.first().time) {
            return keyframes.first().value
        }
        if (clamped >= keyframes.last().time) {
            return keyframes.last().value
        }

        for (i in 1 until keyframes.size) {
            val prev = keyframes[i - 1]
            val next = keyframes[i]
            if (clamped <= next.time) {
                val seg = (next.time - prev.time).coerceAtLeast(1e-9)
                val alpha = (clamped - prev.time) / seg
                return GraphMathHelper.lerp(alpha, prev.value, next.value)
            }
        }
        return keyframes.last().value
    }
}
