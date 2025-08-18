package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

class StringControlerBuffer : ParticleControlerDataBuffer<String> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "string"
            )
        )
    }
    override var loadedValue: String? = ""
    override fun encode(value: String): ByteArray {
        return value.toByteArray()
    }


    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): String {
        return String(buf)
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}
