package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

data class BezierFloatKeyframe(
    val time: Double,
    val value: Double,
    val outX: Double = 0.0,
    val outY: Double = 0.0,
    val inX: Double = 0.0,
    val inY: Double = 0.0
)
