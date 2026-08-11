package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Quaternionf
import org.joml.Vector3f

class InterpolatorVector3f(value: Vector3f) : AbstractInterpolatorData<Vector3f>(value) {

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorVector3f>(
            { buf, data ->
                buf.writeVector3f(data.last)
                buf.writeVector3f(data.value)
            }, {
                val last = it.readVector3f()
                val current = it.readVector3f()

                InterpolatorVector3f(current).apply {
                    this.last = last
                }
            }
        )

    }

    override fun getWithInterpolator(progress: Number): Vector3f {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): Vector3f {
        return value
    }

}