package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

import cn.coostack.cooparticlesapi.utils.GraphMathHelper

/**
 * 线性曲线：在 `[0,1]` 区间内从 `from` 匀速过渡到 `to`。
 *
 * 典型用途：
 * - 生命周期内“线性增强/衰减”
 * - 先快速验证一个效果趋势，再换成关键帧曲线精调
 */
class LinearFloatCurve(
    /**
     * `t=0` 时的值。
     */
    var from: Double = 0.0,

    /**
     * `t=1` 时的值。
     */
    var to: Double = 1.0
) : FloatCurve {
    override fun sample(t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        return GraphMathHelper.lerp(clamped, from, to)
    }
}
