package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

class IntControlerBuffer : ParticleControlerDataBuffer<Int> {

    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "int"
            )
        )
    }

    override var loadedValue: Int? = 0
    override fun encode(value: Int): ByteArray {
        return value.toString().toByteArray()
    }


    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): Int {
        return String(buf).toInt()
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}
