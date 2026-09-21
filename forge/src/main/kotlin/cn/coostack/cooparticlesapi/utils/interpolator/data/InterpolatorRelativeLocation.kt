package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.RelativeLocation

import net.minecraft.world.phys.Vec3

class InterpolatorRelativeLocation(value: RelativeLocation) : AbstractInterpolatorData<RelativeLocation>(value) {

    companion object {
        @JvmStatic
        val CODEC = ForgeStreamCodec.of(InterpolatorRelativeLocation>(
            { buf, data ->
                buf.writeDouble(data.value.x)
                buf.writeDouble(data.value.y)
                buf.writeDouble(data.value.z)
            }, {
                val x = it.readDouble()
                val y = it.readDouble()
                val z = it.readDouble()
                InterpolatorRelativeLocation(RelativeLocation(x, y, z))
            }
        )
    }

    override fun getWithInterpolator(progress: Number): RelativeLocation {
        return GraphMathHelper.lerp(progress.toDouble(), last, value)
    }

    override fun getCurrent(): RelativeLocation {
        return RelativeLocation(value.x, value.y, value.z)
    }
}
