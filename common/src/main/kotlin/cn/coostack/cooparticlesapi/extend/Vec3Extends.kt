package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.core.Vec3i
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val random = Random(System.currentTimeMillis())

fun Vec3.asRelative() = RelativeLocation.of(this)
fun Vector3f.asRelative() = RelativeLocation.of(this)
fun Vector3f.asVec3() = Vec3(this)


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

fun Vector3f.relativize(target: Vec3): Vector3f {
    return target.relativize(this).toVector3f()
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

operator fun Vector3f.plus(other: Vector3f): Vector3f {
    return this.add(other, Vector3f())
}

operator fun Vector3f.minus(other: Vector3f): Vector3f {
    return this.add(other.mul(-1f, Vector3f()), Vector3f())
}

operator fun Vector3f.times(other: Float): Vector3f {
    return this.mul(other, Vector3f())
}

operator fun Float.times(other: Vector3f): Vector3f {
    return other * this
}

operator fun Vector3f.times(other: Double): Vector3f {
    return this.mul(other.toFloat(), Vector3f())
}

operator fun Double.times(other: Vector3f): Vector3f {
    return other * this
}

operator fun Vector3f.unaryMinus(): Vector3f {
    return -1f * this
}

operator fun Vector3f.unaryPlus(): Vector3f {
    return this
}

operator fun Vec3.unaryMinus(): Vec3 {
    return -1.0 * this
}

operator fun Vec3.unaryPlus(): Vec3 {
    return this
}

operator fun Vec3.times(other: Float): Vec3 {
    return this.scale(other.toDouble())
}

operator fun Float.times(other: Vec3): Vec3 {
    return other * this
}

operator fun Vec3.times(other: Double): Vec3 {
    return this.scale(other)
}

operator fun Double.times(other: Vec3): Vec3 {
    return other * this
}

/**
 * 球面随机分布
 * 方向分布更均匀
 */
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

/**
 * 强制限制向量长度
 * 可能会有误差 向量 越接近0 误差越大
 *
 * @param min 最小值
 * @param max 最大值
 * @return Vec3.ZERO 当你输入一个0向量时则此方法失效
 */
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


/**
 * 强制限制向量长度
 * 可能会有误差 向量 越接近0 误差越大
 *
 * @param min 最小值
 * @param max 最大值
 * @return Vec3.ZERO 当你输入一个0向量时则此方法失效
 */
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