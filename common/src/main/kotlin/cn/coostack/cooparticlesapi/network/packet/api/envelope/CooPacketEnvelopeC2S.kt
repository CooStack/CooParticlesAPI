package cn.coostack.cooparticlesapi.network.packet.api.envelope

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * # CooPacket 信封 (C2S)
 *
 * 与 [CooPacketEnvelopeS2C] 字段一致，仅 payloadID 不同以匹配 MC 注册要求。
 */
class CooPacketEnvelopeC2S(
    val kindId: Int,
    val packetId: ResourceLocation,
    val correlationId: Long,
    val timeoutTicks: Int,
    val data: ByteArray,
) : CustomPacketPayload {

    companion object {
        private val identifier =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "coo_packet_envelope_c2s")

        @JvmField
        val payloadID: CustomPacketPayload.Type<CooPacketEnvelopeC2S> =
            CustomPacketPayload.Type<CooPacketEnvelopeC2S>(identifier)

        @JvmField
        val CODEC: StreamCodec<FriendlyByteBuf, CooPacketEnvelopeC2S> = CustomPacketPayload.codec(
            { packet, buf ->
                buf.writeVarInt(packet.kindId)
                buf.writeResourceLocation(packet.packetId)
                buf.writeLong(packet.correlationId)
                buf.writeVarInt(packet.timeoutTicks)
                buf.writeByteArray(packet.data)
            },
            { buf ->
                CooPacketEnvelopeC2S(
                    kindId = buf.readVarInt(),
                    packetId = buf.readResourceLocation(),
                    correlationId = buf.readLong(),
                    timeoutTicks = buf.readVarInt(),
                    data = buf.readByteArray()
                )
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = payloadID
}
