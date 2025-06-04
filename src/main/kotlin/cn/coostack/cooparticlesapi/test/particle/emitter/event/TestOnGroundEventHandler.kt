package cn.coostack.cooparticlesapi.test.particle.emitter.event

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnGroundEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import net.minecraft.util.math.Vec3d
import kotlin.math.exp

object TestOnGroundEventHandler : ParticleEventHandler {
    override fun handle(event: ParticleEvent) {
        if (event !is ParticleOnGroundEvent) return
        event.particleData.velocity = Vec3d(
            event.particleData.velocity.x,
            -event.particleData.velocity.y,
            event.particleData.velocity.z,
        )
//        val explode = ExplodeClassParticleEmitters(event.particle.pos, event.particle.clientWorld)
//            .apply {
//                maxTick = 1
//            }
//        ParticleEmittersManager.addEmitters(explode)
    }

    override fun getTargetEventID(): String {
        return ParticleOnGroundEvent.EVENT_ID
    }

    override fun getHandlerID(): String {
        return "TestOnGroundEventHandler"
    }

    override fun getPriority(): Int {
        return 1
    }
}