package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.data.cache.ClientEntityCacheManager
import cn.coostack.cooparticlesapi.data.cache.EntityCacher
import cn.coostack.cooparticlesapi.data.cache.ServerEntityCacheManager
import cn.coostack.cooparticlesapi.data.holder.DataHolder
import cn.coostack.cooparticlesapi.data.holder.DataHolderManager
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate
import kotlin.jvm.java

infix fun Entity.canSee(another: Entity): Boolean {
    if (level() != another.level()) return false

    val to = another.eyePosition
    val ctx = ClipContext(
        eyePosition, to,
        ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE,
        this
    )
    val res = level().clip(ctx)

    return when (res.type) {
        HitResult.Type.MISS -> true
        HitResult.Type.ENTITY -> true
        else -> false
    }
}

infix fun Entity.canSee(to: Vec3): Boolean {
    val ctx = ClipContext(
        eyePosition, to,
        ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE,
        this
    )
    val res = level().clip(ctx)

    return when (res.type) {
        HitResult.Type.MISS -> true
        HitResult.Type.ENTITY -> true
        else -> false
    }
}

/**
 * 数据持有器， 客服互通
 *
 * @see cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
 */
val Entity.dataHolder: DataHolder
    get() = DataHolderManager.getOrCreate(this)

/**
 * 临时缓存， 客服不互通
 */
val Entity.cacher: EntityCacher
    get() = if (level().isClientSide) ClientEntityCacheManager.getOrCreate(this) else ServerEntityCacheManager.getOrCreate(
        this
    )

fun Entity.intersectsCylinder(start: Vec3, end: Vec3, radius: Double): Boolean {
    return Math3DUtil.intersectsCylinder(start, end, radius, boundingBox)
}

fun Entity.intersectsCylinder(start: Vec3, direction: Vec3, length: Double, radius: Double): Boolean {
    return Math3DUtil.intersectsCylinder(start, start.add(direction.toCylinderOffset(length)), radius, boundingBox)
}

fun Entity.intersectsCylinder(start: Vec3, direction: RelativeLocation, length: Double, radius: Double): Boolean {
    return intersectsCylinder(start, direction.toVector(), length, radius)
}

infix fun Entity.intersectsBox(box: AABB): Boolean {
    return Math3DUtil.intersectsBox(boundingBox, box)
}

fun Entity.intersectsBox(center: Vec3, hitBox: HitBox): Boolean {
    return Math3DUtil.intersectsBox(center, hitBox, boundingBox)
}

inline fun <reified T : Entity> Entity.getEntitiesByClass(box: AABB, predicate: Predicate<T>) = let {
    level().getEntitiesOfClass<T>(T::class.java, box, predicate)
}

inline fun <reified T : Entity> Entity.getEntitiesByClass(size: Double, predicate: Predicate<T>) = let {
    level().getEntitiesOfClass<T>(T::class.java, boundingBox.inflate(size), predicate)
}

inline fun <reified T : Entity> Entity.getEntitiesByClass(box: HitBox, predicate: Predicate<T>) = let {
    level().getEntitiesOfClass<T>(T::class.java, box.ofBox(position()), predicate)
}

private fun Vec3.toCylinderOffset(length: Double): Vec3 {
    if (this.lengthSqr() <= 1e-12 || length <= 0.0) return Vec3.ZERO
    return this.normalize().scale(length)
}
