package cn.coostack.cooparticlesapi.utils.builder

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


/**
 * y轴为对称轴
 * 快捷构建傅里叶级数
 */
class FourierSeriesBuilder {
    data class Fourier(
        var w: Double, var r: Double, var startAngle: Double,
    )

    private var count = 360
    private var scale = 1.0
    private val fouriers = ArrayList<Fourier>()
    fun addFourier(r: Double, w: Double, startAngle: Double = 0.0): FourierSeriesBuilder {
        fouriers.add(Fourier(w, r, startAngle))
        return this
    }

    fun scale(scale: Double): FourierSeriesBuilder {
        this.scale = scale
        return this
    }

    fun count(count: Int): FourierSeriesBuilder {
        this.count = count
        return this
    }

    fun build(): List<RelativeLocation> {
        val res = ArrayList<RelativeLocation>()
        val precision = 2 * PI / count
        if (fouriers.isEmpty()) return res
        repeat(count) { i ->
            val t = i * precision
            var x = 0.0
            var z = 0.0
            fouriers.forEach {
                val angle = Math.toRadians(it.startAngle) + it.w * t
                val px = it.r * cos(angle) * scale
                val pz = it.r * sin(angle) * scale
                val ax = x + px
                val az = z + pz
                x = ax
                z = az
            }
            res.add(RelativeLocation(x, 0.0, z))
        }
        return res
    }

}