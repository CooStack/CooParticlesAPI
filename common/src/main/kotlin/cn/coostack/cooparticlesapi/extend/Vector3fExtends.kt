package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.core.Vec3i
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val random = Random(System.currentTimeMillis())

fun Vector3f.withX(handler: Vector3f.() -> Float): Vector3f {
    return Vector3f(handler(), y, z)
}

fun Vector3f.withY(handler: Vector3f.() -> Float): Vector3f {
    return Vector3f(x, handler(), z)
}

fun Vector3f.withZ(handler: Vector3f.() -> Float): Vector3f {
    return Vector3f(x, y, handler())
}

fun Vector3f.asRelative() = RelativeLocation.of(this)
fun Vector3f.asVec3() = Vec3(this)

fun Vector3f.relativize(target: Vec3): Vector3f {
    return target.relativize(this).toVector3f()
}

// Vector3f +-*/ Vec3 / Vector3d / RelativeLocation / Vector3f / Number
operator fun Vector3f.plus(other: Vector3f): Vector3f {
    return this.add(other, Vector3f())
}

operator fun Vector3f.plus(other: Vec3): Vector3f = Vector3f(this.x + other.x.toFloat(), this.y + other.y.toFloat(), this.z + other.z.toFloat())
operator fun Vector3f.plus(other: Vector3d): Vector3f = Vector3f(this.x + other.x.toFloat(), this.y + other.y.toFloat(), this.z + other.z.toFloat())
operator fun Vector3f.plus(other: RelativeLocation): Vector3f = Vector3f(this.x + other.x.toFloat(), this.y + other.y.toFloat(), this.z + other.z.toFloat())
operator fun Vector3f.plus(other: Vec3i): Vector3f = Vector3f(this.x + other.x, this.y + other.y, this.z + other.z)

operator fun Vector3f.minus(other: Vector3f): Vector3f {
    return this.add(other.mul(-1f, Vector3f()), Vector3f())
}

operator fun Vector3f.minus(other: Vec3): Vector3f = Vector3f(this.x - other.x.toFloat(), this.y - other.y.toFloat(), this.z - other.z.toFloat())
operator fun Vector3f.minus(other: Vector3d): Vector3f = Vector3f(this.x - other.x.toFloat(), this.y - other.y.toFloat(), this.z - other.z.toFloat())
operator fun Vector3f.minus(other: RelativeLocation): Vector3f = Vector3f(this.x - other.x.toFloat(), this.y - other.y.toFloat(), this.z - other.z.toFloat())
operator fun Vector3f.minus(other: Vec3i): Vector3f = Vector3f(this.x - other.x, this.y - other.y, this.z - other.z)

operator fun Float.times(other: Vector3f): Vector3f {
    return other * this
}

operator fun Vector3f.times(other: Number): Vector3f {
    return this.mul(other.toFloat(), Vector3f())
}

operator fun Double.times(other: Vector3f): Vector3f {
    return other * this
}

operator fun Vector3f.times(other: Vec3): Vector3f = Vector3f(this.x * other.x.toFloat(), this.y * other.y.toFloat(), this.z * other.z.toFloat())
operator fun Vector3f.times(other: Vector3d): Vector3f = Vector3f(this.x * other.x.toFloat(), this.y * other.y.toFloat(), this.z * other.z.toFloat())
operator fun Vector3f.times(other: RelativeLocation): Vector3f = Vector3f(this.x * other.x.toFloat(), this.y * other.y.toFloat(), this.z * other.z.toFloat())
operator fun Vector3f.times(other: Vec3i): Vector3f = Vector3f(this.x * other.x, this.y * other.y, this.z * other.z)

operator fun Vector3f.div(other: Vec3): Vector3f = Vector3f(this.x / other.x.toFloat(), this.y / other.y.toFloat(), this.z / other.z.toFloat())
operator fun Vector3f.div(other: Vector3d): Vector3f = Vector3f(this.x / other.x.toFloat(), this.y / other.y.toFloat(), this.z / other.z.toFloat())
operator fun Vector3f.div(other: RelativeLocation): Vector3f = Vector3f(this.x / other.x.toFloat(), this.y / other.y.toFloat(), this.z / other.z.toFloat())
operator fun Vector3f.div(other: Vec3i): Vector3f = Vector3f(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vector3f.div(other: Vector3f): Vector3f = Vector3f(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vector3f.div(other: Number): Vector3f = Vector3f(this.x / other.toFloat(), this.y / other.toFloat(), this.z / other.toFloat())

operator fun Vector3f.unaryMinus(): Vector3f {
    return -1f * this
}

operator fun Vector3f.unaryPlus(): Vector3f {
    return this
}

// Vector3f dot/cross with Vec3 / Vector3d / RelativeLocation
fun Vector3f.dot(other: Vec3): Float = this.x * other.x.toFloat() + this.y * other.y.toFloat() + this.z * other.z.toFloat()
fun Vector3f.dot(other: Vector3d): Float = this.x * other.x.toFloat() + this.y * other.y.toFloat() + this.z * other.z.toFloat()
fun Vector3f.dot(other: RelativeLocation): Float = this.x * other.x.toFloat() + this.y * other.y.toFloat() + this.z * other.z.toFloat()
fun Vector3f.dot(other: Vec3i): Float = this.x * other.x + this.y * other.y + this.z * other.z

fun Vector3f.cross(other: Vec3): Vector3f = Vector3f(
    this.y * other.z.toFloat() - this.z * other.y.toFloat(),
    this.z * other.x.toFloat() - this.x * other.z.toFloat(),
    this.x * other.y.toFloat() - this.y * other.x.toFloat()
)
fun Vector3f.cross(other: Vector3d): Vector3f = Vector3f(
    this.y * other.z.toFloat() - this.z * other.y.toFloat(),
    this.z * other.x.toFloat() - this.x * other.z.toFloat(),
    this.x * other.y.toFloat() - this.y * other.x.toFloat()
)
fun Vector3f.cross(other: RelativeLocation): Vector3f = Vector3f(
    this.y * other.z.toFloat() - this.z * other.y.toFloat(),
    this.z * other.x.toFloat() - this.x * other.z.toFloat(),
    this.x * other.y.toFloat() - this.y * other.x.toFloat()
)
fun Vector3f.cross(other: Vec3i): Vector3f = Vector3f(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)

// --- 随机函数 ---
fun randomVec3f(): Vector3f {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vector3f(
        (sinPhi * cos(theta)).toFloat(),
        (sinPhi * sin(theta)).toFloat(),
        cos(phi).toFloat()
    )
}

fun randomVec3f(random: Random): Vector3f {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vector3f(
        (sinPhi * cos(theta)).toFloat(),
        (sinPhi * sin(theta)).toFloat(),
        cos(phi).toFloat()
    )
}

fun randomVec3f(random: java.util.Random): Vector3f {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vector3f(
        (sinPhi * cos(theta)).toFloat(),
        (sinPhi * sin(theta)).toFloat(),
        cos(phi).toFloat()
    )
}

fun randomVec3f(random: RandomSource): Vector3f {
    val theta = random.nextDouble() * 2 * PI
    val phi = random.nextDouble() * 2 * PI
    val sinPhi = sin(phi)
    return Vector3f(
        (sinPhi * cos(theta)).toFloat(),
        (sinPhi * sin(theta)).toFloat(),
        cos(phi).toFloat()
    )
}

fun Vector3f.random() = randomVec3f()
fun Vector3f.random(random: Random) = randomVec3f(random)
fun Vector3f.random(random: java.util.Random) = randomVec3f(random)
fun Vector3f.random(random: RandomSource) = randomVec3f(random)

fun randomHorizontalVec3f(): Vector3f {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vector3f(cos(angle).toFloat(), 0f, sin(angle).toFloat())
}

fun randomHorizontalVec3f(random: Random): Vector3f {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vector3f(cos(angle).toFloat(), 0f, sin(angle).toFloat())
}

fun randomHorizontalVec3f(random: java.util.Random): Vector3f {
    val angle = random.nextDouble() * 2 * PI
    return Vector3f(cos(angle).toFloat(), 0f, sin(angle).toFloat())
}

fun randomHorizontalVec3f(random: RandomSource): Vector3f {
    val angle = random.nextDouble() * 2 * PI
    return Vector3f(cos(angle).toFloat(), 0f, sin(angle).toFloat())
}

fun Vector3f.randomHorizontal() = randomHorizontalVec3f()
fun Vector3f.randomHorizontal(random: Random) = randomHorizontalVec3f(random)
fun Vector3f.randomHorizontal(random: java.util.Random) = randomHorizontalVec3f(random)
fun Vector3f.randomHorizontal(random: RandomSource) = randomHorizontalVec3f(random)

fun Vector3f.offsetRandomly(offset: Double): Vector3f {
    val r = randomVec3f().mul(offset.toFloat())
    return Vector3f(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3f.offsetRandomly(offset: Double, random: Random): Vector3f {
    val r = randomVec3f(random).mul(offset.toFloat())
    return Vector3f(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3f.offsetRandomly(offset: Double, random: java.util.Random): Vector3f {
    val r = randomVec3f(random).mul(offset.toFloat())
    return Vector3f(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3f.offsetRandomly(offset: Double, random: RandomSource): Vector3f {
    val r = randomVec3f(random).mul(offset.toFloat())
    return Vector3f(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3f.offsetRandomlyHorizontal(offset: Double): Vector3f {
    val dir = randomHorizontalVec3f().mul(offset.toFloat())
    return Vector3f(this.x + dir.x, this.y, this.z + dir.z)
}

fun Vector3f.offsetRandomlyHorizontal(offset: Double, random: Random): Vector3f {
    val dir = randomHorizontalVec3f(random).mul(offset.toFloat())
    return Vector3f(this.x + dir.x, this.y, this.z + dir.z)
}

fun Vector3f.offsetRandomlyHorizontal(offset: Double, random: java.util.Random): Vector3f {
    val dir = randomHorizontalVec3f(random).mul(offset.toFloat())
    return Vector3f(this.x + dir.x, this.y, this.z + dir.z)
}

fun Vector3f.offsetRandomlyHorizontal(offset: Double, random: RandomSource): Vector3f {
    val dir = randomHorizontalVec3f(random).mul(offset.toFloat())
    return Vector3f(this.x + dir.x, this.y, this.z + dir.z)
}

// --- 向量长度限制 ---
fun Vector3f.lengthCoerceIn(min: Double, max: Double): Vector3f {
    require(min < max) {
        "最小值必须小于最大值"
    }
    val len = this.length()
    if (abs(len) < 1e-7) {
        return Vector3f()
    }
    if (len in min..max) {
        return this
    }
    if (len < min) {
        return this.normalize() * min
    }

    return this.normalize() * max
}

fun Vector3f.lengthCoerceAtLeast(min: Double): Vector3f {
    val len = length()
    val abs = abs(len)
    if (abs < 1e-7) {
        return Vector3f()
    }
    if (len < min) {
        return this.normalize() * min
    }
    return this
}

fun Vector3f.lengthCoerceAtMost(max: Double): Vector3f {
    val len = length()
    val abs = abs(len)
    if (abs < 1e-7) {
        return Vector3f()
    }
    if (len > max) {
        return this.normalize() * max
    }
    return this
}
