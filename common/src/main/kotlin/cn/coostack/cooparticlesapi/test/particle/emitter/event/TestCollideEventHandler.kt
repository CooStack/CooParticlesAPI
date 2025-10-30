package cn.coostack.cooparticlesapi.test.particle.emitter.event

import cn.coostack.cooparticlesapi.extend.asAbs
import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleCollideEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.utils.PhysicsUtil

object TestCollideEventHandler : ParticleEventHandler {
    override fun handle(event: ParticleEvent) {
        if (event !is ParticleCollideEvent) {
            return
        }
        if (event.particleData.velocity.length() <= 1e-6) {
            event.canceled = true
            return
        }
        val pos = PhysicsUtil.fixBeforeCollidePosition(event.res)
        event.particle.teleportTo(pos)
        val movement = PhysicsUtil.collideMovement(
            event.res,
            event.particleData.velocity
        )
        val data = event.particleData
        val vec = event.res.direction.normal.asVec3()

        data.velocity = movement
    }

    override fun getTargetEventID(): String {
        return ParticleCollideEvent.EVENT_ID
    }

    override fun getHandlerID(): String {
        return "testCollideEventHandler"
    }

    override fun getPriority(): Int {
        return 1
    }
}