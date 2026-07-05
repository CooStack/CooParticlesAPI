package cn.coostack.cooparticlesapi.test.api

import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import java.util.UUID

class ControlableParticleEffectBuilder(
    val id: String,
    private val factory: (UUID, Boolean) -> ControlableParticleEffect
) {
    fun build(uuid: UUID, faceToPlayer: Boolean = true): ControlableParticleEffect {
        return factory(uuid, faceToPlayer).also { effect ->
            effect.controlUUID = uuid
        }
    }

    override fun toString(): String {
        return id
    }
}
