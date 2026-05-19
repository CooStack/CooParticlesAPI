package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

class InterpolatorDouble(private var value: Double) : InterpolatorData<Double> {
    var last = value

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorDouble>(
            { buf, data ->
                buf.writeDouble(data.last)
                buf.writeDouble(data.value)
            }, {
                val last = it.readDouble()
                val current = it.readDouble()
                InterpolatorDouble(last)
                    .update(current)
            }
        )
    }

    override fun update(current: Double): InterpolatorDouble {
        last = this.value
        this.value = current
        return this
    }

    override fun getWithInterpolator(progress: Number): Double {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): Double {
        return value
    }


    operator fun plus(double: Double): InterpolatorDouble {
        update(value + double)
        return this
    }

    operator fun minus(double: Double): InterpolatorDouble {
        update(value - double)
        return this
    }

    operator fun times(double: Double): InterpolatorDouble {
        update(value * double)
        return this
    }

    operator fun div(double: Double): InterpolatorDouble {
        require(double != 0.0) { "Division by zero" }
        update(value / double)
        return this
    }

    operator fun unaryMinus(): InterpolatorDouble {
        update(-value)
        return this
    }

    operator fun plus(other: InterpolatorDouble): InterpolatorDouble {
        update(value + other.value)
        return this
    }

    operator fun minus(other: InterpolatorDouble): InterpolatorDouble {
        update(value - other.value)
        return this
    }

    operator fun times(other: InterpolatorDouble): InterpolatorDouble {
        update(value * other.value)
        return this
    }

    operator fun div(other: InterpolatorDouble): InterpolatorDouble {
        require(other.value != 0.0) { "Division by zero" }
        update(value / other.value)
        return this
    }
}