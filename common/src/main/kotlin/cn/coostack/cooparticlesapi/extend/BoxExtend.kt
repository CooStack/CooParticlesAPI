package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 原参数直接转换为hitBox
 *
 * @return
 */
fun AABB.asHitBox(): HitBox {
    return HitBox.of(maxX - minX, maxY - minY, maxZ - minZ)
}

fun AABB.intersectsCylinder(start: Vec3, end: Vec3, radius: Double): Boolean {
    return Math3DUtil.intersectsCylinder(start, end, radius, this)
}

fun AABB.intersectsCylinder(start: Vec3, direction: Vec3, length: Double, radius: Double): Boolean {
    return Math3DUtil.intersectsCylinder(start, start.add(direction.toBoxCylinderOffset(length)), radius, this)
}

fun AABB.intersectsCylinder(start: Vec3, direction: RelativeLocation, length: Double, radius: Double): Boolean {
    return intersectsCylinder(start, direction.toVector(), length, radius)
}

infix fun AABB.intersectsBox(other: AABB): Boolean {
    return Math3DUtil.intersectsBox(this, other)
}

infix fun AABB.intersectRange(other: AABB): AABB? {
    return Math3DUtil.intersectRange(this, other)
}

fun AABB.intersectsBox(center: Vec3, hitBox: HitBox): Boolean {
    return Math3DUtil.intersectsBox(center, hitBox, this)
}

fun HitBox.intersectsCylinder(center: Vec3, start: Vec3, end: Vec3, radius: Double): Boolean {
    return Math3DUtil.intersectsCylinder(start, end, radius, center, this)
}

fun HitBox.intersectsCylinder(
    center: Vec3,
    start: Vec3,
    direction: Vec3,
    length: Double,
    radius: Double,
): Boolean {
    return Math3DUtil.intersectsCylinder(start, start.add(direction.toBoxCylinderOffset(length)), radius, center, this)
}

fun HitBox.intersectsCylinder(
    center: Vec3,
    start: Vec3,
    direction: RelativeLocation,
    length: Double,
    radius: Double,
): Boolean {
    return intersectsCylinder(center, start, direction.toVector(), length, radius)
}

fun HitBox.intersectsBox(center: Vec3, box: AABB): Boolean {
    return Math3DUtil.intersectsBox(center, this, box)
}

fun HitBox.intersectsBox(center: Vec3, otherCenter: Vec3, other: HitBox): Boolean {
    return Math3DUtil.intersectsBox(center, this, otherCenter, other)
}

private fun Vec3.toBoxCylinderOffset(length: Double): Vec3 {
    if (this.lengthSqr() <= 1e-12 || length <= 0.0) return Vec3.ZERO
    return this.normalize().scale(length)
}
