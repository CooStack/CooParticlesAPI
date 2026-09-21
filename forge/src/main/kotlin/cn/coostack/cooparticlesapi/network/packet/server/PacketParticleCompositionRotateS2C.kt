package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

class PacketParticleCompositionRotateS2C(
    val uuid: UUID,
    val direction: Vec3?,
    val rollDelta: Double
) {
    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_composition_rotate")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_composition_rotate")
        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeUUID(packet.uuid)
            buf.writeBoolean(packet.direction != null)
            packet.direction?.let { buf.writeVec3(it) }
            buf.writeDouble(packet.rollDelta)
        }, { buf ->
            val uuid = buf.readUUID()
            val direction = if (buf.readBoolean()) buf.readVec3() else null
            val rollDelta = buf.readDouble()
            PacketParticleCompositionRotateS2C(uuid, direction, rollDelta)
        })
    }
}
