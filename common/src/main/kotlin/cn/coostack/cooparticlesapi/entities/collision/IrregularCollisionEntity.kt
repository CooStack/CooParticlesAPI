package cn.coostack.cooparticlesapi.entities.collision

import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond

/**
 * 让 Minecraft 实体提供独立于索引 AABB 的精确碰撞和选取几何。
 *
 * 实现者须同时继承 Entity，在双方游戏线程提供当前世界坐标几何，
 * 并让 boundingBox 包含碰撞与选取范围。查询按已加载实体的真实范围执行，
 * 不限制几何距实体位置的距离。禁用原版整框碰撞和拥挤推挤，
 * 避免两套碰撞同时生效；本接口不接管实现者自身的运动、持久化或网络同步。
 * 结构实现展示了静态障碍物的完整生命周期；不提供刚体旋转或移动平台物理。
 */
interface IrregularCollisionEntity {
    /** 当前世界空间碰撞并集；死亡或失效时须返回空几何，不得返回过时的位置缓存。 */
    val collisionGeometry: CollisionGeometry

    /** 攻击及投射物选取并集，默认与碰撞一致；可包含无物理碰撞的装饰。 */
    val selectionGeometry: CollisionGeometry get() = collisionGeometry

    /** 默认粒子点集原点，通常为实体 position，而非结构放置锚点。 */
    val collisionOrigin: Vec3

    /** F3+B 显示的世界空间变换支点，默认使用实体原点。 */
    val collisionPivot: Vec3 get() = collisionOrigin

    /** F3+B 坐标轴旋转，返回调用方独占的四元数；坐标轴不接受模型缩放。 */
    val collisionRotation: Quaterniond get() = Quaterniond()

    /**
     * 从当前碰撞表面生成粒子点集，默认相对于实体原点，可直接交给 PointsBuilder 下游。
     * 必须在实体所属游戏线程调用；返回点不会自动跟踪之后的传送或变换。
     * 示例：`val points = model.sampleSurfacePoints(200).create()`。
     * @param count 所需点数，范围 0 到 100000
     * @param relativeTo 点集的世界参考原点，默认使用 collisionOrigin
     * @param maxAttempts 面积加权候选预算，不足时抛出异常，范围 0 到 10000000
     * @return 独立构建器；空碰撞几何返回空点集
     * @throws IllegalArgumentException 参数无效
     * @throws IllegalStateException 达到采样预算后点数仍不足
     */
    fun sampleSurfacePoints(count: Int, relativeTo: Vec3 = collisionOrigin, maxAttempts: Int = (count * 128).coerceAtMost(10000000)): PointsBuilder =
        collisionGeometry.sampleSurfacePoints(count, relativeTo, maxAttempts)
}
