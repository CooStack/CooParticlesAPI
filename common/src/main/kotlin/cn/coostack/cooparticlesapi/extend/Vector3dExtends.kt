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

fun Vector3d.asRelative() = RelativeLocation.of(this)
fun Vector3d.asVec3() = Vec3(this.x, this.y, this.z)

// Vector3d +-*/ Vec3 / Vector3f / RelativeLocation / Vector3d / Number
operator fun Vector3d.plus(other: Vector3d): Vector3d = Vector3d(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vector3d.plus(other: Vec3): Vector3d = Vector3d(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vector3d.plus(other: Vector3f): Vector3d = Vector3d(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vector3d.plus(other: RelativeLocation): Vector3d = Vector3d(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vector3d.plus(other: Vec3i): Vector3d = Vector3d(this.x + other.x, this.y + other.y, this.z + other.z)

operator fun Vector3d.minus(other: Vector3d): Vector3d = Vector3d(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vector3d.minus(other: Vec3): Vector3d = Vector3d(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vector3d.minus(other: Vector3f): Vector3d = Vector3d(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vector3d.minus(other: RelativeLocation): Vector3d = Vector3d(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vector3d.minus(other: Vec3i): Vector3d = Vector3d(this.x - other.x, this.y - other.y, this.z - other.z)

operator fun Vector3d.times(other: Vector3d): Vector3d = Vector3d(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vector3d.times(other: Vec3): Vector3d = Vector3d(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vector3d.times(other: Vector3f): Vector3d = Vector3d(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vector3d.times(other: RelativeLocation): Vector3d = Vector3d(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vector3d.times(other: Vec3i): Vector3d = Vector3d(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vector3d.times(other: Number): Vector3d = Vector3d(this.x * other.toDouble(), this.y * other.toDouble(), this.z * other.toDouble())

operator fun Vector3d.div(other: Vector3d): Vector3d = Vector3d(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vector3d.div(other: Vec3): Vector3d = Vector3d(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vector3d.div(other: Vector3f): Vector3d = Vector3d(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vector3d.div(other: RelativeLocation): Vector3d = Vector3d(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vector3d.div(other: Vec3i): Vector3d = Vector3d(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vector3d.div(other: Number): Vector3d = Vector3d(this.x / other.toDouble(), this.y / other.toDouble(), this.z / other.toDouble())

operator fun Vector3d.unaryMinus(): Vector3d = Vector3d(-this.x, -this.y, -this.z)
operator fun Vector3d.unaryPlus(): Vector3d = this

operator fun Double.times(other: Vector3d): Vector3d = other * this
operator fun Float.times(other: Vector3d): Vector3d = other * this

// Vector3d dot/cross with Vec3 / Vector3f / RelativeLocation
fun Vector3d.dot(other: Vec3): Double = this.x * other.x + this.y * other.y + this.z * other.z
fun Vector3d.dot(other: Vector3f): Double = this.x * other.x + this.y * other.y + this.z * other.z
fun Vector3d.dot(other: RelativeLocation): Double = this.x * other.x + this.y * other.y + this.z * other.z
fun Vector3d.dot(other: Vec3i): Double = this.x * other.x + this.y * other.y + this.z * other.z

fun Vector3d.cross(other: Vec3): Vector3d = Vector3d(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)
fun Vector3d.cross(other: Vector3f): Vector3d = Vector3d(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)
fun Vector3d.cross(other: RelativeLocation): Vector3d = Vector3d(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)
fun Vector3d.cross(other: Vec3i): Vector3d = Vector3d(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)

// --- 随机函数 ---
fun randomVec3d(): Vector3d {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vector3d(
        sinPhi * cos(theta),
        sinPhi * sin(theta),
        cos(phi)
    )
}

fun randomVec3d(random: Random): Vector3d {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vector3d(
        sinPhi * cos(theta),
        sinPhi * sin(theta),
        cos(phi)
    )
}

fun randomVec3d(random: java.util.Random): Vector3d {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vector3d(
        sinPhi * cos(theta),
        sinPhi * sin(theta),
        cos(phi)
    )
}

fun randomVec3d(random: RandomSource): Vector3d {
    val theta = random.nextDouble() * 2 * PI
    val phi = random.nextDouble() * 2 * PI
    val sinPhi = sin(phi)
    return Vector3d(
        sinPhi * cos(theta),
        sinPhi * sin(theta),
        cos(phi)
    )
}

fun Vector3d.random() = randomVec3d()
fun Vector3d.random(random: Random) = randomVec3d(random)
fun Vector3d.random(random: java.util.Random) = randomVec3d(random)
fun Vector3d.random(random: RandomSource) = randomVec3d(random)

fun randomHorizontalVec3d(): Vector3d {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vector3d(cos(angle), 0.0, sin(angle))
}

fun randomHorizontalVec3d(random: Random): Vector3d {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vector3d(cos(angle), 0.0, sin(angle))
}

fun randomHorizontalVec3d(random: java.util.Random): Vector3d {
    val angle = random.nextDouble() * 2 * PI
    return Vector3d(cos(angle), 0.0, sin(angle))
}

fun randomHorizontalVec3d(random: RandomSource): Vector3d {
    val angle = random.nextDouble() * 2 * PI
    return Vector3d(cos(angle), 0.0, sin(angle))
}

fun Vector3d.randomHorizontal() = randomHorizontalVec3d()
fun Vector3d.randomHorizontal(random: Random) = randomHorizontalVec3d(random)
fun Vector3d.randomHorizontal(random: java.util.Random) = randomHorizontalVec3d(random)
fun Vector3d.randomHorizontal(random: RandomSource) = randomHorizontalVec3d(random)

fun Vector3d.offsetRandomly(offset: Double): Vector3d {
    val r = randomVec3d().mul(offset)
    return Vector3d(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3d.offsetRandomly(offset: Double, random: Random): Vector3d {
    val r = randomVec3d(random).mul(offset)
    return Vector3d(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3d.offsetRandomly(offset: Double, random: java.util.Random): Vector3d {
    val r = randomVec3d(random).mul(offset)
    return Vector3d(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3d.offsetRandomly(offset: Double, random: RandomSource): Vector3d {
    val r = randomVec3d(random).mul(offset)
    return Vector3d(this.x + r.x, this.y + r.y, this.z + r.z)
}

fun Vector3d.offsetRandomlyHorizontal(offset: Double): Vector3d {
    val dir = randomHorizontalVec3d().mul(offset)
    return Vector3d(this.x + dir.x, this.y, this.z + dir.z)
}

fun Vector3d.offsetRandomlyHorizontal(offset: Double, random: Random): Vector3d {
    val dir = randomHorizontalVec3d(random).mul(offset)
    return Vector3d(this.x + dir.x, this.y, this.z + dir.z)
}

fun Vector3d.offsetRandomlyHorizontal(offset: Double, random: java.util.Random): Vector3d {
    val dir = randomHorizontalVec3d(random).mul(offset)
    return Vector3d(this.x + dir.x, this.y, this.z + dir.z)
}

fun Vector3d.offsetRandomlyHorizontal(offset: Double, random: RandomSource): Vector3d {
    val dir = randomHorizontalVec3d(random).mul(offset)
    return Vector3d(this.x + dir.x, this.y, this.z + dir.z)
}

// --- 向量长度限制 ---
fun Vector3d.lengthCoerceIn(min: Double, max: Double): Vector3d {
    require(min < max) {
        "最小值必须小于最大值"
    }
    val len = this.length()
    if (abs(len) < 1e-7) {
        return Vector3d()
    }
    if (len in min..max) {
        return this
    }
    if (len < min) {
        return this.normalize() * min
    }

    return this.normalize() * max
}

fun Vector3d.lengthCoerceAtLeast(min: Double): Vector3d {
    val len = length()
    val abs = abs(len)
    if (abs < 1e-7) {
        return Vector3d()
    }
    if (len < min) {
        return this.normalize() * min
    }
    return this
}

fun Vector3d.lengthCoerceAtMost(max: Double): Vector3d {
    val len = length()
    val abs = abs(len)
    if (abs < 1e-7) {
        return Vector3d()
    }
    if (len > max) {
        return this.normalize() * max
    }
    return this
}
