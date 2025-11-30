package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.extend.asAbs
import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.function.Predicate

object PhysicsUtil {
    /**
     * 如果粒子移动前没碰撞，然后移动后碰撞，可以调用此方法修正碰撞位置
     * 否则粒子卡在了方块内，则不适用 (会反向弹出罢)
     *
     * @param res 碰撞结果
     * @return 修正后的位置(恰好擦边)
     */
    fun fixBeforeCollidePosition(res: BlockHitResult): Vec3 {
        val offset = res.direction.normal.asVec3()
        return res.location + offset.normalize() * 0.07
    }

    fun collide(currentPos: Vec3, velocity: Vec3, world: Level): BlockHitResult {
        val context = ClipContext(
            currentPos, currentPos + velocity, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
            CollisionContext.empty()
        )
        return world.clip(context)
    }

    fun collide(currentPos: Vec3, velocity: Vec3, world: Level, entity: Entity): BlockHitResult {
        val context = ClipContext(
            currentPos, currentPos + velocity, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity
        )
        return world.clip(context)
    }


    /**
     * @param currentPos 当前位置
     * @param velocity 移动方向
     * @param world 移动产生的世界
     *
     * @return 物理模拟运动后的移动方向
     */
    fun collideMovement(currentPos: Vec3, velocity: Vec3, world: Level): Vec3 {
        val next = currentPos + velocity

        val context = ClipContext(
            currentPos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()
        )
        val clip = world.clip(context)
        return collideMovement(clip, velocity)
    }


    /**
     * @param res 碰撞的结果
     * @param velocity 碰撞之前的移动方向
     * @return 物理模拟运动后的移动方向
     */
    fun collideMovement(res: BlockHitResult, velocity: Vec3): Vec3 {
        val normal = res.direction.normal.asVec3()
        val mulNormal = normal * velocity.asAbs()
        return velocity + mulNormal // normal运动方向本身就是反向的 所以需要相加消除
    }


    /**
     * 射线实体检测，当pos尝试移动时进行碰撞检测
     *
     * @param currentPos 当前位置
     * @param velocity 移动速度
     * @param world 当前世界
     * @param ignoreBlock 是否屏蔽方块检测
     * @return
     */
    fun rayCast(
        currentPos: Vec3,
        velocity: Vec3,
        world: Level,
        entityPredicate: Predicate<LivingEntity>,
        ignoreBlock: Boolean
    ): HitResult? {
        val entityHit = getEntityHitResult(world, currentPos, currentPos + velocity, entityPredicate)

        if (ignoreBlock) {
            return entityHit
        }
        val blockHit = collide(currentPos, velocity, world)

        return if (entityHit != null) {
            if (blockHit.location.distanceTo(currentPos) > entityHit.location.distanceTo(currentPos)) {
                entityHit
            } else {
                blockHit
            }
        } else {
            blockHit
        }
    }


    private fun getEntityHitResult(
        world: Level,
        start: Vec3,
        end: Vec3,
        predicate: Predicate<LivingEntity>
    ): EntityHitResult? {
        var searchEntity: LivingEntity? = null
        var searchPos: Vec3? = null
        val searchBox = AABB.ofSize(start, 2.0, 2.0, 2.0).expandTowards((start + end) * 3.0).inflate(1.0)
        var currentDistance = 0.0
        for (entity in world.getEntitiesOfClass(LivingEntity::class.java, searchBox, predicate)) {
            val entityBox = entity.boundingBox.inflate(entity.pickRadius.toDouble())
            val clip = entityBox.clip(start, end)
            if (entityBox.contains(start)) {
                searchEntity = entity
                searchPos = clip.orElse(start)
            } else if (clip.isPresent) {
                val p = clip.get()
                val distance = start.distanceToSqr(p)
                if (distance < currentDistance || currentDistance == 0.0) {
                    searchEntity = entity
                    searchPos = p
                    currentDistance = distance
                }
            }
        }

        return if (searchEntity == null) null else EntityHitResult(searchEntity, searchPos!!)
    }

}