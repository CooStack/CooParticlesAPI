package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import io.netty.buffer.Unpooled
import net.minecraft.resources.ResourceLocation

class IntArrayControlerBuffer : ParticleControlerDataBuffer<IntArray> {

    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "int_array"
            )
        )
    }

    override var loadedValue: IntArray? = IntArray(0)

    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun encode(value: IntArray): ByteArray {
        val buffer = Unpooled.buffer()
        buffer.writeInt(value.size)
        value.forEach { buffer.writeInt(it) }
        return buffer.copy().array()
    }

    override fun decode(buf: ByteArray): IntArray {
        val wrap = Unpooled.wrappedBuffer(buf)
        val size = wrap.readInt()
        val arr = IntArray(size)
        for (i in 0 until size) {
            arr[i] = wrap.readInt()
        }
        return arr
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}