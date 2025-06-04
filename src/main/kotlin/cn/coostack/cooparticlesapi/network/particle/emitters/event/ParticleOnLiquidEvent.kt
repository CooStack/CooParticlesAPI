package cn.coostack.cooparticlesapi.network.particle.emitters.event

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos

/**
 * 粒子接触实体事件
 */
class ParticleOnLiquidEvent(
    override var particle: ControlableParticle,
    override var particleData: ControlableParticleData,
    var hit: BlockPos
) : ParticleEvent {
    override var canceled: Boolean = false

    companion object {
        const val EVENT_ID = "ParticleOnLiquidEvent"
    }

    override fun getEventID(): String {
        return EVENT_ID
    }
}