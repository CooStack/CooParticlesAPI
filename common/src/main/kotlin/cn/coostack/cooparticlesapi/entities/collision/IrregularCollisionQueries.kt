package cn.coostack.cooparticlesapi.entities.collision

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.EntityGetter
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.Predicate

/**
 * 原版查询与不规则实体之间的公共适配入口，使用按生命周期维护的弱实体索引。
 * 由双方移动查询和射线 Mixin 调用，普通实体仍走原版算法。
 */
object IrregularCollisionQueries {
    /**
     * 查询对当前移动者有效的自定义碰撞，保留同载具与旁观者排除规则。
     * 示例：`append(level, player, query)`。
     * @param view 当前逻辑侧的世界实体视图
     * @param source 移动者，独立空间检查时可为空
     * @param query 原版请求的世界范围
     * @return 需要追加到原版结果的精确运动形状
     */
    @JvmStatic
    fun append(view: EntityGetter, source: Entity?, query: AABB): List<VoxelShape> =
        IrregularEntityIndex.find(view, source, query).filter {
            it.isAlive && !it.isSpectator &&
                (source == null || !source.isPassengerOfSameVehicle(it))
        }.flatMap { (it as IrregularCollisionEntity).collisionGeometry.collisionShapes(query) }

    /**
     * 找出可能参与射线的自定义实体；过滤器始终保留调用方的权限和目标规则。
     * 示例：`find(level, shooter, query, predicate)`。
     * @param view 实体所在世界
     * @param source 被排除的射线来源实体
     * @param query 原版宽相位范围
     * @param predicate 原版目标筛选条件
     * @return 包含空几何实体的候选，以便屏蔽其原版整框命中
     */
    @JvmStatic
    fun find(view: EntityGetter, source: Entity, query: AABB, predicate: Predicate<Entity>): List<Entity> =
        IrregularEntityIndex.find(view, source, query).filter { predicate.test(it) }.toMutableList()

    /**
     * 在原版命中与自定义精确命中间选择最近点。
     * 示例：`nearest(models, start, end, distanceSquared, vanilla)`。
     * @param models 已通过原版过滤器的自定义实体
     * @param start 世界起点
     * @param end 世界终点
     * @param maxDistanceSquared 方块遮挡或攻击范围给定的距离平方上限
     * @param vanilla 原版其他实体的命中，允许为空
     * @return 最近有效命中，全部未命中时为空
     */
    @JvmStatic
    fun nearest(models: List<Entity>, start: Vec3, end: Vec3, maxDistanceSquared: Double,
                vanilla: EntityHitResult?): EntityHitResult? {
        var result = vanilla
        var distance = vanilla?.location?.distanceToSqr(start) ?: maxDistanceSquared
        for (entity in models) {
            val point = (entity as IrregularCollisionEntity).selectionGeometry.raycast(start, end) ?: continue
            val candidateDistance = start.distanceToSqr(point)
            if (candidateDistance <= distance) {
                result = EntityHitResult(entity, point)
                distance = candidateDistance
            }
        }
        return result
    }
}
