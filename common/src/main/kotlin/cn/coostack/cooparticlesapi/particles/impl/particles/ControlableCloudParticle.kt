package cn.coostack.cooparticlesapi.particles.impl.particles

import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.world.phys.Vec3
import java.util.UUID

class ControlableCloudParticle(
    world: ClientLevel,
    pos: Vec3,
    velocity: Vec3,
    controlUUID: UUID,
    faceToCamera: Boolean,
    val provider: SpriteSet,
) :
    ControlableParticle(world, pos, velocity, controlUUID, faceToCamera) {

    init {
        setSprite(provider.get(age, lifetime))
        controler.addPreTickAction {
            setSpriteFromAge(provider)
        }
    }

    init {
        setSprite(provider.get(age, lifetime))
        controler.addPreTickAction {
            setSpriteFromAge(provider)
        }
    }

    class Factory(val provider: SpriteSet) : ParticleProvider<ControlableCloudEffect> {
        override fun createParticle(
            parameters: ControlableCloudEffect,
            world: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            velocityX: Double,
            velocityY: Double,
            velocityZ: Double
        ): Particle {
            return ControlableCloudParticle(
                world,
                Vec3(x, y, z),
                Vec3(velocityX, velocityY, velocityZ),
                parameters.controlUUID,
                parameters.faceToPlayer,
                provider,
            )
        }
    }

}