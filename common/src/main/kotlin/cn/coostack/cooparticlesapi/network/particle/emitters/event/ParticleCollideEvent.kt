package cn.coostack.cooparticlesapi.network.particle.emitters.event

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.BlockHitResult

/**
 * 粒子碰撞方块事件
 * 用于复用物理粒子
 */
class ParticleCollideEvent(
    override var particle: ControlableParticle,
    override var particleData: ControlableParticleData,
    var res: BlockHitResult
) : ParticleEvent {
    override var canceled: Boolean = false

    companion object {
        const val EVENT_ID = "ParticleColliderEvent"
    }

    override fun getEventID(): String {
        return EVENT_ID
    }
}