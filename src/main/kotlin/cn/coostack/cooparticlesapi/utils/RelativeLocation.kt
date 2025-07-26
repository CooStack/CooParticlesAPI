package cn.coostack.cooparticlesapi.utils


import io.netty.buffer.Unpooled
import net.minecraft.util.math.Vec3d
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * 描述粒子之间相对位置的类
 * 相对位置 又名向量
 * 草
 */
data class RelativeLocation(var x: Double, var y: Double, var z: Double) {
    companion object {
        @JvmStatic
        fun of(start: Vec3d, end: Vec3d): RelativeLocation {
            return RelativeLocation(end.x - start.x, end.y - start.y, end.z - start.z)
        }

        @JvmStatic
        fun of(vector: Vec3d): RelativeLocation {
            return RelativeLocation(vector.x, vector.y, vector.z)
        }

        @JvmStatic
        fun toVector(relativeLocation: RelativeLocation): Vec3d {
            return Vec3d(relativeLocation.x, relativeLocation.y, relativeLocation.z)
        }

        @JvmStatic
        fun fromBytes(bytes: ByteArray): RelativeLocation {
            val buffer = Unpooled.wrappedBuffer(bytes)
            val x = buffer.readDouble()
            val y = buffer.readDouble()
            val z = buffer.readDouble()
            return RelativeLocation(x, y, z)
        }

        @JvmStatic
        fun yAxis(): RelativeLocation = RelativeLocation(0.0, 1.0, 0.0)

        @JvmStatic
        fun xAxis(): RelativeLocation = RelativeLocation(1.0, 0.0, 0.0)

        @JvmStatic
        fun zAxis(): RelativeLocation = RelativeLocation(0.0, 0.0, 1.0)

        @JvmStatic
        fun zero(): RelativeLocation = RelativeLocation(0.0, 0.0, 0.0)
    }

    constructor() : this(0.0, 0.0, 0.0)
    constructor(x: Int, y: Int, z: Int) : this(x.toDouble(), y.toDouble(), z.toDouble())
    constructor(x: Float, y: Float, z: Float) : this(x.toDouble(), y.toDouble(), z.toDouble())
    constructor(x: Long, y: Long, z: Long) : this(x.toDouble(), y.toDouble(), z.toDouble())

    operator fun minus(other: RelativeLocation): RelativeLocation {
        return RelativeLocation(x - other.x, y - other.y, z - other.z)
    }

    fun toLocation(origin: Vec3d): Vec3d {
        return Vec3d(origin.x + x, origin.y + y, origin.z + z)
    }

    // 转换为单位向量
    fun normalize(): RelativeLocation {
        if (length() <= 1e-6) {
            return RelativeLocation(1.0, 0.0, 0.0)
        }
        val length = sqrt(x * x + y * y + z * z)
        return RelativeLocation(x / length, y / length, z / length)
    }

    override fun toString(): String {
        return "RelativeLocation(x=$x, y=$y, z=$z)"
    }

    fun clone(): RelativeLocation {
        return RelativeLocation(x, y, z)
    }

    /**
     * 向量点乘
     */
    fun dot(other: RelativeLocation): Double {
        return x * other.x + y * other.y + z * other.z
    }

    fun add(other: RelativeLocation): RelativeLocation {
        x += other.x
        y += other.y
        z += other.z
        return this
    }

    fun remove(other: RelativeLocation): RelativeLocation {
        x -= other.x
        y -= other.y
        z -= other.z
        return this
    }

    operator fun times(scalar: Double): RelativeLocation {
        return RelativeLocation(x * scalar, y * scalar, z * scalar)
    }

    operator fun plus(other: RelativeLocation): RelativeLocation {
        return RelativeLocation(x + other.x, y + other.y, z + other.z)
    }

    fun multiply(m: Double): RelativeLocation {
        x *= m
        y *= m
        z *= m
        return this
    }

    fun multiply(m: Int): RelativeLocation {
        return multiply(m.toDouble())
    }

    fun multiplyClone(m: Double): RelativeLocation {
        return RelativeLocation(x * m, y * m, z * m)
    }

    fun multiplyClone(m: Int): RelativeLocation {
        return multiply(m.toDouble())
    }

    fun toVector(): Vec3d {
        return Vec3d(x, y, z)
    }

    /**
     * 向量叉乘
     */
    fun cross(vector: RelativeLocation): RelativeLocation {
        return RelativeLocation(
            y * vector.z - z * vector.y,
            z * vector.x - x * vector.z,
            x * vector.y - y * vector.x
        )
    }

    fun length() = sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    fun distance(relativeLocation: RelativeLocation) =
        sqrt((x - relativeLocation.x).pow(2) + (y - relativeLocation.y).pow(2) + (z - relativeLocation.z).pow(2))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RelativeLocation

        if (!doubleEquals(x, other.x)) return false
        if (!doubleEquals(y, other.y)) return false
        if (!doubleEquals(z, other.z)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    private fun doubleEquals(a: Double, b: Double): Boolean = a - b in -10e-6..10e-6

    fun toBytes(): ByteArray {
        val buffer = Unpooled.buffer(24)
        buffer.writeDouble(x)
        buffer.writeDouble(y)
        buffer.writeDouble(z)
        return buffer.copy().array()
    }

}