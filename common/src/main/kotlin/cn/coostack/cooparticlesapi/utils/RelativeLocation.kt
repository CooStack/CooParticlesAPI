package cn.coostack.cooparticlesapi.utils


import io.netty.buffer.Unpooled
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


/** 描述粒子之间相对位置的类 相对位置 又名向量 草 */
data class RelativeLocation(var x: Double, var y: Double, var z: Double) {
    companion object {
        @JvmStatic
        fun of(start: Vec3, end: Vec3): RelativeLocation {
            return RelativeLocation(end.x - start.x, end.y - start.y, end.z - start.z)
        }

        @JvmStatic
        fun of(vector: Vec3): RelativeLocation {
            return RelativeLocation(vector.x, vector.y, vector.z)
        }

        @JvmStatic
        fun of(vector: Vector3f): RelativeLocation {
            return RelativeLocation(vector.x, vector.y, vector.z)
        }

        @JvmStatic
        fun toVector(relativeLocation: RelativeLocation): Vec3 {
            return Vec3(relativeLocation.x, relativeLocation.y, relativeLocation.z)
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

    fun toLocation(origin: Vec3): Vec3 {
        return Vec3(origin.x + x, origin.y + y, origin.z + z)
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

    /** 向量点乘 */
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

    fun remove(other: Vec3): RelativeLocation {
        x -= other.x
        y -= other.y
        z -= other.z
        return this
    }


    operator fun unaryMinus(): RelativeLocation {
        return this * -1.0
    }

    operator fun times(scalar: Double): RelativeLocation {
        return RelativeLocation(x * scalar, y * scalar, z * scalar)
    }

    operator fun times(scalar: Float): RelativeLocation {
        return RelativeLocation(x * scalar, y * scalar, z * scalar)
    }

    operator fun times(scalar: Int): RelativeLocation {
        return RelativeLocation(x * scalar, y * scalar, z * scalar)
    }

    operator fun times(scalar: Vec3): RelativeLocation {
        return RelativeLocation(x * scalar.x, y * scalar.y, z * scalar.z)
    }

    operator fun times(scalar: RelativeLocation): RelativeLocation {
        return RelativeLocation(x * scalar.x, y * scalar.y, z * scalar.z)
    }


    operator fun plus(other: RelativeLocation): RelativeLocation {
        return RelativeLocation(x + other.x, y + other.y, z + other.z)
    }

    fun multiply(m: Number): RelativeLocation {
        val s = m.toDouble()
        x *= s
        y *= s
        z *= s
        return this
    }

    fun multiplyClone(s: Number): RelativeLocation {
        val m = s.toDouble()
        return RelativeLocation(x * m, y * m, z * m)
    }

    fun multiplyClone(m: Int): RelativeLocation {
        return multiply(m.toDouble())
    }

    fun toVector(): Vec3 {
        return Vec3(x, y, z)
    }

    fun toVector3d(): Vector3d {
        return Vector3d(x, y, z)
    }

    fun toVector3f(): Vector3f {
        return Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun relativize(other: RelativeLocation): RelativeLocation {
        return other - this
    }

    fun relativize(other: Vec3): RelativeLocation {
        return relativize(of(other))
    }

    fun relativize(other: Vector3f): RelativeLocation {
        return relativize(of(other))
    }

    /** 向量叉乘 */
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

    fun distance(pos: Vec3) = distance(
        of(
            pos
        )
    )

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

    fun copyFrom(other: RelativeLocation): RelativeLocation {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        return this
    }

    fun copyFrom(other: Vec3): RelativeLocation {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        return this
    }

    fun copyFrom(other: Vector3f): RelativeLocation {
        this.x = other.x.toDouble()
        this.y = other.y.toDouble()
        this.z = other.z.toDouble()
        return this
    }

    fun lengthCoerceIn(min: Double, max: Double): RelativeLocation {
        require(min < max) {
            "最小值必须小于最大值"
        }
        val len = this.length()
        if (abs(len) < 1e-7) {
            return RelativeLocation()
        }
        if (len in min..max) {
            return this
        }
        if (len < min) {
            return this.normalize() * min
        }

        return this.normalize() * max
    }

    fun lengthCoerceAtLeast(min: Double): RelativeLocation {
        val len = length()
        val abs = abs(len)
        if (abs < 1e-7) {
            return RelativeLocation()
        }
        if (len < min) {
            return this.normalize() * min
        }
        return this
    }

    fun lengthCoerceAtMost(max: Double): RelativeLocation {
        val len = length()
        val abs = abs(len)
        if (abs < 1e-7) {
            return RelativeLocation()
        }
        if (len > max) {
            return this.normalize() * max
        }
        return this
    }
}