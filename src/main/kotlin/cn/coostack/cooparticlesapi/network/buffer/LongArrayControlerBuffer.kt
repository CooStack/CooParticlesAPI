package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticleAPI
import io.netty.buffer.Unpooled
import net.minecraft.util.Identifier

class LongArrayControlerBuffer : ParticleControlerDataBuffer<LongArray> {

    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            Identifier.of(
                CooParticleAPI.MOD_ID, "long_array"
            )
        )
    }

    override var loadedValue: LongArray? = LongArray(0)

    override fun encode(): ByteArray? {
        return encode(loadedValue!!)
    }

    override fun encode(value: LongArray): ByteArray {
        val buffer = Unpooled.buffer()
        buffer.writeInt(value.size)
        value.forEach {
            buffer.writeLong(it)
        }
        return buffer.copy().array()
    }

    override fun decode(buf: ByteArray): LongArray {
        val wrap = Unpooled.wrappedBuffer(buf)
        val size = wrap.readInt()
        val arr = LongArray(size)
        for (i in 0 until size) {
            arr[i] = wrap.readLong()
        }
        return arr
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }
}