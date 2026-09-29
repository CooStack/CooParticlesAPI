package cn.coostack.cooparticlesapi.cparticle.path

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

/**
 * # 可序列化的路径几何
 *
 * 描述一条路径的**全部形状数据**：任意数量的控制点、每点的入/出控制柄、段类型与闭合属性。
 * 它不含任何 GPU 句柄或槽位号，因此可以直接放进发射器的 `@CodecField`，随发射器一起同步到客户端。
 *
 * ## 为什么需要它
 * 路径几何是**任意数据**：空间贝塞尔曲线有无数种，控制点数量也不固定。因此：
 * - 不能用枚举或编号来代表「用哪条路径」——那只能覆盖有限几种预设。
 * - [CParticlePathSlot] 是进程内的本地句柄（槽位号 + 图层世代），**不能跨网络传输**。
 *
 * 正确做法就是本类型：几何作为数据随持有者（发射器 / 实体）同步，每一侧用它建立**自己的**本地路径槽位。
 * 两侧从同一份数据建立，因此形状一致；而几何有多少种，就能表达多少种。
 *
 * ## 数据格式
 * 帧格式为：`点数量（varint）` → 每点 `位置/入控制柄/出控制柄` 三个 [Vec3] → `段类型（byte）` → `是否闭合（boolean）`。
 * 控制柄是**相对位置的偏移**，因此整体平移几何时不需要改写控制柄。
 *
 * @property points 控制点序列，至少两个；闭合路径不需要手动补接缝点
 * @property segmentType 段类型；整条路径共用一种
 * @property closed 几何是否首尾相连
 */
data class CParticlePathGeometry(
    val points: List<CParticlePathPoint>,
    val segmentType: CParticlePathSegmentType = CParticlePathSegmentType.LINEAR,
    val closed: Boolean = false,
) {
    init {
        require(points.size >= 2) { "Particle path geometry needs at least two points, got ${points.size}" }
        require(points.size <= MAX_POINTS) {
            "Particle path geometry holds at most $MAX_POINTS points, got ${points.size}"
        }
    }

    /** 控制点数量。 */
    val pointCount: Int
        get() = points.size

    /**
     * 建立一份**独立**的路径定义。
     *
     * 返回的是新对象，因此调用方对它的修改（例如动态改点）不会影响本几何，
     * 也不会影响其他从同一几何建立路径的使用者。
     *
     * @return 可用于建立本地路径槽位的定义
     */
    fun toDefinition(): CParticlePathDefinition =
        CParticlePathDefinition(points, segmentType, closed)

    companion object {
        /**
         * 控制点数量上限。
         *
         * 解码时会校验，避免损坏或恶意的数据包让客户端按一个巨大的数量分配内存。
         * 这个上限远高于实际需要（路径负载本身也受 [CooPathLayer.MAX_FLOATS_PER_PATH] 约束）。
         */
        const val MAX_POINTS = 4096

        /**
         * 普通网络缓冲上的编解码器。
         *
         * 只使用 `writeVec3` / `writeVarInt` 等基础操作，**不依赖注册表上下文**，
         * 因此按 [cn.coostack.cooparticlesapi.annotations.codec.CodecHelper.register] 注册，
         * 而不是 `registerRegistry`。
         */
        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CParticlePathGeometry> = StreamCodec.of(
            { buf, geometry ->
                buf.writeVarInt(geometry.points.size)
                geometry.points.forEach { point ->
                    buf.writeVec3(point.position)
                    buf.writeVec3(point.inHandle)
                    buf.writeVec3(point.outHandle)
                }
                buf.writeByte(geometry.segmentType.wireValue)
                buf.writeBoolean(geometry.closed)
            },
            { buf ->
                val count = buf.readVarInt()
                require(count in 2..MAX_POINTS) {
                    "Particle path geometry point count out of range: $count"
                }
                val points = ArrayList<CParticlePathPoint>(count)
                repeat(count) {
                    points.add(
                        CParticlePathPoint(
                            position = buf.readVec3(),
                            inHandle = buf.readVec3(),
                            outHandle = buf.readVec3(),
                        ),
                    )
                }
                val wire = buf.readUnsignedByte().toInt()
                val segmentType = CParticlePathSegmentType.entries.firstOrNull { it.wireValue == wire }
                    ?: CParticlePathSegmentType.LINEAR
                CParticlePathGeometry(points, segmentType, buf.readBoolean())
            },
        )

        /**
         * 由一条已有的本地路径定义取几何快照。
         *
         * 用于把手工建立的路径转成可同步数据；控制柄与段类型都会保留。
         *
         * @param definition 来源定义
         * @return 独立的几何副本
         */
        @JvmStatic
        fun of(definition: CParticlePathDefinition): CParticlePathGeometry =
            CParticlePathGeometry(definition.points, definition.segmentType, definition.closed)

        /**
         * 建立一条只有位置的折线几何，控制柄为零。
         *
         * @param positions 折线顶点，至少两个
         * @param closed 是否首尾相连
         */
        @JvmStatic
        fun polyline(positions: List<Vec3>, closed: Boolean = false): CParticlePathGeometry =
            CParticlePathGeometry(CParticlePathPoint.polyline(positions), CParticlePathSegmentType.LINEAR, closed)
    }
}
