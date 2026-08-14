package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketParticleEmittersS2C(
    val emitterID: String,
    val emitterUUID: UUID,
    val emitterData: ByteArray,
    val type: PacketType
) :
    CustomPacketPayload {
    /**
     * 发射器同步包的生命周期操作。
     *
     * [CREATE] 用于首次进入玩家可见范围，客户端必须创建并启动新实例；
     * [CHANGE] 只更新已经存在的客户端实例，不得重新执行启动逻辑；
     * [REMOVE] 按 UUID 立即清理客户端实例，不携带完整发射器状态。
     * 数字 ID 是网络协议的一部分，其中 `0` 和 `1` 保留旧协议的创建与移除编号。
     */
    enum class PacketType(val id: Int) {
        CREATE(0),
        REMOVE(1),
        CHANGE(2);

        companion object {
            @JvmStatic
            fun fromID(id: Int): PacketType {
                return when (id) {
                    0 -> CREATE
                    1 -> REMOVE
                    2 -> CHANGE
                    else -> CREATE
                }
            }
        }
    }

    companion object {
        private val id =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_emitters")
        val payloadID = CustomPacketPayload.Type<PacketParticleEmittersS2C>(id)

        val CODEC =
            StreamCodec.of<FriendlyByteBuf, PacketParticleEmittersS2C>({ buf, packet ->
                val emitterID = packet.emitterID
                buf.writeInt(packet.type.id)
                buf.writeUtf(emitterID)
                buf.writeUUID(packet.emitterUUID)
                buf.writeInt(packet.emitterData.size)
                buf.writeBytes(packet.emitterData)
            }, { buf ->
                val packetTypeID = buf.readInt()
                val emitterID = buf.readUtf()
                val emitterUUID = buf.readUUID()
                val size = buf.readInt()
                PacketParticleEmittersS2C(
                    emitterID,
                    emitterUUID,
                    ByteArray(size).also { buf.readBytes(it) },
                    PacketType.fromID(packetTypeID)
                )
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return payloadID
    }
}
