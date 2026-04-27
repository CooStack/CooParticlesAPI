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

fun Vec3.asRelative() = RelativeLocation.of(this)

fun Vec3.asAbs(): Vec3 {
    return Vec3(abs(this.x), abs(this.y), abs(this.z))
}

fun Vec3.relativize(target: Vec3): Vec3 {
    return target.subtract(this)
}

fun Vec3.relativize(target: Vector3f): Vec3 {
    val back = this.toVector3f().mul(-1f)
    return Vec3(target.add(back, Vector3f()))
}

fun Vec3.relativize(target: RelativeLocation): Vec3 {
    return relativize(target.toVector())
}

fun Vec3.multiply(scaled: Number): Vec3 {
    return this.scale(scaled.toDouble())
}

operator fun Vec3.minus(other: Vec3): Vec3 {
    return this.subtract(other)
}

operator fun Vec3.plus(other: Vec3): Vec3 {
    return this.add(other)
}

operator fun Vec3.times(other: Vec3): Vec3 {
    return this.multiply(other)
}

operator fun Vec3.unaryMinus(): Vec3 {
    return -1.0 * this
}

operator fun Vec3.unaryPlus(): Vec3 {
    return this
}

operator fun Float.times(other: Vec3): Vec3 {
    return other * this
}

operator fun Vec3.times(other: Number): Vec3 {
    return this.scale(other.toDouble())
}

operator fun Double.times(other: Vec3): Vec3 {
    return other * this
}

// Vec3 +-*/ Vector3f / Vector3d / RelativeLocation / Vec3i
operator fun Vec3.plus(other: Vector3f): Vec3 = Vec3(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vec3.plus(other: Vector3d): Vec3 = Vec3(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vec3.plus(other: RelativeLocation): Vec3 = this.add(other.toVector())
operator fun Vec3.plus(other: Vec3i): Vec3 = Vec3(this.x + other.x, this.y + other.y, this.z + other.z)

operator fun Vec3.minus(other: Vector3f): Vec3 = Vec3(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vec3.minus(other: Vector3d): Vec3 = Vec3(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vec3.minus(other: RelativeLocation): Vec3 = this.subtract(other.toVector())
operator fun Vec3.minus(other: Vec3i): Vec3 = Vec3(this.x - other.x, this.y - other.y, this.z - other.z)

operator fun Vec3.times(other: Vector3f): Vec3 = Vec3(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vec3.times(other: Vector3d): Vec3 = Vec3(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vec3.times(other: RelativeLocation): Vec3 = this.multiply(other.toVector())
operator fun Vec3.times(other: Vec3i): Vec3 = Vec3(this.x * other.x, this.y * other.y, this.z * other.z)

operator fun Vec3.div(other: Vec3): Vec3 = Vec3(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vec3.div(other: Vector3f): Vec3 = Vec3(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vec3.div(other: Vector3d): Vec3 = Vec3(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vec3.div(other: RelativeLocation): Vec3 = Vec3(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vec3.div(other: Vec3i): Vec3 = Vec3(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vec3.div(other: Number): Vec3 = Vec3(this.x / other.toDouble(), this.y / other.toDouble(), this.z / other.toDouble())

// Vec3 dot/cross with Vector3f / Vector3d / RelativeLocation / Vec3i
fun Vec3.dot(other: Vector3f): Double = this.x * other.x + this.y * other.y + this.z * other.z
fun Vec3.dot(other: Vector3d): Double = this.x * other.x + this.y * other.y + this.z * other.z
fun Vec3.dot(other: RelativeLocation): Double = this.dot(other.toVector())
fun Vec3.dot(other: Vec3i): Double = this.x * other.x + this.y * other.y + this.z * other.z

fun Vec3.cross(other: Vector3f): Vec3 = Vec3(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)
fun Vec3.cross(other: Vector3d): Vec3 = Vec3(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)
fun Vec3.cross(other: RelativeLocation): Vec3 = this.cross(other.toVector())
fun Vec3.cross(other: Vec3i): Vec3 = Vec3(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)

// --- 球面随机分布 ---
fun randomVec3(): Vec3 {
    return randomVec3(random)
}

fun randomVec3(random: Random): Vec3 {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vec3(
        sinPhi * cos(theta),
        sinPhi * sin(theta),
        cos(phi)
    )
}

fun randomVec3(random: java.util.Random): Vec3 {
    val theta = random.nextDouble(-PI, PI)
    val phi = random.nextDouble(-PI, PI)
    val sinPhi = sin(phi)
    return Vec3(
        sinPhi * cos(theta),
        sinPhi * sin(theta),
        cos(phi)
    )
}

fun randomVec3(random: RandomSource): Vec3 {
    val theta = random.nextDouble() * 2 * PI
    val phi = random.nextDouble() * 2 * PI
    val sinPhi = sin(phi)
    return Vec3(
        sinPhi * cos(theta),
        sinPhi * sin(theta),
        cos(phi)
    )
}

fun Vec3.random() = randomVec3()
fun Vec3.random(random: Random) = randomVec3(random)
fun Vec3.random(random: java.util.Random) = randomVec3(random)
fun Vec3.random(random: RandomSource) = randomVec3(random)

// --- 水平球状随机（XZ平面）---
fun randomHorizontalVec3(): Vec3 {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun randomHorizontalVec3(random: Random): Vec3 {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun randomHorizontalVec3(random: java.util.Random): Vec3 {
    val angle = random.nextDouble() * 2 * PI
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun randomHorizontalVec3(random: RandomSource): Vec3 {
    val angle = random.nextDouble() * 2 * PI
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun Vec3.randomHorizontal() = randomHorizontalVec3()
fun Vec3.randomHorizontal(random: Random) = randomHorizontalVec3(random)
fun Vec3.randomHorizontal(random: java.util.Random) = randomHorizontalVec3(random)
fun Vec3.randomHorizontal(random: RandomSource) = randomHorizontalVec3(random)

// --- 随机偏移 ---
fun Vec3.offsetRandomly(offset: Double): Vec3 {
    return this + randomVec3() * offset
}

fun Vec3.offsetRandomly(offset: Double, random: Random): Vec3 {
    return this + randomVec3(random) * offset
}

fun Vec3.offsetRandomly(offset: Double, random: java.util.Random): Vec3 {
    return this + randomVec3(random) * offset
}

fun Vec3.offsetRandomly(offset: Double, random: RandomSource): Vec3 {
    return this + randomVec3(random) * offset
}

fun Vec3.offsetRandomlyHorizontal(offset: Double): Vec3 {
    val dir = randomHorizontalVec3()
    return Vec3(this.x + dir.x * offset, this.y, this.z + dir.z * offset)
}

fun Vec3.offsetRandomlyHorizontal(offset: Double, random: Random): Vec3 {
    val dir = randomHorizontalVec3(random)
    return Vec3(this.x + dir.x * offset, this.y, this.z + dir.z * offset)
}

fun Vec3.offsetRandomlyHorizontal(offset: Double, random: java.util.Random): Vec3 {
    val dir = randomHorizontalVec3(random)
    return Vec3(this.x + dir.x * offset, this.y, this.z + dir.z * offset)
}

fun Vec3.offsetRandomlyHorizontal(offset: Double, random: RandomSource): Vec3 {
    val dir = randomHorizontalVec3(random)
    return Vec3(this.x + dir.x * offset, this.y, this.z + dir.z * offset)
}

// --- 向量长度限制 ---
fun Vec3.lengthCoerceIn(min: Double, max: Double): Vec3 {
    require(min < max) {
        "最小值必须小于最大值"
    }
    val len = this.length()
    if (abs(len) < 1e-7) {
        return Vec3.ZERO
    }
    if (len in min..max) {
        return this
    }
    if (len < min) {
        return this.normalize() * min
    }

    return this.normalize() * max
}

fun Vec3.lengthCoerceAtLeast(min: Double): Vec3 {
    val len = length()
    val abs = abs(len)
    if (abs < 1e-7) {
        return Vec3.ZERO
    }
    if (len < min) {
        return this.normalize() * min
    }
    return this
}

fun Vec3.lengthCoerceAtMost(max: Double): Vec3 {
    val len = length()
    val abs = abs(len)
    if (abs < 1e-7) {
        return Vec3.ZERO
    }
    if (len > max) {
        return this.normalize() * max
    }
    return this
}
