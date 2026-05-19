package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.data.holder.DataHolder
import cn.coostack.cooparticlesapi.data.holder.DataHolderKey
import cn.coostack.cooparticlesapi.data.holder.DataHolderManager
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

class PacketDataHolderS2C(
    val entityId: Int,
    val cacheAllToggle: Boolean,
    val fullSync: Boolean,
    val entries: List<Entry>
) : CustomPacketPayload {
    data class Entry(
        val key: String,
        val type: String,
        val data: ByteArray
    )

    companion object {
        private val id = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "data_holder")
        val payloadID = CustomPacketPayload.Type<PacketDataHolderS2C>(id)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketDataHolderS2C> =
            StreamCodec.of({ buf, packet ->
                buf.writeInt(packet.entityId)
                buf.writeBoolean(packet.cacheAllToggle)
                buf.writeBoolean(packet.fullSync)
                buf.writeInt(packet.entries.size)
                packet.entries.forEach { entry ->
                    buf.writeUtf(entry.key)
                    buf.writeUtf(entry.type)
                    buf.writeInt(entry.data.size)
                    buf.writeBytes(entry.data)
                }
            }, { buf ->
                val entityId = buf.readInt()
                val cacheAllToggle = buf.readBoolean()
                val fullSync = buf.readBoolean()
                val size = buf.readInt()
                val entries = ArrayList<Entry>(size)
                repeat(size) {
                    val key = buf.readUtf()
                    val type = buf.readUtf()
                    val dataSize = buf.readInt()
                    val bytes = ByteArray(dataSize)
                    buf.readBytes(bytes)
                    entries.add(Entry(key, type, bytes))
                }
                PacketDataHolderS2C(entityId, cacheAllToggle, fullSync, entries)
            })

        fun fromEntity(entity: Entity, holder: DataHolder, fullSync: Boolean): PacketDataHolderS2C {
            val entries = holder.snapshotServer().map { (key, value) ->
                Entry(key.id.toString(), key.targetType.name, encodeValue(key.targetType.name, value, entity))
            }
            return PacketDataHolderS2C(entity.id, holder.cacheAllToggle, fullSync, entries)
        }

        private fun encodeValue(type: String, value: Any, entity: Entity): ByteArray {
            val codec = DataHolderManager.getCodecFromID(type)
                ?: throw IllegalStateException("DataHolder codec not registered for type: $type")
            val buf = RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                entity.level().registryAccess()
            )
            @Suppress("UNCHECKED_CAST")
            (codec as StreamCodec<RegistryFriendlyByteBuf, Any>).encode(buf, value)
            val data = ByteArray(buf.readableBytes())
            buf.readBytes(data)
            return data
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }

    fun decodeData(): Map<DataHolderKey<*>, Any> {
        val entity = Minecraft.getInstance().level?.getEntity(entityId)
            ?: return emptyMap()
        return entries.associate { entry ->
            val keyType = runCatching {
                Class.forName(entry.type)
            }.getOrDefault(Any::class.java)
            val key = DataHolderKey.ofRaw(keyType, ResourceLocation.parse(entry.key))
            key to decodeValue(entry.type, entry.data, entity)
        }
    }

    private fun decodeValue(type: String, data: ByteArray, entity: Entity): Any {
        val codec = DataHolderManager.getCodecFromID(type)
            ?: throw IllegalStateException("DataHolder codec not registered for type: $type")
        val buf = RegistryFriendlyByteBuf(
            Unpooled.wrappedBuffer(data),
            Minecraft.getInstance().connection?.registryAccess()
                ?: entity.level().registryAccess()
        )
        @Suppress("UNCHECKED_CAST")
        return (codec as StreamCodec<RegistryFriendlyByteBuf, Any>).decode(buf)
    }
}
