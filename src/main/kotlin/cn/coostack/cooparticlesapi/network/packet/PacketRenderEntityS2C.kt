package cn.coostack.cooparticlesapi.network.packet

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

class PacketRenderEntityS2C(var uuid: UUID, var entityData: ByteBuf, var id: Identifier, var method: Method) :
    CustomPayload {
    enum class Method(val id: Int) {
        CREATE(0),
        TOGGLE(1),
        REMOVE(2);

        companion object {
            fun idOf(id: Int): Method {
                return when (id) {
                    CREATE.id -> CREATE
                    TOGGLE.id -> TOGGLE
                    REMOVE.id -> REMOVE
                    else -> CREATE
                }
            }
        }
    }

    companion object {
        private val identifierID = Identifier.of(CooParticleAPI.MOD_ID, "renderer_entity_packet")
        val payloadID = CustomPayload.Id<PacketRenderEntityS2C>(identifierID)
        private val CODEC: PacketCodec<RegistryByteBuf, PacketRenderEntityS2C> =
            CustomPayload.codecOf({ packet, buf ->
                val entity = packet.entityData
                buf.writeInt(packet.method.id)
                buf.writeUuid(packet.uuid)
                buf.writeIdentifier(packet.id)
                buf.writeBytes(entity)
            }, { buf ->
                val method = buf.readInt()
                val uuid = buf.readUuid()
                val id = buf.readIdentifier()
                val entity = buf.readBytes(buf.readableBytes())
                val packet = PacketRenderEntityS2C(uuid, entity, id, Method.idOf(method))
                return@codecOf packet
            })

        fun init() {
            PayloadTypeRegistry.playS2C().register(payloadID, CODEC)
        }
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> {
        return payloadID
    }
}