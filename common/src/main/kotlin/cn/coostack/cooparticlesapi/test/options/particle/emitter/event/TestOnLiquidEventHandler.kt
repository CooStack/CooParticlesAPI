package cn.coostack.cooparticlesapi.test.options.particle.emitter.event

import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnLiquidEvent
import net.minecraft.world.phys.Vec3
import kotlin.math.exp
import kotlin.random.Random

object TestOnLiquidEventHandler : ParticleEventHandler {
    val random = Random(System.currentTimeMillis())
    override fun handle(event: ParticleEvent) {
        if (event !is ParticleOnLiquidEvent) return
        event.particleData.velocity = Vec3(
            random.nextDouble(-1.0, 1.0),
            random.nextDouble(-1.0, 1.0),
            random.nextDouble(-1.0, 1.0),
        )
    }

    override fun getTargetEventID(): String {
        return ParticleOnLiquidEvent.EVENT_ID
    }

    override fun getHandlerID(): String {
        return "TestOnLiquidEventHandler"
    }

    override fun getPriority(): Int {
        return 1
    }
}