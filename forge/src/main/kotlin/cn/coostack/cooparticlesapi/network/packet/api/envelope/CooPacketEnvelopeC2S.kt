package cn.coostack.cooparticlesapi.network.packet.api.envelope

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

class CooPacketEnvelopeC2S(
    val kindId: Int,
    val packetId: ResourceLocation,
    val correlationId: Long,
    val timeoutTicks: Int,
    val data: ByteArray,
) {
    companion object {
        @JvmStatic
        fun write(buf: PacketByteBuf, packet: CooPacketEnvelopeC2S) {
            buf.writeVarInt(packet.kindId)
            buf.writeResourceLocation(packet.packetId)
            buf.writeLong(packet.correlationId)
            buf.writeVarInt(packet.timeoutTicks)
            buf.writeByteArray(packet.data)
        }

        @JvmStatic
        fun read(buf: PacketByteBuf): CooPacketEnvelopeC2S {
            return CooPacketEnvelopeC2S(
                kindId = buf.readVarInt(),
                packetId = buf.readResourceLocation(),
                correlationId = buf.readLong(),
                timeoutTicks = buf.readVarInt(),
                data = buf.readByteArray()
            )
        }
    }
}
