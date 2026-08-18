package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.ClientContext
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectComposition
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingInstance
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegion
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 程序化 terrain mapping 的服务端到客户端同步包。
 *
 * 包只携带区域参数和实例参数，不携带 BlockPos、非空气方块或高度图结果。
 */
@CooAutoRegister
class PacketTerrainMappingS2C() : CooPacket() {
    var operation: Int = REPLACE
    var dimension: ResourceLocation = ResourceLocation.withDefaultNamespace("overworld")
    var instanceId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")
    var mappingId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")
    var pipelineId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")
    var region: CooTerrainMappingRegion? = null
    var startedAt: Long = 0L
    var expiresAt: Long? = null
    var sequence: Long = 0L
    var revision: Long = 0L
    var pausedAt: Long? = null
    var priority: Int = 0
    var composition: CooTerrainEffectComposition = CooTerrainEffectComposition.REPLACE
    var uniforms: Map<String, CooUniformValue> = emptyMap()

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        PACKET_ID
    )

    override fun codec(): StreamCodec<FriendlyByteBuf, PacketTerrainMappingS2C> = CODEC

    override fun onClientReceive(context: ClientContext) {
        context.client.execute {
            when (operation) {
                REPLACE -> CooTerrainMappingRegistry.install(toInstance())
                UPDATE_UNIFORMS -> CooTerrainMappingRegistry.updateUniforms(
                    dimension,
                    instanceId,
                    revision,
                    uniforms
                )
                REMOVE -> CooTerrainMappingRegistry.remove(dimension, instanceId, revision)
            }
        }
    }

    private fun toInstance(): CooTerrainMappingInstance = CooTerrainMappingInstance(
        instanceId,
        mappingId,
        dimension,
        requireNotNull(region) { "Terrain mapping replace packet has no region" },
        uniforms,
        priority,
        composition,
        startedAt,
        expiresAt,
        sequence,
        revision,
        pausedAt
    )

    companion object {
        private const val PACKET_ID = "terrain_mapping_s2c"
        private const val REPLACE = 0
        private const val UPDATE_UNIFORMS = 1
        private const val REMOVE = 2
        private val CODEC: StreamCodec<FriendlyByteBuf, PacketTerrainMappingS2C> =
            StreamCodec.of(::encode, ::decode)

        internal fun replace(instance: CooTerrainMappingInstance, pipelineId: ResourceLocation): PacketTerrainMappingS2C =
            PacketTerrainMappingS2C().also {
                it.operation = REPLACE
                it.dimension = instance.dimension
                it.instanceId = instance.instanceId
                it.mappingId = instance.mappingId
                it.pipelineId = pipelineId
                it.region = instance.region
                it.startedAt = instance.startedAt
                it.expiresAt = instance.expiresAt
                it.sequence = instance.sequence
                it.revision = instance.revision
                it.pausedAt = instance.pausedAt
                it.priority = instance.priority
                it.composition = instance.composition
                it.uniforms = instance.uniforms
            }

        internal fun updateUniforms(instance: CooTerrainMappingInstance): PacketTerrainMappingS2C =
            PacketTerrainMappingS2C().also {
                it.operation = UPDATE_UNIFORMS
                it.dimension = instance.dimension
                it.instanceId = instance.instanceId
                it.revision = instance.revision
                it.uniforms = instance.uniforms
            }

        internal fun remove(
            dimension: ResourceLocation,
            instanceId: ResourceLocation,
            revision: Long
        ): PacketTerrainMappingS2C = PacketTerrainMappingS2C().also {
            it.operation = REMOVE
            it.dimension = dimension
            it.instanceId = instanceId
            it.revision = revision
        }

        private fun encode(buffer: FriendlyByteBuf, packet: PacketTerrainMappingS2C) {
            buffer.writeByte(packet.operation)
            buffer.writeResourceLocation(packet.dimension)
            buffer.writeResourceLocation(packet.instanceId)
            buffer.writeVarLong(packet.revision)
            when (packet.operation) {
                REPLACE -> {
                    buffer.writeResourceLocation(packet.mappingId)
                    buffer.writeResourceLocation(packet.pipelineId)
                    buffer.writeVarLong(packet.startedAt)
                    buffer.writeVarLong(packet.sequence)
                    buffer.writeVarInt(packet.priority)
                    buffer.writeVarInt(packet.composition.ordinal)
                    buffer.writeBoolean(packet.expiresAt != null)
                    packet.expiresAt?.let(buffer::writeVarLong)
                    buffer.writeBoolean(packet.pausedAt != null)
                    packet.pausedAt?.let(buffer::writeVarLong)
                    packet.region?.encode(buffer) ?: error("Terrain mapping replace packet has no region")
                    writeUniforms(buffer, packet.uniforms)
                }
                UPDATE_UNIFORMS -> writeUniforms(buffer, packet.uniforms)
            }
        }

        private fun decode(buffer: FriendlyByteBuf): PacketTerrainMappingS2C {
            val packet = PacketTerrainMappingS2C()
            packet.operation = buffer.readUnsignedByte().toInt()
            packet.dimension = buffer.readResourceLocation()
            packet.instanceId = buffer.readResourceLocation()
            packet.revision = buffer.readVarLong()
            when (packet.operation) {
                REPLACE -> {
                    packet.mappingId = buffer.readResourceLocation()
                    packet.pipelineId = buffer.readResourceLocation()
                    packet.startedAt = buffer.readVarLong()
                    packet.sequence = buffer.readVarLong()
                    packet.priority = buffer.readVarInt()
                    packet.composition = CooTerrainEffectComposition.fromWire(buffer.readVarInt())
                    packet.expiresAt = if (buffer.readBoolean()) buffer.readVarLong() else null
                    packet.pausedAt = if (buffer.readBoolean()) buffer.readVarLong() else null
                    packet.region = CooTerrainMappingRegion.decode(buffer)
                    packet.uniforms = readUniforms(buffer)
                }
                UPDATE_UNIFORMS -> packet.uniforms = readUniforms(buffer)
            }
            return packet
        }

        private fun writeUniforms(buffer: FriendlyByteBuf, uniforms: Map<String, CooUniformValue>) {
            buffer.writeVarInt(uniforms.size)
            uniforms.forEach { (name, value) ->
                buffer.writeUtf(name)
                CooUniformValue.STREAM_CODEC.encode(buffer, value)
            }
        }

        private fun readUniforms(buffer: FriendlyByteBuf): Map<String, CooUniformValue> {
            val count = buffer.readVarInt()
            val result = LinkedHashMap<String, CooUniformValue>(count)
            repeat(count) {
                result[buffer.readUtf()] = CooUniformValue.STREAM_CODEC.decode(buffer)
            }
            return result
        }
    }
}
