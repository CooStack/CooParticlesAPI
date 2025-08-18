package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation


class CharControlerBuffer : ParticleControlerDataBuffer<Char> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "char"
            )
        )
    }
    override var loadedValue: Char? = ' '
    override fun encode(value: Char): ByteArray {
        return value.toString().toByteArray()
    }


    override fun encode(): ByteArray {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): Char {
        return String(buf)[0]
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}
