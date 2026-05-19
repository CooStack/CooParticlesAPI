package cn.coostack.cooparticlesapi.network.packet.api.envelope

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * # CooPacket 信封
 *
 * 所有[cn.coostack.cooparticlesapi.network.packet.api.CooPacket] 都通过本信封发送/接收
 *
 * - kindId: 0=NORMAL, 1=REQUEST, 2=RESPONSE
 * - packetId: 业务包的 ResourceLocation, 由 CooPacket.id() 提供
 * - correlationId: 0 表示普通包；非0 表示请求/响应关联ID
 * - timeoutTicks: 仅请求时有效
 * - data: 业务包字段编码后的字节流
 */
class CooPacketEnvelopeS2C(
    val kindId: Int,
    val packetId: ResourceLocation,
    val correlationId: Long,
    val timeoutTicks: Int,
    val data: ByteArray,
) : CustomPacketPayload {

    companion object {
        private val identifier =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "coo_packet_envelope_s2c")

        @JvmField
        val payloadID: CustomPacketPayload.Type<CooPacketEnvelopeS2C> =
            CustomPacketPayload.Type<CooPacketEnvelopeS2C>(identifier)

        @JvmField
        val CODEC: StreamCodec<FriendlyByteBuf, CooPacketEnvelopeS2C> = CustomPacketPayload.codec(
            { packet, buf ->
                buf.writeVarInt(packet.kindId)
                buf.writeResourceLocation(packet.packetId)
                buf.writeLong(packet.correlationId)
                buf.writeVarInt(packet.timeoutTicks)
                buf.writeByteArray(packet.data)
            },
            { buf ->
                CooPacketEnvelopeS2C(
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
