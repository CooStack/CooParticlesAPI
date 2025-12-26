package cn.coostack.cooparticlesapi.test.options.particle.emitter.event

import cn.coostack.cooparticlesapi.extend.asAbs
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnGroundEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import cn.coostack.cooparticlesapi.utils.PhysicsUtil
import net.minecraft.world.phys.Vec3
import kotlin.math.exp

object TestOnGroundEventHandler : ParticleEventHandler {
    override fun handle(event: ParticleEvent) {
        if (event !is ParticleOnGroundEvent) return
        val movement = PhysicsUtil.collideMovement(
            event.res,
            event.particleData.velocity
        )
        val data = event.particleData
        event.particle.teleportTo(event.intersection)
        val vec = event.res.direction.normal.asVec3()

        data.velocity = data.velocity.asAbs() * vec + movement
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