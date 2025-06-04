package cn.coostack.cooparticlesapi.test.particle.emitter.event

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleHitEntityEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnGroundEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnLiquidEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Vec3d
import kotlin.math.exp
import kotlin.random.Random

object TestEntityHitEventHandler : ParticleEventHandler {
    val random = Random(System.currentTimeMillis())
    override fun handle(event: ParticleEvent) {
        if (event !is ParticleHitEntityEvent) return
        if (event.hit is LivingEntity) {
            event.particleData.velocity = Vec3d(
                random.nextDouble(-1.0,1.0),
                1.0,
                random.nextDouble(-1.0,1.0),
            ).normalize()
        }
    }

    override fun getTargetEventID(): String {
        return ParticleHitEntityEvent.EVENT_ID
    }

    override fun getHandlerID(): String {
        return "TestEntityHitEventHandler"
    }

    override fun getPriority(): Int {
        return 1
    }
}