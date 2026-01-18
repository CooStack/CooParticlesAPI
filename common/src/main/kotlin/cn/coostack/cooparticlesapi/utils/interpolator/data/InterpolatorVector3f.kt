package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f

class InterpolatorVector3f(private var value: Vector3f) : InterpolatorData<Vector3f> {
    var last = value

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorVector3f>(
            { buf, data ->
                buf.writeVector3f(data.last)
                buf.writeVector3f(data.value)
            }, {
                val last = it.readVector3f()
                val current = it.readVector3f()
                InterpolatorVector3f(last)
                    .update(current)
            }
        )

    }

    override fun update(current: Vector3f): InterpolatorVector3f {
        last = this.value
        this.value = current
        return this
    }

    override fun getWithInterpolator(progress: Number): Vector3f {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): Vector3f {
        return value
    }

}