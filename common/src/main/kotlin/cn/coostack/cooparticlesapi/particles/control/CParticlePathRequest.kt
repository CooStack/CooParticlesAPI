package cn.coostack.cooparticlesapi.particles.control

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * # 待应用的路径位置约束
 *
 * 传统粒子路径（[cn.coostack.cooparticlesapi.particles.ControlableParticle]）的运动由发射器统一完成：
 * 命令在发射器运动**之前**执行，而默认实现会把位置写成 `当前位置 + 速度`。因此路径约束不能只在命令里
 * 写位置，必须提供一条在运动之后才生效的通道。
 *
 * 命令在 `execute` 中把求值结果放进 [ParticleControler.pendingPathRequest]，发射器在完成自己的
 * 位移与碰撞后读取并应用，从而保证“路径位置写入后不再叠加一次速度积分”。
 *
 * 实现类应当是**无状态**的：一个命令实例会被同一发射器的所有粒子共享，逐粒子结果由参数传入。
 */
interface CParticlePathRequest {
    /** 本请求是否包含有效的结果；返回 `false` 时发射器不会改写位置。 */
    fun isActive(): Boolean

    /** 路径给出的世界坐标位置。 */
    fun pathPosition(): Vec3

    /** 单位运动方向；不可用时返回 [Vec3.ZERO]。 */
    fun pathDirection(): Vec3

    /**
     * 本次推进是否跨越了播放周期边界。
     *
     * 循环跳转需要重置渲染插值的历史位置，否则会画出终点到起点的错误穿越轨迹。
     */
    fun pathWrapped(): Boolean

    /**
     * 本次是否到达路径终点且终点模式为“到达后消失”。
     *
     * 返回 `true` 时发射器应结束该粒子；是否真正结束仍由调用方的寿命与删除流程决定。
     */
    fun pathReachedEndAndDisappears(): Boolean

    /** 是否让粒子朝向对齐运动方向。 */
    fun pathFacesMotion(): Boolean

    /** 显式跟随时的 XYZ 欧拉角（弧度）；关闭或运动方向退化时返回 null。 */
    fun pathRotation(): Vector3f? = null
}
