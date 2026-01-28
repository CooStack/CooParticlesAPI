package cn.coostack.cooparticlesapi.animation.timeline

fun interface Ease {
    fun cal(t: Double): Double
}