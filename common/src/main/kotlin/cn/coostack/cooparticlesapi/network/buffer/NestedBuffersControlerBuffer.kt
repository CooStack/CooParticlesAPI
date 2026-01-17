package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import io.netty.buffer.Unpooled
import net.minecraft.resources.ResourceLocation


/**
 * 如果某个style使用了嵌套 (内容使用了其他Style或者group)
 * 制作数据同步时 可以在最外层style加入 subGroup的 data buffer
 * 如果你使用了自定义的ParticleControlableDataBuffer
 * 那么你的buffer一定要通过 ParticleControlableDataBuffer.register(id, buffer::class.java)注册类型 否则会出现npe
 */
class NestedBuffersControlerBuffer : ParticleControlerDataBuffer<Map<String, ParticleControlerDataBuffer<*>>> {

    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "nested"
            )
        )
    }

    override var loadedValue: Map<String, ParticleControlerDataBuffer<*>>? = null
    override fun encode(): ByteArray {
        return encode(loadedValue!!)
    }

    override fun encode(value: Map<String, ParticleControlerDataBuffer<*>>): ByteArray {
        val buf = Unpooled.buffer(value.size * 8)
        // 第一个存 接下来的参数个数
        // 第一个 int代表key的长度
        // 第二个 int代表value类型的字符串类的长度
        // 第三个 int代表value的长度
        buf.writeInt(value.size)
        value.forEach { (key, value) ->
            buf.writeInt(key.length)
            buf.writeBytes(key.toByteArray())
            val id = "${value.getBufferID().value.namespace}:${value.getBufferID().value.path}"
            buf.writeInt(id.length)
            buf.writeBytes(id.toByteArray())
            val subBytes = value.encode()!!
            buf.writeInt(subBytes.size)
            buf.writeBytes(subBytes)
        }
        return buf.copy().array()
    }

    override fun decode(buf: ByteArray): Map<String, ParticleControlerDataBuffer<*>> {
        val buf = Unpooled.copiedBuffer(buf)
        val len = buf.readInt()
        val res = mutableMapOf<String, ParticleControlerDataBuffer<*>>()
        for (i in 0 until len) {
            val keyLen = buf.readInt()
            val key = String(buf.readBytes(keyLen).copy().array())
            val typeLen = buf.readInt()
            val idSplit = String(buf.readBytes(typeLen).copy().array()).split(":")
            val id = ResourceLocation.fromNamespaceAndPath(idSplit[0], idSplit[1])
            val valueLen = buf.readInt()
            val valueBytes = buf.readBytes(valueLen).copy().array()
            val buffer = ParticleControlerDataBuffers.withIdDecode(id, valueBytes)!!
            res[key] = buffer
        }
        return res
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }
}