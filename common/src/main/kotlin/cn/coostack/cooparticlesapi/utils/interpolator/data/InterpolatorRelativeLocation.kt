package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

class InterpolatorRelativeLocation(private var value: RelativeLocation) : InterpolatorData<RelativeLocation> {
    var last = value

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, InterpolatorRelativeLocation>(
            { buf, data ->
                buf.writeVec3(data.last.toVector())
                buf.writeVec3(data.value.toVector())
            }, {
                val last = it.readVec3().asRelative()
                val current = it.readVec3().asRelative()
                InterpolatorRelativeLocation(last)
                    .update(current)
            }
        )

    }

    override fun update(current: RelativeLocation): InterpolatorRelativeLocation {
        last = this.value
        this.value = current
        return this
    }

    override fun getWithInterpolator(progress: Number): RelativeLocation {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): RelativeLocation {
        return value
    }

}