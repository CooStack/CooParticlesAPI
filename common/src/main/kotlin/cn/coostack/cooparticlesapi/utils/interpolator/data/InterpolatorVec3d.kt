package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

class InterpolatorVec3d(private var value: Vec3) : InterpolatorData<Vec3> {
    var last = value

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorVec3d>(
            { buf, data ->
                buf.writeVec3(data.last)
                buf.writeVec3(data.value)
            }, {
                val last = it.readVec3()
                val current = it.readVec3()
                InterpolatorVec3d(last)
                    .update(current)
            }
        )

    }

    override fun update(current: Vec3): InterpolatorVec3d {
        last = this.value
        this.value = current
        return this
    }

    override fun getWithInterpolator(progress: Number): Vec3 {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): Vec3 {
        return value
    }

}