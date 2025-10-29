package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.extend.asAbs
import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

object PhysicsUtil {

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
        val normal = clip.direction.normal.asVec3()
        val mulNormal = normal * velocity.asAbs()
        return velocity + mulNormal // normal运动方向本身就是反向的 所以需要相加消除
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
}