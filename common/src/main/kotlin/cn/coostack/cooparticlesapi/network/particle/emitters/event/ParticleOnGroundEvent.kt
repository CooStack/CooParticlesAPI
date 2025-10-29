package cn.coostack.cooparticlesapi.network.particle.emitters.event

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * 粒子接触地面事件
 */
class ParticleOnGroundEvent(
    override var particle: ControlableParticle,
    override var particleData: ControlableParticleData,
    /**
     * 击中的方块
     */
    var hit: BlockPos,
    /**
     * 和方块的交点
     */
    var intersection: Vec3,
    var res: BlockHitResult
) : ParticleEvent {
    override var canceled: Boolean = false

    companion object {
        const val EVENT_ID = "ParticleOnGroundEvent"
    }

    override fun getEventID(): String {
        return EVENT_ID
    }
}