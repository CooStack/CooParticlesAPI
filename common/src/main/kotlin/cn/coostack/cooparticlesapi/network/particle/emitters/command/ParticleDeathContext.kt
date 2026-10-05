package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import net.minecraft.world.phys.Vec3

/**
 * 普通粒子的死亡上下文或 CParticle 出生时的预计算上下文，不依赖粒子控制器。
 *
 * [data] 是独立的出生配置副本。preparingGpu=false 时运动字段为真实死亡快照；
 * preparingGpu=true 时它们只是出生参考，不能据此判断未来的死亡状态。
 * 回调只在客户端执行，不会把 Kotlin 闭包编码到网络。
 *
 * @property data 父粒子的出生配置副本，保留普通或 GPU data 类型
 * @property position CPU 真实死亡坐标；GPU 为预计算时的出生参考坐标
 * @property velocity CPU 真实死亡速度；GPU 为模板初速度，单位为方块每 tick
 * @property age CPU 死亡年龄；GPU 为模板初始年龄
 * @property reason CPU 真实移除原因；GPU 预计算固定为 LIFECYCLE，手动删除由 includeManualRemoval 控制
 * @property emitterPosition 本次执行配置时的发射器世界坐标
 * @property respawnCount 父粒子所属的重生代数；发射器直接产生的粒子为 0
 */
data class ParticleDeathContext(
    val data: ControlableParticleData,
    val position: Vec3,
    val velocity: Vec3,
    val age: Int,
    val reason: RemoveReason,
    val emitterPosition: Vec3,
    val respawnCount: Int,
    /** GPU 出生时预计算为 true；此时运动字段是出生参考，不能用于预测死亡状态。 */
    val preparingGpu: Boolean = false,
) {
    /**
     * 在指定世界位置重生，默认使用死亡位置并将模板年龄重置为 0。
     *
     * 示例：`respawn(template, position.add(0.0, 1.0, 0.0)) { maxAge = 40 }`。
     * 不自动继承父粒子的速度或外观，需要继承时在 [configure] 中显式指定。
     *
     * @param template 要克隆的普通或 GPU 粒子模板，不会修改原模板
     * @param position 固定世界坐标；null 表示在真实死亡位置出生，GPU 也在死亡时取位置
     * @param configure 修改独立的新粒子配置，可覆盖年龄、寿命、速度及外观
     * @param T 模板的具体类型，GPU 配置块可直接访问 alphaCurve 等专用字段
     * @return 待生成请求，由发射器统一处理可见距离和容量限制
     * @throws IllegalArgumentException 自定义 data 的 clone 未保留具体类型时抛出
     */
    fun <T : ControlableParticleData> respawn(
        template: T,
        position: Vec3? = null,
        configure: T.() -> Unit = {},
    ): ParticleRespawnRequest {
        val cloned = template.clone()
        require(template.javaClass.isInstance(cloned)) {
            "Particle data clone must preserve its concrete type: ${template.javaClass.name}"
        }
        // clone 的返回类型由旧 data API 决定；运行时验证后保留配置块的具体接收类型。
        @Suppress("UNCHECKED_CAST")
        val child = (cloned as T).apply {
            age = 0
            configure()
        }
        return ParticleRespawnRequest(
            child,
            position ?: Vec3.ZERO,
            relativeToDeath = position == null,
            ownsData = true,
        )
    }

    /**
     * 在真实死亡位置加世界空间偏移后生成，并可继承真实死亡速度。
     * CPU 在死亡时求值，GPU 在死亡时执行已上传的偏移和倍率，不调用 Kotlin。
     * @param template 子粒子模板
     * @param offset 相对死亡位置的世界空间偏移
     * @param inheritVelocity 模板速度之外继承的父速度倍率
     * @param configure CPU 死亡时或 GPU 出生预计算时执行的模板配置
     * @return 包含死亡位置偏移和速度继承倍率的独立请求
     */
    fun <T : ControlableParticleData> respawnAtDeath(
        template: T,
        offset: Vec3 = Vec3.ZERO,
        inheritVelocity: Double = 0.0,
        configure: T.() -> Unit = {},
    ): ParticleRespawnRequest = respawn(template, configure = configure).copy(
        position = offset, relativeToDeath = true, inheritVelocity = inheritVelocity,
    )
}
