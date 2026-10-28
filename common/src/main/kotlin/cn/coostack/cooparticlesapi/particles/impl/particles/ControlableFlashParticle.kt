package cn.coostack.cooparticlesapi.particles.impl.particles

import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableFlashEffect
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * 使用闪光动画帧的受控普通粒子。
 * @property provider 由原版资源系统维护的动态图集帧集
 */
class ControlableFlashParticle(
    world: ClientLevel,
    pos: Vec3,
    velocity: Vec3,
    controlUUID: UUID,
    faceToCamera: Boolean,
    val provider: SpriteSet
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

    /**
     * 将闪光选项转换为受控粒子。
     * @property provider 原版注册回调提供的动态图集帧集
     */
    class Factory(val provider: SpriteSet) : ParticleProvider<ControlableFlashEffect> {
        /** 接受 26.3 引擎随机源；动画帧仍由控制器年龄决定。 */
        override fun createParticle(
            parameters: ControlableFlashEffect,
            world: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            velocityX: Double,
            velocityY: Double,
            velocityZ: Double,
            random: RandomSource,
        ): Particle {
            return ControlableFlashParticle(
                world,
                Vec3(x, y, z),
                Vec3(velocityX, velocityY, velocityZ),
                parameters.controlUUID,
                parameters.faceToPlayer,
                provider
            )
        }
    }

}
