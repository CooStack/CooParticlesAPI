package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

class InterpolatorFloat( value: Float) : AbstractInterpolatorData<Float>(value) {

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorFloat>(
            { buf, data ->
                buf.writeFloat(data.value)
            }, {
                val current = it.readFloat()
                InterpolatorFloat(current)
            }
        )
    }


    override fun getWithInterpolator(progress: Number): Float {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }
    

    override fun getCurrent(): Float {
        return value
    }

    operator fun plus(float: Float): InterpolatorFloat {
        uploadData(value + float)
        return this
    }

    operator fun minus(float: Float): InterpolatorFloat {
        uploadData(value - float)
        return this
    }

    operator fun times(float: Float): InterpolatorFloat {
        uploadData(value * float)
        return this
    }

    operator fun div(float: Float): InterpolatorFloat {
        require(float != 0f) { "Division by zero" }
        uploadData(value / float)
        return this
    }


    operator fun unaryMinus(): InterpolatorFloat {
        uploadData(-value)
        return this
    }

    operator fun plus(other: InterpolatorFloat): InterpolatorFloat {
        uploadData(value + other.value)
        return this
    }

    operator fun minus(other: InterpolatorFloat): InterpolatorFloat {
        uploadData(value - other.value)
        return this
    }

    operator fun times(other: InterpolatorFloat): InterpolatorFloat {
        uploadData(value * other.value)
        return this
    }

    operator fun div(other: InterpolatorFloat): InterpolatorFloat {
        require(other.value != 0f) { "Division by zero" }
        uploadData(value / other.value)
        return this
    }

}