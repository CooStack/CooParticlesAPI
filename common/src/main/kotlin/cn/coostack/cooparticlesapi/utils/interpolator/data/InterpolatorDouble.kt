package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

class InterpolatorDouble(value: Double) : AbstractInterpolatorData<Double>(value) {

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorDouble>(
            { buf, data ->
                buf.writeDouble(data.value)
            }, {
                val current = it.readDouble()
                InterpolatorDouble(current)
            }
        )
    }


    override fun getWithInterpolator(progress: Number): Double {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): Double {
        return value
    }


    operator fun plus(double: Double): InterpolatorDouble {
        uploadData(value + double)
        return this
    }

    operator fun minus(double: Double): InterpolatorDouble {
        uploadData(value - double)
        return this
    }

    operator fun times(double: Double): InterpolatorDouble {
        uploadData(value * double)
        return this
    }

    operator fun div(double: Double): InterpolatorDouble {
        require(double != 0.0) { "Division by zero" }
        uploadData(value / double)
        return this
    }

    operator fun unaryMinus(): InterpolatorDouble {
        uploadData(-value)
        return this
    }

    operator fun plus(other: InterpolatorDouble): InterpolatorDouble {
        uploadData(value + other.value)
        return this
    }

    operator fun minus(other: InterpolatorDouble): InterpolatorDouble {
        uploadData(value - other.value)
        return this
    }

    operator fun times(other: InterpolatorDouble): InterpolatorDouble {
        uploadData(value * other.value)
        return this
    }

    operator fun div(other: InterpolatorDouble): InterpolatorDouble {
        require(other.value != 0.0) { "Division by zero" }
        uploadData(value / other.value)
        return this
    }
}