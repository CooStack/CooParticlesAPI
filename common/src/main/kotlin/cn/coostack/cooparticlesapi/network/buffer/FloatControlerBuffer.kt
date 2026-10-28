package cn.coostack.cooparticlesapi.network.buffer


import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.Identifier

class FloatControlerBuffer() : ParticleControlerDataBuffer<Float> {

    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            Identifier.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "float"
            )
        )
    }

    override var loadedValue: Float? = 0.0F
    override fun encode(value: Float): ByteArray {
        return value.toString().toByteArray()
    }


    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): Float {
        return String(buf).toFloat()

    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}