package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectComposition
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectGroupSnapshot
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectRegistry
import net.minecraft.core.BlockPos
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

class PacketTerrainEffectGroupS2C {
    var operation: Int = REPLACE
    var dimension: ResourceLocation = ResourceLocation.withDefaultNamespace("overworld")
    var groupId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")
    var pipelineId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")
    var startedAt: Long = 0L
    var expiresAt: Long? = null
    var sequence: Long = 0L
    var revision: Long = 0L
    var priority: Int = 0
    var composition: CooTerrainEffectComposition = CooTerrainEffectComposition.REPLACE
    var positions: Map<BlockPos, Long> = emptyMap()
    var uniforms: Map<String, CooUniformValue> = emptyMap()

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        PACKET_ID
    )

    override fun codec(): CommonStreamCodec<PacketTerrainEffectGroupS2C> = CODEC

    override fun onClientReceive(context: cn.coostack.cooparticlesapi.network.packet.api.ClientContext) {
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
                        uniforms = uniforms,
                        sequence = sequence,
                        revision = revision,
                        priority = priority,
                        composition = composition
                    )
                )
                APPEND -> CooTerrainEffectRegistry.append(dimension, groupId, revision, positions)
                REMOVE_POSITIONS -> CooTerrainEffectRegistry.removePositions(
                    dimension,
                    groupId,
                    revision,
                    positions.keys
                )
                UPDATE_UNIFORMS -> CooTerrainEffectRegistry.updateUniforms(
                    dimension,
                    groupId,
                    revision,
                    uniforms
                )
                REMOVE_GROUP -> CooTerrainEffectRegistry.remove(dimension, groupId, revision)
                UPDATE_GROUP_OPTIONS -> CooTerrainEffectRegistry.updateOrdering(
                    dimension,
                    groupId,
                    revision,
                    priority,
                    composition
                )
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
        private const val UPDATE_GROUP_OPTIONS = 5

        val CODEC: CommonStreamCodec<PacketTerrainEffectGroupS2C> = CommonStreamCodec.of(::encode, ::decode)

        internal fun replace(snapshot: CooTerrainEffectGroupSnapshot): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = REPLACE
                it.dimension = snapshot.dimension
                it.groupId = snapshot.id
                it.pipelineId = snapshot.pipelineId
                it.startedAt = snapshot.startedAt
                it.expiresAt = snapshot.expiresAt
                it.sequence = snapshot.sequence
                it.revision = snapshot.revision
                it.priority = snapshot.priority
                it.composition = snapshot.composition
                it.positions = snapshot.activations
                it.uniforms = snapshot.uniforms
            }
        }

        internal fun updateOrdering(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            revision: Long,
            priority: Int,
            composition: CooTerrainEffectComposition
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = UPDATE_GROUP_OPTIONS
                it.dimension = dimension
                it.groupId = groupId
                it.revision = revision
                it.priority = priority
                it.composition = composition
            }
        }

        internal fun append(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            revision: Long,
            positions: Map<BlockPos, Long>
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = APPEND
                it.dimension = dimension
                it.groupId = groupId
                it.revision = revision
                it.positions = positions
            }
        }

        internal fun remove(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            revision: Long
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = REMOVE_GROUP
                it.dimension = dimension
                it.groupId = groupId
                it.revision = revision
            }
        }

        internal fun removePositions(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            revision: Long,
            positions: Set<BlockPos>
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = REMOVE_POSITIONS
                it.dimension = dimension
                it.groupId = groupId
                it.revision = revision
                it.positions = positions.associateWith { 0L }
            }
        }

        internal fun updateUniforms(
            dimension: ResourceLocation,
            groupId: ResourceLocation,
            revision: Long,
            uniforms: Map<String, CooUniformValue>
        ): PacketTerrainEffectGroupS2C {
            return PacketTerrainEffectGroupS2C().also {
                it.operation = UPDATE_UNIFORMS
                it.dimension = dimension
                it.groupId = groupId
                it.revision = revision
                it.uniforms = uniforms
            }
        }

        private fun encode(buffer: PacketByteBuf, packet: PacketTerrainEffectGroupS2C) {
            buffer.writeByte(packet.operation)
            buffer.writeResourceLocation(packet.dimension)
            buffer.writeResourceLocation(packet.groupId)
            buffer.writeVarLong(packet.revision)
            when (packet.operation) {
                REPLACE -> {
                    buffer.writeResourceLocation(packet.pipelineId)
                    buffer.writeVarLong(packet.startedAt)
                    buffer.writeVarLong(packet.sequence)
                    buffer.writeVarInt(packet.priority)
                    buffer.writeVarInt(packet.composition.ordinal)
                    buffer.writeBoolean(packet.expiresAt != null)
                    packet.expiresAt?.let(buffer::writeVarLong)
                    writeUniforms(buffer, packet.uniforms)
                    writeTimedPositions(buffer, packet.positions)
                }
                APPEND -> writeTimedPositions(buffer, packet.positions)
                REMOVE_POSITIONS -> writePositions(buffer, packet.positions.keys)
                UPDATE_UNIFORMS -> writeUniforms(buffer, packet.uniforms)
                UPDATE_GROUP_OPTIONS -> {
                    buffer.writeVarInt(packet.priority)
                    buffer.writeVarInt(packet.composition.ordinal)
                }
            }
        }

        private fun decode(buffer: PacketByteBuf): PacketTerrainEffectGroupS2C {
            val packet = PacketTerrainEffectGroupS2C()
            packet.operation = buffer.readUnsignedByte().toInt()
            packet.dimension = buffer.readResourceLocation()
            packet.groupId = buffer.readResourceLocation()
            packet.revision = buffer.readVarLong()
            when (packet.operation) {
                REPLACE -> {
                    packet.pipelineId = buffer.readResourceLocation()
                    packet.startedAt = buffer.readVarLong()
                    packet.sequence = buffer.readVarLong()
                    packet.priority = buffer.readVarInt()
                    packet.composition = CooTerrainEffectComposition.fromWire(buffer.readVarInt())
                    packet.expiresAt = if (buffer.readBoolean()) buffer.readVarLong() else null
                    packet.uniforms = readUniforms(buffer)
                    packet.positions = readTimedPositions(buffer)
                }
                APPEND -> packet.positions = readTimedPositions(buffer)
                REMOVE_POSITIONS -> packet.positions = readPositions(buffer).associateWith { 0L }
                UPDATE_UNIFORMS -> packet.uniforms = readUniforms(buffer)
                UPDATE_GROUP_OPTIONS -> {
                    packet.priority = buffer.readVarInt()
                    packet.composition = CooTerrainEffectComposition.fromWire(buffer.readVarInt())
                }
            }
            return packet
        }

        private fun writeTimedPositions(buffer: PacketByteBuf, positions: Map<BlockPos, Long>) {
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

        private fun readTimedPositions(buffer: PacketByteBuf): Map<BlockPos, Long> {
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

        private fun writePositions(buffer: PacketByteBuf, positions: Collection<BlockPos>) {
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

        private fun readPositions(buffer: PacketByteBuf): Set<BlockPos> {
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

        private fun writeUniforms(buffer: PacketByteBuf, uniforms: Map<String, CooUniformValue>) {
            buffer.writeVarInt(uniforms.size)
            uniforms.forEach { (name, value) ->
                buffer.writeUtf(name)
                CooUniformValue.STREAM_CODEC.encode(buffer, value)
            }
        }

        private fun readUniforms(buffer: PacketByteBuf): Map<String, CooUniformValue> {
            val count = buffer.readVarInt()
            val result = LinkedHashMap<String, CooUniformValue>(count)
            repeat(count) {
                val name = buffer.readUtf()
                result[name] = CooUniformValue.STREAM_CODEC.decode(buffer)
            }
            return result
        }

        private fun zigZag(value: Int): Int = (value shl 1) xor (value shr 31)
        private fun unZigZag(value: Int): Int = (value ushr 1) xor -(value and 1)
        private fun zigZag(value: Long): Long = (value shl 1) xor (value shr 63)
        private fun unZigZag(value: Long): Long = (value ushr 1) xor -(value and 1L)
    }
}
