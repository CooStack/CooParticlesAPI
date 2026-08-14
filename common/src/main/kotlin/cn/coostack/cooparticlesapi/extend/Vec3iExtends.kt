package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val random = Random(System.currentTimeMillis())

fun Vec3i.withX(handler: Vec3i.() -> Int): Vec3i {
    return Vec3i(handler(), y, z)
}

fun Vec3i.withY(handler: Vec3i.() -> Int): Vec3i {
    return Vec3i(x, handler(), z)
}

fun Vec3i.withZ(handler: Vec3i.() -> Int): Vec3i {
    return Vec3i(x, y, handler())
}

@JvmOverloads
fun Vec3i.playSoundAt(world: Level, sound: SoundEvent, source: SoundSource, volume: Float = 1f, pitch: Float = 1f) =
    apply {
        world.playSound(null, this.x.toDouble(), this.y.toDouble(), this.z.toDouble(), sound, source, volume, pitch)
    }

fun Vec3i.asVec3(): Vec3 = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
fun Vec3i.asRelative() = RelativeLocation.of(asVec3())

// Vec3i +-*/ Vec3i（整数运算，结果保持 Vec3i）
operator fun Vec3i.plus(other: Vec3i): Vec3i = Vec3i(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vec3i.minus(other: Vec3i): Vec3i = Vec3i(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vec3i.times(other: Vec3i): Vec3i = Vec3i(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vec3i.div(other: Vec3i): Vec3 =
    Vec3(this.x.toDouble() / other.x, this.y.toDouble() / other.y, this.z.toDouble() / other.z)

// Vec3i +-*/ 跨类型 — 结果类型跟右边的操作数走
operator fun Vec3i.plus(other: Vec3): Vec3 = this.asVec3() + other
operator fun Vec3i.plus(other: Vector3f): Vector3f = Vector3f(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vec3i.plus(other: Vector3d): Vector3d = Vector3d(this.x + other.x, this.y + other.y, this.z + other.z)
operator fun Vec3i.plus(other: RelativeLocation): RelativeLocation =
    RelativeLocation(this.x + other.x, this.y + other.y, this.z + other.z)

operator fun Vec3i.minus(other: Vec3): Vec3 = this.asVec3() - other
operator fun Vec3i.minus(other: Vector3f): Vector3f = Vector3f(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vec3i.minus(other: Vector3d): Vector3d = Vector3d(this.x - other.x, this.y - other.y, this.z - other.z)
operator fun Vec3i.minus(other: RelativeLocation): RelativeLocation =
    RelativeLocation(this.x - other.x, this.y - other.y, this.z - other.z)

operator fun Vec3i.times(other: Vec3): Vec3 = this.asVec3() * other
operator fun Vec3i.times(other: Vector3f): Vector3f = Vector3f(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vec3i.times(other: Vector3d): Vector3d = Vector3d(this.x * other.x, this.y * other.y, this.z * other.z)
operator fun Vec3i.times(other: RelativeLocation): RelativeLocation =
    RelativeLocation(this.x * other.x, this.y * other.y, this.z * other.z)

operator fun Vec3i.div(other: Vec3): Vec3 = this.asVec3() / other
operator fun Vec3i.div(other: Vector3f): Vector3f = Vector3f(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vec3i.div(other: Vector3d): Vector3d = Vector3d(this.x / other.x, this.y / other.y, this.z / other.z)
operator fun Vec3i.div(other: RelativeLocation): RelativeLocation =
    RelativeLocation(this.x / other.x, this.y / other.y, this.z / other.z)

// Vec3i * scalar — 整数系数保持 Vec3i，浮点系数跟类型走
operator fun Vec3i.times(scalar: Int): Vec3i = Vec3i(this.x * scalar, this.y * scalar, this.z * scalar)
operator fun Vec3i.times(scalar: Double): Vec3 = this.asVec3() * scalar
operator fun Vec3i.times(scalar: Float): Vector3f = Vector3f(this.x * scalar, this.y * scalar, this.z * scalar)

operator fun Vec3i.div(scalar: Int): Vec3 =
    Vec3(this.x.toDouble() / scalar, this.y.toDouble() / scalar, this.z.toDouble() / scalar)

operator fun Vec3i.div(scalar: Double): Vec3 = this.asVec3() / scalar
operator fun Vec3i.div(scalar: Float): Vector3f =
    Vector3f(this.x / scalar, this.y / scalar, this.z / scalar)

// scalar * Vec3i
operator fun Int.times(other: Vec3i): Vec3i = other * this
operator fun Double.times(other: Vec3i): Vec3 = this * other.asVec3()
operator fun Float.times(other: Vec3i): Vector3f = Vector3f(this * other.x, this * other.y, this * other.z)

operator fun Vec3i.unaryMinus(): Vec3i = Vec3i(-this.x, -this.y, -this.z)
operator fun Vec3i.unaryPlus(): Vec3i = this

// dot / cross — 结果类型跟参数类型走
fun Vec3i.dot(other: Vec3i): Int = this.x * other.x + this.y * other.y + this.z * other.z
fun Vec3i.dot(other: Vec3): Double = this.asVec3().dot(other)
fun Vec3i.dot(other: Vector3f): Float = this.x * other.x + this.y * other.y + this.z * other.z
fun Vec3i.dot(other: Vector3d): Double = this.asVec3().dot(other)
fun Vec3i.dot(other: RelativeLocation): Double = this.x * other.x + this.y * other.y + this.z * other.z

fun Vec3i.cross(other: Vec3i): Vec3i = Vec3i(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)

fun Vec3i.cross(other: Vec3): Vec3 = this.asVec3().cross(other)
fun Vec3i.cross(other: Vector3f): Vector3f = Vector3f(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)

fun Vec3i.cross(other: Vector3d): Vector3d = Vector3d(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)

fun Vec3i.cross(other: RelativeLocation): RelativeLocation = RelativeLocation(
    this.y * other.z - this.z * other.y,
    this.z * other.x - this.x * other.z,
    this.x * other.y - this.y * other.x
)

// --- 随机方法 — 涉及浮点，返回 Vec3 ---
fun Vec3i.randomHorizontal(): Vec3 {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun Vec3i.randomHorizontal(random: Random): Vec3 {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun Vec3i.randomHorizontal(random: java.util.Random): Vec3 {
    val angle = random.nextDouble() * 2 * PI
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun Vec3i.randomHorizontal(random: RandomSource): Vec3 {
    val angle = random.nextDouble() * 2 * PI
    return Vec3(cos(angle), 0.0, sin(angle))
}

fun Vec3i.offsetRandomly(offset: Double): Vec3 = asVec3() + randomVec3() * offset
fun Vec3i.offsetRandomly(offset: Double, random: Random): Vec3 = asVec3() + randomVec3(random) * offset
fun Vec3i.offsetRandomly(offset: Double, random: java.util.Random): Vec3 = asVec3() + randomVec3(random) * offset
fun Vec3i.offsetRandomly(offset: Double, random: RandomSource): Vec3 = asVec3() + randomVec3(random) * offset

fun Vec3i.offsetRandomlyHorizontal(offset: Double): Vec3 = asVec3().offsetRandomlyHorizontal(offset)
fun Vec3i.offsetRandomlyHorizontal(offset: Double, random: Random): Vec3 =
    asVec3().offsetRandomlyHorizontal(offset, random)

fun Vec3i.offsetRandomlyHorizontal(offset: Double, random: java.util.Random): Vec3 =
    asVec3().offsetRandomlyHorizontal(offset, random)

fun Vec3i.offsetRandomlyHorizontal(offset: Double, random: RandomSource): Vec3 =
    asVec3().offsetRandomlyHorizontal(offset, random)
