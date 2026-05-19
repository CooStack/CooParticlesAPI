package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

class InterpolatorFloat(private var value: Float) : InterpolatorData<Float> {
    var last = value


    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorFloat>(
            { buf, data ->
                buf.writeFloat(data.last)
                buf.writeFloat(data.value)
            }, {
                val last = it.readFloat()
                val current = it.readFloat()
                InterpolatorFloat(last)
                    .update(current)
            }
        )

    }

    override fun update(current: Float): InterpolatorFloat {
        last = this.value
        this.value = current
        return this
    }

    override fun getWithInterpolator(progress: Number): Float {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): Float {
        return value
    }

    operator fun plus(float: Float): InterpolatorFloat {
        update(value + float)
        return this
    }

    operator fun minus(float: Float): InterpolatorFloat {
        update(value - float)
        return this
    }

    operator fun times(float: Float): InterpolatorFloat {
        update(value * float)
        return this
    }

    operator fun div(float: Float): InterpolatorFloat {
        require(float != 0f) { "Division by zero" }
        update(value / float)
        return this
    }


    operator fun unaryMinus(): InterpolatorFloat {
        update(-value)
        return this
    }

    operator fun plus(other: InterpolatorFloat): InterpolatorFloat {
        update(value + other.value)
        return this
    }

    operator fun minus(other: InterpolatorFloat): InterpolatorFloat {
        update(value - other.value)
        return this
    }

    operator fun times(other: InterpolatorFloat): InterpolatorFloat {
        update(value * other.value)
        return this
    }

    operator fun div(other: InterpolatorFloat): InterpolatorFloat {
        require(other.value != 0f) { "Division by zero" }
        update(value / other.value)
        return this
    }

}