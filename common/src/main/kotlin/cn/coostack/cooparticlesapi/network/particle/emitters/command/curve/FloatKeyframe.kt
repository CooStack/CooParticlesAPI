package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

/**
 * 标量关键帧。
 *
 * @property time 关键帧时间点，建议使用 `[0,1]` 的生命周期归一化时间
 * @property value 关键帧数值
 */
data class FloatKeyframe(
    val time: Double,
    val value: Double
)
