package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation


class DoubleControlerBuffer() : ParticleControlerDataBuffer<Double> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "double"
            )
        )
    }
    override var loadedValue: Double? = 0.0
    override fun encode(value: Double): ByteArray {
        return value.toString().toByteArray()
    }

    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): Double {
        return String(buf).toDouble()
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}