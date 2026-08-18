package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 服务端和客户端共享的程序化区域 tagged union。
 *
 * 区域参数会原样同步到客户端；客户端渲染器只在片元阶段用世界坐标判断成员关系，绝不查询世界或枚举方块。
 */
sealed interface CooTerrainMappingRegion {
    /** 当前区域的稳定 wire 类型。 */
    val type: CooTerrainMappingRegionType

    /** 判断一个世界坐标是否属于区域。 */
    fun contains(position: Vec3): Boolean

    /** 将版本化 tagged union 写入网络缓冲区。 */
    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeVarInt(1)
        buffer.writeResourceLocation(type.id)
        when (this) {
            is Sphere -> {
                buffer.writeDouble(center.x)
                buffer.writeDouble(center.y)
                buffer.writeDouble(center.z)
                buffer.writeDouble(radius)
            }
        }
    }

    /** 返回可用于服务端候选筛选的轴对齐方块边界。 */
    fun bounds(): CooTerrainMappingBounds

    /** 判断一个包含式 section 包围盒是否与区域相交。 */
    fun intersects(
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int
    ): Boolean = bounds().intersects(minX, minY, minZ, maxX, maxY, maxZ)


    /** 球形空间选择域。 */
    data class Sphere(val center: Vec3, val radius: Double) : CooTerrainMappingRegion {
        init {
            require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite()) {
                "Terrain mapping sphere center must be finite"
            }
            require(radius.isFinite() && radius > 0.0) {
                "Terrain mapping sphere radius must be finite and greater than zero"
            }
        }

        override val type: CooTerrainMappingRegionType = CooTerrainMappingRegionType.SPHERE

        override fun contains(position: Vec3): Boolean {
            val dx = position.x - center.x
            val dy = position.y - center.y
            val dz = position.z - center.z
            return dx * dx + dy * dy + dz * dz <= radius * radius
        }

        override fun bounds(): CooTerrainMappingBounds {
            return CooTerrainMappingBounds(
                floor(center.x - radius).toInt(),
                floor(center.y - radius).toInt(),
                floor(center.z - radius).toInt(),
                ceil(center.x + radius).toInt(),
                ceil(center.y + radius).toInt(),
                ceil(center.z + radius).toInt()
            )
        }

        override fun intersects(
            minX: Int,
            minY: Int,
            minZ: Int,
            maxX: Int,
            maxY: Int,
            maxZ: Int
        ): Boolean {
            val nearestX = center.x.coerceIn(minX.toDouble(), maxX.toDouble())
            val nearestY = center.y.coerceIn(minY.toDouble(), maxY.toDouble())
            val nearestZ = center.z.coerceIn(minZ.toDouble(), maxZ.toDouble())
            val dx = center.x - nearestX
            val dy = center.y - nearestY
            val dz = center.z - nearestZ
            return dx * dx + dy * dy + dz * dz <= radius * radius
        }
    }

    companion object {
        /** 从版本化 tagged union 解码区域；未知版本或类型会明确拒绝。 */
        fun decode(buffer: FriendlyByteBuf): CooTerrainMappingRegion {
            val version = buffer.readVarInt()
            require(version == 1) { "Unsupported terrain mapping region version: $version" }
            val typeId = buffer.readResourceLocation()
            return when (CooTerrainMappingRegionType.fromId(typeId)) {
                CooTerrainMappingRegionType.SPHERE -> Sphere(
                    Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readDouble()
                )
                null -> error("Unknown terrain mapping region type: $typeId")
            }
        }
    }
}

/** 区域候选边界；上限和下限均为包含式方块坐标，可用于服务端和客户端 section 筛选。 */
data class CooTerrainMappingBounds(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int
) {
    /** 判断本区域边界是否与包含式方块包围盒相交。 */
    fun intersects(
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int
    ): Boolean {
        return this.maxX >= minX && this.minX <= maxX &&
            this.maxY >= minY && this.minY <= maxY &&
            this.maxZ >= minZ && this.minZ <= maxZ
    }
}
