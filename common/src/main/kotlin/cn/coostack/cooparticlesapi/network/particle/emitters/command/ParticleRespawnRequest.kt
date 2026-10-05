package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import net.minecraft.world.phys.Vec3

/**
 * 与粒子种类无关的一次重生请求。提交时克隆配置，允许安全地重复使用同一模板。
 *
 * 示例：`ParticleRespawnRequest(template, death.position)`；需要重新开始年龄时可用
 * [ParticleDeathContext.respawn]。本类型直接提交时保留 [data] 中显式设置的初始年龄。
 * 普通和 GPU 父粒子都可选择任一种 data；配置方式相同。
 *
 * @property data 新粒子的完整配置；使用 ControlableCParticleData 时继续走 GPU 路径
 * @property position 固定世界坐标或 relativeToDeath=true 时的死亡位置偏移，三个分量必须有限
 */
data class ParticleRespawnRequest(
    val data: ControlableParticleData,
    val position: Vec3,
    /** 为 true 时 position 表示相对实际死亡位置的世界空间偏移。 */
    val relativeToDeath: Boolean = false,
    /** 在模板速度上叠加父粒子实际死亡速度的倍率；默认不继承。 */
    val inheritVelocity: Double = 0.0,
    /**
     * 由 [ParticleDeathContext.respawn] 创建的独立 data。
     *
     * GPU 出生预计算时可直接复用这份 clone；直接构造请求仍按旧语义再次复制，
     * 避免用户把 emitter 模板对象交给后继链后被修改。
     */
    internal val ownsData: Boolean = false,
) {
    init {
        require(inheritVelocity.isFinite()) { "Inherited velocity factor must be finite" }
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite()) {
            "Respawn position must be finite: $position"
        }
    }
}
