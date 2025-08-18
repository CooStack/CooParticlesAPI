package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation


class BooleanControlerBuffer() : ParticleControlerDataBuffer<Boolean> {

    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "boolean"
            )
        )
    }

    override var loadedValue: Boolean? = false

    override fun encode(value: Boolean): ByteArray {
        return byteArrayOf(if (value) 1 else 0)
    }


    override fun encode(): ByteArray {
        return encode(loadedValue ?: false)
    }

    override fun decode(buf: ByteArray): Boolean {
        if (buf.isNotEmpty()) {
            return buf[0] == 1.toByte()
        }
        return false
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}