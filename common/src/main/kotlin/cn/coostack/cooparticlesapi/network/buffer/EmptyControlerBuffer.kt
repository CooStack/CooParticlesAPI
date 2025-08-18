package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation


class EmptyControlerBuffer() : ParticleControlerDataBuffer<Unit> {

    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "empty"
            )
        )
    }

    override var loadedValue: Unit? = null

    override fun encode(value: Unit): ByteArray {
        return byteArrayOf(0)
    }


    override fun encode(): ByteArray {
        return byteArrayOf()
    }

    override fun decode(buf: ByteArray) {
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}