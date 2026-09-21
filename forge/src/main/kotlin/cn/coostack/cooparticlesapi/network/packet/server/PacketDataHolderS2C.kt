package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

class PacketDataHolderS2C(
    val entityId: Int,
    val cacheAllToggle: Boolean,
    val fullSync: Boolean,
    val entries: List<Entry>
) {
    data class Entry(
        val key: String,
        val type: String,
        val data: ByteArray
    )

    companion object {
        private val id = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "data_holder")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "data_holder")
        val CODEC = ForgeStreamCodec.of({ buf, packet ->
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

        fun fromEntity(entity: net.minecraft.world.entity.Entity, holder: cn.coostack.cooparticlesapi.data.holder.DataHolder, fullSync: Boolean): PacketDataHolderS2C {
            val entries = holder.snapshotServer().map { (key, value) ->
                Entry(key.id.toString(), key.targetType.name, encodeValue(key.targetType.name, value, entity))
            }
            return PacketDataHolderS2C(entity.id, holder.cacheAllToggle, fullSync, entries)
        }

        private fun encodeValue(type: String, value: Any, entity: net.minecraft.world.entity.Entity): ByteArray {
            val codec = cn.coostack.cooparticlesapi.data.holder.DataHolderManager.getCodecFromID(type)
                ?: throw IllegalStateException("DataHolder codec not registered for type: $type")
            val buf = PacketByteBuf(io.netty.buffer.Unpooled.buffer())
            @Suppress("UNCHECKED_CAST")
            (codec as CommonStreamCodec<Any>).encode(buf, value)
            val data = ByteArray(buf.readableBytes())
            buf.readBytes(data)
            buf.release()
            return data
        }
    }

    fun decodeData(): Map<cn.coostack.cooparticlesapi.data.holder.DataHolderKey<*>, Any> {
        val entity = net.minecraft.client.Minecraft.getInstance().level?.getEntity(entityId)
            ?: return emptyMap()
        return entries.associate { entry ->
            val keyType = runCatching {
                Class.forName(entry.type)
            }.getOrDefault(Any::class.java)
            val key = cn.coostack.cooparticlesapi.data.holder.DataHolderKey.ofRaw(keyType, net.minecraft.resources.ResourceLocation.parse(entry.key))
            key to decodeValue(entry.type, entry.data, entity)
        }
    }

    private fun decodeValue(type: String, data: ByteArray, entity: net.minecraft.world.entity.Entity): Any {
        val codec = cn.coostack.cooparticlesapi.data.holder.DataHolderManager.getCodecFromID(type)
            ?: throw IllegalStateException("DataHolder codec not registered for type: $type")
        val buf = PacketByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data))
        @Suppress("UNCHECKED_CAST")
        return (codec as CommonStreamCodec<Any>).decode(buf)
    }
}
