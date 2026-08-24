package cn.coostack.cooparticlesapi.utils.interpolator.data

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Quaternionf

class InterpolatorQuaternionf(value: Quaternionf) : AbstractInterpolatorData<Quaternionf>(value) {
    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorQuaternionf>(
            { buf, data ->
                buf.writeQuaternion(data.value)
            }, {
                val current = it.readQuaternion()
                InterpolatorQuaternionf(current)
            }
        )
    }

    override fun getWithInterpolator(progress: Number): Quaternionf {
        return value.nlerp(last, progress.toFloat(), Quaternionf())
    }

    override fun getCurrent(): Quaternionf {
        return value
    }
}