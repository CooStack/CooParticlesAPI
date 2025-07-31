package cn.coostack.cooparticlesapi.network.packet

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.particles.control.ControlType
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2

class PacketParticleStyleS2C(
    val uuid: UUID,
    val type: ControlType,
    val args: Map<String, ParticleControlerDataBuffer<*>>
) : CustomPayload {
    companion object {
        private val identifierID = Identifier.of(CooParticleAPI.MOD_ID, "particle_style")
        val payloadID = CustomPayload.Id<PacketParticleStyleS2C>(identifierID)
        private val CODEC: PacketCodec<PacketByteBuf, PacketParticleStyleS2C> =
            CustomPayload.codecOf({ packet, buf ->
                buf.writeUuid(packet.uuid)
                buf.writeInt(packet.type.id)
                buf.writeInt(packet.args.size)
                packet.args.forEach { (t, u) ->
                    val encode = ParticleControlerDataBuffers.encode(u)
                    val len = encode.size
                    buf.writeInt(len)
                    buf.writeString(t)
                    buf.writeBytes(encode)
                }
            }, { buf ->
                val args = HashMap<String, ParticleControlerDataBuffer<*>>()
                val uuid = buf.readUuid()
                val id = buf.readInt()
                val type = ControlType.Companion.getTypeById(id)
                val argsCount = buf.readInt()
                repeat(argsCount) {
                    val len = buf.readInt()
                    val key = buf.readString()
                    val buf = buf.readBytes(len)
                    val decode = ParticleControlerDataBuffers.decodeToBuffer<Any>(buf)
                    args[key] = decode
                }
                return@codecOf PacketParticleStyleS2C(
                    uuid, type, args
                )
            }
            )

        fun init() {
            PayloadTypeRegistry.playS2C().register(payloadID, CODEC)
        }
    }

    override fun getId(): CustomPayload.Id<out CustomPayload?>? {
        return payloadID
    }
}