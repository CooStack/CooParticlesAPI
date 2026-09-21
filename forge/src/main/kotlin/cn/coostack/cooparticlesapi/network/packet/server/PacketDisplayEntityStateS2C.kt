package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

class PacketDisplayEntityStateS2C(
    val uuid: UUID,
    val position: Vec3,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val scale: Float,
) {
    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "display_entity_state")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "display_entity_state")
        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeUUID(packet.uuid)
            buf.writeVec3(packet.position)
            buf.writeFloat(packet.yaw)
            buf.writeFloat(packet.pitch)
            buf.writeFloat(packet.roll)
            buf.writeFloat(packet.scale)
        }, { buf ->
            PacketDisplayEntityStateS2C(
                buf.readUUID(),
                buf.readVec3(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
            )
        })
    }
}
