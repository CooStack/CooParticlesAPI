package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.ClientContext
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectGroupSnapshot
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectRegistry
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/** 按效果组同步批量位置；位置和 uniform 的局部更新不会重发整组。 */
@CooAutoRegister
class PacketTerrainEffectGroupS2C() : CooPacket() {
    var operation: Int = REPLACE
    var dimension: ResourceLocation = ResourceLocation.withDefaultNamespace("overworld")
    var groupId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")
    var pipelineId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")
    var startedAt: Long = 0L
    var expiresAt: Long? = null
    var positions: Map<BlockPos, Long> = emptyMap()
    var uniforms: Map<String, CooUniformValue> = emptyMap()

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        PACKET_ID
    )

    override fun codec(): StreamCodec<FriendlyByteBuf, PacketTerrainEffectGroupS2C> = CODEC

    override fun onClientReceive(context: ClientContext) {
        context.client.execute {
            when (operation) {
                REPLACE -> CooTerrainEffectRegistry.install(
                    CooTerrainEffectGroupSnapshot(
                        dimension = dimension,
                        id = groupId,
                        pipelineId = pipelineId,
                        startedAt = startedAt,
                        expiresAt = expiresAt,
                        activations = positions,
                        uniforms = uniforms
                    )
                )
                APPEND -> CooTerrainEffectRegistry.append(dimension, groupId, positions)
                REMOVE_POSITIONS -> CooTerrainEffectRegistry.removePositions(dimension, groupId, positions.keys)
                UPDATE_UNIFORMS -> CooTerrainEffectRegistry.updateUniforms(dimension, groupId, uniforms)
                REMOVE_GROUP -> CooTerrainEffectRegistry.remove(dimension, groupId)
            }
        }
    }

    companion object {
        private const val PACKET_ID = "terrain_effect_group_s2c"
        private const val REPLACE = 0
        private const val APPEND = 1
        private const val REMOVE_POSITIONS = 2
        private const val UPDATE_UNIFORMS = 3
        private const val REMOVE_GROUP = 4

        private const val FLOAT = 0
        private const val INT = 1
        private const val VEC2 = 2
        private const val VEC3 = 3
        private const val VEC4 = 4

        private val CODEC: StreamCodec<FriendlyByteBuf, PacketTerrainEffectGroupS2C> =
            StreamCodec.of(::encode, ::decode)

        internal fun replace(snapshot: CooTerrainEffectGroupSnapshot): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = REPLACE
                it.dimension = snapshot.dimension
                it.groupId = snapshot.id
                it.pipelineId = snapshot.pipelineId
                it.startedAt = snapshot.startedAt
                it.expiresAt = snapshot.expiresAt
                it.positions = snapshot.activations
                it.uniforms = snapshot.uniforms
            }
        }

        internal fun append(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            positions: Map<BlockPos, Long>
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = APPEND
                it.dimension = dimension
                it.groupId = groupId
                it.positions = positions
            }
        }

        internal fun remove(
            dimension: ResourceLocation,
            groupId: ResourceLocation
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = REMOVE_GROUP
                it.dimension = dimension
                it.groupId = groupId
            }
        }

        internal fun removePositions(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            positions: Set<BlockPos>
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = REMOVE_POSITIONS
                it.dimension = dimension
                it.groupId = groupId
                it.positions = positions.associateWith { 0L }
            }
        }

        internal fun updateUniforms(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            uniforms: Map<String, CooUniformValue>
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = UPDATE_UNIFORMS
                it.dimension = dimension
                it.groupId = groupId
                it.uniforms = uniforms
            }
        }

        private fun encode(buffer: FriendlyByteBuf, packet: PacketTerrainEffectGroupS2C) {
            buffer.writeByte(packet.operation)
            buffer.writeResourceLocation(packet.dimension)
            buffer.writeResourceLocation(packet.groupId)
            when (packet.operation) {
                REPLACE -> {
                    buffer.writeResourceLocation(packet.pipelineId)
                    buffer.writeVarLong(packet.startedAt)
                    buffer.writeBoolean(packet.expiresAt != null)
                    packet.expiresAt?.let(buffer::writeVarLong)
                    writeUniforms(buffer, packet.uniforms)
                    writeTimedPositions(buffer, packet.positions)
                }
                APPEND -> writeTimedPositions(buffer, packet.positions)
                REMOVE_POSITIONS -> writePositions(buffer, packet.positions.keys)
                UPDATE_UNIFORMS -> writeUniforms(buffer, packet.uniforms)
            }
        }

        private fun decode(buffer: FriendlyByteBuf): PacketTerrainEffectGroupS2C {
            val packet = PacketTerrainEffectGroupS2C()
            packet.operation = buffer.readUnsignedByte().toInt()
            packet.dimension = buffer.readResourceLocation()
            packet.groupId = buffer.readResourceLocation()
            when (packet.operation) {
                REPLACE -> {
                    packet.pipelineId = buffer.readResourceLocation()
                    packet.startedAt = buffer.readVarLong()
                    packet.expiresAt = if (buffer.readBoolean()) buffer.readVarLong() else null
                    packet.uniforms = readUniforms(buffer)
                    packet.positions = readTimedPositions(buffer)
                }
                APPEND -> packet.positions = readTimedPositions(buffer)
                REMOVE_POSITIONS -> packet.positions = readPositions(buffer).associateWith { 0L }
                UPDATE_UNIFORMS -> packet.uniforms = readUniforms(buffer)
            }
            return packet
        }

        private fun writeTimedPositions(buffer: FriendlyByteBuf, positions: Map<BlockPos, Long>) {
            val entries = positions.entries.toList()
            buffer.writeVarInt(entries.size)
            if (entries.isEmpty()) return
            val origin = entries.first().key
            val activationBase = entries.first().value
            buffer.writeBlockPos(origin)
            buffer.writeVarLong(activationBase)
            entries.forEach { (position, activation) ->
                buffer.writeVarInt(zigZag(position.x - origin.x))
                buffer.writeVarInt(zigZag(position.y - origin.y))
                buffer.writeVarInt(zigZag(position.z - origin.z))
                buffer.writeVarLong(zigZag(activation - activationBase))
            }
        }

        private fun readTimedPositions(buffer: FriendlyByteBuf): Map<BlockPos, Long> {
            val count = buffer.readVarInt()
            if (count == 0) return emptyMap()
            val origin = buffer.readBlockPos()
            val activationBase = buffer.readVarLong()
            val result = LinkedHashMap<BlockPos, Long>(count)
            repeat(count) {
                val position = origin.offset(
                    unZigZag(buffer.readVarInt()),
                    unZigZag(buffer.readVarInt()),
                    unZigZag(buffer.readVarInt())
                )
                result[position] = activationBase + unZigZag(buffer.readVarLong())
            }
            return result
        }

        private fun writePositions(buffer: FriendlyByteBuf, positions: Collection<BlockPos>) {
            val entries = positions.toList()
            buffer.writeVarInt(entries.size)
            if (entries.isEmpty()) return
            val origin = entries.first()
            buffer.writeBlockPos(origin)
            entries.forEach { position ->
                buffer.writeVarInt(zigZag(position.x - origin.x))
                buffer.writeVarInt(zigZag(position.y - origin.y))
                buffer.writeVarInt(zigZag(position.z - origin.z))
            }
        }

        private fun readPositions(buffer: FriendlyByteBuf): Set<BlockPos> {
            val count = buffer.readVarInt()
            if (count == 0) return emptySet()
            val origin = buffer.readBlockPos()
            return buildSet(count) {
                repeat(count) {
                    add(
                        origin.offset(
                            unZigZag(buffer.readVarInt()),
                            unZigZag(buffer.readVarInt()),
                            unZigZag(buffer.readVarInt())
                        )
                    )
                }
            }
        }

        private fun writeUniforms(buffer: FriendlyByteBuf, uniforms: Map<String, CooUniformValue>) {
            buffer.writeVarInt(uniforms.size)
            uniforms.forEach { (name, value) ->
                buffer.writeUtf(name)
                when (value) {
                    is CooUniformValue.FloatValue -> {
                        buffer.writeByte(FLOAT)
                        buffer.writeFloat(value.value)
                    }
                    is CooUniformValue.IntValue -> {
                        buffer.writeByte(INT)
                        buffer.writeVarInt(zigZag(value.value))
                    }
                    is CooUniformValue.Vec2Value -> {
                        buffer.writeByte(VEC2)
                        buffer.writeFloat(value.x)
                        buffer.writeFloat(value.y)
                    }
                    is CooUniformValue.Vec3Value -> {
                        buffer.writeByte(VEC3)
                        buffer.writeFloat(value.x)
                        buffer.writeFloat(value.y)
                        buffer.writeFloat(value.z)
                    }
                    is CooUniformValue.Vec4Value -> {
                        buffer.writeByte(VEC4)
                        buffer.writeFloat(value.x)
                        buffer.writeFloat(value.y)
                        buffer.writeFloat(value.z)
                        buffer.writeFloat(value.w)
                    }
                }
            }
        }

        private fun readUniforms(buffer: FriendlyByteBuf): Map<String, CooUniformValue> {
            val count = buffer.readVarInt()
            val result = LinkedHashMap<String, CooUniformValue>(count)
            repeat(count) {
                val name = buffer.readUtf()
                val value = when (buffer.readUnsignedByte().toInt()) {
                    FLOAT -> CooUniformValue.FloatValue(buffer.readFloat())
                    INT -> CooUniformValue.IntValue(unZigZag(buffer.readVarInt()))
                    VEC2 -> CooUniformValue.Vec2Value(buffer.readFloat(), buffer.readFloat())
                    VEC3 -> CooUniformValue.Vec3Value(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
                    VEC4 -> CooUniformValue.Vec4Value(
                        buffer.readFloat(),
                        buffer.readFloat(),
                        buffer.readFloat(),
                        buffer.readFloat()
                    )
                    else -> error("Unknown terrain effect uniform type")
                }
                result[name] = value
            }
            return result
        }

        private fun zigZag(value: Int): Int = (value shl 1) xor (value shr 31)

        private fun unZigZag(value: Int): Int = (value ushr 1) xor -(value and 1)

        private fun zigZag(value: Long): Long = (value shl 1) xor (value shr 63)

        private fun unZigZag(value: Long): Long = (value ushr 1) xor -(value and 1L)
    }
}
