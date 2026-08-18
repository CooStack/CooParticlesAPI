package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.ClientContext
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectComposition
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectGroupSnapshot
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectRegistry
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 从服务端向客户端同步一个地形效果组的替换或增量更新。
 *
 * 协议支持完整替换、追加位置、移除位置、替换 uniform 和删除整组。位置使用相对首坐标的 ZigZag
 * 差值编码，生效时间使用相对首个绝对 tick 的差值编码，避免大批连续方块重复传输完整坐标和时间。
 * 包实例只由 companion object 中的工厂创建，并由
 * [cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectManager] 发送。
 */
@CooAutoRegister
class PacketTerrainEffectGroupS2C() : CooPacket() {
    /** 当前包的协议操作类型。 */
    var operation: Int = REPLACE

    /** 目标效果组所属的维度 ID。 */
    var dimension: ResourceLocation = ResourceLocation.withDefaultNamespace("overworld")

    /** 目标效果组 ID。 */
    var groupId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")

    /** 完整替换操作引用的方块 Pipeline ID。 */
    var pipelineId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "empty")

    /** 完整替换操作中组开始的绝对游戏 tick。 */
    var startedAt: Long = 0L

    /** 完整替换操作中组到期的绝对游戏 tick；`null` 表示持久组。 */
    var expiresAt: Long? = null

    /** 完整替换操作中用于重叠组排序的服务端序号。 */
    var sequence: Long = 0L

    /** 用于丢弃重复或乱序更新的单调协议版本号。 */
    var revision: Long = 0L

    /** 完整替换和 options 更新中的有符号绘制优先级。 */
    var priority: Int = 0

    /** 完整替换和 options 更新中的合成方式。 */
    var composition: CooTerrainEffectComposition = CooTerrainEffectComposition.REPLACE

    /**
     * 操作涉及的位置数据。
     *
     * 完整替换和追加操作中，键为方块坐标，值为绝对生效 tick；移除位置操作只使用键。
     */
    var positions: Map<BlockPos, Long> = emptyMap()

    /** 键为 shader uniform 名称，值为完整替换或 uniform 更新携带的数据。 */
    var uniforms: Map<String, CooUniformValue> = emptyMap()

    /** @return 地形效果组服务端到客户端协议的固定资源 ID。 */
    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        PACKET_ID
    )

    /** @return 编解码本协议全部操作类型的 StreamCodec。 */
    override fun codec(): StreamCodec<FriendlyByteBuf, PacketTerrainEffectGroupS2C> = CODEC

    /**
     * 在客户端主线程把本包更新应用到 [CooTerrainEffectRegistry]。
     *
     * @param context 当前客户端网络处理上下文
     */
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

    /** 创建各协议操作并实现紧凑的坐标、时间和 uniform 编解码。 */
    companion object {
        private const val PACKET_ID = "terrain_effect_group_s2c"
        private const val REPLACE = 0
        private const val APPEND = 1
        private const val REMOVE_POSITIONS = 2
        private const val UPDATE_UNIFORMS = 3
        private const val REMOVE_GROUP = 4
        private const val UPDATE_GROUP_OPTIONS = 5

        private val CODEC: StreamCodec<FriendlyByteBuf, PacketTerrainEffectGroupS2C> =
            StreamCodec.of(::encode, ::decode)

        /**
         * 创建完整替换效果组的协议包。
         *
         * @param snapshot 要同步的完整服务端效果组快照
         * @return 携带 Pipeline、时间、位置和 uniform 的替换包
         */
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

        /**
         * 创建只追加位置和绝对生效时间的协议包。
         *
         * @param groupId 组 ID
         * @param revision 本次更新的单调协议版本号
         * @param positions 键为新增方块坐标，值为绝对生效 tick
         * @return 位置追加包
         */
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

        /**
         * 创建删除整个效果组的协议包。
         *
         * @param dimension 组所属维度 ID
         * @param groupId 组 ID
         * @param revision 本次更新的单调协议版本号
         * @return 整组删除包
         */
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

        /**
         * 创建只删除组内指定位置的协议包。
         *
         * @param dimension 组所属维度 ID
         * @param groupId 组 ID
         * @param revision 本次更新的单调协议版本号
         * @param positions 要删除的方块坐标
         * @return 位置删除包
         */
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

        /**
         * 创建替换整组 uniform 集合的协议包。
         *
         * @param dimension 组所属维度 ID
         * @param groupId 组 ID
         * @param revision 本次更新的单调协议版本号
         * @param uniforms 键为 shader uniform 名称，值为新的完整 uniform 数据
         * @return uniform 更新包
         */
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

        /** 按操作类型写入公共头和对应载荷。 */
        private fun encode(buffer: FriendlyByteBuf, packet: PacketTerrainEffectGroupS2C) {
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

        /** 从公共头中的操作类型选择对应载荷读取流程。 */
        private fun decode(buffer: FriendlyByteBuf): PacketTerrainEffectGroupS2C {
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

        /**
         * 以首项为坐标和时间基准写入一组位置。
         *
         * @param buffer 目标网络缓冲
         * @param positions 键为方块坐标，值为该位置的绝对生效 tick
         */
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

        /**
         * 读取以首项为基准编码的位置和绝对生效时间。
         *
         * @param buffer 源网络缓冲
         * @return 键为方块坐标、值为绝对生效 tick 的映射
         */
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

        /** 以首个方块坐标为基准写入无需附带时间的位置集合。 */
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

        /** @return 从相对坐标差值还原出的方块位置集合。 */
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

        /**
         * 写入带类型标记的 uniform 映射。
         *
         * @param buffer 目标网络缓冲
         * @param uniforms 键为 uniform 名称，值为浮点数、整数或向量数据
         */
        private fun writeUniforms(buffer: FriendlyByteBuf, uniforms: Map<String, CooUniformValue>) {
            buffer.writeVarInt(uniforms.size)
            uniforms.forEach { (name, value) ->
                buffer.writeUtf(name)
                CooUniformValue.STREAM_CODEC.encode(buffer, value)
            }
        }

        /**
         * 读取带类型标记的 uniform 映射。
         *
         * @param buffer 源网络缓冲
         * @return 键为 uniform 名称、值为对应类型数据的映射
         */
        private fun readUniforms(buffer: FriendlyByteBuf): Map<String, CooUniformValue> {
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
