package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

class InterpolatorRelativeLocation(value: RelativeLocation) : AbstractInterpolatorData<RelativeLocation>(value) {

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorRelativeLocation>(
            { buf, data ->
                buf.writeVec3(data.value.toVector())
            }, {
                val current = it.readVec3().asRelative()
                InterpolatorRelativeLocation(current)
            }
        )

    }


    override fun getWithInterpolator(progress: Number): RelativeLocation {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): RelativeLocation {
        return value
    }

}