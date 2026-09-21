package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketParticleCompositionS2C(val uuid: UUID, val type: String, val data: ByteArray) {
    var distanceRemove = false
    var recreate = false

    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_composition")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_composition")
        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeUtf(packet.type)
            buf.writeUUID(packet.uuid)
            buf.writeBoolean(packet.distanceRemove)
            buf.writeBoolean(packet.recreate)
            buf.writeInt(packet.data.size)
            buf.writeBytes(packet.data)
        }, { buf ->
            val type = buf.readUtf()
            val uuid = buf.readUUID()
            val distanceRemove = buf.readBoolean()
            val recreate = buf.readBoolean()
            val size = buf.readInt()
            val data = ByteArray(size).also { buf.readBytes(it) }
            PacketParticleCompositionS2C(uuid, type, data).apply {
                this.distanceRemove = distanceRemove
                this.recreate = recreate
            }
        })
    }
}
