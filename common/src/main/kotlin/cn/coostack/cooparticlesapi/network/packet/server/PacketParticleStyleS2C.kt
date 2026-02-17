package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.particles.control.ControlType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketParticleStyleS2C(
    val uuid: UUID,
    val type: ControlType,
    val args: Map<String, ParticleControlerDataBuffer<*>>
) : CustomPacketPayload {
    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_style")
        val payloadID = CustomPacketPayload.Type<PacketParticleStyleS2C>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketParticleStyleS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeUUID(packet.uuid)
                buf.writeInt(packet.type.id)
                buf.writeInt(packet.args.size)
                packet.args.forEach { (t, u) ->
                    val encode = ParticleControlerDataBuffers.encode(u)
                    val len = encode.size
                    buf.writeInt(len)
                    buf.writeUtf(t)
                    buf.writeBytes(encode)
                }
            }, { buf ->
                val args = HashMap<String, ParticleControlerDataBuffer<*>>()
                val uuid = buf.readUUID()
                val id = buf.readInt()
                val type = ControlType.getTypeById(id)
                val argsCount = buf.readInt()
                repeat(argsCount) {
                    val len = buf.readInt()
                    val key = buf.readUtf()
                    val buf = buf.readBytes(len)
                    val decode = ParticleControlerDataBuffers.decodeToBuffer<Any>(buf)
                    args[key] = decode
                }
                return@codec PacketParticleStyleS2C(
                    uuid, type, args
                )
            }
            )

    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return payloadID
    }
}