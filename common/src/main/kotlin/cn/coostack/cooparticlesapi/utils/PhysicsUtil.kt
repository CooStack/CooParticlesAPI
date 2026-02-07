package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.extend.asAbs
import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.extend.minus
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
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

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
     * 计算“吸引力运动”下，下一个 tick 应该使用的速度。
     *
     * - 若本 tick 内路径会跨越/命中目标（半径 arriveRadius），返回 null
     *
     * @param currentPos 当前tick的位置
     * @param currentVelocity 当前tick的速度
     * @param targetPos 吸引源坐标
     * @param dt 速度变化间隔 即间隔多少个tick执行 v = nextAttractVelocity()
     * @param subSteps 子tick数量 （如果速度/吸引力很大，可以加大运算步骤，避免漏判）
     * @param strength 吸引力大小
     * @param falloffPow 吸引力变化指数常数 (计算: accMag = strength * distance ^ falloffPow)
     * @param damping 阻尼系数 防止过近时的抖动
     * @param maxAccel 最大加速度
     * @param maxSpeed 最大移动速度
     * @param arriveRadius 命中半径 (当currentPos与targetPos距离小于这个值时 则判定命中/经过)
     * @return null 命中目标
     */
    fun nextAttractVelocityNullable(
        currentPos: Vec3,
        currentVelocity: Vec3,
        targetPos: Vec3,
        dt: Double = 1.0,
        subSteps: Int = 8,
        strength: Double = 0.35,
        falloffPow: Int = 2,
        damping: Double = 0.06,
        maxAccel: Double = 3.0,
        maxSpeed: Double = 4.0,
        arriveRadius: Double = 0.25
    ): Vec3? {
        val next = nextAttractVelocity(
            currentPos,
            currentVelocity,
            targetPos,
            dt,
            subSteps,
            strength,
            falloffPow,
            damping,
            maxAccel,
            maxSpeed,
            arriveRadius
        )
        return if (next.lengthSqr() <= 1e-6) null else next
    }

    /**
     * 计算“吸引力运动”下，下一个 tick 应该使用的速度。
     *
     * - 若本 tick 内路径会跨越/命中目标（半径 arriveRadius），返回 Vec3.ZERO
     *
     * @param currentPos 当前tick的位置
     * @param currentVelocity 当前tick的速度
     * @param targetPos 吸引源坐标
     * @param dt 速度变化间隔 即间隔多少个tick执行 v = nextAttractVelocity()
     * @param subSteps 子tick数量 （如果速度/吸引力很大，可以加大运算步骤，避免漏判）
     * @param strength 吸引力大小
     * @param falloffPow 吸引力变化指数常数 (计算: accMag = strength * distance ^ falloffPow)
     * @param damping 阻尼系数 防止过近时的抖动
     * @param maxAccel 最大加速度
     * @param maxSpeed 最大移动速度
     * @param arriveRadius 命中半径 (当currentPos与targetPos距离小于这个值时 则判定命中/经过)
     */
    fun nextAttractVelocity(
        currentPos: Vec3,
        currentVelocity: Vec3,
        targetPos: Vec3,
        dt: Double = 1.0,
        subSteps: Int = 8,
        strength: Double = 0.35,
        falloffPow: Int = 2,
        damping: Double = 0.06,
        maxAccel: Double = 3.0,
        maxSpeed: Double = 4.0,
        arriveRadius: Double = 0.25
    ): Vec3 {
        val dtAll = if (dt.isFinite() && dt > 0.0) dt else 0.0
        if (dtAll == 0.0) return currentVelocity

        val steps = subSteps.coerceIn(1, 64)
        val h = dtAll / steps.toDouble()

        // arriveRadius==0 时给一个极小容差
        val eps = 1e-6
        val rEff = max(arriveRadius, 0.0).let { if (it == 0.0) eps else it }

        val maxA = max(0.0, maxAccel)
        val maxV = max(0.0, maxSpeed)

        var p = currentPos
        var v = currentVelocity

        // 如果起点已经在命中半径内，直接停
        if (segmentIntersectsSphere(p, p, targetPos, rEff)) return Vec3.ZERO

        val powN = falloffPow.coerceAtLeast(0)

        repeat(steps) {
            val to = targetPos - p
            val distSq = max(1e-12, to.dot(to))
            val dist = sqrt(distSq)

            val dir = to.normalize()

            // accMag = strength / (dist ^ powN)
            val denom = when (powN) {
                0 -> 1.0
                1 -> dist
                2 -> distSq
                3 -> distSq * dist
                4 -> distSq * distSq
                else -> dist.pow(powN.toDouble())
            }.coerceAtLeast(1e-12)

            val accMag = strength / denom

            var a = dir * accMag - v * damping
            a = clampLen(a, maxA)

            v += a * h
            v = clampLen(v, maxV)

            val nextP = p + v * h

            // 线段与球相交判定
            if (segmentIntersectsSphere(p, nextP, targetPos, rEff)) return Vec3.ZERO

            p = nextP
        }

        return v
    }

    /**
     * 最准确的越过/命中判定：线段 [a,b] 与球 (center, radius) 是否相交
     * 允许 a==b 的退化情况
     */
    private fun segmentIntersectsSphere(a: Vec3, b: Vec3, center: Vec3, radius: Double): Boolean {
        val r = max(0.0, radius)
        val r2 = r * r

        val d = b - a          // 线段方向
        val f = a - center     // 从球心到线段起点

        val A = d.dot(d)
        if (A <= 1e-18) {
            // 退化为点
            return f.dot(f) <= r2
        }

        val B = 2.0 * f.dot(d)
        val C = f.dot(f) - r2

        val disc = B * B - 4.0 * A * C
        if (disc < 0.0) return false

        val sqrtDisc = sqrt(disc)
        val inv2A = 1.0 / (2.0 * A)

        val t1 = (-B - sqrtDisc) * inv2A
        val t2 = (-B + sqrtDisc) * inv2A

        return (t1 in 0.0..1.0) || (t2 in 0.0..1.0)
    }

    private fun clampLen(v: Vec3, maxLen: Double): Vec3 {
        if (maxLen <= 0.0) return Vec3.ZERO
        val len2 = v.dot(v)
        val m2 = maxLen * maxLen
        if (!len2.isFinite() || len2 <= m2) return v
        val inv = maxLen / sqrt(len2)
        return v * inv
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