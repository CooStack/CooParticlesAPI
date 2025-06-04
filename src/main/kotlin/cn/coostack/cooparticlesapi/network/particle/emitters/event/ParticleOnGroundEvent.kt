package cn.coostack.cooparticlesapi.network.particle.emitters.event

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.util.math.BlockPos

/**
 * 粒子接触地面事件
 */
class ParticleOnGroundEvent(
    override var particle: ControlableParticle,
    override var particleData: ControlableParticleData,
    var hit: BlockPos
) : ParticleEvent {
    override var canceled: Boolean = false

    companion object {
        const val EVENT_ID = "ParticleOnGroundEvent"
    }

    override fun getEventID(): String {
        return EVENT_ID
    }
}