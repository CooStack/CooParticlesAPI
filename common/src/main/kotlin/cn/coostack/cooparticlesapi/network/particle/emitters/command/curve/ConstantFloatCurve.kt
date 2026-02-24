package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

/**
 * 常量曲线：全生命周期输出同一个值。
 *
 * 典型用途：
 * - 固定重力倍率
 * - 固定速度修正
 * - 作为其它更复杂曲线的默认占位
 */
class ConstantFloatCurve(
    /**
     * 常量值。
     */
    var value: Double = 0.0
) : FloatCurve {
    override fun sample(t: Double): Double = value
}
