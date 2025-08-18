package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

class ShortControlerBuffer() : ParticleControlerDataBuffer<Short> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "short"
            )
        )
    }
    override var loadedValue: Short? = 0
    override fun encode(value: Short): ByteArray {
        return value.toString().toByteArray()
    }


    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): Short {
        return String(buf).toShort()
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}
