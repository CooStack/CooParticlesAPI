package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper

import org.joml.Quaternionf

class InterpolatorQuaternionf(value: Quaternionf) : AbstractInterpolatorData<Quaternionf>(value) {

    companion object {
        @JvmStatic
        val CODEC = ForgeStreamCodec.of(InterpolatorQuaternionf>(
            { buf, data ->
                buf.writeFloat(data.value.x)
                buf.writeFloat(data.value.y)
                buf.writeFloat(data.value.z)
                buf.writeFloat(data.value.w)
            }, {
                val x = it.readFloat()
                val y = it.readFloat()
                val z = it.readFloat()
                val w = it.readFloat()
                InterpolatorQuaternionf(Quaternionf(x, y, z, w))
            }
        )
    }

    override fun getWithInterpolator(progress: Number): Quaternionf {
        return GraphMathHelper.lerp(progress.toFloat(), last, value)
    }

    override fun getCurrent(): Quaternionf {
        return Quaternionf(value)
    }
}
