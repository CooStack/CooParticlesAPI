package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation


class LongControlerBuffer : ParticleControlerDataBuffer<Long> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "long"
            )
        )
    }

    override var loadedValue: Long? = 0L
    override fun encode(value: Long): ByteArray {
        return value.toString().toByteArray()
    }

    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): Long {
        return String(buf).toLong()
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}
