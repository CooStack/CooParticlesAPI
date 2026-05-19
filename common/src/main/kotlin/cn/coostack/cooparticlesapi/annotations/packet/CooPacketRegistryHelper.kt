package cn.coostack.cooparticlesapi.annotations.packet

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object CooPacketRegistryHelper {
    fun generateClassParticleCodec(type: Class<out CooPacket>): StreamCodec<out FriendlyByteBuf, out CooPacket> {
        val constructor = type.getConstructor()
        return StreamCodec.of(
            { buf, packet ->
                packet as CooPacket
                val fields = codecFields(type)
                fields.forEach { field ->
                    field.isAccessible = true
                    val codec = codecByField(field)
                    codec.encode(buf, field.get(packet))
                }
            },
            { buf ->
                constructor.newInstance().apply {
                    val fields = codecFields(type)
                    fields.forEach { field ->
                        field.isAccessible = true
                        val codec = codecByField(field)
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

    @Suppress("UNCHECKED_CAST")
    private fun codecByField(field: Field): StreamCodec<FriendlyByteBuf, Any> {
        return CodecHelper.codecOf(field.genericType) as StreamCodec<FriendlyByteBuf, Any>
    }
}