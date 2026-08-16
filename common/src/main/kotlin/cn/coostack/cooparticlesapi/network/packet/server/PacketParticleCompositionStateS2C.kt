package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

class PacketParticleCompositionStateS2C(
    val uuid: UUID,
    val position: Vec3,
    val visibleRange: Double,
    val scale: Double,
    val displayStatus: Int,
    val closedInterval: Int,
    val current: Int,
) : CustomPacketPayload {
    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_composition_state")
        val payloadID = CustomPacketPayload.Type<PacketParticleCompositionStateS2C>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketParticleCompositionStateS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeUUID(packet.uuid)
                buf.writeVec3(packet.position)
                buf.writeDouble(packet.visibleRange)
                buf.writeDouble(packet.scale)
                buf.writeVarInt(packet.displayStatus)
                buf.writeVarInt(packet.closedInterval)
                buf.writeVarInt(packet.current)
            }, { buf ->
                PacketParticleCompositionStateS2C(
                    buf.readUUID(),
                    buf.readVec3(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                )
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = payloadID
}
