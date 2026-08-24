package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

class InterpolatorVec3d(value: Vec3) : AbstractInterpolatorData<Vec3>(value) {

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorVec3d>(
            { buf, data ->
                buf.writeVec3(data.value)
            }, {
                val current = it.readVec3()
                InterpolatorVec3d(current)
            }
        )

    }


    override fun getWithInterpolator(progress: Number): Vec3 {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): Vec3 {
        return value
    }

}