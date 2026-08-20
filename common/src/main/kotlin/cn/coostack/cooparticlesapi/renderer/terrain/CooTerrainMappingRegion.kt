package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 服务端和客户端共享的程序化区域带类型标签联合体。
 *
 * 区域参数会原样同步到客户端；客户端渲染器只在片元阶段用世界坐标判断成员关系，绝不查询世界或枚举方块。
 */
sealed interface CooTerrainMappingRegion {
    /** 当前区域的稳定网络类型。 */
    val type: CooTerrainMappingRegionType

    /** 判断一个世界坐标是否属于区域。 */
    fun contains(position: Vec3): Boolean

    /** 将版本化带类型标签联合体写入网络缓冲区。 */
    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeVarInt(WIRE_VERSION)
        buffer.writeResourceLocation(type.id)
        when (this) {
            is Sphere -> {
                buffer.writeDouble(center.x)
                buffer.writeDouble(center.y)
                buffer.writeDouble(center.z)
                buffer.writeDouble(radius)
            }
            is Box -> {
                buffer.writeDouble(center.x)
                buffer.writeDouble(center.y)
                buffer.writeDouble(center.z)
                buffer.writeDouble(halfExtents.x)
                buffer.writeDouble(halfExtents.y)
                buffer.writeDouble(halfExtents.z)
            }
            is Cylinder -> {
                buffer.writeDouble(center.x)
                buffer.writeDouble(center.y)
                buffer.writeDouble(center.z)
                buffer.writeDouble(radius)
                buffer.writeDouble(height)
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

    /** 轴对齐长方体空间选择域；halfExtents 是三个方向的半轴长度。 */
    data class Box(val center: Vec3, val halfExtents: Vec3) : CooTerrainMappingRegion {
        init {
            require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite()) {
                "Terrain mapping box center must be finite"
            }
            require(
                halfExtents.x.isFinite() && halfExtents.y.isFinite() && halfExtents.z.isFinite() &&
                    halfExtents.x > 0.0 && halfExtents.y > 0.0 && halfExtents.z > 0.0
            ) {
                "Terrain mapping box half extents must be finite and greater than zero"
            }
        }

        override val type: CooTerrainMappingRegionType = CooTerrainMappingRegionType.BOX

        override fun contains(position: Vec3): Boolean {
            return abs(position.x - center.x) <= halfExtents.x &&
                abs(position.y - center.y) <= halfExtents.y &&
                abs(position.z - center.z) <= halfExtents.z
        }

        override fun bounds(): CooTerrainMappingBounds {
            return CooTerrainMappingBounds(
                floor(center.x - halfExtents.x).toInt(),
                floor(center.y - halfExtents.y).toInt(),
                floor(center.z - halfExtents.z).toInt(),
                ceil(center.x + halfExtents.x).toInt(),
                ceil(center.y + halfExtents.y).toInt(),
                ceil(center.z + halfExtents.z).toInt()
            )
        }
    }

    /** 沿 Y 轴的圆柱体空间选择域；height 是完整高度。 */
    data class Cylinder(val center: Vec3, val radius: Double, val height: Double) : CooTerrainMappingRegion {
        init {
            require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite()) {
                "Terrain mapping cylinder center must be finite"
            }
            require(radius.isFinite() && radius > 0.0) {
                "Terrain mapping cylinder radius must be finite and greater than zero"
            }
            require(height.isFinite() && height > 0.0) {
                "Terrain mapping cylinder height must be finite and greater than zero"
            }
        }

        override val type: CooTerrainMappingRegionType = CooTerrainMappingRegionType.CYLINDER

        override fun contains(position: Vec3): Boolean {
            val dx = position.x - center.x
            val dz = position.z - center.z
            return dx * dx + dz * dz <= radius * radius &&
                abs(position.y - center.y) <= height * 0.5
        }

        override fun bounds(): CooTerrainMappingBounds {
            val halfHeight = height * 0.5
            return CooTerrainMappingBounds(
                floor(center.x - radius).toInt(),
                floor(center.y - halfHeight).toInt(),
                floor(center.z - radius).toInt(),
                ceil(center.x + radius).toInt(),
                ceil(center.y + halfHeight).toInt(),
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
            val halfHeight = height * 0.5
            if (center.y + halfHeight < minY || center.y - halfHeight > maxY) return false
            val nearestX = center.x.coerceIn(minX.toDouble(), maxX.toDouble())
            val nearestZ = center.z.coerceIn(minZ.toDouble(), maxZ.toDouble())
            val dx = center.x - nearestX
            val dz = center.z - nearestZ
            return dx * dx + dz * dz <= radius * radius
        }
    }

    companion object {
        /** 当前区域网络格式；版本 1 仅支持球体，版本 2 增加长方体和圆柱体。 */
        private const val WIRE_VERSION = 2

        /** 从版本化带类型标签联合体解码区域；未知版本或类型会明确拒绝。 */
        fun decode(buffer: FriendlyByteBuf): CooTerrainMappingRegion {
            val version = buffer.readVarInt()
            require(version in 1..WIRE_VERSION) { "Unsupported terrain mapping region version: $version" }
            val typeId = buffer.readResourceLocation()
            val type = CooTerrainMappingRegionType.fromId(typeId)
            require(version != 1 || type == CooTerrainMappingRegionType.SPHERE) {
                "Terrain mapping region version 1 only supports sphere"
            }
            return when (type) {
                CooTerrainMappingRegionType.SPHERE -> Sphere(
                    Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readDouble()
                )
                CooTerrainMappingRegionType.BOX -> Box(
                    Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                )
                CooTerrainMappingRegionType.CYLINDER -> Cylinder(
                    Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readDouble(),
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
