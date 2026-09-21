package cn.coostack.cooparticlesapi.annotations.packet

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object CooPacketRegistryHelper {
    fun generateClassParticleCodec(type: Class<out CooPacket>): CommonStreamCodec<out CooPacket> {
        val constructor = type.getConstructor()
        return CommonStreamCodec.of(
            { buf, packet ->
                packet as CooPacket
                val fields = codecFields(type)
                fields.forEach { field ->
                    field.isAccessible = true
                    val codec = CodecHelper.codecOf(field.genericType)
                    codec.encode(buf, field.get(packet))
                }
            },
            { buf ->
                constructor.newInstance().apply {
                    val fields = codecFields(type)
                    fields.forEach { field ->
                        field.isAccessible = true
                        val codec = CodecHelper.codecOf(field.genericType)
                        val value = codec.decode(buf)
                        field.set(this, value)
                    }
                }
            }
        )
    }

    private fun codecFields(type: Class<*>): List<Field> {
        return type.declaredFields
            .filter { it.isAnnotationPresent(CodecField::class.java) && !Modifier.isFinal(it.modifiers) }
            .sortedBy { it.name }
    }
}
