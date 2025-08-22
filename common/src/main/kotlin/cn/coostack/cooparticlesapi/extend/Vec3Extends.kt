package cn.coostack.cooparticlesapi.extend

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f


fun Vec3.relativize(target: Vec3): Vec3 {
    return target.subtract(this)
}

fun Vec3.relativize(target: Vector3f): Vec3 {
    val back = this.toVector3f().mul(-1f)
    return Vec3(target.add(back, Vector3f()))
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