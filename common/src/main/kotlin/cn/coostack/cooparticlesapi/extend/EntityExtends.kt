package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.data.cache.ClientEntityCacheManager
import cn.coostack.cooparticlesapi.data.cache.EntityCacher
import cn.coostack.cooparticlesapi.data.cache.ServerEntityCacheManager
import cn.coostack.cooparticlesapi.data.holder.DataHolder
import cn.coostack.cooparticlesapi.data.holder.DataHolderManager
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

fun Entity.canSee(another: Entity): Boolean {
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

fun Entity.canSee(to: Vec3): Boolean {
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

val Entity.dataHolder: DataHolder
    get() = DataHolderManager.getOrCreate(this)

val Entity.cacher: EntityCacher
    get() = if (level().isClientSide) ClientEntityCacheManager.getOrCreate(this) else ServerEntityCacheManager.getOrCreate(
        this
    )